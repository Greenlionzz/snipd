package com.mink.app.ai

import android.content.Context
import android.content.SharedPreferences
import com.mink.app.core.AppGraph
import com.mink.app.core.Cancelable
import com.mink.app.core.ErrorCode
import com.mink.app.core.Result
import com.mink.app.data.model.AiUsage

class AiService(private val context: Context) {

    val secureKeyStore = SecureKeyStore(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("tuned_ai_settings", Context.MODE_PRIVATE)

    val providers: Map<String, AiProvider> = mapOf(
        "openai" to OpenAiCompatibleProvider(
            name = "OpenAI",
            providerId = "openai",
            baseUrl = "https://api.openai.com/v1",
            defaultModel = "gpt-4o-mini",
            availableModels = listOf("gpt-4o-mini", "gpt-4o")
        ),
        "anthropic" to AnthropicProvider(),
        "gemini" to GeminiProvider(),
        "groq" to OpenAiCompatibleProvider(
            name = "Groq",
            providerId = "groq",
            baseUrl = "https://api.groq.com/openai/v1",
            defaultModel = "llama-3.3-70b-versatile",
            availableModels = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant")
        ),
        "openrouter" to OpenAiCompatibleProvider(
            name = "OpenRouter",
            providerId = "openrouter",
            baseUrl = "https://openrouter.ai/api/v1",
            defaultModel = "meta-llama/llama-3.3-70b-instruct",
            availableModels = listOf("meta-llama/llama-3.3-70b-instruct", "google/gemini-flash-1.5")
        )
    )

    var activeProviderId: String
        get() = prefs.getString("active_provider", "openai") ?: "openai"
        set(value) = prefs.edit().putString("active_provider", value).apply()

    fun getSelectedModel(providerId: String): String {
        val provider = providers[providerId] ?: return "gpt-4o-mini"
        return prefs.getString("model_$providerId", provider.defaultModel) ?: provider.defaultModel
    }

    fun setSelectedModel(providerId: String, model: String) {
        prefs.edit().putString("model_$providerId", model).apply()
    }

    fun getApiKey(providerId: String): String? {
        return secureKeyStore.get("api_key_$providerId")
    }

    fun setApiKey(providerId: String, key: String) {
        secureKeyStore.put("api_key_$providerId", key)
    }

    fun getActiveProvider(): Pair<AiProvider, String> {
        val provId = activeProviderId
        val provider = providers[provId] ?: providers["openai"]!!
        val apiKey = getApiKey(provider.providerId) ?: ""
        return Pair(provider, apiKey)
    }

    fun stream(
        feature: String,
        messages: List<AiMessage>,
        systemPrompt: String = "",
        onChunk: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit
    ): Cancelable {
        val (provider, apiKey) = getActiveProvider()
        if (apiKey.isBlank()) {
            onError(IllegalStateException("No API key configured for ${provider.name}. Go to Settings -> AI."))
            return Cancelable.NOOP
        }
        val model = getSelectedModel(provider.providerId)

        return provider.streamChat(
            apiKey = apiKey,
            model = model,
            messages = messages,
            systemPrompt = systemPrompt,
            onChunk = onChunk,
            onComplete = { resp ->
                recordUsage(provider.providerId, model, feature, resp.inputTokens, resp.outputTokens)
                onComplete(resp.content)
            },
            onError = onError
        )
    }

    fun complete(
        feature: String,
        messages: List<AiMessage>,
        systemPrompt: String = ""
    ): Result<String> {
        val (provider, apiKey) = getActiveProvider()
        if (apiKey.isBlank()) {
            return Result.Err("No API key configured for ${provider.name}. Go to Settings.", code = ErrorCode.NO_KEY)
        }
        val model = getSelectedModel(provider.providerId)
        val res = provider.completeChat(apiKey, model, messages, systemPrompt)
        return when (res) {
            is Result.Ok -> {
                recordUsage(provider.providerId, model, feature, res.value.inputTokens, res.value.outputTokens)
                Result.Ok(res.value.content)
            }
            is Result.Err -> Result.Err(res.message, res.code, res.cause)
        }
    }

    private fun recordUsage(provider: String, model: String, feature: String, inTokens: Int, outTokens: Int) {
        try {
            AppGraph.get().miscDao.recordAiUsage(
                AiUsage(
                    provider = provider,
                    model = model,
                    feature = feature,
                    inputTokens = inTokens,
                    outputTokens = outTokens
                )
            )
        } catch (_: Exception) {}
    }
}
