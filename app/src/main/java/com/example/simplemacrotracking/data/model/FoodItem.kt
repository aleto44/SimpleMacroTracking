package com.example.simplemacrotracking.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.simplemacrotracking.data.model.enums.FoodSource

@Entity(tableName = "food_items")
data class FoodItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val baseAmount: Float,
    val measurementType: String,   // "g", "oz", or custom e.g. "taco"
    val calories: Float,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
    val source: FoodSource = FoodSource.MANUAL
)

