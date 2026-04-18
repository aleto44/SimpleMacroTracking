package com.example.simplemacrotracking.data.network

import com.example.simplemacrotracking.data.network.dto.CopilotTokenResponse
import com.example.simplemacrotracking.data.network.dto.GitHubUser
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

/** GitHub API (base: https://api.github.com/) */
interface GitHubApi {

    @GET("copilot_internal/v2/token")
    suspend fun getCopilotToken(
        @Header("Authorization") token: String  // "token <github_pat>"
    ): Response<CopilotTokenResponse>

    @GET("user")
    suspend fun getUser(
        @Header("Authorization") token: String  // "token <github_pat>"
    ): Response<GitHubUser>
}
