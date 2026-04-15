package com.example.simplemacrotracking.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class DiaryEntryWithFood(
    @Embedded val entry: DiaryEntry,
    @Relation(
        parentColumn = "foodItemId",
        entityColumn = "id"
    )
    val food: FoodItem
)

