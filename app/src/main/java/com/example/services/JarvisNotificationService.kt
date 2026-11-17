package com.example.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.security.PrivacyGuard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CapturedNotification(
    val id: Int,
    val packageName: String,
    val title: String,
    val text: String,
    val timestampMillis: Long,
    val wasRedacted: Boolean
)

object NotificationBridge {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _notifications = MutableStateFlow<List<CapturedNotification>>(emptyList())
    val notifications: StateFlow<List<CapturedNotification>> = _notifications.asStateFlow()

    internal fun setConnected(connected: Boolean) {
        _isConnected.value = connected
    }

    internal fun updateNotifications(items: List<CapturedNotification>) {
        _notifications.value = items
    }

    internal fun addNotification(item: CapturedNotification) {
        val updated = (listOf(item) + _notifications.value)
            .distinctBy { "${it.packageName}:${it.id}:${it.title}:${it.text}" }
            .take(25)
        _notifications.value = updated
    }
}

class JarvisNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationBridge.setConnected(true)
        refreshActive()
    }

    override fun onListenerDisconnected() {
        NotificationBridge.setConnected(false)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { NotificationBridge.addNotification(sanitize(it)) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshActive()
    }

    private fun refreshActive() {
        try {
            val active = activeNotifications.orEmpty()
                .map { sanitize(it) }
                .sortedByDescending { it.timestampMillis }
                .take(25)
            NotificationBridge.updateNotifications(active)
        } catch (_: Exception) {
            // Listener may be disconnecting
        }
    }

    private fun sanitize(sbn: StatusBarNotification): CapturedNotification {
        val extras = sbn.notification.extras
        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val rawText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        val titleRes = PrivacyGuard.redactSensitiveText(rawTitle)
        val textRes = PrivacyGuard.redactSensitiveText(rawText)

        return CapturedNotification(
            id = sbn.id,
            packageName = sbn.packageName,
            title = titleRes.sanitizedText.ifBlank { "Notification" },
            text = textRes.sanitizedText.take(220),
            timestampMillis = sbn.postTime,
            wasRedacted = (titleRes.redactedCount + textRes.redactedCount) > 0
        )
    }
}
