package com.example.simplemacrotracking.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.simplemacrotracking.data.model.enums.WeightUnit
import java.time.LocalDate

@Entity(tableName = "weight_entries")
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val value: Float,
    val unit: WeightUnit
)

