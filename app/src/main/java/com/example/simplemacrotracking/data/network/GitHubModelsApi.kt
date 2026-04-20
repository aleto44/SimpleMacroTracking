package com.example.simplemacrotracking.data.network

import com.example.simplemacrotracking.data.network.dto.ChatRequest
import com.example.simplemacrotracking.data.network.dto.ChatResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * GitHub Models inference API.
 * Base URL: https://models.github.ai/inference/
 *
 * Authentication: GitHub PAT with models:read permission, used directly as Bearer token.
 * No Copilot subscription required — available to all GitHub users (with free-tier rate limits).
 *
 * Model IDs use provider/model-name format, e.g. "openai/gpt-4.1", "meta/llama-3.3-70b-instruct".
 */
interface GitHubModelsApi {
    @POST("chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") bearerToken: String,          // "Bearer <github_pat>"
        @Header("X-GitHub-Api-Version") apiVersion: String = "2022-11-28",
        @Body body: ChatRequest
    ): Response<ChatResponse>
}

