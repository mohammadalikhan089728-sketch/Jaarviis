package com.example.tools

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.data.JarvisPreferencesRepository
import com.example.security.PrivacyGuard
import com.example.services.AccessibilityBridge
import com.example.services.JarvisAccessibilityService
import com.example.services.JarvisNotificationService
import com.example.services.NotificationBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class PermissionStatusSnapshot(
    val accessibilityEnabled: Boolean = false,
    val notificationAccessEnabled: Boolean = false,
    val callPhoneGranted: Boolean = false,
    val sendSmsGranted: Boolean = false,
    val recordAudioGranted: Boolean = false,
    val hasFlashlight: Boolean = false,
    val flashlightEnabled: Boolean = false,
    val mediaVolumePercent: Int = 50
)

data class ToolExecutionResult(
    val toolName: String,
    val success: Boolean,
    val jsonResponse: JSONObject,
    val summary: String
)

class DeviceToolExecutor(
    private val context: Context,
    private val prefsRepo: JarvisPreferencesRepository,
    private val onClearChatContext: suspend () -> Unit,
    private val getConversationSummaryText: () -> String
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val _deviceStatus = MutableStateFlow(PermissionStatusSnapshot())
    val deviceStatus: StateFlow<PermissionStatusSnapshot> = _deviceStatus.asStateFlow()

    private val torchCameraId: String? by lazy {
        try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val lens = chars.get(CameraCharacteristics.LENS_FACING)
                hasFlash && lens == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
    }

    private var trackedTorchState = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                trackedTorchState = enabled
                refreshStatus()
            }
        }
    }

    init {
        try {
            cameraManager.registerTorchCallback(torchCallback, null)
        } catch (_: Exception) {
            // Safe fallback if camera service unavailable in headless test environments
        }
        refreshStatus()
    }

    fun unregister() {
        try {
            cameraManager.unregisterTorchCallback(torchCallback)
        } catch (_: Exception) {
        }
    }

    fun refreshStatus(): PermissionStatusSnapshot {
        val snapshot = PermissionStatusSnapshot(
            accessibilityEnabled = isAccessibilityServiceEnabled(),
            notificationAccessEnabled = isNotificationListenerEnabled(),
            callPhoneGranted = hasPermission(Manifest.permission.CALL_PHONE),
            sendSmsGranted = hasPermission(Manifest.permission.SEND_SMS),
            recordAudioGranted = hasPermission(Manifest.permission.RECORD_AUDIO),
            hasFlashlight = torchCameraId != null,
            flashlightEnabled = trackedTorchState,
            mediaVolumePercent = getMediaVolumePercent()
        )
        _deviceStatus.value = snapshot
        return snapshot
    }

    fun getMediaVolumePercent(): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return ((current * 100f) / max).toInt().coerceIn(0, 100)
    }

    fun setMediaVolumePercent(percent: Int): Pair<Boolean, Int> {
        val clamped = percent.coerceIn(0, 100)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val targetIndex = ((clamped / 100f) * max).toInt().coerceIn(0, max)
        return try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
            refreshStatus()
            true to getMediaVolumePercent()
        } catch (_: Exception) {
            false to getMediaVolumePercent()
        }
    }

    fun setFlashlight(enabled: Boolean): Pair<Boolean, String> {
        val id = torchCameraId ?: return false to "No hardware flashlight found on this device."
        return try {
            cameraManager.setTorchMode(id, enabled)
            trackedTorchState = enabled
            refreshStatus()
            true to if (enabled) "Flashlight turned ON." else "Flashlight turned OFF."
        } catch (e: Exception) {
            false to "Unable to toggle flashlight: ${e.localizedMessage ?: "hardware error"}"
        }
    }

    suspend fun executeTool(name: String, args: JSONObject): ToolExecutionResult {
        return when (name) {
            "read_screen_content" -> execReadScreen(args)
            "click_ui_element" -> execClickElement(args)
            "scroll_screen" -> execScroll(args)
            "perform_global_action" -> execGlobalAction(args)
            "read_notifications" -> execReadNotifications(args)
            "send_sms" -> execSendSms(args)
            "send_whatsapp_message" -> execSendWhatsApp(args)
            "initiate_phone_call" -> execPhoneCall(args)
            "get_device_state" -> execGetDeviceState()
            "set_media_volume" -> execSetVolume(args)
            "set_flashlight" -> execSetFlashlight(args)
            "manage_conversation_context" -> execManageContext(args)
            else -> ToolExecutionResult(
                toolName = name,
                success = false,
                jsonResponse = JSONObject()
                    .put("status", "unknown_tool")
                    .put("error", "Tool '$name' is not declared."),
                summary = "Unknown tool '$name'"
            )
        }
    }

    private fun execReadScreen(args: JSONObject): ToolExecutionResult {
        if (!isAccessibilityServiceEnabled() || !AccessibilityBridge.isConnected.value) {
            return permissionError(
                toolName = "read_screen_content",
                status = "service_disabled",
                message = "Accessibility Service 'Jarvis Screen & UI Controller' is disabled. Ask user to enable it in Android Settings -> Accessibility."
            )
        }
        val includeNonClickable = args.optBoolean("include_non_clickable", true)
        val snapshot = AccessibilityBridge.readScreen(includeNonClickable)
            ?: return permissionError(
                toolName = "read_screen_content",
                status = "unavailable",
                message = "Active window root is temporarily unavailable."
            )

        val nodesJson = JSONArray()
        snapshot.nodes.forEach { n ->
            nodesJson.put(
                JSONObject().apply {
                    put("index", n.index)
                    put("class", n.className)
                    if (n.text.isNotEmpty()) put("text", n.text)
                    if (n.contentDescription.isNotEmpty()) put("content_description", n.contentDescription)
                    if (n.viewId.isNotEmpty()) put("view_id", n.viewId)
                    put("clickable", n.clickable)
                    put("scrollable", n.scrollable)
                    if (n.isSensitiveRedacted) put("redacted", true)
                }
            )
        }
        val json = JSONObject().apply {
            put("status", "ok")
            put("active_package", snapshot.packageName)
            put("node_count", snapshot.nodes.size)
            put("redacted_sensitive_fields", snapshot.redactedCount)
            put("elements", nodesJson)
        }
        return ToolExecutionResult(
            toolName = "read_screen_content",
            success = true,
            jsonResponse = json,
            summary = "Captured ${snapshot.nodes.size} nodes (${snapshot.redactedCount} redacted)"
        )
    }

    private fun execClickElement(args: JSONObject): ToolExecutionResult {
        if (!isAccessibilityServiceEnabled() || !AccessibilityBridge.isConnected.value) {
            return permissionError(
                toolName = "click_ui_element",
                status = "service_disabled",
                message = "Accessibility Service is disabled. Enable it in Android Settings -> Accessibility to click UI elements."
            )
        }
        val targetText = args.optString("target_text").takeIf { it.isNotBlank() }
        val desc = args.optString("content_description").takeIf { it.isNotBlank() }
        val viewId = args.optString("view_id").takeIf { it.isNotBlank() }
        val matchIndex = args.optInt("match_index", 0)

        val (ok, msg) = AccessibilityBridge.clickElement(targetText, desc, viewId, matchIndex)
        val json = JSONObject().apply {
            put("status", if (ok) "ok" else "not_found")
            put("message", msg)
        }
        return ToolExecutionResult("click_ui_element", ok, json, msg)
    }

    private fun execScroll(args: JSONObject): ToolExecutionResult {
        if (!isAccessibilityServiceEnabled() || !AccessibilityBridge.isConnected.value) {
            return permissionError(
                toolName = "scroll_screen",
                status = "service_disabled",
                message = "Accessibility Service is disabled. Enable it in Android Settings -> Accessibility."
            )
        }
        val direction = args.optString("direction", "down")
        val (ok, msg) = AccessibilityBridge.scroll(direction)
        return ToolExecutionResult(
            toolName = "scroll_screen",
            success = ok,
            jsonResponse = JSONObject().put("status", if (ok) "ok" else "failed").put("message", msg),
            summary = msg
        )
    }

    private fun execGlobalAction(args: JSONObject): ToolExecutionResult {
        if (!isAccessibilityServiceEnabled() || !AccessibilityBridge.isConnected.value) {
            return permissionError(
                toolName = "perform_global_action",
                status = "service_disabled",
                message = "Accessibility Service is disabled. Enable it in Android Settings -> Accessibility."
            )
        }
        val action = args.optString("action", "back")
        val (ok, msg) = AccessibilityBridge.globalAction(action)
        return ToolExecutionResult(
            toolName = "perform_global_action",
            success = ok,
            jsonResponse = JSONObject().put("status", if (ok) "ok" else "failed").put("message", msg),
            summary = msg
        )
    }

    private fun execReadNotifications(args: JSONObject): ToolExecutionResult {
        if (!isNotificationListenerEnabled()) {
            return permissionError(
                toolName = "read_notifications",
                status = "service_disabled",
                message = "Notification Access is disabled. Ask user to enable 'Jarvis Notification Reader' in Android Settings -> Notification Access."
            )
        }
        val limit = args.optInt("limit", 10).coerceIn(1, 20)
        val items = NotificationBridge.notifications.value.take(limit)
        val arr = JSONArray()
        items.forEach { n ->
            arr.put(
                JSONObject().apply {
                    put("package", n.packageName)
                    put("title", n.title)
                    put("text", n.text)
                    put("was_redacted", n.wasRedacted)
                }
            )
        }
        val json = JSONObject().apply {
            put("status", "ok")
            put("count", items.size)
            put("notifications", arr)
        }
        return ToolExecutionResult(
            toolName = "read_notifications",
            success = true,
            jsonResponse = json,
            summary = "Read ${items.size} redacted notifications"
        )
    }

    private fun execSendSms(args: JSONObject): ToolExecutionResult {
        val number = args.optString("phone_number").trim()
        val rawBody = args.optString("message").trim()
        if (number.isEmpty() || rawBody.isEmpty()) {
            return permissionError("send_sms", "invalid_arguments", "Both phone_number and message are required.")
        }

        val redacted = PrivacyGuard.redactSensitiveText(rawBody)
        if (redacted.redactedCount > 0) {
            return permissionError(
                "send_sms",
                "privacy_blocked",
                "Blocked sending SMS containing potential OTP, PIN, password, or card details."
            )
        }

        val composerOnly = args.optBoolean("open_composer_only", true)
        if (!composerOnly && hasPermission(Manifest.permission.SEND_SMS)) {
            return try {
                @Suppress("DEPRECATION")
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    SmsManager.getDefault()
                }
                smsManager.sendTextMessage(number, null, rawBody, null, null)
                val msg = "SMS sent directly to $number."
                ToolExecutionResult(
                    "send_sms",
                    true,
                    JSONObject().put("status", "ok").put("mode", "direct_sms").put("message", msg),
                    msg
                )
            } catch (e: Exception) {
                openSmsComposer(number, rawBody, "Direct SMS failed (${e.localizedMessage}); opened SMS composer instead.")
            }
        }

        return openSmsComposer(number, rawBody, "Opened SMS composer for $number.")
    }

    private fun openSmsComposer(number: String, body: String, successMsg: String): ToolExecutionResult {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:${Uri.encode(number)}")
                putExtra("sms_body", body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult(
                "send_sms",
                true,
                JSONObject().put("status", "ok").put("mode", "composer_intent").put("message", successMsg),
                successMsg
            )
        } catch (e: Exception) {
            permissionError("send_sms", "no_sms_app", "No SMS app available: ${e.localizedMessage}")
        }
    }

    private fun execSendWhatsApp(args: JSONObject): ToolExecutionResult {
        val number = args.optString("phone_number").trim()
        val rawBody = args.optString("message").trim()
        if (rawBody.isEmpty()) {
            return permissionError("send_whatsapp_message", "invalid_arguments", "Message text is required.")
        }

        val redacted = PrivacyGuard.redactSensitiveText(rawBody)
        if (redacted.redactedCount > 0) {
            return permissionError(
                "send_whatsapp_message",
                "privacy_blocked",
                "Blocked sending WhatsApp message containing potential OTP, PIN, password, or card details."
            )
        }

        val cleanDigits = number.filter { it.isDigit() }
        val encodedText = URLEncoder.encode(rawBody, "UTF-8")

        return try {
            if (cleanDigits.isNotEmpty()) {
                val url = "https://api.whatsapp.com/send?phone=$cleanDigits&text=$encodedText"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                val msg = "Opened WhatsApp chat for +$cleanDigits with message pre-filled."
                ToolExecutionResult(
                    "send_whatsapp_message",
                    true,
                    JSONObject().put("status", "ok").put("mode", "whatsapp_deeplink").put("message", msg),
                    msg
                )
            } else {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, rawBody)
                    `package` = "com.whatsapp"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                val msg = "Opened WhatsApp contact chooser with message pre-filled."
                ToolExecutionResult(
                    "send_whatsapp_message",
                    true,
                    JSONObject().put("status", "ok").put("mode", "whatsapp_share").put("message", msg),
                    msg
                )
            }
        } catch (_: Exception) {
            permissionError(
                "send_whatsapp_message",
                "app_not_installed",
                "WhatsApp is not installed or could not handle the intent."
            )
        }
    }

    private fun execPhoneCall(args: JSONObject): ToolExecutionResult {
        val number = args.optString("phone_number").trim()
        if (number.isEmpty()) {
            return permissionError("initiate_phone_call", "invalid_arguments", "phone_number is required.")
        }
        val directCall = args.optBoolean("direct_call", false)
        val useDirect = directCall && hasPermission(Manifest.permission.CALL_PHONE)

        return try {
            val action = if (useDirect) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val intent = Intent(action, Uri.parse("tel:${Uri.encode(number)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val mode = if (useDirect) "direct_call" else "dialer_intent"
            val msg = if (useDirect) {
                "Initiated phone call to $number."
            } else {
                "Opened phone dialer with $number (grant Phone permission for instant direct dialing)."
            }
            ToolExecutionResult(
                "initiate_phone_call",
                true,
                JSONObject().put("status", "ok").put("mode", mode).put("message", msg),
                msg
            )
        } catch (e: Exception) {
            permissionError("initiate_phone_call", "no_dialer", "Could not launch phone app: ${e.localizedMessage}")
        }
    }

    private fun execGetDeviceState(): ToolExecutionResult {
        val s = refreshStatus()
        val json = JSONObject().apply {
            put("status", "ok")
            put("media_volume_percent", s.mediaVolumePercent)
            put("flashlight_available", s.hasFlashlight)
            put("flashlight_enabled", s.flashlightEnabled)
            put("accessibility_service_enabled", s.accessibilityEnabled)
            put("notification_access_enabled", s.notificationAccessEnabled)
            put("call_phone_permission", s.callPhoneGranted)
            put("send_sms_permission", s.sendSmsGranted)
            put("record_audio_permission", s.recordAudioGranted)
        }
        return ToolExecutionResult(
            toolName = "get_device_state",
            success = true,
            jsonResponse = json,
            summary = "Vol ${s.mediaVolumePercent}% • Torch ${if (s.flashlightEnabled) "ON" else "OFF"}"
        )
    }

    private fun execSetVolume(args: JSONObject): ToolExecutionResult {
        val requested = args.optInt("level_percent", 50)
        val (ok, actual) = setMediaVolumePercent(requested)
        val msg = if (ok) "Media volume set to $actual%." else "Failed to modify media volume."
        return ToolExecutionResult(
            toolName = "set_media_volume",
            success = ok,
            jsonResponse = JSONObject()
                .put("status", if (ok) "ok" else "failed")
                .put("media_volume_percent", actual)
                .put("message", msg),
            summary = msg
        )
    }

    private fun execSetFlashlight(args: JSONObject): ToolExecutionResult {
        val enabled = args.optBoolean("enabled", false)
        val (ok, msg) = setFlashlight(enabled)
        return ToolExecutionResult(
            toolName = "set_flashlight",
            success = ok,
            jsonResponse = JSONObject()
                .put("status", if (ok) "ok" else "failed")
                .put("flashlight_enabled", trackedTorchState)
                .put("message", msg),
            summary = msg
        )
    }

    private suspend fun execManageContext(args: JSONObject): ToolExecutionResult {
        val action = args.optString("action", "get_summary")
        return when (action) {
            "get_summary" -> {
                val summaryText = getConversationSummaryText()
                val snapshot = prefsRepo.getSnapshot()
                val json = JSONObject().apply {
                    put("status", "ok")
                    put("conversation_summary", summaryText)
                    put("pinned_memories", JSONObject(snapshot.memories as Map<*, *>))
                }
                ToolExecutionResult("manage_conversation_context", true, json, "Retrieved context summary")
            }
            "save_memory" -> {
                val key = args.optString("key")
                val value = args.optString("value")
                val saved = prefsRepo.saveMemory(key, value)
                val msg = if (saved) {
                    "Saved memory '$key'."
                } else {
                    "Rejected memory: invalid key/value or blocked by sensitive data privacy rules."
                }
                ToolExecutionResult(
                    "manage_conversation_context",
                    saved,
                    JSONObject().put("status", if (saved) "ok" else "privacy_blocked").put("message", msg),
                    msg
                )
            }
            "delete_memory" -> {
                val key = args.optString("key")
                val removed = prefsRepo.deleteMemory(key)
                val msg = if (removed) "Deleted memory '$key'." else "Memory '$key' not found."
                ToolExecutionResult(
                    "manage_conversation_context",
                    removed,
                    JSONObject().put("status", if (removed) "ok" else "not_found").put("message", msg),
                    msg
                )
            }
            "list_memories" -> {
                val snapshot = prefsRepo.getSnapshot()
                ToolExecutionResult(
                    "manage_conversation_context",
                    true,
                    JSONObject().put("status", "ok").put("memories", JSONObject(snapshot.memories as Map<*, *>)),
                    "Listed ${snapshot.memories.size} memories"
                )
            }
            "clear_context" -> {
                onClearChatContext()
                prefsRepo.clearConversationSummary()
                val msg = "Conversation context cleared."
                ToolExecutionResult(
                    "manage_conversation_context",
                    true,
                    JSONObject().put("status", "ok").put("message", msg),
                    msg
                )
            }
            else -> permissionError("manage_conversation_context", "invalid_action", "Unknown action '$action'.")
        }
    }

    private fun permissionError(toolName: String, status: String, message: String): ToolExecutionResult {
        return ToolExecutionResult(
            toolName = toolName,
            success = false,
            jsonResponse = JSONObject()
                .put("status", status)
                .put("message", message),
            summary = message
        )
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(context, JarvisAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun isNotificationListenerEnabled(): Boolean {
        val expected = ComponentName(context, JarvisNotificationService::class.java).flattenToString()
        val listeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return listeners.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
