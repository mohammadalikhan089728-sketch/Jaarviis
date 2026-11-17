package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.AssistantLanguage
import com.example.security.PrivacyGuard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jarvis_preferences"
)

data class JarvisSettingsData(
    val language: AssistantLanguage = AssistantLanguage.AUTO,
    val modelId: String = DEFAULT_GEMINI_MODEL,
    val customAddendum: String = "",
    val ttsEnabled: Boolean = false,
    val memories: Map<String, String> = emptyMap(),
    val conversationSummary: String = ""
) {
    companion object {
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
    }
}

class JarvisPreferencesRepository(private val context: Context) {

    private val keyLanguage = stringPreferencesKey("assistant_language")
    private val keyModel = stringPreferencesKey("gemini_model_id")
    private val keyAddendum = stringPreferencesKey("custom_prompt_addendum")
    private val keyTts = booleanPreferencesKey("tts_auto_speak")
    private val keyMemories = stringPreferencesKey("pinned_memories_json")
    private val keySummary = stringPreferencesKey("conversation_summary")

    val settingsFlow: Flow<JarvisSettingsData> = context.jarvisDataStore.data.map { prefs ->
        val langName = prefs[keyLanguage] ?: AssistantLanguage.AUTO.name
        val language = AssistantLanguage.entries.firstOrNull { it.name == langName }
            ?: AssistantLanguage.AUTO
        val model = prefs[keyModel]?.takeIf { it.isNotBlank() }
            ?: JarvisSettingsData.DEFAULT_GEMINI_MODEL
        val addendum = prefs[keyAddendum].orEmpty()
        val tts = prefs[keyTts] ?: false
        val memories = parseMemories(prefs[keyMemories])
        val summary = prefs[keySummary].orEmpty()

        JarvisSettingsData(
            language = language,
            modelId = model,
            customAddendum = addendum,
            ttsEnabled = tts,
            memories = memories,
            conversationSummary = summary
        )
    }

    suspend fun getSnapshot(): JarvisSettingsData = settingsFlow.first()

    suspend fun setLanguage(language: AssistantLanguage) {
        context.jarvisDataStore.edit { it[keyLanguage] = language.name }
    }

    suspend fun setModelId(modelId: String) {
        context.jarvisDataStore.edit {
            it[keyModel] = modelId.trim().ifEmpty { JarvisSettingsData.DEFAULT_GEMINI_MODEL }
        }
    }

    suspend fun setCustomAddendum(addendum: String) {
        context.jarvisDataStore.edit { it[keyAddendum] = addendum }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { it[keyTts] = enabled }
    }

    suspend fun saveMemory(key: String, value: String): Boolean {
        val cleanKey = key.trim()
        val cleanValue = value.trim()
        if (cleanKey.isEmpty() || cleanValue.isEmpty()) return false
        if (looksLikeSensitiveCredential(cleanKey, cleanValue)) return false

        val redacted = PrivacyGuard.redactSensitiveText(cleanValue)
        if (redacted.redactedCount > 0) return false

        context.jarvisDataStore.edit { prefs ->
            val current = parseMemories(prefs[keyMemories]).toMutableMap()
            current[cleanKey] = redacted.sanitizedText
            prefs[keyMemories] = JSONObject(current as Map<*, *>).toString()
        }
        return true
    }

    suspend fun deleteMemory(key: String): Boolean {
        var removed = false
        context.jarvisDataStore.edit { prefs ->
            val current = parseMemories(prefs[keyMemories]).toMutableMap()
            if (current.remove(key.trim()) != null) {
                removed = true
                prefs[keyMemories] = JSONObject(current as Map<*, *>).toString()
            }
        }
        return removed
    }

    suspend fun setConversationSummary(summary: String) {
        val sanitized = PrivacyGuard.redactSensitiveText(summary).sanitizedText
        context.jarvisDataStore.edit { it[keySummary] = sanitized }
    }

    suspend fun clearConversationSummary() {
        context.jarvisDataStore.edit { it.remove(keySummary) }
    }

    private fun parseMemories(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val json = JSONObject(raw)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    put(k, json.optString(k))
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun looksLikeSensitiveCredential(key: String, value: String): Boolean {
        val combined = "${key.lowercase()} ${value.lowercase()}"
        val forbiddenTerms = listOf(
            "password", "passwd", "pin", "mpin", "upi", "otp", "cvv", "cvc",
            "api_key", "apikey", "gemini_key", "aiza", "card_number", "credit_card", "debit_card"
        )
        return forbiddenTerms.any { combined.contains(it) }
    }
}
