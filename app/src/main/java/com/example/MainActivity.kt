package com.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.AssistantLanguage
import com.example.ui.JarvisViewModel
import com.example.ui.screens.ConsoleScreen
import com.example.ui.screens.DeviceToolsScreen
import com.example.ui.screens.PromptBlueprintScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.JarvisAssistantTheme
import kotlinx.coroutines.launch
import java.util.Locale

enum class JarvisTab(
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    CONSOLE(R.string.tab_console, Icons.Filled.Terminal, Icons.Outlined.Terminal, "tab_console"),
    PROMPT(R.string.tab_prompt, Icons.Filled.Description, Icons.Outlined.Description, "tab_prompt"),
    TOOLS(R.string.tab_tools, Icons.Filled.Build, Icons.Outlined.Build, "tab_tools"),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings, "tab_settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JarvisAssistantTheme(darkTheme = true) {
                JarvisRootApp()
            }
        }
    }
}

@Composable
fun JarvisRootApp(
    viewModel: JarvisViewModel = viewModel()
) {
    var activeTab by rememberSaveable { mutableStateOf(JarvisTab.CONSOLE) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val runState by viewModel.runState.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    val maskedApiKey by viewModel.maskedApiKey.collectAsStateWithLifecycle()
    val keyVerification by viewModel.keyVerification.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val deviceStatus by viewModel.deviceStatus.collectAsStateWithLifecycle()
    val screenSnapshot by viewModel.lastScreenSnapshot.collectAsStateWithLifecycle()
    val notifications by viewModel.recentNotifications.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionsAndHardware()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (activeTab != JarvisTab.CONSOLE) {
        BackHandler { activeTab = JarvisTab.CONSOLE }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                viewModel.sendCommand(spoken)
            }
        }
    }

    val launchVoiceInput: () -> Unit = {
        val localeTag = when (settings.language) {
            AssistantLanguage.HINDI -> "hi-IN"
            AssistantLanguage.HINGLISH -> "en-IN"
            else -> Locale.getDefault().toLanguageTag()
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JARVIS in English, Hindi, or Hinglish")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            scope.launch {
                snackbarHostState.showSnackbar("Speech recognition service unavailable on this device")
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        JarvisTab.entries.forEach { tab ->
                            val selected = tab == activeTab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.titleRes)
                                    )
                                },
                                label = { Text(stringResource(tab.titleRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        JarvisTab.entries.forEach { tab ->
                            val selected = tab == activeTab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.titleRes)
                                    )
                                },
                                label = { Text(stringResource(tab.titleRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val contentModifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp)

                    when (activeTab) {
                        JarvisTab.CONSOLE -> ConsoleScreen(
                            messages = messages,
                            runState = runState,
                            hasApiKey = hasApiKey,
                            activeLanguage = settings.language,
                            ttsEnabled = settings.ttsEnabled,
                            deviceStatus = deviceStatus,
                            onSelectLanguage = viewModel::setLanguage,
                            onToggleTts = viewModel::setTtsEnabled,
                            onSendCommand = viewModel::sendCommand,
                            onVoiceCommandClick = launchVoiceInput,
                            onClearContext = viewModel::clearConversationContext,
                            onNavigateToSettings = { activeTab = JarvisTab.SETTINGS },
                            modifier = contentModifier
                        )

                        JarvisTab.PROMPT -> PromptBlueprintScreen(
                            activeLanguage = settings.language,
                            customAddendum = settings.customAddendum,
                            snackbarHostState = snackbarHostState,
                            onSaveCustomAddendum = viewModel::setCustomAddendum,
                            modifier = contentModifier
                        )

                        JarvisTab.TOOLS -> DeviceToolsScreen(
                            deviceStatus = deviceStatus,
                            screenSnapshot = screenSnapshot,
                            notifications = notifications,
                            memories = settings.memories,
                            snackbarHostState = snackbarHostState,
                            onSetVolume = viewModel::setMediaVolume,
                            onToggleFlashlight = viewModel::toggleFlashlight,
                            onRunQuickTool = viewModel::executeQuickLocalTool,
                            onSaveMemory = viewModel::saveMemory,
                            onDeleteMemory = viewModel::deleteMemory,
                            onOpenSettings = { activeTab = JarvisTab.SETTINGS },
                            modifier = contentModifier
                        )

                        JarvisTab.SETTINGS -> SettingsScreen(
                            hasApiKey = hasApiKey,
                            maskedApiKey = maskedApiKey,
                            keyVerification = keyVerification,
                            selectedModel = settings.modelId,
                            selectedLanguage = settings.language,
                            ttsEnabled = settings.ttsEnabled,
                            deviceStatus = deviceStatus,
                            snackbarHostState = snackbarHostState,
                            onSaveApiKey = viewModel::saveApiKey,
                            onVerifyApiKey = viewModel::verifySavedApiKey,
                            onClearApiKey = viewModel::clearApiKey,
                            onSelectModel = viewModel::setModelId,
                            onSelectLanguage = viewModel::setLanguage,
                            onToggleTts = viewModel::setTtsEnabled,
                            onPermissionsChanged = viewModel::refreshPermissionsAndHardware,
                            modifier = contentModifier
                        )
                    }
                }
            }
        }
    }
}
