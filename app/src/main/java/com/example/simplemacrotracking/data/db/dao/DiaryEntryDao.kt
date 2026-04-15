package com.example.simplemacrotracking.data.db.dao

import androidx.room.*
import com.example.simplemacrotracking.data.model.DiaryEntry
import com.example.simplemacrotracking.data.model.DiaryEntryWithFood
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryEntryDao {

    @Transaction
    @Query("SELECT * FROM diary_entries WHERE date = :date")
    fun getEntriesWithFoodForDate(date: String): Flow<List<DiaryEntryWithFood>>

    @Query("SELECT * FROM diary_entries WHERE id = :id")
    suspend fun getDiaryEntryById(id: Long): DiaryEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiaryEntry(entry: DiaryEntry): Long

    @Update
    suspend fun updateDiaryEntry(entry: DiaryEntry)

    @Delete
    suspend fun deleteDiaryEntry(entry: DiaryEntry)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteDiaryEntryById(id: Long)

    @Transaction
    @Query("SELECT * FROM diary_entries ORDER BY date ASC")
    suspend fun getAllEntriesWithFood(): List<DiaryEntryWithFood>

    @Query("SELECT * FROM diary_entries WHERE date = :date AND foodItemId = :foodItemId LIMIT 1")
    suspend fun getEntryForDateAndFood(date: String, foodItemId: Long): DiaryEntry?
}

