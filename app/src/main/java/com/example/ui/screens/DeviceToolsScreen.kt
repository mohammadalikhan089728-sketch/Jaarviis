package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.services.CapturedNotification
import com.example.services.ScreenSnapshot
import com.example.tools.PermissionStatusSnapshot
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun DeviceToolsScreen(
    deviceStatus: PermissionStatusSnapshot,
    screenSnapshot: ScreenSnapshot?,
    notifications: List<CapturedNotification>,
    memories: Map<String, String>,
    snackbarHostState: SnackbarHostState,
    onSetVolume: (Int) -> Unit,
    onToggleFlashlight: (Boolean) -> Unit,
    onRunQuickTool: (String, JSONObject) -> Unit,
    onSaveMemory: (String, String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var sliderValue by remember(deviceStatus.mediaVolumePercent) {
        mutableFloatStateOf(deviceStatus.mediaVolumePercent.toFloat())
    }
    var memKey by rememberSaveable { mutableStateOf("") }
    var memValue by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "hardware_deck") {
            Card(
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Hardware Control Deck",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VolumeUp,
                                    contentDescription = "Media Volume",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text("Media Volume", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                text = "${sliderValue.toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            onValueChangeFinished = { onSetVolume(sliderValue.toInt()) },
                            valueRange = 0f..100f,
                            modifier = Modifier.testTag("volume_slider")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (deviceStatus.flashlightEnabled) {
                                    Icons.Filled.FlashlightOn
                                } else {
                                    Icons.Filled.FlashlightOff
                                },
                                contentDescription = "Flashlight",
                                tint = if (deviceStatus.flashlightEnabled) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Column {
                                Text("Device Flashlight", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = if (deviceStatus.hasFlashlight) {
                                        if (deviceStatus.flashlightEnabled) "Torch Active" else "Torch Off"
                                    } else {
                                        "No rear torch detected on this device"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = deviceStatus.flashlightEnabled,
                            onCheckedChange = { onToggleFlashlight(it) },
                            enabled = deviceStatus.hasFlashlight,
                            modifier = Modifier.testTag("flashlight_switch")
                        )
                    }
                }
            }
        }

        item(key = "accessibility_inspector") {
            Card(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ScreenshotMonitor,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("Screen Reader (Redacted)", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = if (deviceStatus.accessibilityEnabled) "READY" else "DISABLED",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (deviceStatus.accessibilityEnabled) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (!deviceStatus.accessibilityEnabled) {
                                    onOpenSettings()
                                } else {
                                    onRunQuickTool("read_screen_content", JSONObject().put("include_non_clickable", true))
                                    scope.launch { snackbarHostState.showSnackbar("Screen snapshot updated") }
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("capture_screen_button")
                        ) {
                            Text(if (deviceStatus.accessibilityEnabled) "Capture Screen" else "Enable in Settings")
                        }
                    }

                    if (screenSnapshot != null) {
                        Text(
                            text = "Package: ${screenSnapshot.packageName} • ${screenSnapshot.nodes.size} nodes (${screenSnapshot.redactedCount} sensitive redacted)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        screenSnapshot.nodes.take(6).forEach { node ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "[${node.index}] ${node.className}: ${node.text.ifBlank { node.contentDescription.ifBlank { node.viewId } }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "No screen capture recorded yet. Sensitive password, PIN, OTP, and card fields are automatically stripped on-device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item(key = "notifications_preview") {
            Card(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("Notification Buffer", style = MaterialTheme.typography.titleMedium)
                        }
                        if (!deviceStatus.notificationAccessEnabled) {
                            OutlinedButton(
                                onClick = onOpenSettings,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Grant Access")
                            }
                        }
                    }
                    if (notifications.isEmpty()) {
                        Text(
                            text = "No active captured notifications. OTPs and banking codes are redacted before buffering.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        notifications.take(5).forEach { item ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "${item.title} (${item.packageName.substringAfterLast('.')})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = item.text,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "context_memories_header") {
            Card(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("Context Memory Vault", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "Store non-sensitive user preferences (e.g., city, preferred reply style). Passwords, PINs, OTPs, and API keys are automatically blocked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = memKey,
                            onValueChange = { memKey = it },
                            label = { Text("Key") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.42f)
                                .testTag("memory_key_input")
                        )
                        OutlinedTextField(
                            value = memValue,
                            onValueChange = { memValue = it },
                            label = { Text("Value") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.58f)
                                .testTag("memory_value_input")
                        )
                    }
                    Button(
                        onClick = {
                            if (memKey.isNotBlank() && memValue.isNotBlank()) {
                                onSaveMemory(memKey, memValue)
                                memKey = ""
                                memValue = ""
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("add_memory_button")
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pin Memory")
                    }
                }
            }
        }

        items(memories.entries.toList(), key = { it.key }) { (k, v) ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(text = v, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(
                        onClick = { onDeleteMemory(k) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete memory $k",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}
