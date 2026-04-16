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

    private val weightViewModel: WeightViewModel by activityViewModels()
    private var selectedDate: LocalDate = LocalDate.now()
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    // When editing an existing entry, this holds its id so we update rather than insert
    private var editingEntryId: Long? = null

    companion object {
        private const val ARG_ENTRY_ID = "entry_id"
        private const val ARG_ENTRY_VALUE = "entry_value"
        private const val ARG_ENTRY_DATE = "entry_date"

        fun newEditInstance(entry: WeightEntry): AddWeightDialogFragment {
            return AddWeightDialogFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_ENTRY_ID, entry.id)
                    putFloat(ARG_ENTRY_VALUE, entry.value)
                    putString(ARG_ENTRY_DATE, entry.date.toString())
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogAddWeightBinding.inflate(LayoutInflater.from(requireContext()))
        val unit = settingsPrefs.preferredWeightUnit
        binding.tvWeightLabel.text = "Weight (${unit.name.lowercase()})"

        // Pre-populate if editing
        arguments?.let { args ->
            if (args.containsKey(ARG_ENTRY_ID)) {
                editingEntryId = args.getLong(ARG_ENTRY_ID)
                binding.etWeight.setText(args.getFloat(ARG_ENTRY_VALUE).toString())
                selectedDate = LocalDate.parse(args.getString(ARG_ENTRY_DATE))
            }
        }

        binding.etDate.setText(selectedDate.format(dateFormatter))
        binding.etDate.setOnClickListener { showDatePicker(binding) }

        val title = if (editingEntryId != null) "Edit Weight Entry" else "Add Weight Entry"

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(binding.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val value = binding.etWeight.text.toString().toFloatOrNull() ?: return@setPositiveButton
                val id = editingEntryId
                if (id != null) {
                    weightViewModel.updateWeightEntry(WeightEntry(id = id, date = selectedDate, value = value, unit = unit))
                } else {
                    weightViewModel.addWeightEntry(WeightEntry(date = selectedDate, value = value, unit = unit))
                }
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
