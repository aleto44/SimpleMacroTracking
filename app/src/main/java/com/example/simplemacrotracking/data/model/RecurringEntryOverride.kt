package com.example.simplemacrotracking.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class OverrideType { SKIP, EDIT }

@Entity(
    tableName = "recurring_entry_overrides",
    foreignKeys = [ForeignKey(
        entity = RecurringEntry::class,
        parentColumns = ["id"],
        childColumns = ["recurringEntryId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("recurringEntryId")]
)
data class RecurringEntryOverride(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recurringEntryId: Long,
    val date: LocalDate,
    val overrideType: String,          // "SKIP" or "EDIT" — stored as String to avoid extra converter
    val overrideAmount: Float? = null  // only non-null when overrideType == "EDIT"
)
