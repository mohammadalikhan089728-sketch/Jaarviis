package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ai.ExecutedToolLog
import com.example.services.CapturedNotification
import com.example.ui.ChatAuthor
import com.example.ui.ChatMessage
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

private enum class ActivityFilter { CONVERSATION, NOTIFICATIONS }

@Composable
fun ActivityScreen(
    messages: List<ChatMessage>,
    notifications: List<CapturedNotification>,
    notificationAccessEnabled: Boolean,
    onClearHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var filter by rememberSaveable { mutableStateOf(ActivityFilter.CONVERSATION) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Activity",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimaryDark
            )
            if (messages.isNotEmpty()) {
                IconButton(
                    onClick = onClearHistory,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("clear_context_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = "Clear activity history",
                        tint = TextSecondaryDark
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter == ActivityFilter.CONVERSATION,
                onClick = { filter = ActivityFilter.CONVERSATION },
                label = { Text("Commands & Tools (${messages.size})") }
            )
            FilterChip(
                selected = filter == ActivityFilter.NOTIFICATIONS,
                onClick = { filter = ActivityFilter.NOTIFICATIONS },
                label = { Text("Notifications (${notifications.size})") },
                leadingIcon = {
                    Icon(Icons.Filled.Notifications, null, modifier = Modifier.size(16.dp))
                }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filter == ActivityFilter.CONVERSATION) {
                if (messages.isEmpty()) {
                    item(key = "activity_empty") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ObsidianSurface,
                            border = BorderStroke(1.dp, ObsidianOutline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No activity yet. Speak or type a command from Home to view replies and tool execution logs here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondaryDark,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                } else {
                    items(messages.reversed(), key = { it.id }) { msg ->
                        ActivityMessageCard(
                            message = msg,
                            onNavigateToSettings = onNavigateToSettings
                        )
                    }
                }
            } else {
                if (!notificationAccessEnabled) {
                    item(key = "notif_access_prompt") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ObsidianSurface,
                            border = BorderStroke(1.dp, ObsidianOutline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Notification Access is disabled. Enable it in Settings to read redacted notifications.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondaryDark
                                )
                                Button(
                                    onClick = onNavigateToSettings,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("Open Settings")
                                }
                            }
                        }
                    }
                } else if (notifications.isEmpty()) {
                    item(key = "notif_empty") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ObsidianSurface,
                            border = BorderStroke(1.dp, ObsidianOutline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No notifications captured yet. OTPs, PINs, and card numbers are redacted on-device.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondaryDark,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                } else {
                    items(notifications, key = { "${it.packageName}:${it.id}:${it.timestampMillis}" }) { item ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ObsidianSurface,
                            border = BorderStroke(1.dp, ObsidianOutline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${item.title} • ${item.packageName.substringAfterLast('.')}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ArcCyan
                                )
                                Text(
                                    text = item.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimaryDark
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityMessageCard(
    message: ChatMessage,
    onNavigateToSettings: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        border = BorderStroke(
            1.dp,
            if (message.isError) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else ObsidianOutline
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = when (message.author) {
                    ChatAuthor.USER -> "YOU"
                    ChatAuthor.JARVIS -> "JARVIS"
                    ChatAuthor.SYSTEM -> "DEVICE TOOL"
                },
                style = MaterialTheme.typography.labelMedium,
                color = when (message.author) {
                    ChatAuthor.USER -> ArcCyan
                    ChatAuthor.JARVIS -> MaterialTheme.colorScheme.secondary
                    ChatAuthor.SYSTEM -> MaterialTheme.colorScheme.tertiary
                }
            )
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimaryDark
            )
            message.toolLogs.forEach { log ->
                ActivityToolLogRow(log)
            }
            if (message.requiresApiKeySetup) {
                Button(
                    onClick = onNavigateToSettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Settings, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Configure API Key")
                }
            }
        }
    }
}

@Composable
private fun ActivityToolLogRow(log: ExecutedToolLog) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = ObsidianBg,
        border = BorderStroke(1.dp, ObsidianOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (log.success) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = if (log.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(15.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Filled.Build, null, tint = ArcCyan, modifier = Modifier.size(12.dp))
                    Text(log.toolName, style = MaterialTheme.typography.labelSmall, color = ArcCyan)
                }
                Text(log.resultSummary, style = MaterialTheme.typography.bodySmall, color = TextSecondaryDark)
            }
        }
    }
}
