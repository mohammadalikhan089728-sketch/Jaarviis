package com.example.services

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.security.PrivacyGuard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

data class ScreenUiNode(
    val index: Int,
    val className: String,
    val text: String,
    val contentDescription: String,
    val viewId: String,
    val clickable: Boolean,
    val scrollable: Boolean,
    val isSensitiveRedacted: Boolean,
    val bounds: String
)

data class ScreenSnapshot(
    val packageName: String,
    val capturedAtMillis: Long,
    val nodes: List<ScreenUiNode>,
    val redactedCount: Int
)

object AccessibilityBridge {
    private var serviceRef: WeakReference<JarvisAccessibilityService>? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _lastSnapshot = MutableStateFlow<ScreenSnapshot?>(null)
    val lastSnapshot: StateFlow<ScreenSnapshot?> = _lastSnapshot.asStateFlow()

    internal fun attach(service: JarvisAccessibilityService) {
        serviceRef = WeakReference(service)
        _isConnected.value = true
    }

    internal fun detach() {
        serviceRef = null
        _isConnected.value = false
    }

    fun readScreen(includeNonClickable: Boolean = true): ScreenSnapshot? {
        val service = serviceRef?.get() ?: return null
        val snapshot = service.captureScreenSnapshot(includeNonClickable)
        if (snapshot != null) {
            _lastSnapshot.value = snapshot
        }
        return snapshot
    }

    fun clickElement(
        targetText: String?,
        contentDescription: String?,
        viewId: String?,
        matchIndex: Int = 0
    ): Pair<Boolean, String> {
        val service = serviceRef?.get()
            ?: return false to "Accessibility service is not enabled."
        return service.clickMatchingNode(targetText, contentDescription, viewId, matchIndex)
    }

    fun scroll(direction: String): Pair<Boolean, String> {
        val service = serviceRef?.get()
            ?: return false to "Accessibility service is not enabled."
        return service.scrollActiveContainer(direction)
    }

    fun globalAction(action: String): Pair<Boolean, String> {
        val service = serviceRef?.get()
            ?: return false to "Accessibility service is not enabled."
        return service.triggerGlobalAction(action)
    }
}

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridge.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // On-demand capture keeps battery and privacy overhead minimal.
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        AccessibilityBridge.detach()
        super.onDestroy()
    }

    fun captureScreenSnapshot(includeNonClickable: Boolean): ScreenSnapshot? {
        val root = rootInActiveWindow ?: return null
        val collected = mutableListOf<ScreenUiNode>()
        var redactedCount = 0

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null || collected.size >= MAX_NODES) return

            val sensitive = PrivacyGuard.isSensitiveNode(node)
            val rawText = node.text?.toString()?.trim().orEmpty()
            val rawDesc = node.contentDescription?.toString()?.trim().orEmpty()
            val viewId = node.viewIdResourceName?.trim().orEmpty()
            val isClickable = node.isClickable
            val isScrollable = node.isScrollable

            val safeText: String
            val safeDesc: String
            var nodeRedacted = false

            if (sensitive) {
                safeText = if (rawText.isNotEmpty()) "[REDACTED_PASSWORD_FIELD]" else ""
                safeDesc = if (rawDesc.isNotEmpty()) "[REDACTED_PASSWORD_FIELD]" else ""
                redactedCount++
                nodeRedacted = true
            } else {
                val textResult = PrivacyGuard.redactSensitiveText(rawText)
                val descResult = PrivacyGuard.redactSensitiveText(rawDesc)
                safeText = textResult.sanitizedText
                safeDesc = descResult.sanitizedText
                val delta = textResult.redactedCount + descResult.redactedCount
                if (delta > 0) {
                    redactedCount += delta
                    nodeRedacted = true
                }
            }

            val hasMeaningfulContent = safeText.isNotEmpty() || safeDesc.isNotEmpty() || isClickable || isScrollable
            if (hasMeaningfulContent && (includeNonClickable || isClickable || isScrollable)) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                collected.add(
                    ScreenUiNode(
                        index = collected.size,
                        className = node.className?.toString()?.substringAfterLast('.') ?: "View",
                        text = safeText.take(160),
                        contentDescription = safeDesc.take(160),
                        viewId = viewId,
                        clickable = isClickable,
                        scrollable = isScrollable,
                        isSensitiveRedacted = nodeRedacted,
                        bounds = "${rect.left},${rect.top},${rect.right},${rect.bottom}"
                    )
                )
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        traverse(root)
        return ScreenSnapshot(
            packageName = root.packageName?.toString() ?: "unknown",
            capturedAtMillis = System.currentTimeMillis(),
            nodes = collected,
            redactedCount = redactedCount
        )
    }

    fun clickMatchingNode(
        targetText: String?,
        contentDescription: String?,
        viewId: String?,
        matchIndex: Int
    ): Pair<Boolean, String> {
        val root = rootInActiveWindow ?: return false to "No active window available."
        if (targetText.isNullOrBlank() && contentDescription.isNullOrBlank() && viewId.isNullOrBlank()) {
            return false to "Provide target_text, content_description, or view_id to click."
        }

        val matches = mutableListOf<AccessibilityNodeInfo>()

        fun search(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (!PrivacyGuard.isSensitiveNode(node)) {
                val nodeText = node.text?.toString().orEmpty()
                val nodeDesc = node.contentDescription?.toString().orEmpty()
                val nodeViewId = node.viewIdResourceName.orEmpty()

                val textOk = targetText.isNullOrBlank() ||
                    nodeText.contains(targetText, ignoreCase = true)
                val descOk = contentDescription.isNullOrBlank() ||
                    nodeDesc.contains(contentDescription, ignoreCase = true)
                val idOk = viewId.isNullOrBlank() ||
                    nodeViewId.contains(viewId, ignoreCase = true)

                if (textOk && descOk && idOk) {
                    matches.add(node)
                }
            }
            for (i in 0 until node.childCount) {
                search(node.getChild(i))
            }
        }

        search(root)
        if (matches.isEmpty()) {
            return false to "No matching non-sensitive UI element found on screen."
        }

        val clampedIndex = matchIndex.coerceIn(0, matches.lastIndex)
        var current: AccessibilityNodeInfo? = matches[clampedIndex]
        while (current != null && !current.isClickable) {
            current = current.parent
        }

        val clickableTarget = current ?: matches[clampedIndex]
        if (PrivacyGuard.isSensitiveNode(clickableTarget)) {
            return false to "Blocked interaction with a sensitive credential field."
        }

        val clicked = clickableTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return if (clicked) {
            val label = matches[clampedIndex].text?.toString()?.takeIf { it.isNotBlank() }
                ?: matches[clampedIndex].contentDescription?.toString()?.takeIf { it.isNotBlank() }
                ?: matches[clampedIndex].viewIdResourceName
                ?: "UI element"
            true to "Clicked '$label'."
        } else {
            false to "Element matched but declined ACTION_CLICK."
        }
    }

    fun scrollActiveContainer(direction: String): Pair<Boolean, String> {
        val root = rootInActiveWindow ?: return false to "No active window available."
        val scrollables = mutableListOf<AccessibilityNodeInfo>()

        fun findScrollable(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.isScrollable) scrollables.add(node)
            for (i in 0 until node.childCount) {
                findScrollable(node.getChild(i))
            }
        }

        findScrollable(root)
        val container = scrollables.firstOrNull()
            ?: return false to "No scrollable container found on screen."

        val action = when (direction.lowercase()) {
            "up", "left" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        val ok = container.performAction(action)
        return if (ok) {
            true to "Scrolled $direction."
        } else {
            false to "Container could not scroll $direction."
        }
    }

    fun triggerGlobalAction(action: String): Pair<Boolean, String> {
        val code = when (action.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            else -> return false to "Unsupported global action: $action"
        }
        val ok = performGlobalAction(code)
        return if (ok) {
            true to "Executed global action '$action'."
        } else {
            false to "System rejected global action '$action'."
        }
    }

    private companion object {
        const val MAX_NODES = 80
    }
}
