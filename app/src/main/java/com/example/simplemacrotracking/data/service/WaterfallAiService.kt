package com.example.simplemacrotracking.data.service

import android.util.Log
import com.example.simplemacrotracking.BuildConfig
import com.example.simplemacrotracking.data.model.AiModels
import com.example.simplemacrotracking.data.model.AiProviderConfig
import com.example.simplemacrotracking.data.model.AiProviderType
import com.example.simplemacrotracking.data.network.GeminiApi
import com.example.simplemacrotracking.data.network.GitHubModelsApi
import com.example.simplemacrotracking.data.network.dto.ChatMessage
import com.example.simplemacrotracking.data.network.dto.ChatRequest
import com.example.simplemacrotracking.data.network.dto.GeminiContent
import com.example.simplemacrotracking.data.network.dto.GeminiPart
import com.example.simplemacrotracking.data.network.dto.GeminiRequest
import com.example.simplemacrotracking.data.prefs.SettingsPrefs
import javax.inject.Inject
import javax.inject.Singleton

/** Tries AI providers in user-defined order. Falls through on rate-limit / error. */
@Singleton
class WaterfallAiService @Inject constructor(
    private val settingsPrefs: SettingsPrefs,
    private val geminiApi: GeminiApi,
    private val gitHubModelsApi: GitHubModelsApi
) {

    /** Returns the raw text from whichever provider succeeds first.
     *  Throws [AllProvidersFailedException] if all configured/enabled providers fail. */
    suspend fun generateContent(prompt: String): String {
        val providers = settingsPrefs.aiProviders.filter { it.enabled }
        if (providers.isEmpty()) throw AllProvidersFailedException("No AI providers configured.")

        val errors = mutableListOf<String>()
        for (provider in providers) {
            try {
                return callProvider(provider, prompt)
            } catch (e: Exception) {
                val msg = "${provider.type.name}: ${e.message}"
                if (BuildConfig.DEBUG) Log.w("WaterfallAiService", "Provider failed: $msg", e)
                errors.add(msg)
            }
        }
        throw AllProvidersFailedException("All AI providers failed:\n${errors.joinToString("\n")}")
    }

    private suspend fun callProvider(config: AiProviderConfig, prompt: String): String {
        val model = config.model.ifBlank { AiModels.defaultFor(config.type) }
        return when (config.type) {
            AiProviderType.GEMINI        -> callGemini(config.apiKey, model, prompt)
            AiProviderType.GITHUB_MODELS -> callGitHubModels(config.apiKey, model, prompt)
        }
    }

    /** Test a single provider. Returns a human-readable result message. */
    suspend fun testProvider(config: AiProviderConfig): String {
        val model = config.model.ifBlank { AiModels.defaultFor(config.type) }
        return try {
            when (config.type) {
                AiProviderType.GEMINI -> {
                    val request = GeminiRequest(
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = "Reply with: OK"))))
                    )
                    val response = geminiApi.generateContent(model, config.apiKey, request)
                    when {
                        response.isSuccessful -> "✓ Gemini key is valid (model: $model)"
                        response.code() == 429 -> "✓ Key is valid — rate limit hit (model: $model)\n\nYour key works fine."
                        response.code() == 401 || response.code() == 403 -> "✗ Invalid or unauthorized Gemini API key"
                        response.code() == 404 -> "✗ Model not found: $model"
                        else -> "✗ Error: HTTP ${response.code()}"
                    }
                }
                AiProviderType.GITHUB_MODELS -> {
                    val request = ChatRequest(
                        model = model,
                        messages = listOf(ChatMessage(role = "user", content = "Reply with: OK"))
                    )
                    val response = gitHubModelsApi.chatCompletions(
                        bearerToken = "Bearer ${config.apiKey}",
                        body = request
                    )
                    when {
                        response.isSuccessful -> "✓ GitHub Models PAT is valid (model: $model)"
                        response.code() == 429 -> "✓ PAT is valid — rate limit hit (model: $model)"
                        response.code() == 401 || response.code() == 403 -> "✗ Invalid PAT or missing models:read permission"
                        response.code() == 404 -> "✗ Model not found: $model"
                        else -> "✗ Error: HTTP ${response.code()}"
                    }
                }
            }
        } catch (e: java.io.IOException) {
            "✗ No internet connection"
        } catch (e: Exception) {
            "✗ Error: ${e.message}"
        }
    }

    private suspend fun callGemini(apiKey: String, model: String, prompt: String): String {
        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
        )
        val response = geminiApi.generateContent(model, apiKey, request)
        if (response.isSuccessful) {
            return response.body()?.candidates
                ?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw Exception("Empty response from Gemini")
        }
        when (response.code()) {
            429 -> throw RateLimitException("Gemini rate limit (429)")
            401, 403 -> throw AuthException("Gemini auth error (${response.code()})")
            else -> throw Exception("Gemini error ${response.code()}")
        }
    }

    /**
     * GitHub Models: PAT used directly as Bearer token — no session token exchange needed.
     * Requires a fine-grained PAT with the models:read permission.
     */
    private suspend fun callGitHubModels(githubPat: String, model: String, prompt: String): String {
        val request = ChatRequest(
            model = model,
            messages = listOf(ChatMessage(role = "user", content = prompt))
        )
        val response = gitHubModelsApi.chatCompletions(
            bearerToken = "Bearer $githubPat",
            body = request
        )
        if (response.isSuccessful) {
            return response.body()?.choices
                ?.firstOrNull()?.message?.content
                ?: throw Exception("Empty response from GitHub Models")
        }
        when (response.code()) {
            429  -> throw RateLimitException("GitHub Models rate limit (429)")
            401, 403 -> throw AuthException("GitHub Models auth error (${response.code()}) — check PAT has models:read permission")
            else -> throw Exception("GitHub Models error ${response.code()}")
        }
    }
}

class AllProvidersFailedException(message: String) : Exception(message)
class RateLimitException(message: String) : Exception(message)
class AuthException(message: String) : Exception(message)
