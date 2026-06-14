package com.example.simplemacrotracking.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.simplemacrotracking.data.db.converters.LocalDateConverter
import com.example.simplemacrotracking.data.db.dao.DiaryEntryDao
import com.example.simplemacrotracking.data.db.dao.FoodItemDao
import com.example.simplemacrotracking.data.db.dao.RecipeIngredientDao
import com.example.simplemacrotracking.data.db.dao.RecurringEntryDao
import com.example.simplemacrotracking.data.db.dao.WeightEntryDao
import com.example.simplemacrotracking.data.model.DiaryEntry
import com.example.simplemacrotracking.data.model.FoodItem
import com.example.simplemacrotracking.data.model.RecipeIngredient
import com.example.simplemacrotracking.data.model.RecurringEntry
import com.example.simplemacrotracking.data.model.RecurringEntryOverride
import com.example.simplemacrotracking.data.model.WeightEntry

@Database(
    entities = [
        FoodItem::class,
        DiaryEntry::class,
        WeightEntry::class,
        RecurringEntry::class,
        RecurringEntryOverride::class,
        RecipeIngredient::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(LocalDateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodItemDao(): FoodItemDao
    abstract fun diaryEntryDao(): DiaryEntryDao
    abstract fun weightEntryDao(): WeightEntryDao
    abstract fun recurringEntryDao(): RecurringEntryDao
    abstract fun recipeIngredientDao(): RecipeIngredientDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recipe_ingredients (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        recipeId INTEGER NOT NULL,
                        ingredientFoodItemId INTEGER NOT NULL,
                        amount REAL NOT NULL,
                        measurementType TEXT NOT NULL,
                        FOREIGN KEY(recipeId) REFERENCES food_items(id) ON DELETE CASCADE,
                        FOREIGN KEY(ingredientFoodItemId) REFERENCES food_items(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_ingredients_recipeId ON recipe_ingredients(recipeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_ingredients_ingredientFoodItemId ON recipe_ingredients(ingredientFoodItemId)")
            }
        }
    }
}
