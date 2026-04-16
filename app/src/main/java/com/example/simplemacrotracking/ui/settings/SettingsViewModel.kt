package com.example.simplemacrotracking.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simplemacrotracking.data.model.DiaryEntryWithFood
import com.example.simplemacrotracking.data.model.WeightEntry
import com.example.simplemacrotracking.data.model.enums.WeightUnit
import com.example.simplemacrotracking.data.network.GeminiApi
import com.example.simplemacrotracking.data.network.dto.GeminiContent
import com.example.simplemacrotracking.data.network.dto.GeminiPart
import com.example.simplemacrotracking.data.network.dto.GeminiRequest
import com.example.simplemacrotracking.data.prefs.SettingsPrefs
import com.example.simplemacrotracking.data.repository.DiaryRepository
import com.example.simplemacrotracking.data.repository.WeightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val calorieGoal: Int = 2000,
    val proteinGoal: Int = 150,
    val carbsGoal: Int = 200,
    val fatGoal: Int = 65,
    val weightUnit: WeightUnit = WeightUnit.LB,
    val aiApiKey: String = "",
    val isConverting: Boolean = false,
    val isTesting: Boolean = false,
    val apiKeyTestResult: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsPrefs: SettingsPrefs,
    private val weightRepository: WeightRepository,
    private val diaryRepository: DiaryRepository,
    private val geminiApi: GeminiApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(loadFromPrefs())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun loadFromPrefs() = SettingsUiState(
        calorieGoal = settingsPrefs.calorieGoal,
        proteinGoal = settingsPrefs.proteinGoal,
        carbsGoal   = settingsPrefs.carbsGoal,
        fatGoal     = settingsPrefs.fatGoal,
        weightUnit  = settingsPrefs.preferredWeightUnit,
        aiApiKey    = settingsPrefs.aiApiKey
    )

    fun saveGoals(calories: Int, protein: Int, carbs: Int, fat: Int) {
        settingsPrefs.calorieGoal = calories
        settingsPrefs.proteinGoal = protein
        settingsPrefs.carbsGoal   = carbs
        settingsPrefs.fatGoal     = fat
        _uiState.update { it.copy(calorieGoal = calories, proteinGoal = protein, carbsGoal = carbs, fatGoal = fat) }
    }

    fun setWeightUnit(newUnit: WeightUnit) {
        val oldUnit = settingsPrefs.preferredWeightUnit
        if (oldUnit == newUnit) return
        settingsPrefs.preferredWeightUnit = newUnit
        _uiState.update { it.copy(weightUnit = newUnit, isConverting = true) }
        viewModelScope.launch {
            weightRepository.convertAllEntries(oldUnit, newUnit)
            _uiState.update { it.copy(isConverting = false) }
        }
    }

    fun saveApiKey(key: String) {
        settingsPrefs.aiApiKey = key
        _uiState.update { it.copy(aiApiKey = key) }
    }

    fun testApiKey(keyFromField: String) {
        val key = keyFromField.trim()
        if (key.isBlank()) {
            _uiState.update { it.copy(apiKeyTestResult = "Please enter an API key in the field first.") }
            return
        }
        _uiState.update { it.copy(isTesting = true) }
        viewModelScope.launch {
            val message = try {
                val request = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = "Reply with: OK"))))
                )
                val response = geminiApi.generateContent(key, request)
                when {
                    response.isSuccessful -> {
                        // Save the key only on success
                        settingsPrefs.aiApiKey = key
                        _uiState.update { it.copy(aiApiKey = key) }
                        "✓ API key is valid and saved"
                    }
                    response.code() == 429 -> {
                        // Save the key on rate limit too (it's valid)
                        settingsPrefs.aiApiKey = key
                        _uiState.update { it.copy(aiApiKey = key) }
                        "✓ API key is valid and saved\n\n(Rate limit hit — this just means the free tier quota was briefly exceeded. Your key works fine.)"
                    }
                    response.code() == 404 -> "✗ Model not found — the AI model may have been renamed or is unavailable"
                    response.code() == 401 || response.code() == 403 -> "✗ Invalid or unauthorized API key"
                    else -> "✗ Error: HTTP ${response.code()}"
                }
            } catch (e: Exception) {
                "✗ Error: ${e.message}"
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(apiKeyTestResult = message, isTesting = false) }
            }
        }
    }

    fun clearApiKeyTestResult() {
        _uiState.update { it.copy(apiKeyTestResult = null) }
    }

    suspend fun getAllDiaryEntries(): List<DiaryEntryWithFood> = diaryRepository.getAllEntriesWithFood()

    suspend fun getAllWeightEntries(): List<WeightEntry> = weightRepository.getAllWeightEntriesOnce()
}
