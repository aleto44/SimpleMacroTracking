package com.example.simplemacrotracking.data.db.dao

import androidx.room.*
import com.example.simplemacrotracking.data.model.FoodItem
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodItemDao {

    @Query("SELECT * FROM food_items ORDER BY name ASC")
    fun getAllFoodItems(): Flow<List<FoodItem>>

    @Query("""
        SELECT food_items.* FROM food_items
        LEFT JOIN diary_entries ON food_items.id = diary_entries.foodItemId
        GROUP BY food_items.id
        ORDER BY
            CASE WHEN MAX(diary_entries.date) IS NULL THEN 1 ELSE 0 END,
            MAX(diary_entries.date) DESC,
            food_items.name ASC
    """)
    fun getAllFoodItemsSortedByLastDiary(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchFoodItems(query: String): Flow<List<FoodItem>>

    @Query("""
        SELECT food_items.* FROM food_items
        LEFT JOIN diary_entries ON food_items.id = diary_entries.foodItemId
        WHERE food_items.name LIKE '%' || :query || '%'
        GROUP BY food_items.id
        ORDER BY
            CASE WHEN MAX(diary_entries.date) IS NULL THEN 1 ELSE 0 END,
            MAX(diary_entries.date) DESC,
            food_items.name ASC
    """)
    fun searchFoodItemsSortedByLastDiary(query: String): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun getFoodItemById(id: Long): FoodItem?

    @Query("SELECT * FROM food_items WHERE barcode = :barcode LIMIT 1")
    suspend fun getFoodItemByBarcode(barcode: String): FoodItem?

    @Query("SELECT * FROM food_items WHERE name = :name LIMIT 1")
    suspend fun getFoodItemByName(name: String): FoodItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodItem(item: FoodItem): Long

    @Update
    suspend fun updateFoodItem(item: FoodItem)

    @Delete
    suspend fun deleteFoodItem(item: FoodItem)
}

