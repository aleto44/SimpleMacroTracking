package com.example.simplemacrotracking.data.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OpenFoodResponse(
    @Json(name = "status") val status: Int = 0,
    @Json(name = "product") val product: OpenFoodProduct? = null
)

@JsonClass(generateAdapter = true)
data class OpenFoodProduct(
    @Json(name = "product_name") val productName: String? = null,
    @Json(name = "brands") val brands: String? = null,
    @Json(name = "nutriments") val nutriments: OpenFoodNutriments? = null
)

@JsonClass(generateAdapter = true)
data class OpenFoodNutriments(
    @Json(name = "energy-kcal_100g") val caloriesPer100g: Float? = null,
    @Json(name = "proteins_100g") val proteinPer100g: Float? = null,
    @Json(name = "carbohydrates_100g") val carbsPer100g: Float? = null,
    @Json(name = "fat_100g") val fatPer100g: Float? = null
)

