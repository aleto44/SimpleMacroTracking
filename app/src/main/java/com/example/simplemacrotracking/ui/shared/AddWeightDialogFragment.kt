package com.example.simplemacrotracking.ui.shared

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.example.simplemacrotracking.data.model.WeightEntry
import com.example.simplemacrotracking.data.prefs.SettingsPrefs
import com.example.simplemacrotracking.databinding.DialogAddWeightBinding
import com.example.simplemacrotracking.ui.weight.WeightViewModel
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class AddWeightDialogFragment : DialogFragment() {

    @Inject lateinit var settingsPrefs: SettingsPrefs

    // Activity-scoped so WeightFragment observes the same instance
    private val weightViewModel: WeightViewModel by activityViewModels()
    private var selectedDate: LocalDate = LocalDate.now()
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogAddWeightBinding.inflate(LayoutInflater.from(requireContext()))
        val unit = settingsPrefs.preferredWeightUnit
        binding.tvWeightLabel.text = "Weight (${unit.name.lowercase()})"

        // Initialise date field
        binding.etDate.setText(selectedDate.format(dateFormatter))
        binding.etDate.setOnClickListener { showDatePicker(binding) }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Weight Entry")
            .setView(binding.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val value = binding.etWeight.text.toString().toFloatOrNull() ?: return@setPositiveButton
                weightViewModel.addWeightEntry(WeightEntry(date = selectedDate, value = value, unit = unit))
            }
            .create()
    }

    private fun showDatePicker(binding: DialogAddWeightBinding) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select Date")
            .setSelection(selectedDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
            binding.etDate.setText(selectedDate.format(dateFormatter))
        }
        picker.show(parentFragmentManager, "weight_date_picker")
    }
}
