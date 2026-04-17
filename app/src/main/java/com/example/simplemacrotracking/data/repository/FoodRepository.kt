package com.example.simplemacrotracking.data.repository

import com.example.simplemacrotracking.data.db.dao.FoodItemDao
import com.example.simplemacrotracking.data.model.FoodItem
import com.example.simplemacrotracking.data.model.enums.FoodSource
import com.example.simplemacrotracking.data.network.OpenFoodFactsApi
import com.example.simplemacrotracking.util.NetworkResult
import com.example.simplemacrotracking.util.NetworkUtils
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoodRepository @Inject constructor(
    private val dao: FoodItemDao,
    private val api: OpenFoodFactsApi,
    private val networkUtils: NetworkUtils
) {

    fun getAllFoodItems(): Flow<List<FoodItem>> = dao.getAllFoodItems()

    fun searchFoodItems(query: String): Flow<List<FoodItem>> = dao.searchFoodItems(query)

    suspend fun getFoodItemById(id: Long): FoodItem? = dao.getFoodItemById(id)

    suspend fun getFoodItemByBarcode(barcode: String): FoodItem? = dao.getFoodItemByBarcode(barcode)

    suspend fun getFoodItemByName(name: String): FoodItem? = dao.getFoodItemByName(name)

    suspend fun saveFoodItem(item: FoodItem): Long = dao.insertFoodItem(item)

    suspend fun updateFoodItem(item: FoodItem) = dao.updateFoodItem(item)

    suspend fun deleteFoodItem(item: FoodItem) = dao.deleteFoodItem(item)

    suspend fun fetchByBarcode(barcode: String): NetworkResult<FoodItem> {
        // Check local cache first
        val cached = dao.getFoodItemByBarcode(barcode)
        if (cached != null) return NetworkResult.Success(cached)

        if (!networkUtils.isOnline()) return NetworkResult.Error("No internet connection")

        return try {
            val response = api.getProduct(barcode)
            if (response.isSuccessful) {
                val product = response.body()?.product
                if (product != null) {
                    val n = product.nutriments
                    val item = FoodItem(
                        name = product.productName?.ifBlank { "Unknown Product" } ?: "Unknown Product",
                        brand = product.brands?.ifBlank { null },
                        barcode = barcode,
                        baseAmount = 100f,
                        measurementType = "g",
                        calories = n?.caloriesPer100g ?: 0f,
                        proteinG = n?.proteinPer100g ?: 0f,
                        carbsG = n?.carbsPer100g ?: 0f,
                        fatG = n?.fatPer100g ?: 0f,
                        source = FoodSource.BARCODE
                    )
                    val id = dao.insertFoodItem(item)
                    NetworkResult.Success(item.copy(id = id))
                } else {
                    NetworkResult.Error("Product not found in Open Food Facts")
                }
            } else {
                NetworkResult.Error("Not found (HTTP ${response.code()})")
            }
        } catch (e: IOException) {
            NetworkResult.Error("No internet connection")
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "Unknown error")
        }
    }
}
