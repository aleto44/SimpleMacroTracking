package com.example.simplemacrotracking.data.repository

import com.example.simplemacrotracking.data.db.dao.FoodItemDao
import com.example.simplemacrotracking.data.db.dao.RecipeIngredientDao
import com.example.simplemacrotracking.data.model.FoodItem
import com.example.simplemacrotracking.data.model.RecipeIngredient
import com.example.simplemacrotracking.data.model.enums.FoodSource
import javax.inject.Inject
import javax.inject.Singleton

data class IngredientDraft(val food: FoodItem, val amount: Float)

@Singleton
class RecipeRepository @Inject constructor(
    private val foodItemDao: FoodItemDao,
    private val recipeIngredientDao: RecipeIngredientDao
) {
    suspend fun getIngredientsForRecipe(recipeId: Long): List<Pair<RecipeIngredient, FoodItem?>> =
        recipeIngredientDao.getIngredientsForRecipe(recipeId)
            .map { it to foodItemDao.getFoodItemById(it.ingredientFoodItemId) }

    suspend fun saveRecipe(
        name: String,
        baseAmount: Float,
        measurementType: String,
        ingredients: List<IngredientDraft>
    ): Long {
        var totalCal = 0f; var totalP = 0f; var totalC = 0f; var totalF = 0f; var totalFib = 0f
        for (d in ingredients) {
            val scale = if (d.food.baseAmount > 0f) d.amount / d.food.baseAmount else 0f
            totalCal += d.food.calories * scale
            totalP   += d.food.proteinG * scale
            totalC   += d.food.carbsG   * scale
            totalF   += d.food.fatG     * scale
            totalFib += d.food.fiberG   * scale
        }
        val recipeId = foodItemDao.insertFoodItem(
            FoodItem(
                name = name,
                baseAmount = baseAmount,
                measurementType = measurementType,
                calories = totalCal,
                proteinG = totalP,
                carbsG = totalC,
                fatG = totalF,
                fiberG = totalFib,
                source = FoodSource.RECIPE
            )
        )
        recipeIngredientDao.insertIngredients(
            ingredients.map { d ->
                RecipeIngredient(
                    recipeId = recipeId,
                    ingredientFoodItemId = d.food.id,
                    amount = d.amount,
                    measurementType = d.food.measurementType
                )
            }
        )
        return recipeId
    }
}
