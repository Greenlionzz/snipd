package com.mink.app.ai

import com.mink.app.core.Cancelable
import com.mink.app.core.Result

data class AiStreamChunk(
    val delta: String,
    val isComplete: Boolean = false,
    val finishReason: String? = null
)

data class AiResponse(
    val content: String,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0
)

data class AiMessage(
    val role: String, // system, user, assistant
    val content: String
)

interface AiProvider {
    val name: String
    val providerId: String
    val defaultModel: String
    val availableModels: List<String>

    fun streamChat(
        apiKey: String,
        model: String,
        messages: List<AiMessage>,
        systemPrompt: String = "",
        onChunk: (String) -> Unit,
        onComplete: (AiResponse) -> Unit,
        onError: (Throwable) -> Unit
    ): Cancelable

    fun completeChat(
        apiKey: String,
        model: String,
        messages: List<AiMessage>,
        systemPrompt: String = ""
    ): Result<AiResponse>
}
