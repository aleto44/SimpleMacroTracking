package com.example.simplemacrotracking.data.repository

import com.example.simplemacrotracking.data.db.dao.DiaryEntryDao
import com.example.simplemacrotracking.data.model.DiaryEntry
import com.example.simplemacrotracking.data.model.DiaryEntryWithFood
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiaryRepository @Inject constructor(private val dao: DiaryEntryDao) {

    fun getEntriesWithFoodForDate(date: LocalDate): Flow<List<DiaryEntryWithFood>> =
        dao.getEntriesWithFoodForDate(date.toString())

    suspend fun getDiaryEntryById(id: Long): DiaryEntry? = dao.getDiaryEntryById(id)

    suspend fun insertDiaryEntry(entry: DiaryEntry): Long = dao.insertDiaryEntry(entry)

    suspend fun updateDiaryEntry(entry: DiaryEntry) = dao.updateDiaryEntry(entry)

    suspend fun deleteDiaryEntry(entry: DiaryEntry) = dao.deleteDiaryEntry(entry)

    suspend fun deleteDiaryEntryById(id: Long) = dao.deleteDiaryEntryById(id)

    suspend fun getAllEntriesWithFood(): List<DiaryEntryWithFood> = dao.getAllEntriesWithFood()

    suspend fun getEntryForDateAndFood(date: String, foodItemId: Long): DiaryEntry? =
        dao.getEntryForDateAndFood(date, foodItemId)
}

