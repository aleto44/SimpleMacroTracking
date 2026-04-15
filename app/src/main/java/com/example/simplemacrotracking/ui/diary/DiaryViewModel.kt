package com.example.simplemacrotracking.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simplemacrotracking.data.model.DiaryEntryWithFood
import com.example.simplemacrotracking.data.prefs.SettingsPrefs
import com.example.simplemacrotracking.data.repository.DiaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class Macros(
    val calories: Float = 0f,
    val proteinG: Float = 0f,
    val carbsG: Float = 0f,
    val fatG: Float = 0f
)

data class DiaryUiState(
    val date: LocalDate = LocalDate.now(),
    val entries: List<DiaryEntryWithFood> = emptyList(),
    val consumed: Macros = Macros(),
    val goals: Macros = Macros(),
    val isLoading: Boolean = false
)

@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val diaryRepository: DiaryRepository,
    private val settingsPrefs: SettingsPrefs
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiaryUiState())
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())

    init {
        loadGoals()
        observeEntries()
    }

    private fun loadGoals() {
        _uiState.update {
            it.copy(
                goals = Macros(
                    calories = settingsPrefs.calorieGoal.toFloat(),
                    proteinG = settingsPrefs.proteinGoal.toFloat(),
                    carbsG = settingsPrefs.carbsGoal.toFloat(),
                    fatG = settingsPrefs.fatGoal.toFloat()
                )
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeEntries() {
        viewModelScope.launch {
            _selectedDate.flatMapLatest { date ->
                diaryRepository.getEntriesWithFoodForDate(date)
            }.collect { entries ->
                val consumed = entries.fold(Macros()) { acc, ewf ->
                    val scale = ewf.entry.actualAmount / ewf.food.baseAmount
                    acc.copy(
                        calories = acc.calories + ewf.food.calories * scale,
                        proteinG = acc.proteinG + ewf.food.proteinG * scale,
                        carbsG = acc.carbsG + ewf.food.carbsG * scale,
                        fatG = acc.fatG + ewf.food.fatG * scale
                    )
                }
                _uiState.update {
                    it.copy(
                        date = _selectedDate.value,
                        entries = entries,
                        consumed = consumed
                    )
                }
            }
        }
    }

    fun setDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun previousDay() {
        _selectedDate.value = _selectedDate.value.minusDays(1)
    }

    fun nextDay() {
        _selectedDate.value = _selectedDate.value.plusDays(1)
    }

    fun deleteDiaryEntry(id: Long) {
        viewModelScope.launch {
            diaryRepository.deleteDiaryEntryById(id)
        }
    }

    fun refreshGoals() = loadGoals()
}

