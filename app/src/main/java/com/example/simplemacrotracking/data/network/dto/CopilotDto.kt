package com.example.simplemacrotracking.data.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ── OpenAI-compatible chat completions (used by GitHub Copilot) ──────────────

@JsonClass(generateAdapter = true)
data class ChatRequest(
    @Json(name = "model") val model: String = "gpt-4o",
    @Json(name = "messages") val messages: List<ChatMessage>,
    @Json(name = "max_tokens") val maxTokens: Int = 800,
    @Json(name = "temperature") val temperature: Double = 0.1
)

@JsonClass(generateAdapter = true)
data class ChatMessage(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class ChatResponse(
    @Json(name = "choices") val choices: List<ChatChoice>? = null
)

@JsonClass(generateAdapter = true)
data class ChatChoice(
    @Json(name = "message") val message: ChatMessage? = null
)

// ── GitHub Copilot session token exchange ────────────────────────────────────

@JsonClass(generateAdapter = true)
data class CopilotTokenResponse(
    @Json(name = "token") val token: String = "",
    @Json(name = "expires_at") val expiresAt: Long = 0
)

// ── GitHub user info ──────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class GitHubUser(
    @Json(name = "login") val login: String = "",
    @Json(name = "name") val name: String? = null
)


