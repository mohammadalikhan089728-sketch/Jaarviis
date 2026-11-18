package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.AssistantLanguage
import com.example.ui.JarvisViewModel
import com.example.ui.OrbPhase
import com.example.ui.screens.ActivityScreen
import com.example.ui.screens.AutomationsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.ArcCyanContainer
import com.example.ui.theme.JarvisAssistantTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextSecondaryDark
import kotlinx.coroutines.launch
import java.util.Locale

enum class JarvisTab(
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME(R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home, "tab_home"),
    AUTOMATIONS(R.string.tab_automations, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "tab_automations"),
    ACTIVITY(R.string.tab_activity, Icons.Filled.History, Icons.Outlined.History, "tab_activity"),
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
    var activeTab by rememberSaveable { mutableStateOf(JarvisTab.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val recentInteractions by viewModel.recentInteractions.collectAsStateWithLifecycle()
    val runState by viewModel.runState.collectAsStateWithLifecycle()
    val orbPhase by viewModel.orbPhase.collectAsStateWithLifecycle()
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

    if (activeTab != JarvisTab.HOME) {
        BackHandler { activeTab = JarvisTab.HOME }
    }

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    DisposableEffect(speechRecognizer) {
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    val fallbackSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.setListening(false)
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                viewModel.sendCommand(spoken)
            }
        }
    }

    val startVoiceCapture: () -> Unit = {
        val localeTag = when (settings.language) {
            AssistantLanguage.HINDI -> "hi-IN"
            AssistantLanguage.HINGLISH -> "en-IN"
            else -> Locale.getDefault().toLanguageTag()
        }

        val hasMicPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (orbPhase == OrbPhase.SPEAKING) {
            viewModel.stopSpeaking()
        } else if (orbPhase == OrbPhase.LISTENING) {
            speechRecognizer?.stopListening()
            viewModel.setListening(false)
        } else if (hasMicPerm && speechRecognizer != null) {
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    viewModel.setListening(true)
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    viewModel.setListening(false)
                }

                override fun onError(error: Int) {
                    viewModel.setListening(false)
                }

                override fun onResults(results: Bundle?) {
                    viewModel.setListening(false)
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        viewModel.sendCommand(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            }
            viewModel.setListening(true)
            speechRecognizer.startListening(intent)
        } else {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JARVIS in English, Hindi, or Hinglish")
            }
            try {
                viewModel.setListening(true)
                fallbackSpeechLauncher.launch(intent)
            } catch (_: Exception) {
                viewModel.setListening(false)
                scope.launch {
                    snackbarHostState.showSnackbar("Tap the keyboard icon to type a command")
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            containerColor = ObsidianBg,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = Color(0xFF0E131B),
                        tonalElevation = 0.dp
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
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ArcCyan,
                                    selectedTextColor = ArcCyan,
                                    indicatorColor = ArcCyanContainer,
                                    unselectedIconColor = TextSecondaryDark,
                                    unselectedTextColor = TextSecondaryDark
                                ),
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
                        containerColor = ObsidianSurface,
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
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ArcCyan,
                                    selectedTextColor = ArcCyan,
                                    indicatorColor = ArcCyanContainer,
                                    unselectedIconColor = TextSecondaryDark,
                                    unselectedTextColor = TextSecondaryDark
                                ),
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
                        JarvisTab.HOME -> HomeScreen(
                            orbPhase = orbPhase,
                            runState = runState,
                            hasApiKey = hasApiKey,
                            wakeWordEnabled = settings.wakeWordEnabled,
                            deviceStatus = deviceStatus,
                            customShortcutLabel = settings.customShortcutLabel,
                            customShortcutCommand = settings.customShortcutCommand,
                            recentInteractions = recentInteractions,
                            onToggleWakeWord = viewModel::setWakeWordEnabled,
                            onMicClick = startVoiceCapture,
                            onSendCommand = viewModel::sendCommand,
                            onSetVolume = viewModel::setMediaVolume,
                            onToggleFlashlight = viewModel::toggleFlashlight,
                            onSaveCustomShortcut = viewModel::setCustomShortcut,
                            onSeeAllActivity = { activeTab = JarvisTab.ACTIVITY },
                            onNavigateToSettings = { activeTab = JarvisTab.SETTINGS },
                            modifier = contentModifier
                        )

                        JarvisTab.AUTOMATIONS -> AutomationsScreen(
                            deviceStatus = deviceStatus,
                            screenSnapshot = screenSnapshot,
                            notifications = notifications,
                            memories = settings.memories,
                            activeLanguage = settings.language,
                            customAddendum = settings.customAddendum,
                            snackbarHostState = snackbarHostState,
                            onSetVolume = viewModel::setMediaVolume,
                            onToggleFlashlight = viewModel::toggleFlashlight,
                            onRunQuickTool = { tool, args -> viewModel.executeQuickLocalTool(tool, args) },
                            onSaveMemory = viewModel::saveMemory,
                            onDeleteMemory = viewModel::deleteMemory,
                            onSaveCustomAddendum = viewModel::setCustomAddendum,
                            onOpenSettings = { activeTab = JarvisTab.SETTINGS },
                            modifier = contentModifier
                        )

                        JarvisTab.ACTIVITY -> ActivityScreen(
                            messages = messages,
                            notifications = notifications,
                            notificationAccessEnabled = deviceStatus.notificationAccessEnabled,
                            onClearHistory = viewModel::clearConversationContext,
                            onNavigateToSettings = { activeTab = JarvisTab.SETTINGS },
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
