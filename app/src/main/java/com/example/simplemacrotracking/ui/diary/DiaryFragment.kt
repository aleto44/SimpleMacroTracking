package com.example.simplemacrotracking.ui.diary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.simplemacrotracking.MainActivity
import com.example.simplemacrotracking.R
import com.example.simplemacrotracking.databinding.FragmentDiaryBinding
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@AndroidEntryPoint
class DiaryFragment : Fragment() {

    private var _binding: FragmentDiaryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DiaryViewModel by viewModels()
    private lateinit var adapter: DiaryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DiaryAdapter(
            onItemClick = { ewf ->
                // Navigate to ItemActionSheet for editing
                findNavController().navigate(
                    R.id.action_diary_to_itemActionSheet,
                    bundleOf(
                        "foodItemId" to ewf.food.id,
                        "targetDate" to ewf.entry.date.toString(),
                        "diaryEntryId" to ewf.entry.id
                    )
                )
            },
            onItemLongClick = { ewf ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Remove from diary?")
                    .setMessage("Remove \"${ewf.food.name}\" from your log for ${ewf.entry.date}?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteDiaryEntry(ewf.entry.id)
                    }
                    .show()
            }
        )
        binding.recyclerView.adapter = adapter

        binding.btnPrevDay.setOnClickListener { viewModel.previousDay() }
        binding.btnNextDay.setOnClickListener { viewModel.nextDay() }

        binding.tvDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date")
                .setSelection(
                    viewModel.uiState.value.date
                        .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
                )
                .build()
            picker.addOnPositiveButtonClickListener { millis ->
                val date = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                viewModel.setDate(date)
            }
            picker.show(parentFragmentManager, "date_picker")
        }

        (activity as MainActivity).getFab().setOnClickListener {
            findNavController().navigate(
                R.id.action_diary_to_foodDatabase,
                bundleOf(
                    "pickerMode" to true,
                    "targetDate" to viewModel.uiState.value.date.toString()
                )
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())

    private fun render(state: DiaryUiState) {
        binding.tvDate.text = state.date.format(dateFormatter)
        adapter.submitList(state.entries)

        // Calories
        val calRatio = if (state.goals.calories > 0) state.consumed.calories / state.goals.calories else 0f
        binding.progressCalories.ratio = calRatio
        binding.tvCalories.text = "${state.consumed.calories.toInt()} / ${state.goals.calories.toInt()} kcal"

        // Protein
        val protRatio = if (state.goals.proteinG > 0) state.consumed.proteinG / state.goals.proteinG else 0f
        binding.progressProtein.ratio = protRatio
        binding.tvProtein.text = "%.0fg / %.0fg".format(state.consumed.proteinG, state.goals.proteinG)

        // Carbs
        val carbRatio = if (state.goals.carbsG > 0) state.consumed.carbsG / state.goals.carbsG else 0f
        binding.progressCarbs.ratio = carbRatio
        binding.tvCarbs.text = "%.0fg / %.0fg".format(state.consumed.carbsG, state.goals.carbsG)

        // Fat
        val fatRatio = if (state.goals.fatG > 0) state.consumed.fatG / state.goals.fatG else 0f
        binding.progressFat.ratio = fatRatio
        binding.tvFat.text = "%.0fg / %.0fg".format(state.consumed.fatG, state.goals.fatG)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshGoals()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
