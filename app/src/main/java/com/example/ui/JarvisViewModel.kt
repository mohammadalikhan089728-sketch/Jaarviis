package com.example.ui

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.ExecutedToolLog
import com.example.ai.GeminiResult
import com.example.ai.GeminiToolClient
import com.example.core.AssistantLanguage
import com.example.core.JarvisSystemPrompt
import com.example.data.JarvisPreferencesRepository
import com.example.data.JarvisSettingsData
import com.example.security.ApiKeyVault
import com.example.security.PrivacyGuard
import com.example.services.AccessibilityBridge
import com.example.services.CapturedNotification
import com.example.services.NotificationBridge
import com.example.services.ScreenSnapshot
import com.example.tools.DeviceToolExecutor
import com.example.tools.PermissionStatusSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

enum class ChatAuthor { USER, JARVIS, SYSTEM }

data class ChatMessage(
    val id: Long,
    val author: ChatAuthor,
    val text: String,
    val toolLogs: List<ExecutedToolLog> = emptyList(),
    val isError: Boolean = false,
    val requiresApiKeySetup: Boolean = false,
    val timestampMillis: Long = System.currentTimeMillis()
)

sealed interface AssistantRunState {
    data object Idle : AssistantRunState
    data class Running(val activeToolLabel: String?) : AssistantRunState
}

data class KeyVerificationState(
    val isChecking: Boolean = false,
    val success: Boolean? = null,
    val message: String? = null
)

class JarvisViewModel(application: Application) : AndroidViewModel(application),
    TextToSpeech.OnInitListener {

    private val prefsRepo = JarvisPreferencesRepository(application)
    private val apiKeyVault = ApiKeyVault(application)
    private val geminiClient = GeminiToolClient()

    private val apiHistory = mutableListOf<JSONObject>()
    private var nextMessageId = 1L

    private var tts: TextToSpeech? = TextToSpeech(application, this)
    private var ttsReady = false

    val toolExecutor = DeviceToolExecutor(
        context = application,
        prefsRepo = prefsRepo,
        onClearChatContext = {
            apiHistory.clear()
        },
        getConversationSummaryText = {
            buildConversationSummary()
        }
    )

    val settingsState: StateFlow<JarvisSettingsData> = prefsRepo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = JarvisSettingsData()
    )

    val deviceStatus: StateFlow<PermissionStatusSnapshot> = toolExecutor.deviceStatus
    val accessibilityConnected: StateFlow<Boolean> = AccessibilityBridge.isConnected
    val lastScreenSnapshot: StateFlow<ScreenSnapshot?> = AccessibilityBridge.lastSnapshot
    val notificationConnected: StateFlow<Boolean> = NotificationBridge.isConnected
    val recentNotifications: StateFlow<List<CapturedNotification>> = NotificationBridge.notifications

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _runState = MutableStateFlow<AssistantRunState>(AssistantRunState.Idle)
    val runState: StateFlow<AssistantRunState> = _runState.asStateFlow()

    private val _hasApiKey = MutableStateFlow(apiKeyVault.hasApiKey())
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    private val _maskedApiKey = MutableStateFlow(apiKeyVault.getMaskedPreview())
    val maskedApiKey: StateFlow<String> = _maskedApiKey.asStateFlow()

    private val _keyVerification = MutableStateFlow(KeyVerificationState())
    val keyVerification: StateFlow<KeyVerificationState> = _keyVerification.asStateFlow()

    init {
        _messages.value = listOf(
            ChatMessage(
                id = nextId(),
                author = ChatAuthor.JARVIS,
                text = buildWelcomeMessage()
            )
        )
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.language = Locale.ENGLISH
        }
    }

    fun refreshPermissionsAndHardware() {
        toolExecutor.refreshStatus()
        _hasApiKey.value = apiKeyVault.hasApiKey()
        _maskedApiKey.value = apiKeyVault.getMaskedPreview()
    }

    fun sendCommand(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty() || _runState.value is AssistantRunState.Running) return

        val privacyCheck = PrivacyGuard.redactSensitiveText(trimmed)
        val safeUserText = privacyCheck.sanitizedText

        appendMessage(
            ChatMessage(
                id = nextId(),
                author = ChatAuthor.USER,
                text = safeUserText
            )
        )

        val apiKey = apiKeyVault.getApiKey()
        if (apiKey.isNullOrBlank()) {
            appendMessage(
                ChatMessage(
                    id = nextId(),
                    author = ChatAuthor.JARVIS,
                    text = "Personal Gemini API key is not configured yet. Please navigate to Settings -> Personal Gemini API Key to securely store your unique key in the encrypted vault (never inside the prompt). You can also test local hardware tools directly in the Tools tab.",
                    isError = true,
                    requiresApiKeySetup = true
                )
            )
            return
        }

        val currentSettings = settingsState.value
        val compiledPrompt = JarvisSystemPrompt.buildCompiledPrompt(
            activeLanguage = currentSettings.language,
            customAddendum = currentSettings.customAddendum
        ) + buildContextPreamble(currentSettings)

        viewModelScope.launch {
            _runState.value = AssistantRunState.Running("Thinking...")
            val liveToolLogs = mutableListOf<ExecutedToolLog>()

            when (
                val result = geminiClient.sendTurnWithTools(
                    apiKey = apiKey,
                    modelId = currentSettings.modelId,
                    systemPrompt = compiledPrompt,
                    history = apiHistory.toList(),
                    userMessage = safeUserText,
                    toolExecutor = toolExecutor,
                    onToolStep = { log ->
                        liveToolLogs.add(log)
                        _runState.value = AssistantRunState.Running("Running ${log.toolName}...")
                    }
                )
            ) {
                is GeminiResult.Success -> {
                    apiHistory.clear()
                    apiHistory.addAll(result.updatedHistory)
                    appendMessage(
                        ChatMessage(
                            id = nextId(),
                            author = ChatAuthor.JARVIS,
                            text = result.replyText,
                            toolLogs = result.toolLogs
                        )
                    )
                    if (currentSettings.ttsEnabled) {
                        speakReply(result.replyText, currentSettings.language)
                    }
                }
                is GeminiResult.Failure -> {
                    appendMessage(
                        ChatMessage(
                            id = nextId(),
                            author = ChatAuthor.JARVIS,
                            text = result.message,
                            toolLogs = liveToolLogs,
                            isError = true,
                            requiresApiKeySetup = result.isAuthError
                        )
                    )
                }
            }
            _runState.value = AssistantRunState.Idle
        }
    }

    fun executeQuickLocalTool(toolName: String, args: JSONObject) {
        viewModelScope.launch {
            val res = toolExecutor.executeTool(toolName, args)
            appendMessage(
                ChatMessage(
                    id = nextId(),
                    author = ChatAuthor.SYSTEM,
                    text = res.summary,
                    toolLogs = listOf(
                        ExecutedToolLog(
                            toolName = toolName,
                            argumentsJson = args.toString(),
                            resultSummary = res.summary,
                            success = res.success
                        )
                    ),
                    isError = !res.success
                )
            )
        }
    }

    fun setLanguage(language: AssistantLanguage) {
        viewModelScope.launch { prefsRepo.setLanguage(language) }
    }

    fun setModelId(modelId: String) {
        viewModelScope.launch { prefsRepo.setModelId(modelId) }
    }

    fun setCustomAddendum(addendum: String) {
        viewModelScope.launch { prefsRepo.setCustomAddendum(addendum) }
    }

    fun setTtsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setTtsEnabled(enabled) }
        if (!enabled) {
            tts?.stop()
        }
    }

    fun saveApiKey(rawKey: String) {
        apiKeyVault.saveApiKey(rawKey)
        _hasApiKey.value = apiKeyVault.hasApiKey()
        _maskedApiKey.value = apiKeyVault.getMaskedPreview()
        _keyVerification.value = KeyVerificationState(
            success = if (_hasApiKey.value) true else null,
            message = if (_hasApiKey.value) "Saved securely to Android KeyStore vault." else "API key cleared."
        )
    }

    fun clearApiKey() {
        apiKeyVault.clearApiKey()
        _hasApiKey.value = false
        _maskedApiKey.value = "Not configured"
        _keyVerification.value = KeyVerificationState(message = "API key removed from vault.")
    }

    fun verifySavedApiKey(candidateKey: String? = null) {
        val keyToCheck = candidateKey?.trim()?.takeIf { it.isNotEmpty() } ?: apiKeyVault.getApiKey()
        if (keyToCheck.isNullOrBlank()) {
            _keyVerification.value = KeyVerificationState(
                success = false,
                message = "Enter a Gemini API key first."
            )
            return
        }
        viewModelScope.launch {
            _keyVerification.value = KeyVerificationState(isChecking = true, message = "Testing connection...")
            val (ok, msg) = geminiClient.verifyApiKey(keyToCheck, settingsState.value.modelId)
            if (ok && !candidateKey.isNullOrBlank()) {
                apiKeyVault.saveApiKey(candidateKey)
                _hasApiKey.value = true
                _maskedApiKey.value = apiKeyVault.getMaskedPreview()
            }
            _keyVerification.value = KeyVerificationState(
                isChecking = false,
                success = ok,
                message = msg
            )
        }
    }

    fun setMediaVolume(percent: Int) {
        toolExecutor.setMediaVolumePercent(percent)
    }

    fun toggleFlashlight(enabled: Boolean) {
        toolExecutor.setFlashlight(enabled)
    }

    fun saveMemory(key: String, value: String) {
        viewModelScope.launch { prefsRepo.saveMemory(key, value) }
    }

    fun deleteMemory(key: String) {
        viewModelScope.launch { prefsRepo.deleteMemory(key) }
    }

    fun clearConversationContext() {
        apiHistory.clear()
        viewModelScope.launch { prefsRepo.clearConversationSummary() }
        _messages.value = listOf(
            ChatMessage(
                id = nextId(),
                author = ChatAuthor.JARVIS,
                text = "Conversation context cleared. Ready for your next command in English, Hindi, or Hinglish."
            )
        )
    }

    private fun speakReply(text: String, language: AssistantLanguage) {
        if (!ttsReady) return
        val locale = when (language) {
            AssistantLanguage.HINDI -> Locale("hi", "IN")
            AssistantLanguage.HINGLISH -> Locale("en", "IN")
            else -> Locale.getDefault()
        }
        tts?.language = locale
        tts?.speak(text.take(450), TextToSpeech.QUEUE_FLUSH, null, "jarvis_${System.currentTimeMillis()}")
    }

    private fun buildContextPreamble(settings: JarvisSettingsData): String {
        if (settings.memories.isEmpty() && settings.conversationSummary.isBlank()) return ""
        val sb = StringBuilder("\n\n### Current Device Context Memory\n")
        if (settings.memories.isNotEmpty()) {
            sb.append("Pinned non-sensitive memories:\n")
            settings.memories.forEach { (k, v) -> sb.append("- $k: $v\n") }
        }
        if (settings.conversationSummary.isNotBlank()) {
            sb.append("Session summary: ${settings.conversationSummary}\n")
        }
        return sb.toString()
    }

    private fun buildConversationSummary(): String {
        val recent = _messages.value.takeLast(6)
        return recent.joinToString(" | ") { "${it.author.name}: ${it.text.take(80)}" }
    }

    private fun buildWelcomeMessage(): String {
        val keyStatus = if (apiKeyVault.hasApiKey()) {
            "Gemini API key vault is active."
        } else {
            "To activate AI reasoning, open Settings and enter your personal Gemini API key (stored encrypted on-device, never inside the prompt)."
        }
        return "JARVIS Core online. Speak or type in English, Hindi, or Hinglish. $keyStatus For full screen reading & UI clicks, enable Accessibility Service and Notification Access in Settings."
    }

    private fun appendMessage(msg: ChatMessage) {
        _messages.value = _messages.value + msg
    }

    private fun nextId(): Long = nextMessageId++

    override fun onCleared() {
        toolExecutor.unregister()
        tts?.stop()
        tts?.shutdown()
        super.onCleared()
    }
}
