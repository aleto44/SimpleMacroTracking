package com.example.simplemacrotracking.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "recurring_entries",
    foreignKeys = [ForeignKey(
        entity = FoodItem::class,
        parentColumns = ["id"],
        childColumns = ["foodItemId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("foodItemId")]
)
data class RecurringEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodItemId: Long,
    val actualAmount: Float,
    val measurementType: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,  // null = active indefinitely
    val isActive: Boolean = true
)
