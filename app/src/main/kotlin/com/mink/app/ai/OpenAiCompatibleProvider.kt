package com.mink.app.ai

import com.mink.app.core.AppExecutors
import com.mink.app.core.Cancelable
import com.mink.app.core.ErrorCode
import com.mink.app.core.Result
import com.mink.app.net.HttpClient
import com.mink.app.net.SseReader
import org.json.JSONArray
import org.json.JSONObject

class OpenAiCompatibleProvider(
    override val name: String,
    override val providerId: String,
    private val baseUrl: String,
    override val defaultModel: String,
    override val availableModels: List<String>
) : AiProvider {

    override fun streamChat(
        apiKey: String,
        model: String,
        messages: List<AiMessage>,
        systemPrompt: String,
        onChunk: (String) -> Unit,
        onComplete: (AiResponse) -> Unit,
        onError: (Throwable) -> Unit
    ): Cancelable {
        val payload = buildPayload(model, messages, systemPrompt, stream = true)
        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json"
        )

        val fullText = StringBuilder()

        return AppExecutors.executeIo {
            val streamResult = HttpClient.openStream("$baseUrl/chat/completions", headers, payload)
            when (streamResult) {
                is Result.Ok -> {
                    val (conn, inputStream) = streamResult.value
                    val reader = SseReader(
                        conn = conn,
                        inputStream = inputStream,
                        onChunk = { data ->
                            try {
                                val json = JSONObject(data)
                                val choices = json.optJSONArray("choices")
                                if (choices != null && choices.length() > 0) {
                                    val delta = choices.getJSONObject(0).optJSONObject("delta")
                                    val text = delta?.optString("content", "") ?: ""
                                    if (text.isNotEmpty()) {
                                        fullText.append(text)
                                        AppExecutors.main { onChunk(text) }
                                    }
                                }
                            } catch (_: Exception) {}
                        },
                        onDone = {
                            AppExecutors.main {
                                onComplete(AiResponse(fullText.toString(), inputTokens = 0, outputTokens = 0))
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
        val payload = buildPayload(model, messages, systemPrompt, stream = false)
        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json"
        )
        val res = HttpClient.post("$baseUrl/chat/completions", headers, payload, timeoutMs = 45000)
        return when (res) {
            is Result.Ok -> {
                try {
                    val json = JSONObject(res.value.body)
                    val choices = json.getJSONArray("choices")
                    val msg = choices.getJSONObject(0).getJSONObject("message")
                    val content = msg.getString("content")
                    val usage = json.optJSONObject("usage")
                    val promptTokens = usage?.optInt("prompt_tokens", 0) ?: 0
                    val completionTokens = usage?.optInt("completion_tokens", 0) ?: 0
                    Result.Ok(AiResponse(content, promptTokens, completionTokens))
                } catch (e: Exception) {
                    Result.Err("Failed to parse completion: ${e.message}", code = ErrorCode.PARSE, cause = e)
                }
            }
            is Result.Err -> Result.Err(res.message, res.code, res.cause)
        }
    }

    private fun buildPayload(
        model: String,
        messages: List<AiMessage>,
        systemPrompt: String,
        stream: Boolean
    ): String {
        val json = JSONObject()
        json.put("model", model)
        json.put("stream", stream)

        val msgArray = JSONArray()
        if (systemPrompt.isNotBlank()) {
            val sysObj = JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            }
            msgArray.put(sysObj)
        }
        for (m in messages) {
            val mObj = JSONObject().apply {
                put("role", m.role)
                put("content", m.content)
            }
            msgArray.put(mObj)
        }
        json.put("messages", msgArray)
        return json.toString()
    }
}
