package com.example.simplemacrotracking.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simplemacrotracking.data.model.WeightEntry
import com.example.simplemacrotracking.data.repository.DiaryRepository
import com.example.simplemacrotracking.data.repository.WeightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class TimeRange { W1, M1, M3, Y1, ALL }

data class WeightUiState(
    val allEntries: List<WeightEntry> = emptyList(),
    val filteredEntries: List<WeightEntry> = emptyList(),
    val dailyCalories: Map<LocalDate, Float> = emptyMap(),
    val timeRange: TimeRange = TimeRange.M3,
    val showWeight: Boolean = true,
    val showCalories: Boolean = true,
    val showMovingAverage: Boolean = false,
    val isLoading: Boolean = false
)

@HiltViewModel
class WeightViewModel @Inject constructor(
    private val weightRepository: WeightRepository,
    private val diaryRepository: DiaryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeightUiState())
    val uiState: StateFlow<WeightUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            weightRepository.getAllWeightEntries().collect { entries ->
                val filtered = filterEntries(entries, _uiState.value.timeRange)
                _uiState.update { it.copy(allEntries = entries, filteredEntries = filtered) }
            }
        }
        viewModelScope.launch {
            diaryRepository.streamAllEntriesWithFood().collect { allDiary ->
                val dailyCals = allDiary.groupBy { it.entry.date }.mapValues { (_, items) ->
                    items.sumOf { ewf ->
                        val ratio = if (ewf.food.baseAmount > 0) ewf.entry.actualAmount / ewf.food.baseAmount else 0f
                        (ewf.food.calories * ratio).toDouble()
                    }.toFloat()
                }
                _uiState.update { it.copy(dailyCalories = dailyCals) }
            }
        }
    }

    fun setTimeRange(range: TimeRange) {
        val filtered = filterEntries(_uiState.value.allEntries, range)
        _uiState.update { it.copy(timeRange = range, filteredEntries = filtered) }
    }

    fun setShowWeight(show: Boolean) { _uiState.update { it.copy(showWeight = show) } }
    fun setShowCalories(show: Boolean) { _uiState.update { it.copy(showCalories = show) } }
    fun setShowMovingAverage(show: Boolean) { _uiState.update { it.copy(showMovingAverage = show) } }

    fun addWeightEntry(entry: WeightEntry) {
        viewModelScope.launch { weightRepository.insertWeightEntry(entry) }
    }

    fun updateWeightEntry(entry: WeightEntry) {
        viewModelScope.launch { weightRepository.updateWeightEntry(entry) }
    }

    fun deleteWeightEntry(entry: WeightEntry) {
        viewModelScope.launch { weightRepository.deleteWeightEntry(entry) }
    }

    private fun filterEntries(entries: List<WeightEntry>, range: TimeRange): List<WeightEntry> {
        val cutoff = when (range) {
            TimeRange.W1  -> LocalDate.now().minusWeeks(1)
            TimeRange.M1  -> LocalDate.now().minusMonths(1)
            TimeRange.M3  -> LocalDate.now().minusMonths(3)
            TimeRange.Y1  -> LocalDate.now().minusYears(1)
            TimeRange.ALL -> LocalDate.MIN
        }
        return entries.filter { !it.date.isBefore(cutoff) }
    }
}
