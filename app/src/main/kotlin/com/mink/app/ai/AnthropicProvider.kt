package com.mink.app.ai

import com.mink.app.core.AppExecutors
import com.mink.app.core.Cancelable
import com.mink.app.core.ErrorCode
import com.mink.app.core.Result
import com.mink.app.net.HttpClient
import com.mink.app.net.SseReader
import org.json.JSONArray
import org.json.JSONObject

class AnthropicProvider : AiProvider {

    override val name: String = "Anthropic Claude"
    override val providerId: String = "anthropic"
    override val defaultModel: String = "claude-3-5-haiku-20241022"
    override val availableModels: List<String> = listOf(
        "claude-3-5-haiku-20241022",
        "claude-3-5-sonnet-20241022"
    )

    private val apiUrl = "https://api.anthropic.com/v1/messages"

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
            "x-api-key" to apiKey,
            "anthropic-version" to "2023-06-01",
            "Content-Type" to "application/json"
        )
        val fullText = StringBuilder()

        return AppExecutors.executeIo {
            val streamResult = HttpClient.openStream(apiUrl, headers, payload)
            when (streamResult) {
                is Result.Ok -> {
                    val (conn, inputStream) = streamResult.value
                    val reader = SseReader(
                        conn = conn,
                        inputStream = inputStream,
                        onChunk = { data ->
                            try {
                                val json = JSONObject(data)
                                val type = json.optString("type")
                                if (type == "content_block_delta") {
                                    val delta = json.optJSONObject("delta")
                                    val text = delta?.optString("text", "") ?: ""
                                    if (text.isNotEmpty()) {
                                        fullText.append(text)
                                        AppExecutors.main { onChunk(text) }
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
        val payload = buildPayload(model, messages, systemPrompt, stream = false)
        val headers = mapOf(
            "x-api-key" to apiKey,
            "anthropic-version" to "2023-06-01",
            "Content-Type" to "application/json"
        )
        val res = HttpClient.post(apiUrl, headers, payload, timeoutMs = 45000)
        return when (res) {
            is Result.Ok -> {
                try {
                    val json = JSONObject(res.value.body)
                    val contentArr = json.getJSONArray("content")
                    val text = contentArr.getJSONObject(0).getString("text")
                    val usage = json.optJSONObject("usage")
                    val inTokens = usage?.optInt("input_tokens", 0) ?: 0
                    val outTokens = usage?.optInt("output_tokens", 0) ?: 0
                    Result.Ok(AiResponse(text, inTokens, outTokens))
                } catch (e: Exception) {
                    Result.Err("Failed to parse Claude response: ${e.message}", code = ErrorCode.PARSE, cause = e)
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
        json.put("max_tokens", 4096)
        json.put("stream", stream)
        if (systemPrompt.isNotBlank()) {
            json.put("system", systemPrompt)
        }
        val msgArray = JSONArray()
        for (m in messages) {
            if (m.role != "system") {
                val obj = JSONObject().apply {
                    put("role", if (m.role == "assistant") "assistant" else "user")
                    put("content", m.content)
                }
                msgArray.put(obj)
            }
        }
        json.put("messages", msgArray)
        return json.toString()
    }
}
