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

    fun getAllFoodItemsSortedByLastDiary(): Flow<List<FoodItem>> = dao.getAllFoodItemsSortedByLastDiary()

    fun searchFoodItems(query: String): Flow<List<FoodItem>> = dao.searchFoodItems(query)

    fun searchFoodItemsSortedByLastDiary(query: String): Flow<List<FoodItem>> = dao.searchFoodItemsSortedByLastDiary(query)

    suspend fun searchBaseFoods(query: String): List<FoodItem> = dao.searchBaseFoods(query)

    suspend fun getFoodItemById(id: Long): FoodItem? = dao.getFoodItemById(id)

    suspend fun getFoodItemByBarcode(barcode: String): FoodItem? = dao.getFoodItemByBarcode(barcode)

    suspend fun getFoodItemByName(name: String): FoodItem? = dao.getFoodItemByName(name)

    suspend fun saveFoodItem(item: FoodItem): Long = dao.insertFoodItem(item)

    suspend fun updateFoodItem(item: FoodItem) = dao.updateFoodItem(item)

    suspend fun deleteFoodItem(item: FoodItem) = dao.deleteFoodItem(item)

    suspend fun fetchByBarcode(barcode: String): NetworkResult<FoodItem> {
        if (!networkUtils.isOnline()) {
            val cached = dao.getFoodItemByBarcode(barcode)
            return if (cached != null) {
                NetworkResult.Success(cached)
            } else {
                NetworkResult.Error("No internet connection")
            }
        }

        return try {
            val response = api.getProduct(barcode)
            if (response.isSuccessful) {
                val product = response.body()?.product
                if (product != null) {
                    val n = product.nutriments
                    // Prefer per-serving values when the product has a serving size defined
                    val servingQty = product.servingQuantity
                    val useServing = servingQty != null && servingQty > 0f &&
                        n?.caloriesPerServing != null
                    val baseAmount: Float
                    val calories: Float
                    val protein: Float
                    val carbs: Float
                    val fat: Float
                    val fiber: Float
                    if (useServing) {
                        baseAmount = servingQty!!
                        calories = n?.caloriesPerServing ?: 0f
                        protein  = n?.proteinPerServing  ?: 0f
                        carbs    = n?.carbsPerServing    ?: 0f
                        fat      = n?.fatPerServing      ?: 0f
                        fiber    = n?.fiberPerServing    ?: 0f
                    } else {
                        baseAmount = 100f
                        calories = n?.caloriesPer100g ?: 0f
                        protein  = n?.proteinPer100g  ?: 0f
                        carbs    = n?.carbsPer100g    ?: 0f
                        fat      = n?.fatPer100g      ?: 0f
                        fiber    = n?.fiberPer100g    ?: 0f
                    }

                    val cached = dao.getFoodItemByBarcode(barcode)
                    val item = FoodItem(
                        id = cached?.id ?: 0,
                        name = product.productName?.ifBlank { "Unknown Product" } ?: "Unknown Product",
                        brand = product.brands?.ifBlank { null },
                        barcode = barcode,
                        baseAmount = baseAmount,
                        measurementType = "g",
                        calories = calories,
                        proteinG = protein,
                        carbsG = carbs,
                        fatG = fat,
                        fiberG = fiber,
                        source = FoodSource.BARCODE
                    )

                    val id = if (cached != null) {
                        dao.updateFoodItem(item)
                        cached.id
                    } else {
                        dao.insertFoodItem(item)
                    }

                    NetworkResult.Success(item.copy(id = id))
                } else {
                    NetworkResult.Error("Product not found in Open Food Facts")
                }
            } else {
                NetworkResult.Error("Not found (HTTP ${response.code()})")
            }
        } catch (e: IOException) {
            val cached = dao.getFoodItemByBarcode(barcode)
            return if (cached != null) {
                NetworkResult.Success(cached)
            } else {
                NetworkResult.Error("No internet connection")
            }
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "Unknown error")
        }
    }
}
