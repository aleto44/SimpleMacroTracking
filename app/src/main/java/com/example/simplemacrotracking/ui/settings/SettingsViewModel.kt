package com.example.simplemacrotracking.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simplemacrotracking.data.model.AiProviderConfig
import com.example.simplemacrotracking.data.model.AiProviderType
import com.example.simplemacrotracking.data.model.DiaryEntryWithFood
import com.example.simplemacrotracking.data.model.WeightEntry
import com.example.simplemacrotracking.data.model.enums.WeightUnit
import com.example.simplemacrotracking.data.prefs.SettingsPrefs
import com.example.simplemacrotracking.data.repository.DiaryRepository
import com.example.simplemacrotracking.data.repository.WeightRepository
import com.example.simplemacrotracking.data.service.WaterfallAiService
import com.example.simplemacrotracking.util.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class SettingsUiState(
    val calorieGoal: Int = 2000,
    val proteinGoal: Int = 0,
    val carbsGoal: Int = 0,
    val fatGoal: Int = 0,
    val weightUnit: WeightUnit = WeightUnit.LB,
    val aiApiKey: String = "",
    val aiProviders: List<AiProviderConfig> = emptyList(),
    val isConverting: Boolean = false,
    /** ID of the provider currently being tested (null = none). */
    val testingProviderId: String? = null,
    val apiKeyTestResult: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsPrefs: SettingsPrefs,
    private val weightRepository: WeightRepository,
    private val diaryRepository: DiaryRepository,
    private val networkUtils: NetworkUtils,
    private val waterfallAiService: WaterfallAiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(loadFromPrefs())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Fires once after a provider is added — the Int is the new item's adapter position. */
    private val _scrollToProvider = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val scrollToProvider: SharedFlow<Int> = _scrollToProvider.asSharedFlow()

    private fun loadFromPrefs() = SettingsUiState(
        calorieGoal = settingsPrefs.calorieGoal,
        proteinGoal = settingsPrefs.proteinGoal,
        carbsGoal   = settingsPrefs.carbsGoal,
        fatGoal     = settingsPrefs.fatGoal,
        weightUnit  = settingsPrefs.preferredWeightUnit,
        aiApiKey    = settingsPrefs.aiApiKey,
        aiProviders = settingsPrefs.aiProviders
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

    // ── AI Provider management ─────────────────────────────────────────────────

    fun addProvider(type: AiProviderType) {
        val current = try { settingsPrefs.aiProviders } catch (e: Exception) { emptyList() }
        // Enforce one-of-each rule
        if (current.any { it.type == type }) return
        val updated = current.toMutableList().also {
            it.add(AiProviderConfig(id = UUID.randomUUID().toString(), type = type))
        }
        try { settingsPrefs.aiProviders = updated } catch (e: Exception) { /* prefs write failed; state still updates */ }
        _uiState.update { it.copy(aiProviders = updated) }
        _scrollToProvider.tryEmit(updated.lastIndex)
    }

    fun updateProviders(providers: List<AiProviderConfig>) {
        settingsPrefs.aiProviders = providers
        _uiState.update { it.copy(aiProviders = providers) }
    }

    fun testProvider(position: Int) {
        val providers = settingsPrefs.aiProviders
        if (position < 0 || position >= providers.size) return
        val config = providers[position]

        if (config.apiKey.isBlank()) {
            _uiState.update { it.copy(apiKeyTestResult = "Please enter an API key first.") }
            return
        }
        if (!networkUtils.isOnline()) {
            _uiState.update { it.copy(apiKeyTestResult = "✗ No internet connection.") }
            return
        }

        _uiState.update { it.copy(testingProviderId = config.id) }
        viewModelScope.launch {
            val message = waterfallAiService.testProvider(config)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(apiKeyTestResult = message, testingProviderId = null) }
            }
        }
    }

    fun clearApiKeyTestResult() {
        _uiState.update { it.copy(apiKeyTestResult = null) }
    }

    suspend fun getAllDiaryEntries(): List<DiaryEntryWithFood> = diaryRepository.getAllEntriesWithFood()

    suspend fun getAllWeightEntries(): List<WeightEntry> = weightRepository.getAllWeightEntriesOnce()
}
