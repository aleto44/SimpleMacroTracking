package com.example.simplemacrotracking.data.network

import com.example.simplemacrotracking.data.network.dto.ChatRequest
import com.example.simplemacrotracking.data.network.dto.ChatResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface CopilotApi {
    @POST("chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") bearerToken: String,
        @Header("Editor-Version") editorVersion: String = "vscode/1.85.0",
        @Header("Copilot-Integration-Id") integrationId: String = "vscode-chat",
        @Header("OpenAI-Intent") intent: String = "conversation-panel",
        @Body body: ChatRequest
    ): Response<ChatResponse>
}

