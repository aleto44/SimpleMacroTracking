package com.example.simplemacrotracking.data.model

data class RecurringEntryDisplay(
    val recurringEntry: RecurringEntry,
    val food: FoodItem,
    val displayAmount: Float,
    val overrideId: Long? = null  // ID of today's EDIT override if one exists
)
