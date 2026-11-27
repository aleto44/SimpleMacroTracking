package com.example.simplemacrotracking.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.example.simplemacrotracking.BuildConfig
import com.example.simplemacrotracking.data.db.AppDatabase
import com.example.simplemacrotracking.data.prefs.SettingsPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

/**
 * Writes everything the app stores into one JSON file, for moving to the server version.
 *
 * Every table in the Room database is dumped generically (read from sqlite_master), with raw stored
 * values and original IDs, so nothing is missed: hidden IMPORT-source foods, recipe ingredients,
 * recurring entries and their overrides, and any column added later. Settings are included too,
 * except AI API keys, which are left out on purpose.
 */
object FullDataExporter {

    const val FORMAT = "simple-macro-tracking-backup"
    const val FORMAT_VERSION = 1

    /** Room/SQLite bookkeeping tables that hold no user data. */
    private val INTERNAL_TABLES = setOf("android_metadata", "room_master_table")

    data class Result(val counts: Map<String, Int>)

    suspend fun export(
        context: Context,
        uri: Uri,
        database: AppDatabase,
        settings: SettingsPrefs
    ): Result = withContext(Dispatchers.IO) {
        val db = database.openHelper.readableDatabase
        val tables = JSONObject()
        val counts = linkedMapOf<String, Int>()

        // One read transaction, so the snapshot is consistent across tables.
        db.beginTransaction()
        try {
            val tableNames = mutableListOf<String>()
            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' " +
                    "AND name NOT LIKE 'sqlite_%' ORDER BY name"
            ).use { c -> while (c.moveToNext()) tableNames += c.getString(0) }

            for (table in tableNames.filterNot { it in INTERNAL_TABLES }) {
                db.query("SELECT * FROM `$table` ORDER BY rowid").use { c ->
                    val columns = JSONArray(c.columnNames.toList())
                    val rows = JSONArray()
                    while (c.moveToNext()) {
                        val row = JSONObject()
                        for (i in 0 until c.columnCount) row.put(c.getColumnName(i), cellValue(c, i))
                        rows.put(row)
                    }
                    tables.put(table, JSONObject().put("columns", columns).put("rows", rows))
                    counts[table] = rows.length()
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        val root = JSONObject()
            .put("format", FORMAT)
            .put("formatVersion", FORMAT_VERSION)
            .put("exportedAt", OffsetDateTime.now().toString())
            .put("appVersionName", BuildConfig.VERSION_NAME)
            .put("appVersionCode", BuildConfig.VERSION_CODE)
            .put("databaseVersion", db.version)
            .put("counts", JSONObject(counts as Map<*, *>))
            .put("settings", settingsJson(settings))
            .put("tables", tables)

        val out = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("Could not open the file for writing")
        out.use { it.write(root.toString(2).toByteArray(Charsets.UTF_8)) }

        Result(counts)
    }

    private fun cellValue(c: Cursor, i: Int): Any = when (c.getType(i)) {
        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i).let { if (it.isNaN() || it.isInfinite()) it.toString() else it }
        Cursor.FIELD_TYPE_BLOB -> android.util.Base64.encodeToString(c.getBlob(i), android.util.Base64.NO_WRAP)
        else -> c.getString(i)
    }

    private fun settingsJson(s: SettingsPrefs): JSONObject {
        val providers = JSONArray()
        for (p in s.aiProviders) {
            providers.put(
                JSONObject()
                    .put("id", p.id)
                    .put("type", p.type.name)
                    .put("model", p.model)
                    .put("enabled", p.enabled)
                    .put("hasApiKey", p.apiKey.isNotBlank())
            )
        }
        return JSONObject()
            .put("calorieGoal", s.calorieGoal)
            .put("proteinGoal", s.proteinGoal)
            .put("carbsGoal", s.carbsGoal)
            .put("fatGoal", s.fatGoal)
            .put("fiberGoal", s.fiberGoal)
            .put("preferredWeightUnit", s.preferredWeightUnit.name)
            .put("graphShowWeight", s.graphShowWeight)
            .put("graphShowCalories", s.graphShowCalories)
            .put("graphShowMovingAverage", s.graphShowMovingAverage)
            .put("graphMovingAverageDays", s.graphMovingAverageDays)
            .put("graphTimeRange", s.graphTimeRange)
            .put("aiProviders", providers)
    }
}
