package com.example.simplemacrotracking.data.repository

import com.example.simplemacrotracking.data.db.dao.RecurringEntryDao
import com.example.simplemacrotracking.data.model.RecurringEntry
import com.example.simplemacrotracking.data.model.RecurringEntryDisplay
import com.example.simplemacrotracking.data.model.RecurringEntryOverride
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecurringRepository @Inject constructor(
    private val dao: RecurringEntryDao,
    private val foodRepository: FoodRepository
) {

    /** Emits a resolved list of recurring entries for the given date, re-emitting whenever
     *  the underlying entries or overrides change. SKIP overrides are filtered out entirely. */
    fun observeResolvedForDate(date: LocalDate): Flow<List<RecurringEntryDisplay>> {
        val dateStr = date.toString()
        return combine(
            dao.observeActiveForDate(dateStr),
            dao.observeOverridesForDate(dateStr)
        ) { entries, overrides ->
            val overrideMap = overrides.associateBy { it.recurringEntryId }
            entries.mapNotNull { entry ->
                val override = overrideMap[entry.id]
                if (override?.overrideType == "SKIP") return@mapNotNull null
                val food = foodRepository.getFoodItemById(entry.foodItemId) ?: return@mapNotNull null
                val displayAmount = override?.overrideAmount ?: entry.actualAmount
                RecurringEntryDisplay(
                    recurringEntry = entry,
                    food = food,
                    displayAmount = displayAmount,
                    overrideId = override?.id
                )
            }
        }
    }

    suspend fun insert(entry: RecurringEntry): Long = dao.insert(entry)

    suspend fun getById(id: Long): RecurringEntry? = dao.getById(id)

    suspend fun update(entry: RecurringEntry) = dao.update(entry)

    /** Skip this entry for a single day. */
    suspend fun skipForDate(recurringEntryId: Long, date: LocalDate) {
        dao.upsertOverride(
            RecurringEntryOverride(
                recurringEntryId = recurringEntryId,
                date = date,
                overrideType = "SKIP"
            )
        )
    }

    /** Remove a single-day skip or edit override. */
    suspend fun removeOverride(overrideId: Long) = dao.deleteOverrideById(overrideId)

    /** Edit the amount for a single day only. */
    suspend fun editForDate(recurringEntryId: Long, date: LocalDate, newAmount: Float) {
        dao.upsertOverride(
            RecurringEntryOverride(
                recurringEntryId = recurringEntryId,
                date = date,
                overrideType = "EDIT",
                overrideAmount = newAmount
            )
        )
    }

    /** Edit all future occurrences (including today) by expiring the old entry and creating a new one. */
    suspend fun editFromDate(recurringEntry: RecurringEntry, fromDate: LocalDate, newAmount: Float) {
        // Expire the current entry the day before fromDate
        val expiredEntry = recurringEntry.copy(endDate = fromDate.minusDays(1))
        dao.update(expiredEntry)
        // Create new entry starting from fromDate
        dao.insert(
            RecurringEntry(
                foodItemId = recurringEntry.foodItemId,
                actualAmount = newAmount,
                measurementType = recurringEntry.measurementType,
                startDate = fromDate
            )
        )
    }

    /** Deactivate a recurring entry so it never appears again on or after fromDate. */
    suspend fun deactivateFromDate(recurringEntry: RecurringEntry, fromDate: LocalDate) {
        if (fromDate <= recurringEntry.startDate) {
            // Started today or after — just deactivate entirely
            dao.update(recurringEntry.copy(isActive = false))
        } else {
            dao.update(recurringEntry.copy(endDate = fromDate.minusDays(1)))
        }
    }
}
