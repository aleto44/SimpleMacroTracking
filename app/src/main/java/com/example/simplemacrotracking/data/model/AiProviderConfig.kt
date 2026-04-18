package com.example.simplemacrotracking.data.model

import com.squareup.moshi.JsonClass

enum class AiProviderType { GEMINI, GITHUB_COPILOT }

@JsonClass(generateAdapter = true)
data class AiProviderConfig(
    val id: String,
    val type: AiProviderType,
    /** For GEMINI: Gemini API key. For GITHUB_COPILOT: GitHub Personal Access Token. */
    val apiKey: String = "",
    val model: String = "",   // empty = use the provider's default model
    val enabled: Boolean = true
)

/** Metadata for a single AI model. */
data class AiModel(
    val id: String,
    /** True = included in free tier / no extra charge. */
    val isFree: Boolean,
    /** Human-readable cost note, e.g. "Free", "1×", "0.25×", "3×" */
    val costNote: String
)

object AiModels {
    val GEMINI: List<AiModel> = listOf(
        AiModel("gemini-2.5-flash",  isFree = true,  costNote = "Free"),
        AiModel("gemini-2.5-pro",    isFree = false, costNote = "3×"),
        AiModel("gemini-2.0-flash",  isFree = true,  costNote = "Free · 0.1×"),
        AiModel("gemini-1.5-pro",    isFree = false, costNote = "2×"),
        AiModel("gemini-1.5-flash",  isFree = true,  costNote = "Free · 0.25×")
    )

    val GITHUB_COPILOT: List<AiModel> = listOf(
        AiModel("gpt-4o",             isFree = false, costNote = "1×"),
        AiModel("gpt-4o-mini",        isFree = true,  costNote = "Free"),
        AiModel("gpt-4.1",            isFree = false, costNote = "1×"),
        AiModel("gpt-4.1-mini",       isFree = true,  costNote = "Free"),
        AiModel("claude-3.5-sonnet",  isFree = false, costNote = "1×"),
        AiModel("o3-mini",            isFree = false, costNote = "0.33×"),
        AiModel("o4-mini",            isFree = false, costNote = "0.33×")
    )

    fun defaultFor(type: AiProviderType): String = listFor(type).first().id

    fun listFor(type: AiProviderType): List<AiModel> = when (type) {
        AiProviderType.GEMINI        -> GEMINI
        AiProviderType.GITHUB_COPILOT -> GITHUB_COPILOT
    }

    fun find(type: AiProviderType, id: String): AiModel =
        listFor(type).firstOrNull { it.id == id } ?: listFor(type).first()
}



