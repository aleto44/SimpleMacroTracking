package com.example.simplemacrotracking

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.simplemacrotracking.data.db.AppDatabase
import com.example.simplemacrotracking.data.model.DiaryEntry
import com.example.simplemacrotracking.data.model.FoodItem
import com.example.simplemacrotracking.data.model.RecipeIngredient
import com.example.simplemacrotracking.data.model.RecurringEntry
import com.example.simplemacrotracking.data.model.RecurringEntryOverride
import com.example.simplemacrotracking.data.model.WeightEntry
import com.example.simplemacrotracking.data.model.enums.FoodSource
import com.example.simplemacrotracking.data.model.enums.WeightUnit
import com.example.simplemacrotracking.data.prefs.SettingsPrefs
import com.example.simplemacrotracking.util.FullDataExporter
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class FullDataExporterTest {

    @Test
    fun exportsEveryTableWithRawRowsAndNoApiKeys() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        val day = LocalDate.of(2026, 11, 27)

        val egg = db.foodItemDao().insertFoodItem(
            FoodItem(name = "Egg, \"large\"", baseAmount = 1f, measurementType = "egg",
                calories = 72f, proteinG = 6.3f, carbsG = 0.4f, fatG = 4.8f, barcode = "0123")
        )
        val toast = db.foodItemDao().insertFoodItem(
            FoodItem(name = "Toast", brand = null, baseAmount = 30f, measurementType = "g",
                calories = 80f, proteinG = 3f, carbsG = 15f, fatG = 1f, fiberG = 1.2f)
        )
        val hidden = db.foodItemDao().insertFoodItem(
            FoodItem(name = "Imported thing", baseAmount = 100f, measurementType = "g",
                calories = 1f, proteinG = 0f, carbsG = 0f, fatG = 0f, source = FoodSource.IMPORT)
        )
        val recipe = db.foodItemDao().insertFoodItem(
            FoodItem(name = "Breakfast", baseAmount = 1f, measurementType = "serving",
                calories = 224f, proteinG = 15.6f, carbsG = 15.8f, fatG = 10.6f, source = FoodSource.RECIPE)
        )
        db.recipeIngredientDao().insertIngredients(listOf(
            RecipeIngredient(recipeId = recipe, ingredientFoodItemId = egg, amount = 2f, measurementType = "egg"),
            RecipeIngredient(recipeId = recipe, ingredientFoodItemId = toast, amount = 30f, measurementType = "g")
        ))
        db.diaryEntryDao().insertDiaryEntry(DiaryEntry(date = day, foodItemId = egg, actualAmount = 2f, measurementType = "egg"))
        db.diaryEntryDao().insertDiaryEntry(DiaryEntry(date = day, foodItemId = hidden, actualAmount = 50f, measurementType = "g"))
        val rec = db.recurringEntryDao().insert(
            RecurringEntry(foodItemId = toast, actualAmount = 30f, measurementType = "g", startDate = day.minusDays(7))
        )
        db.recurringEntryDao().upsertOverride(RecurringEntryOverride(recurringEntryId = rec, date = day, overrideType = "SKIP"))
        db.recurringEntryDao().upsertOverride(RecurringEntryOverride(recurringEntryId = rec, date = day.plusDays(1), overrideType = "EDIT", overrideAmount = 60f))
        db.weightEntryDao().insertWeightEntry(WeightEntry(date = day, value = 180.4f, unit = WeightUnit.LB))

        val settings = SettingsPrefs(ctx)
        val oldProviders = settings.aiProviders
        settings.aiProviders = listOf(
            com.example.simplemacrotracking.data.model.AiProviderConfig(
                id = "p1", type = com.example.simplemacrotracking.data.model.AiProviderType.GEMINI,
                apiKey = "SECRET-TEST-KEY", model = "gemini-2.5-flash")
        )

        val file = File(ctx.cacheDir, "export-test.json")
        try {
            val result = FullDataExporter.export(ctx, Uri.fromFile(file), db, settings)
            val text = file.readText()
            val root = JSONObject(text)

            assertEquals(FullDataExporter.FORMAT, root.getString("format"))
            assertEquals(4, root.getInt("databaseVersion"))
            assertFalse("API key must not be exported", text.contains("SECRET-TEST-KEY"))
            assertTrue(root.getJSONObject("settings").getJSONArray("aiProviders").getJSONObject(0).getBoolean("hasApiKey"))

            val tables = root.getJSONObject("tables")
            val expected = mapOf(
                "food_items" to 4, "diary_entries" to 2, "recipe_ingredients" to 2,
                "recurring_entries" to 1, "recurring_entry_overrides" to 2, "weight_entries" to 1
            )
            assertEquals(expected.keys, tables.keys().asSequence().toSet())
            for ((t, n) in expected) {
                assertEquals(t, n, tables.getJSONObject(t).getJSONArray("rows").length())
                assertEquals(t, n, result.counts[t])
                assertEquals(t, n, root.getJSONObject("counts").getInt(t))
            }

            val foods = tables.getJSONObject("food_items").getJSONArray("rows")
            val eggRow = foods.getJSONObject(0)
            assertEquals(egg, eggRow.getLong("id"))
            assertEquals("Egg, \"large\"", eggRow.getString("name"))
            assertEquals("0123", eggRow.getString("barcode"))
            assertTrue(eggRow.isNull("brand"))
            assertEquals(6.3, eggRow.getDouble("proteinG"), 0.001)
            assertEquals("IMPORT", foods.getJSONObject(2).getString("source"))

            val override = tables.getJSONObject("recurring_entry_overrides").getJSONArray("rows")
            assertTrue(override.getJSONObject(0).isNull("overrideAmount"))
            assertEquals(60.0, override.getJSONObject(1).getDouble("overrideAmount"), 0.001)
            assertEquals("2026-11-27", tables.getJSONObject("weight_entries").getJSONArray("rows").getJSONObject(0).getString("date"))
            assertEquals("LB", tables.getJSONObject("weight_entries").getJSONArray("rows").getJSONObject(0).getString("unit"))
        } finally {
            settings.aiProviders = oldProviders
            file.delete()
            db.close()
        }
    }
}
