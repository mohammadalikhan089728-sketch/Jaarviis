package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.AssistantLanguage
import com.example.services.CapturedNotification
import com.example.services.ScreenSnapshot
import com.example.tools.PermissionStatusSnapshot
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextPrimaryDark
import org.json.JSONObject

private enum class AutomationsSubTab { HARDWARE_DECK, SYSTEM_PROMPT }

@Composable
fun AutomationsScreen(
    deviceStatus: PermissionStatusSnapshot,
    screenSnapshot: ScreenSnapshot?,
    notifications: List<CapturedNotification>,
    memories: Map<String, String>,
    activeLanguage: AssistantLanguage,
    customAddendum: String,
    snackbarHostState: SnackbarHostState,
    onSetVolume: (Int) -> Unit,
    onToggleFlashlight: (Boolean) -> Unit,
    onRunQuickTool: (String, JSONObject) -> Unit,
    onSaveMemory: (String, String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onSaveCustomAddendum: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var subTab by rememberSaveable { mutableStateOf(AutomationsSubTab.HARDWARE_DECK) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        Text(
            text = "Automations",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimaryDark,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = subTab == AutomationsSubTab.HARDWARE_DECK,
                onClick = { subTab = AutomationsSubTab.HARDWARE_DECK },
                label = { Text("Device Tools & Memory") },
                leadingIcon = { Icon(Icons.Filled.Build, null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("automations_tab_tools")
            )
            FilterChip(
                selected = subTab == AutomationsSubTab.SYSTEM_PROMPT,
                onClick = { subTab = AutomationsSubTab.SYSTEM_PROMPT },
                label = { Text("System Prompt & 12 Tools") },
                leadingIcon = { Icon(Icons.Filled.Description, null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("automations_tab_prompt")
            )
        }

        when (subTab) {
            AutomationsSubTab.HARDWARE_DECK -> DeviceToolsScreen(
                deviceStatus = deviceStatus,
                screenSnapshot = screenSnapshot,
                notifications = notifications,
                memories = memories,
                snackbarHostState = snackbarHostState,
                onSetVolume = onSetVolume,
                onToggleFlashlight = onToggleFlashlight,
                onRunQuickTool = onRunQuickTool,
                onSaveMemory = onSaveMemory,
                onDeleteMemory = onDeleteMemory,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.weight(1f)
            )
            AutomationsSubTab.SYSTEM_PROMPT -> PromptBlueprintScreen(
                activeLanguage = activeLanguage,
                customAddendum = customAddendum,
                snackbarHostState = snackbarHostState,
                onSaveCustomAddendum = onSaveCustomAddendum,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
