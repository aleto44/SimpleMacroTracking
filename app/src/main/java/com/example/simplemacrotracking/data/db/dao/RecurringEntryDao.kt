package com.example.simplemacrotracking.data.db.dao

import androidx.room.*
import com.example.simplemacrotracking.data.model.RecurringEntry
import com.example.simplemacrotracking.data.model.RecurringEntryOverride
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringEntryDao {

    // Active entries whose date range covers the given date (ISO string comparison works because
    // ISO-8601 date strings sort lexicographically the same as chronologically).
    @Query("""
        SELECT * FROM recurring_entries
        WHERE isActive = 1
          AND startDate <= :date
          AND (endDate IS NULL OR endDate >= :date)
    """)
    fun observeActiveForDate(date: String): Flow<List<RecurringEntry>>

    @Query("SELECT * FROM recurring_entries WHERE id = :id")
    suspend fun getById(id: Long): RecurringEntry?

    @Insert
    suspend fun insert(entry: RecurringEntry): Long

    @Update
    suspend fun update(entry: RecurringEntry)

    // Overrides ──────────────────────────────────────────────────────────────

    @Query("SELECT * FROM recurring_entry_overrides WHERE date = :date")
    fun observeOverridesForDate(date: String): Flow<List<RecurringEntryOverride>>

    @Query("SELECT * FROM recurring_entry_overrides WHERE recurringEntryId = :id AND date = :date LIMIT 1")
    suspend fun getOverride(id: Long, date: String): RecurringEntryOverride?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOverride(override: RecurringEntryOverride)

    @Query("DELETE FROM recurring_entry_overrides WHERE id = :id")
    suspend fun deleteOverrideById(id: Long)
}
