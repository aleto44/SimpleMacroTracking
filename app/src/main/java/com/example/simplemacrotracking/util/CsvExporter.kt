package com.example.simplemacrotracking.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+ — use MediaStore, no permission needed
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Toast.makeText(context, "Saved to Downloads/$filename", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Failed to save file", Toast.LENGTH_LONG).show()
            }
        } else {
            // API 26-28 — write to public Downloads directory
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir.mkdirs()
            val file = File(downloadsDir, filename)
            file.writeText(content)
            Toast.makeText(context, "Saved to Downloads/$filename", Toast.LENGTH_LONG).show()
        }
    }
}
