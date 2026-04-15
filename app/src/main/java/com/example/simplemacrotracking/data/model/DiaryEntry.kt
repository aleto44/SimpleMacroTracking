package com.example.simplemacrotracking.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "diary_entries",
    foreignKeys = [ForeignKey(
        entity = FoodItem::class,
        parentColumns = ["id"],
        childColumns = ["foodItemId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("foodItemId")]
)
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,                   // stored as ISO string via TypeConverter
    val foodItemId: Long,
    val actualAmount: Float,
    val measurementType: String            // copied from FoodItem at save time
)

