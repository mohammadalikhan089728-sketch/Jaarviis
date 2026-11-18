package com.example.ai

import com.example.core.JarvisSystemPrompt
import com.example.data.JarvisSettingsData
import com.example.tools.DeviceToolExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ExecutedToolLog(
    val toolName: String,
    val argumentsJson: String,
    val resultSummary: String,
    val success: Boolean
)

sealed interface GeminiResult {
    data class Success(
        val replyText: String,
        val toolLogs: List<ExecutedToolLog>,
        val updatedHistory: List<JSONObject>,
        val modelUsed: String
    ) : GeminiResult

    data class Failure(
        val message: String,
        val isAuthError: Boolean = false
    ) : GeminiResult
}

class GeminiToolClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun sendTurnWithTools(
        apiKey: String,
        modelId: String,
        systemPrompt: String,
        history: List<JSONObject>,
        userMessage: String,
        toolExecutor: DeviceToolExecutor,
        onToolStep: (ExecutedToolLog) -> Unit
    ): GeminiResult {
        val workingHistory = history.toMutableList()
        workingHistory.add(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
        )

        val allToolLogs = mutableListOf<ExecutedToolLog>()
        val toolsArray = JarvisSystemPrompt.buildGeminiToolsJson()
        var lastModelUsed = modelId.trim().ifEmpty { JarvisSettingsData.DEFAULT_GEMINI_MODEL }

        try {
            return withTimeout(45_000L) {
                var hops = 0
                while (hops < MAX_TOOL_HOPS) {
                    hops++
                    val (responseJson, usedModel) = executeWithSilentFallback(
                        apiKey = apiKey,
                        primaryModel = lastModelUsed,
                        systemPrompt = systemPrompt,
                        toolsArray = toolsArray,
                        contents = JSONArray(workingHistory)
                    )
                    lastModelUsed = usedModel

                    if (responseJson.has("error")) {
                        val err = responseJson.getJSONObject("error")
                        val code = err.optInt("code", 0)
                        val msg = err.optString("message", "Gemini API request failed.")
                        return@withTimeout GeminiResult.Failure(
                            message = msg,
                            isAuthError = code == 400 || code == 401 || code == 403 ||
                                msg.contains("API key", ignoreCase = true)
                        )
                    }

                    val candidates = responseJson.optJSONArray("candidates")
                    if (candidates == null || candidates.length() == 0) {
                        return@withTimeout GeminiResult.Failure("No response candidate returned by Gemini.")
                    }

                    val modelContent = candidates.getJSONObject(0).optJSONObject("content")
                        ?: return@withTimeout GeminiResult.Failure("Empty content returned by Gemini.")
                    val parts = modelContent.optJSONArray("parts") ?: JSONArray()

                    workingHistory.add(modelContent)

                    val functionCalls = mutableListOf<Pair<String, JSONObject>>()
                    val textChunks = mutableListOf<String>()

                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        if (part.has("functionCall")) {
                            val fc = part.getJSONObject("functionCall")
                            val fnName = fc.optString("name")
                            val fnArgs = fc.optJSONObject("args") ?: JSONObject()
                            if (fnName.isNotBlank()) {
                                functionCalls.add(fnName to fnArgs)
                            }
                        } else if (part.has("text")) {
                            val t = part.optString("text")
                            if (t.isNotBlank()) textChunks.add(t)
                        }
                    }

                    if (functionCalls.isEmpty()) {
                        val finalReply = textChunks.joinToString("\n").trim()
                            .ifEmpty { "Done." }
                        return@withTimeout GeminiResult.Success(
                            replyText = finalReply,
                            toolLogs = allToolLogs,
                            updatedHistory = trimHistory(workingHistory),
                            modelUsed = lastModelUsed
                        )
                    }

                    val responseParts = JSONArray()
                    for ((fnName, fnArgs) in functionCalls) {
                        val execResult = toolExecutor.executeTool(fnName, fnArgs)
                        val logEntry = ExecutedToolLog(
                            toolName = fnName,
                            argumentsJson = fnArgs.toString(),
                            resultSummary = execResult.summary,
                            success = execResult.success
                        )
                        allToolLogs.add(logEntry)
                        onToolStep(logEntry)

                        responseParts.put(
                            JSONObject().put(
                                "functionResponse",
                                JSONObject()
                                    .put("name", fnName)
                                    .put("response", execResult.jsonResponse)
                            )
                        )
                    }

                    workingHistory.add(
                        JSONObject()
                            .put("role", "user")
                            .put("parts", responseParts)
                    )
                }

                GeminiResult.Failure("Exceeded maximum tool execution hops ($MAX_TOOL_HOPS).")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return GeminiResult.Failure(
                message = e.localizedMessage ?: "Network request to Gemini failed."
            )
        }
    }

    suspend fun verifyApiKey(apiKey: String, modelId: String): Pair<Boolean, String> {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) return false to "Please enter a Gemini API key."

        return try {
            withTimeout(20_000L) {
                val contents = JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", "Reply with OK")))
                )
                val (res, usedModel) = executeWithSilentFallback(
                    apiKey = cleanKey,
                    primaryModel = modelId,
                    systemPrompt = null,
                    toolsArray = null,
                    contents = contents
                )
                if (res.has("error")) {
                    val msg = res.getJSONObject("error").optString("message", "Verification failed.")
                    false to msg
                } else {
                    true to "Verified with $usedModel"
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false to (e.localizedMessage ?: "Network error while verifying key.")
        }
    }

    private suspend fun executeWithSilentFallback(
        apiKey: String,
        primaryModel: String,
        systemPrompt: String?,
        toolsArray: JSONArray?,
        contents: JSONArray
    ): Pair<JSONObject, String> {
        val candidates = buildModelFallbackChain(primaryModel)
        var lastErrorJson = JSONObject()
        var lastModel = candidates.first()

        for ((index, candidateModel) in candidates.withIndex()) {
            lastModel = candidateModel
            var responseJson = runSingleModelCall(
                apiKey = apiKey,
                modelId = candidateModel,
                systemPrompt = systemPrompt,
                toolsArray = toolsArray,
                contents = contents,
                includeThinkingConfig = true
            )

            // If model rejects thinkingConfig parameter specifically, retry immediately without it
            if (isThinkingConfigUnsupported(responseJson)) {
                responseJson = runSingleModelCall(
                    apiKey = apiKey,
                    modelId = candidateModel,
                    systemPrompt = systemPrompt,
                    toolsArray = toolsArray,
                    contents = contents,
                    includeThinkingConfig = false
                )
            }

            if (!responseJson.has("error")) {
                return responseJson to candidateModel
            }

            lastErrorJson = responseJson

            // Auth errors should surface immediately; transient spikes/429/503/404 fall back silently within < 1s
            if (isAuthOrBadKeyError(responseJson)) {
                return responseJson to candidateModel
            }

            if (isRetryableOrFallbackError(responseJson) && index < candidates.lastIndex) {
                delay(SILENT_FALLBACK_DELAY_MS)
                continue
            }

            // If a single model had a transient hiccup on the last fallback, give it one quick retry
            if (isRetryableOrFallbackError(responseJson) && index == candidates.lastIndex) {
                delay(SILENT_FALLBACK_DELAY_MS)
                val finalRetry = runSingleModelCall(
                    apiKey = apiKey,
                    modelId = candidateModel,
                    systemPrompt = systemPrompt,
                    toolsArray = toolsArray,
                    contents = contents,
                    includeThinkingConfig = false
                )
                return finalRetry to candidateModel
            }

            return responseJson to candidateModel
        }

        return lastErrorJson to lastModel
    }

    private suspend fun runSingleModelCall(
        apiKey: String,
        modelId: String,
        systemPrompt: String?,
        toolsArray: JSONArray?,
        contents: JSONArray,
        includeThinkingConfig: Boolean
    ): JSONObject {
        val genConfig = JSONObject().apply {
            put("temperature", 0.5)
            put("maxOutputTokens", 320)
            if (includeThinkingConfig) {
                put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            }
        }

        val payload = JSONObject().apply {
            if (!systemPrompt.isNullOrBlank()) {
                put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemPrompt))
                    )
                )
            }
            if (toolsArray != null) {
                put("tools", toolsArray)
            }
            put("generationConfig", genConfig)
            put("contents", contents)
        }

        return try {
            executePost(apiKey, modelId, payload)
        } catch (e: IOException) {
            JSONObject().put(
                "error",
                JSONObject()
                    .put("code", 503)
                    .put("message", e.localizedMessage ?: "Temporary network spike")
            )
        }
    }

    private fun buildModelFallbackChain(primaryModel: String): List<String> {
        val clean = primaryModel.trim().ifEmpty { JarvisSettingsData.DEFAULT_GEMINI_MODEL }
        val ordered = linkedSetOf(clean)
        when (clean) {
            "gemini-3.5-flash" -> {
                ordered.add("gemini-3.5-flash-lite")
                ordered.add("gemini-3-flash-preview")
            }
            "gemini-3.5-flash-lite" -> {
                ordered.add("gemini-3.5-flash")
                ordered.add("gemini-3-flash-preview")
            }
            "gemini-3.5-pro" -> {
                ordered.add("gemini-3.5-flash")
                ordered.add("gemini-3.5-flash-lite")
            }
            else -> {
                ordered.add("gemini-3.5-flash")
                ordered.add("gemini-3.5-flash-lite")
            }
        }
        return ordered.toList()
    }

    private fun isThinkingConfigUnsupported(json: JSONObject): Boolean {
        if (!json.has("error")) return false
        val err = json.optJSONObject("error") ?: return false
        val msg = err.optString("message").lowercase()
        return err.optInt("code") == 400 &&
            (msg.contains("thinking") || msg.contains("thinkingbudget") || msg.contains("unknown name"))
    }

    private fun isAuthOrBadKeyError(json: JSONObject): Boolean {
        val err = json.optJSONObject("error") ?: return false
        val code = err.optInt("code", 0)
        val msg = err.optString("message").lowercase()
        return (code == 400 || code == 401 || code == 403) &&
            (msg.contains("api key") || msg.contains("permission") || msg.contains("unauthorized"))
    }

    private fun isRetryableOrFallbackError(json: JSONObject): Boolean {
        val err = json.optJSONObject("error") ?: return false
        val code = err.optInt("code", 0)
        val msg = err.optString("message").lowercase()
        return code in listOf(404, 429, 500, 502, 503, 504) ||
            msg.contains("high demand") ||
            msg.contains("overloaded") ||
            msg.contains("unavailable") ||
            msg.contains("resource exhausted") ||
            msg.contains("rate limit") ||
            msg.contains("quota") ||
            msg.contains("try again") ||
            msg.contains("spike") ||
            msg.contains("not found")
    }

    private suspend fun executePost(
        apiKey: String,
        modelId: String,
        bodyJson: JSONObject
    ): JSONObject = withContext(Dispatchers.IO) {
        val safeModel = modelId.trim().ifEmpty { JarvisSettingsData.DEFAULT_GEMINI_MODEL }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$safeModel:generateContent"
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (raw.isBlank()) {
                return@withContext JSONObject().put(
                    "error",
                    JSONObject()
                        .put("code", response.code)
                        .put("message", "HTTP ${response.code}: Empty server response")
                )
            }
            return@withContext try {
                JSONObject(raw)
            } catch (_: Exception) {
                JSONObject().put(
                    "error",
                    JSONObject()
                        .put("code", response.code)
                        .put("message", "Unexpected response (HTTP ${response.code})")
                )
            }
        }
    }

    private fun trimHistory(history: List<JSONObject>): List<JSONObject> {
        return if (history.size <= MAX_HISTORY_ITEMS) {
            history
        } else {
            history.takeLast(MAX_HISTORY_ITEMS)
        }
    }

    private companion object {
        const val MAX_TOOL_HOPS = 6
        const val MAX_HISTORY_ITEMS = 24
        const val SILENT_FALLBACK_DELAY_MS = 350L
    }
}
