package com.example.simplemacrotracking.data.service

import android.util.Log
import com.example.simplemacrotracking.BuildConfig
import com.example.simplemacrotracking.data.model.AiModels
import com.example.simplemacrotracking.data.model.AiProviderConfig
import com.example.simplemacrotracking.data.model.AiProviderType
import com.example.simplemacrotracking.data.network.CopilotApi
import com.example.simplemacrotracking.data.network.GeminiApi
import com.example.simplemacrotracking.data.network.GitHubApi
import com.example.simplemacrotracking.data.network.dto.ChatMessage
import com.example.simplemacrotracking.data.network.dto.ChatRequest
import com.example.simplemacrotracking.data.network.dto.GeminiContent
import com.example.simplemacrotracking.data.network.dto.GeminiPart
import com.example.simplemacrotracking.data.network.dto.GeminiRequest
import com.example.simplemacrotracking.data.prefs.SettingsPrefs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Tries AI providers in user-defined order. Falls through on rate-limit / error. */
@Singleton
class WaterfallAiService @Inject constructor(
    private val settingsPrefs: SettingsPrefs,
    private val geminiApi: GeminiApi,
    private val copilotApi: CopilotApi,
    private val gitHubApi: GitHubApi
) {

    private val copilotTokenMutex = Mutex()

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
            AiProviderType.GEMINI -> callGemini(config.apiKey, model, prompt)
            AiProviderType.GITHUB_COPILOT -> callCopilot(config.apiKey, model, prompt)
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
                AiProviderType.GITHUB_COPILOT -> {
                    // First validate the PAT by exchanging for a session token
                    val sessionToken = getValidCopilotSessionToken(config.apiKey, forceRefresh = true)
                    val request = ChatRequest(
                        model = model,
                        messages = listOf(ChatMessage(role = "user", content = "Reply with: OK"))
                    )
                    val response = copilotApi.chatCompletions(bearerToken = "Bearer $sessionToken", body = request)
                    when {
                        response.isSuccessful -> "✓ GitHub Copilot key is valid (model: $model)"
                        response.code() == 429 -> "✓ Key is valid — rate limit hit (model: $model)"
                        response.code() == 401 || response.code() == 403 -> "✗ Invalid or unauthorized GitHub token"
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

    private suspend fun callCopilot(githubPat: String, model: String, prompt: String): String {
        val sessionToken = getValidCopilotSessionToken(githubPat)
        val request = ChatRequest(
            model = model,
            messages = listOf(ChatMessage(role = "user", content = prompt))
        )
        val response = copilotApi.chatCompletions(
            bearerToken = "Bearer $sessionToken",
            body = request
        )
        if (response.isSuccessful) {
            return response.body()?.choices
                ?.firstOrNull()?.message?.content
                ?: throw Exception("Empty response from Copilot")
        }
        when (response.code()) {
            429 -> throw RateLimitException("Copilot rate limit (429)")
            401, 403 -> {
                // Invalidate cached token and retry once
                settingsPrefs.copilotSessionToken = ""
                settingsPrefs.copilotSessionTokenExpiresAt = 0L
                val freshToken = getValidCopilotSessionToken(githubPat)
                val retry = copilotApi.chatCompletions(bearerToken = "Bearer $freshToken", body = request)
                if (retry.isSuccessful) {
                    return retry.body()?.choices?.firstOrNull()?.message?.content
                        ?: throw Exception("Empty response from Copilot on retry")
                }
                throw AuthException("Copilot auth error (${retry.code()})")
            }
            else -> throw Exception("Copilot error ${response.code()}")
        }
    }

    private suspend fun getValidCopilotSessionToken(githubPat: String, forceRefresh: Boolean = false): String {
        copilotTokenMutex.withLock {
            val now = System.currentTimeMillis() / 1000L
            val cached = settingsPrefs.copilotSessionToken
            val expiresAt = settingsPrefs.copilotSessionTokenExpiresAt
            if (!forceRefresh && cached.isNotBlank() && expiresAt - now > 120) return cached

            val response = gitHubApi.getCopilotToken("token $githubPat")
            if (response.isSuccessful) {
                val body = response.body()!!
                settingsPrefs.copilotSessionToken = body.token
                settingsPrefs.copilotSessionTokenExpiresAt = body.expiresAt
                return body.token
            }
            throw AuthException("Failed to obtain Copilot session token: HTTP ${response.code()} — make sure your GitHub PAT has the 'copilot' scope")
        }
    }
}

class AllProvidersFailedException(message: String) : Exception(message)
class RateLimitException(message: String) : Exception(message)
class AuthException(message: String) : Exception(message)
