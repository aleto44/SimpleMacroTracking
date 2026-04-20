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
    @Json(name = "nutriments") val nutriments: OpenFoodNutriments? = null,
    /** Human-readable label, e.g. "30 g" or "1 cup (240 mL)" */
    @Json(name = "serving_size") val servingSize: String? = null,
    /** Numeric serving size in grams (or mL for liquids) */
    @Json(name = "serving_quantity") val servingQuantity: Float? = null
)

@JsonClass(generateAdapter = true)
data class OpenFoodNutriments(
    @Json(name = "energy-kcal_100g") val caloriesPer100g: Float? = null,
    @Json(name = "proteins_100g") val proteinPer100g: Float? = null,
    @Json(name = "carbohydrates_100g") val carbsPer100g: Float? = null,
    @Json(name = "fat_100g") val fatPer100g: Float? = null,
    @Json(name = "energy-kcal_serving") val caloriesPerServing: Float? = null,
    @Json(name = "proteins_serving") val proteinPerServing: Float? = null,
    @Json(name = "carbohydrates_serving") val carbsPerServing: Float? = null,
    @Json(name = "fat_serving") val fatPerServing: Float? = null
)

