package com.example.simplemacrotracking.ui.shared

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class PickerRequest(
    val targetDate: String,
    val active: Boolean = true
)

@HiltViewModel
class SharedPickerViewModel @Inject constructor() : ViewModel() {

    private val _pickerRequest = MutableStateFlow<PickerRequest?>(null)
    val pickerRequest: StateFlow<PickerRequest?> = _pickerRequest

    fun requestPicker(targetDate: String) {
        _pickerRequest.value = PickerRequest(targetDate = targetDate)
    }

    fun consumeRequest() {
        _pickerRequest.value = null
    }
}

