package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.AssistantLanguage
import com.example.tools.PermissionStatusSnapshot
import com.example.ui.KeyVerificationState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    hasApiKey: Boolean,
    maskedApiKey: String,
    keyVerification: KeyVerificationState,
    selectedModel: String,
    selectedLanguage: AssistantLanguage,
    ttsEnabled: Boolean,
    deviceStatus: PermissionStatusSnapshot,
    snackbarHostState: SnackbarHostState,
    onSaveApiKey: (String) -> Unit,
    onVerifyApiKey: (String?) -> Unit,
    onClearApiKey: () -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectLanguage: (AssistantLanguage) -> Unit,
    onToggleTts: (Boolean) -> Unit,
    onPermissionsChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var keyInput by rememberSaveable { mutableStateOf("") }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var customModelInput by rememberSaveable(selectedModel) { mutableStateOf(selectedModel) }

    val runtimePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onPermissionsChanged()
        scope.launch {
            snackbarHostState.showSnackbar(
                if (granted) "Permission granted" else "Permission denied — safe intent fallback remains active"
            )
        }
    }

    val presetModels = remember {
        listOf("gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-3.5-pro")
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "api_key_vault_card") {
            Card(
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Personal Gemini API Key",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    Text(
                        text = "Enter your unique personal Gemini API key below. It is encrypted with Android KeyStore (AES-256-GCM) on this device and is NEVER stored or exposed inside the system prompt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (hasApiKey) "Vault Status: $maskedApiKey" else "Vault Status: Not configured",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (hasApiKey) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                            )
                            if (hasApiKey) {
                                TextButton(onClick = onClearApiKey) {
                                    Text("Clear", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Paste personal Gemini API key") },
                        placeholder = { Text("AIza...") },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { showKey = !showKey },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showKey) "Hide key" else "Show key"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (keyInput.isNotBlank()) {
                                    onSaveApiKey(keyInput)
                                    keyInput = ""
                                }
                            },
                            enabled = keyInput.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("save_api_key_button")
                        ) {
                            Text("Save to Vault")
                        }

                        OutlinedButton(
                            onClick = { onVerifyApiKey(keyInput.takeIf { it.isNotBlank() }) },
                            enabled = !keyVerification.isChecking,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("verify_api_key_button")
                        ) {
                            if (keyVerification.isChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Verify Key")
                        }
                    }

                    keyVerification.message?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = when (keyVerification.success) {
                                true -> MaterialTheme.colorScheme.tertiary
                                false -> MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    Text(
                        text = "Gemini Model",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetModels.forEach { model ->
                            FilterChip(
                                selected = selectedModel == model,
                                onClick = {
                                    customModelInput = model
                                    onSelectModel(model)
                                },
                                label = { Text(model, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customModelInput,
                            onValueChange = { customModelInput = it },
                            label = { Text("Active Model ID") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { onSelectModel(customModelInput) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Apply")
                        }
                    }
                }
            }
        }

        item(key = "system_access_card") {
            Card(
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Android Permissions & System Access",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Text(
                        text = "Enable Accessibility Service and Notification Access in Android System Settings for full screen reading and automated UI clicks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    PermissionRow(
                        icon = Icons.Filled.AccessibilityNew,
                        title = "Accessibility Service",
                        subtitle = "Required for reading on-screen UI & clicking elements",
                        granted = deviceStatus.accessibilityEnabled,
                        buttonLabel = if (deviceStatus.accessibilityEnabled) "Manage" else "Open Settings",
                        testTag = "open_accessibility_settings_button",
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    )

                    PermissionRow(
                        icon = Icons.Filled.NotificationsActive,
                        title = "Notification Access",
                        subtitle = "Required for reading redacted incoming notifications",
                        granted = deviceStatus.notificationAccessEnabled,
                        buttonLabel = if (deviceStatus.notificationAccessEnabled) "Manage" else "Open Settings",
                        testTag = "open_notification_settings_button",
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    )

                    PermissionRow(
                        icon = Icons.Filled.Mic,
                        title = "Microphone (Voice Input)",
                        subtitle = "Capture spoken commands in English, Hindi, or Hinglish",
                        granted = deviceStatus.recordAudioGranted,
                        buttonLabel = if (deviceStatus.recordAudioGranted) "Granted" else "Grant",
                        testTag = "grant_mic_button",
                        onClick = {
                            if (!deviceStatus.recordAudioGranted) {
                                runtimePermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )

                    PermissionRow(
                        icon = Icons.Filled.Call,
                        title = "Phone Call Permission",
                        subtitle = "Direct calling (dialer fallback works even if denied)",
                        granted = deviceStatus.callPhoneGranted,
                        buttonLabel = if (deviceStatus.callPhoneGranted) "Granted" else "Grant",
                        testTag = "grant_call_button",
                        onClick = {
                            if (!deviceStatus.callPhoneGranted) {
                                runtimePermLauncher.launch(Manifest.permission.CALL_PHONE)
                            }
                        }
                    )

                    PermissionRow(
                        icon = Icons.Filled.Sms,
                        title = "SMS Permission",
                        subtitle = "Direct SMS send (SMS composer fallback works even if denied)",
                        granted = deviceStatus.sendSmsGranted,
                        buttonLabel = if (deviceStatus.sendSmsGranted) "Granted" else "Grant",
                        testTag = "grant_sms_button",
                        onClick = {
                            if (!deviceStatus.sendSmsGranted) {
                                runtimePermLauncher.launch(Manifest.permission.SEND_SMS)
                            }
                        }
                    )
                }
            }
        }

        item(key = "language_and_voice_card") {
            Card(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Voice & Language Defaults", style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistantLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = lang == selectedLanguage,
                                onClick = { onSelectLanguage(lang) },
                                label = { Text(lang.label) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Speak JARVIS Replies (TTS)", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Read responses aloud using device Text-to-Speech",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = ttsEnabled,
                            onCheckedChange = onToggleTts
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    buttonLabel: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Icon(
                        imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                        contentDescription = if (granted) "Enabled" else "Action needed",
                        tint = if (granted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag(testTag)
            ) {
                Text(buttonLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
