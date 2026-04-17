package com.example.simplemacrotracking.ui.diary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.simplemacrotracking.MainActivity
import com.example.simplemacrotracking.R
import com.example.simplemacrotracking.databinding.FragmentDiaryBinding
import com.example.simplemacrotracking.ui.shared.SharedPickerViewModel
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
    private val sharedPickerViewModel: SharedPickerViewModel by activityViewModels()
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
            sharedPickerViewModel.requestPicker(viewModel.uiState.value.date.toString())
            (activity as MainActivity).selectFoodsTab()
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

        // Hero calories
        val calConsumed = state.consumed.calories.toInt()
        val calGoal = state.goals.calories.toInt()
        val calRemaining = calGoal - calConsumed
        val calRatio = if (calGoal > 0) state.consumed.calories / state.goals.calories else 0f
        binding.progressCalories.ratio = calRatio
        binding.tvCaloriesHero.text = calConsumed.toString()
        binding.tvCaloriesRemaining.text = if (calRemaining >= 0) "$calRemaining remaining" else "${-calRemaining} over"
        binding.tvCaloriesRemaining.setTextColor(
            if (calRemaining >= 0)
                requireContext().getColor(R.color.color_accent_green)
            else
                requireContext().getColor(R.color.color_accent_red)
        )
        binding.tvCaloriesGoalLabel.text = "/ ${calGoal} kcal goal"
        // Keep hidden tv_calories in sync for any code that reads it
        binding.tvCalories.text = "$calConsumed / $calGoal kcal"

        // Protein
        val hasProteinGoal = state.goals.proteinG > 0
        binding.rowProtein.visibility = if (hasProteinGoal) View.VISIBLE else View.GONE
        binding.progressProtein.visibility = if (hasProteinGoal) View.VISIBLE else View.GONE
        if (hasProteinGoal) {
            binding.progressProtein.ratio = state.consumed.proteinG / state.goals.proteinG
            binding.tvProtein.text = "%.0fg / %.0fg".format(state.consumed.proteinG, state.goals.proteinG)
        }

        // Carbs
        val hasCarbsGoal = state.goals.carbsG > 0
        binding.rowCarbs.visibility = if (hasCarbsGoal) View.VISIBLE else View.GONE
        binding.progressCarbs.visibility = if (hasCarbsGoal) View.VISIBLE else View.GONE
        if (hasCarbsGoal) {
            binding.progressCarbs.ratio = state.consumed.carbsG / state.goals.carbsG
            binding.tvCarbs.text = "%.0fg / %.0fg".format(state.consumed.carbsG, state.goals.carbsG)
        }

        // Fat
        val hasFatGoal = state.goals.fatG > 0
        binding.rowFat.visibility = if (hasFatGoal) View.VISIBLE else View.GONE
        binding.progressFat.visibility = if (hasFatGoal) View.VISIBLE else View.GONE
        if (hasFatGoal) {
            binding.progressFat.ratio = state.consumed.fatG / state.goals.fatG
            binding.tvFat.text = "%.0fg / %.0fg".format(state.consumed.fatG, state.goals.fatG)
        }

        // Hide the entire macros card if no macro goals are set
        binding.cardMacros.visibility =
            if (hasProteinGoal || hasCarbsGoal || hasFatGoal) View.VISIBLE else View.GONE
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
