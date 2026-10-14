package com.mink.app.ai

import com.mink.app.core.AppExecutors
import com.mink.app.core.Cancelable
import com.mink.app.core.ErrorCode
import com.mink.app.core.Result
import com.mink.app.net.HttpClient
import com.mink.app.net.SseReader
import org.json.JSONArray
import org.json.JSONObject

class GeminiProvider : AiProvider {

    override val name: String = "Google Gemini"
    override val providerId: String = "gemini"
    override val defaultModel: String = "gemini-1.5-flash"
    override val availableModels: List<String> = listOf(
        "gemini-1.5-flash",
        "gemini-1.5-pro"
    )

    override fun streamChat(
        apiKey: String,
        model: String,
        messages: List<AiMessage>,
        systemPrompt: String,
        onChunk: (String) -> Unit,
        onComplete: (AiResponse) -> Unit,
        onError: (Throwable) -> Unit
    ): Cancelable {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey"
        val payload = buildPayload(messages, systemPrompt)
        val headers = mapOf("Content-Type" to "application/json")
        val fullText = StringBuilder()

        return AppExecutors.executeIo {
            val streamResult = HttpClient.openStream(url, headers, payload)
            when (streamResult) {
                is Result.Ok -> {
                    val (conn, inputStream) = streamResult.value
                    val reader = SseReader(
                        conn = conn,
                        inputStream = inputStream,
                        onChunk = { data ->
                            try {
                                val json = JSONObject(data)
                                val candidates = json.optJSONArray("candidates")
                                if (candidates != null && candidates.length() > 0) {
                                    val content = candidates.getJSONObject(0).optJSONObject("content")
                                    val parts = content?.optJSONArray("parts")
                                    if (parts != null && parts.length() > 0) {
                                        val text = parts.getJSONObject(0).optString("text", "")
                                        if (text.isNotEmpty()) {
                                            fullText.append(text)
                                            AppExecutors.main { onChunk(text) }
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        },
                        onDone = {
                            AppExecutors.main {
                                onComplete(AiResponse(fullText.toString()))
                            }
                        },
                        onError = { err ->
                            AppExecutors.main { onError(err) }
                        }
                    )
                    reader.run()
                }
                is Result.Err -> {
                    AppExecutors.main { onError(Exception(streamResult.message)) }
                }
            }
        }
    }

    override fun completeChat(
        apiKey: String,
        model: String,
        messages: List<AiMessage>,
        systemPrompt: String
    ): Result<AiResponse> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val payload = buildPayload(messages, systemPrompt)
        val headers = mapOf("Content-Type" to "application/json")
        val res = HttpClient.post(url, headers, payload, timeoutMs = 45000)
        return when (res) {
            is Result.Ok -> {
                try {
                    val json = JSONObject(res.value.body)
                    val candidates = json.getJSONArray("candidates")
                    val content = candidates.getJSONObject(0).getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    val text = parts.getJSONObject(0).getString("text")
                    val usageMetadata = json.optJSONObject("usageMetadata")
                    val promptTokens = usageMetadata?.optInt("promptTokenCount", 0) ?: 0
                    val candTokens = usageMetadata?.optInt("candidatesTokenCount", 0) ?: 0
                    Result.Ok(AiResponse(text, promptTokens, candTokens))
                } catch (e: Exception) {
                    Result.Err("Failed to parse Gemini response: ${e.message}", code = ErrorCode.PARSE, cause = e)
                }
            }
            is Result.Err -> Result.Err(res.message, res.code, res.cause)
        }
    }

    private fun buildPayload(messages: List<AiMessage>, systemPrompt: String): String {
        val root = JSONObject()
        if (systemPrompt.isNotBlank()) {
            val sysInstruction = JSONObject()
            val sysParts = JSONArray()
            sysParts.put(JSONObject().apply { put("text", systemPrompt) })
            sysInstruction.put("parts", sysParts)
            root.put("system_instruction", sysInstruction)
        }

        val contents = JSONArray()
        for (m in messages) {
            val contentObj = JSONObject()
            contentObj.put("role", if (m.role == "assistant") "model" else "user")
            val partsArr = JSONArray()
            partsArr.put(JSONObject().apply { put("text", m.content) })
            contentObj.put("parts", partsArr)
            contents.put(contentObj)
        }
        root.put("contents", contents)
        return root.toString()
    }
}
