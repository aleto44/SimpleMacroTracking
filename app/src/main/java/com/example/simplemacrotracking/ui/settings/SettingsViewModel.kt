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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val calorieGoal: Int = 2000,
    val proteinGoal: Int = 150,
    val carbsGoal: Int = 200,
    val fatGoal: Int = 65,
    val weightUnit: WeightUnit = WeightUnit.LB,
    val aiApiKey: String = "",
    val isConverting: Boolean = false
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

    fun testApiKey(onResult: (String) -> Unit) {
        val key = settingsPrefs.aiApiKey
        if (key.isBlank()) { onResult("No API key saved"); return }
        viewModelScope.launch {
            try {
                val request = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = "Reply with: OK"))))
                )
                val response = geminiApi.generateContent(key, request)
                if (response.isSuccessful) onResult("✓ API key is valid")
                else onResult("✗ Error: HTTP ${response.code()}")
            } catch (e: Exception) {
                onResult("✗ Error: ${e.message}")
            }
        }
    }

    suspend fun getAllDiaryEntries(): List<DiaryEntryWithFood> = diaryRepository.getAllEntriesWithFood()

    suspend fun getAllWeightEntries(): List<WeightEntry> = weightRepository.getAllWeightEntriesOnce()
}
