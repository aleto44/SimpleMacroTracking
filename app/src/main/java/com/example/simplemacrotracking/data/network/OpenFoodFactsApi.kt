package com.example.simplemacrotracking.data.network

import retrofit2.http.GET
import retrofit2.http.Path

interface OpenFoodFactsApi {
    @GET("api/v0/product/{barcode}.json")
    suspend fun getProduct(@Path("barcode") barcode: String): retrofit2.Response<com.example.simplemacrotracking.data.network.dto.OpenFoodResponse>
}

