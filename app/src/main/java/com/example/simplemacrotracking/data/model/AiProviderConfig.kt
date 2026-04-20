package com.example.simplemacrotracking.data.model

import com.squareup.moshi.JsonClass

enum class AiProviderType { GEMINI, GITHUB_MODELS }

@JsonClass(generateAdapter = true)
data class AiProviderConfig(
    val id: String,
    val type: AiProviderType,
    /** For GEMINI: Gemini API key. For GITHUB_MODELS: GitHub PAT with models:read permission. */
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

    /** GitHub Models — PAT with models:read scope, no Copilot subscription required.
     *  Model IDs use the provider/model-name format required by the GitHub Models API. */
    val GITHUB_MODELS: List<AiModel> = listOf(
        AiModel("openai/gpt-4.1",                    isFree = true,  costNote = "Free tier"),
        AiModel("openai/gpt-4.1-mini",               isFree = true,  costNote = "Free tier"),
        AiModel("openai/gpt-4o",                     isFree = true,  costNote = "Free tier"),
        AiModel("openai/gpt-4o-mini",                isFree = true,  costNote = "Free tier"),
        AiModel("openai/o4-mini",                    isFree = true,  costNote = "Free tier"),
        AiModel("openai/o3-mini",                    isFree = true,  costNote = "Free tier"),
        AiModel("meta/llama-3.3-70b-instruct",       isFree = true,  costNote = "Free tier"),
        AiModel("meta/llama-4-scout",                isFree = true,  costNote = "Free tier"),
        AiModel("meta/llama-4-maverick",             isFree = true,  costNote = "Free tier"),
        AiModel("deepseek/deepseek-r1",              isFree = true,  costNote = "Free tier"),
        AiModel("deepseek/deepseek-v3",              isFree = true,  costNote = "Free tier"),
        AiModel("mistral-ai/mistral-small",          isFree = true,  costNote = "Free tier"),
        AiModel("anthropic/claude-3-5-sonnet",       isFree = false, costNote = "Copilot Pro")
    )

    fun defaultFor(type: AiProviderType): String = listFor(type).first().id

    fun listFor(type: AiProviderType): List<AiModel> = when (type) {
        AiProviderType.GEMINI        -> GEMINI
        AiProviderType.GITHUB_MODELS -> GITHUB_MODELS
    }

    fun find(type: AiProviderType, id: String): AiModel =
        listFor(type).firstOrNull { it.id == id } ?: listFor(type).first()
}
