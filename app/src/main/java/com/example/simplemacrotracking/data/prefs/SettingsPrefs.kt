package com.example.simplemacrotracking.data.prefs

import android.content.Context
import com.example.simplemacrotracking.data.model.enums.WeightUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsPrefs @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var calorieGoal: Int
        get() = prefs.getInt("calorie_goal", 2000)
        set(v) = prefs.edit().putInt("calorie_goal", v).apply()

    var proteinGoal: Int
        get() = prefs.getInt("protein_goal", 150)
        set(v) = prefs.edit().putInt("protein_goal", v).apply()

    var carbsGoal: Int
        get() = prefs.getInt("carbs_goal", 200)
        set(v) = prefs.edit().putInt("carbs_goal", v).apply()

    var fatGoal: Int
        get() = prefs.getInt("fat_goal", 65)
        set(v) = prefs.edit().putInt("fat_goal", v).apply()

    var preferredWeightUnit: WeightUnit
        get() = WeightUnit.valueOf(prefs.getString("weight_unit", WeightUnit.LB.name)!!)
        set(v) = prefs.edit().putString("weight_unit", v.name).apply()

    var aiApiKey: String
        get() = prefs.getString("ai_api_key", "") ?: ""
        set(v) = prefs.edit().putString("ai_api_key", v).apply()
}

