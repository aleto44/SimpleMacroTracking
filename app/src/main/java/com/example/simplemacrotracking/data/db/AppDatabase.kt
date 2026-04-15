package com.example.simplemacrotracking.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.simplemacrotracking.data.db.converters.LocalDateConverter
import com.example.simplemacrotracking.data.db.dao.DiaryEntryDao
import com.example.simplemacrotracking.data.db.dao.FoodItemDao
import com.example.simplemacrotracking.data.db.dao.WeightEntryDao
import com.example.simplemacrotracking.data.model.DiaryEntry
import com.example.simplemacrotracking.data.model.FoodItem
import com.example.simplemacrotracking.data.model.WeightEntry

@Database(
    entities = [FoodItem::class, DiaryEntry::class, WeightEntry::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(LocalDateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodItemDao(): FoodItemDao
    abstract fun diaryEntryDao(): DiaryEntryDao
    abstract fun weightEntryDao(): WeightEntryDao
}

