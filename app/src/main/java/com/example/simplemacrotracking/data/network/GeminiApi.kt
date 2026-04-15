package com.example.simplemacrotracking.data.network

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface GeminiApi {
    @POST("v1beta/models/gemini-2.0-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body body: com.example.simplemacrotracking.data.network.dto.GeminiRequest
    ): retrofit2.Response<com.example.simplemacrotracking.data.network.dto.GeminiResponse>
}

