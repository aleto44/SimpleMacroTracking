package com.example.simplemacrotracking.data.db.dao

import androidx.room.*
import com.example.simplemacrotracking.data.model.WeightEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightEntryDao {

    @Query("SELECT * FROM weight_entries ORDER BY date ASC")
    fun getAllWeightEntries(): Flow<List<WeightEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightEntry(entry: WeightEntry): Long

    @Update
    suspend fun updateWeightEntry(entry: WeightEntry)

    @Delete
    suspend fun deleteWeightEntry(entry: WeightEntry)

    @Query("SELECT * FROM weight_entries")
    suspend fun getAllWeightEntriesOnce(): List<WeightEntry>

    @Query("SELECT * FROM weight_entries WHERE date = :date LIMIT 1")
    suspend fun getEntryForDate(date: String): WeightEntry?
}

