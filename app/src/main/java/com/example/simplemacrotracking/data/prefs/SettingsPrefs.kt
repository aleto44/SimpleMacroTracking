package com.example.simplemacrotracking.data.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.simplemacrotracking.data.model.AiProviderConfig
import com.example.simplemacrotracking.data.model.AiProviderType
import com.example.simplemacrotracking.data.model.enums.WeightUnit
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsPrefs @Inject constructor(@ApplicationContext context: Context) {

    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val providerListType = Types.newParameterizedType(List::class.java, AiProviderConfig::class.java)
    private val providerAdapter by lazy { moshi.adapter<List<AiProviderConfig>>(providerListType) }

    private val prefs = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback: if encrypted prefs fail (e.g. corrupted keystore), clear and recreate
        context.deleteSharedPreferences("settings")
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var calorieGoal: Int
        get() = prefs.getInt("calorie_goal", 2000)
        set(v) = prefs.edit().putInt("calorie_goal", v).apply()

    var proteinGoal: Int
        get() = prefs.getInt("protein_goal", 0)
        set(v) = prefs.edit().putInt("protein_goal", v).apply()

    var carbsGoal: Int
        get() = prefs.getInt("carbs_goal", 0)
        set(v) = prefs.edit().putInt("carbs_goal", v).apply()

    var fatGoal: Int
        get() = prefs.getInt("fat_goal", 0)
        set(v) = prefs.edit().putInt("fat_goal", v).apply()

    var preferredWeightUnit: WeightUnit
        get() = WeightUnit.valueOf(prefs.getString("weight_unit", WeightUnit.LB.name)!!)
        set(v) = prefs.edit().putString("weight_unit", v.name).apply()

    var aiApiKey: String
        get() = prefs.getString("ai_api_key", "") ?: ""
        set(v) = prefs.edit().putString("ai_api_key", v).apply()

    /**
     * Ordered list of AI providers. The app tries them top-to-bottom (waterfall).
     * On first access, auto-migrates the legacy [aiApiKey] into a Gemini provider.
     */
    var aiProviders: List<AiProviderConfig>
        get() {
            val json = prefs.getString("ai_providers", null)
            if (json != null) {
                return try {
                    providerAdapter.fromJson(json) ?: emptyList()
                } catch (e: Exception) { emptyList() }
            }
            // Migrate legacy key
            val legacyKey = aiApiKey
            return if (legacyKey.isNotBlank()) {
                listOf(AiProviderConfig(id = UUID.randomUUID().toString(), type = AiProviderType.GEMINI, apiKey = legacyKey))
            } else {
                emptyList()
            }
        }
        set(v) = prefs.edit().putString("ai_providers", providerAdapter.toJson(v)).apply()
}
