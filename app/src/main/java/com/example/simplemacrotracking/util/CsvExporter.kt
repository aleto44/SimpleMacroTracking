package com.example.simplemacrotracking.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.simplemacrotracking.data.model.DiaryEntryWithFood
import com.example.simplemacrotracking.data.model.WeightEntry
import java.io.File

object CsvExporter {

    fun exportDiary(context: Context, entries: List<DiaryEntryWithFood>) {
        val sb = StringBuilder("date,food_name,amount,unit,calories,protein_g,carbs_g,fat_g\n")
        for (ewf in entries) {
            val scale = if (ewf.food.baseAmount > 0) ewf.entry.actualAmount / ewf.food.baseAmount else 0f
            sb.append("${ewf.entry.date},")
            sb.append("${csvEscape(ewf.food.name)},")
            sb.append("${ewf.entry.actualAmount},")
            sb.append("${csvEscape(ewf.entry.measurementType)},")
            sb.append("%.2f,".format(ewf.food.calories * scale))
            sb.append("%.2f,".format(ewf.food.proteinG * scale))
            sb.append("%.2f,".format(ewf.food.carbsG * scale))
            sb.append("%.2f\n".format(ewf.food.fatG * scale))
        }
        shareFile(context, sb.toString(), "diary_export.csv")
    }

    fun exportWeight(context: Context, entries: List<WeightEntry>) {
        val sb = StringBuilder("date,weight,unit\n")
        for (e in entries) {
            sb.append("${e.date},${e.value},${e.unit.name}\n")
        }
        shareFile(context, sb.toString(), "weight_export.csv")
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            '"' + value.replace("\"", "\"\"") + '"'
        } else value
    }

    private fun shareFile(context: Context, content: String, filename: String) {
        val file = File(context.cacheDir, filename)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export $filename"))
    }
}

