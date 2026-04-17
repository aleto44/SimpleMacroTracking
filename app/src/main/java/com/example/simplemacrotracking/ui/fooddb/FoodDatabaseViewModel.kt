package com.example.simplemacrotracking.ui.fooddb

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simplemacrotracking.data.model.FoodItem
import com.example.simplemacrotracking.data.repository.FoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FoodDbUiState(
    val items: List<FoodItem> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = false
)

@HiltViewModel
class FoodDatabaseViewModel @Inject constructor(
    private val foodRepository: FoodRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodDbUiState())
    val uiState: StateFlow<FoodDbUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")

    init {
        observeItems()
    }

    @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    private fun observeItems() {
        viewModelScope.launch {
            _query.debounce(200).flatMapLatest { q ->
                if (q.isBlank()) foodRepository.getAllFoodItems()
                else foodRepository.searchFoodItems(q)
            }.collect { items ->
                _uiState.update { it.copy(items = items, query = _query.value) }
            }
        }
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun deleteFoodItem(item: FoodItem) {
        viewModelScope.launch {
            foodRepository.deleteFoodItem(item)
        }
    }
}

