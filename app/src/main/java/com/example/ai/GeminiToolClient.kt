package com.example.ai

import com.example.core.JarvisSystemPrompt
import com.example.tools.DeviceToolExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
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
        val updatedHistory: List<JSONObject>
    ) : GeminiResult

    data class Failure(
        val message: String,
        val isAuthError: Boolean = false
    ) : GeminiResult
}

class GeminiToolClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
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

        try {
            return withTimeout(60_000L) {
                var hops = 0
                while (hops < MAX_TOOL_HOPS) {
                    hops++
                    val payload = JSONObject().apply {
                        put(
                            "systemInstruction",
                            JSONObject().put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", systemPrompt))
                            )
                        )
                        put("tools", toolsArray)
                        put("contents", JSONArray(workingHistory))
                    }

                    val responseJson = executePost(apiKey, modelId, payload)
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
                            .ifEmpty { "Action completed." }
                        return@withTimeout GeminiResult.Success(
                            replyText = finalReply,
                            toolLogs = allToolLogs,
                            updatedHistory = trimHistory(workingHistory)
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
                val payload = JSONObject().apply {
                    put(
                        "contents",
                        JSONArray().put(
                            JSONObject()
                                .put("role", "user")
                                .put("parts", JSONArray().put(JSONObject().put("text", "Reply with OK")))
                        )
                    )
                }
                val res = executePost(cleanKey, modelId, payload)
                if (res.has("error")) {
                    val msg = res.getJSONObject("error").optString("message", "Verification failed.")
                    false to msg
                } else {
                    true to "Verified with $modelId"
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false to (e.localizedMessage ?: "Network error while verifying key.")
        }
    }

    private suspend fun executePost(
        apiKey: String,
        modelId: String,
        bodyJson: JSONObject
    ): JSONObject = withContext(Dispatchers.IO) {
        val safeModel = modelId.trim().ifEmpty { "gemini-2.5-flash" }
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
    }
}
