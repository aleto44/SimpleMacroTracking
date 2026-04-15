package com.example.simplemacrotracking.util

import android.content.Context
import android.net.Uri
import com.example.simplemacrotracking.data.model.DiaryEntry
import com.example.simplemacrotracking.data.model.FoodItem
import com.example.simplemacrotracking.data.model.WeightEntry
import com.example.simplemacrotracking.data.model.enums.FoodSource
import com.example.simplemacrotracking.data.model.enums.WeightUnit
import com.example.simplemacrotracking.data.repository.DiaryRepository
import com.example.simplemacrotracking.data.repository.FoodRepository
import com.example.simplemacrotracking.data.repository.WeightRepository
import java.time.LocalDate

data class ImportResult(val imported: Int, val skipped: Int)

object CsvImporter {

    suspend fun importCsv(
        context: Context,
        uri: Uri,
        foodRepository: FoodRepository,
        diaryRepository: DiaryRepository,
        weightRepository: WeightRepository
    ): ImportResult {
        val lines = try {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.readLines()
                ?.filter { it.isNotBlank() }
                ?: return ImportResult(0, 0)
        } catch (e: Exception) {
            return ImportResult(0, 0)
        }

        if (lines.size < 2) return ImportResult(0, 0)

        val header = parseCsvLine(lines[0]).map { it.trim().lowercase() }

        return when {
            header.contains("food_name") ->
                importDiary(lines, header, foodRepository, diaryRepository)
            header.contains("weight") && header.contains("date") ->
                importWeight(lines, header, weightRepository)
            else -> ImportResult(0, lines.size - 1)
        }
    }

    private suspend fun importDiary(
        lines: List<String>,
        header: List<String>,
        foodRepository: FoodRepository,
        diaryRepository: DiaryRepository
    ): ImportResult {
        val dateIdx   = header.indexOf("date")
        val nameIdx   = header.indexOf("food_name")
        val amountIdx = header.indexOf("amount")
        val unitIdx   = header.indexOf("unit")
        val calIdx    = header.indexOf("calories")
        val protIdx   = header.indexOf("protein_g")
        val carbIdx   = header.indexOf("carbs_g")
        val fatIdx    = header.indexOf("fat_g")

        if (dateIdx < 0 || nameIdx < 0 || amountIdx < 0) return ImportResult(0, lines.size - 1)

        var imported = 0
        var skipped = 0

        for (i in 1 until lines.size) {
            try {
                val cols = parseCsvLine(lines[i])
                if (cols.size <= maxOf(dateIdx, nameIdx, amountIdx)) { skipped++; continue }

                val date   = LocalDate.parse(cols[dateIdx].trim())
                val name   = cols[nameIdx].trim()
                val amountStr = cols.getOrNull(amountIdx)?.trim()
                val amount = amountStr?.toFloatOrNull()
                if (amount == null) { skipped++; continue }

                val unit = cols.getOrNull(unitIdx)?.trim() ?: "g"
                val cal  = cols.getOrNull(calIdx)?.trim()?.toFloatOrNull() ?: 0f
                val prot = cols.getOrNull(protIdx)?.trim()?.toFloatOrNull() ?: 0f
                val carb = cols.getOrNull(carbIdx)?.trim()?.toFloatOrNull() ?: 0f
                val fat  = cols.getOrNull(fatIdx)?.trim()?.toFloatOrNull() ?: 0f

                // Find or create food item
                var food = foodRepository.getFoodItemByName(name)
                if (food == null) {
                    val id = foodRepository.saveFoodItem(
                        FoodItem(
                            name = name, baseAmount = amount, measurementType = unit,
                            calories = cal, proteinG = prot, carbsG = carb, fatG = fat,
                            source = FoodSource.MANUAL
                        )
                    )
                    food = foodRepository.getFoodItemById(id)
                }
                if (food == null) { skipped++; continue }

                // Upsert diary entry by (date, foodItemId)
                val existing = diaryRepository.getEntryForDateAndFood(date.toString(), food.id)
                if (existing != null) {
                    diaryRepository.updateDiaryEntry(
                        existing.copy(actualAmount = amount, measurementType = unit)
                    )
                } else {
                    diaryRepository.insertDiaryEntry(
                        DiaryEntry(
                            date = date, foodItemId = food.id,
                            actualAmount = amount, measurementType = unit
                        )
                    )
                }
                imported++
            } catch (e: Exception) {
                skipped++
            }
        }
        return ImportResult(imported, skipped)
    }

    private suspend fun importWeight(
        lines: List<String>,
        header: List<String>,
        weightRepository: WeightRepository
    ): ImportResult {
        val dateIdx  = header.indexOf("date")
        val valueIdx = header.indexOf("weight")
        val unitIdx  = header.indexOf("unit")

        if (dateIdx < 0 || valueIdx < 0) return ImportResult(0, lines.size - 1)

        var imported = 0
        var skipped = 0

        for (i in 1 until lines.size) {
            try {
                val cols      = parseCsvLine(lines[i])
                val dateStr   = cols.getOrNull(dateIdx)?.trim()
                if (dateStr.isNullOrBlank()) { skipped++; continue }
                val date  = LocalDate.parse(dateStr)
                val valueStr = cols.getOrNull(valueIdx)?.trim()
                val value = valueStr?.toFloatOrNull()
                if (value == null) { skipped++; continue }

                val unit = try {
                    WeightUnit.valueOf(cols.getOrNull(unitIdx)?.trim()?.uppercase() ?: "LB")
                } catch (e: IllegalArgumentException) { WeightUnit.LB }

                val existing = weightRepository.getEntryForDate(date.toString())
                if (existing != null) {
                    weightRepository.updateWeightEntry(existing.copy(value = value, unit = unit))
                } else {
                    weightRepository.insertWeightEntry(
                        WeightEntry(date = date, value = value, unit = unit)
                    )
                }
                imported++
            } catch (e: Exception) {
                skipped++
            }
        }
        return ImportResult(imported, skipped)
    }

    private fun parseCsvLine(line: String): List<String> {
        val result   = mutableListOf<String>()
        val sb       = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { result.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }
}

