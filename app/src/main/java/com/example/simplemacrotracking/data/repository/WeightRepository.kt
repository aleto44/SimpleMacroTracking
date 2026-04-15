package com.example.simplemacrotracking.data.repository

import com.example.simplemacrotracking.data.db.dao.WeightEntryDao
import com.example.simplemacrotracking.data.model.WeightEntry
import com.example.simplemacrotracking.data.model.enums.WeightUnit
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeightRepository @Inject constructor(private val dao: WeightEntryDao) {

    fun getAllWeightEntries(): Flow<List<WeightEntry>> = dao.getAllWeightEntries()

    suspend fun getAllWeightEntriesOnce(): List<WeightEntry> = dao.getAllWeightEntriesOnce()

    suspend fun insertWeightEntry(entry: WeightEntry): Long = dao.insertWeightEntry(entry)

    suspend fun updateWeightEntry(entry: WeightEntry) = dao.updateWeightEntry(entry)

    suspend fun deleteWeightEntry(entry: WeightEntry) = dao.deleteWeightEntry(entry)

    suspend fun getEntryForDate(date: String): WeightEntry? = dao.getEntryForDate(date)

    /**
     * Bulk-converts all stored WeightEntry rows from [from] unit to [to] unit.
     * Called by SettingsViewModel immediately after persisting the new unit preference.
     */
    suspend fun convertAllEntries(from: WeightUnit, to: WeightUnit) {
        if (from == to) return
        val all = dao.getAllWeightEntriesOnce()
        all.forEach { entry ->
            val converted = when {
                from == WeightUnit.LB && to == WeightUnit.KG -> entry.value / 2.20462f
                from == WeightUnit.KG && to == WeightUnit.LB -> entry.value * 2.20462f
                else -> entry.value
            }
            dao.updateWeightEntry(entry.copy(value = converted, unit = to))
        }
    }
}

