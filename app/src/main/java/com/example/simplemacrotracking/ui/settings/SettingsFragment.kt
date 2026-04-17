package com.example.simplemacrotracking.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.simplemacrotracking.data.model.enums.WeightUnit
import com.example.simplemacrotracking.data.repository.DiaryRepository
import com.example.simplemacrotracking.data.repository.FoodRepository
import com.example.simplemacrotracking.data.repository.WeightRepository
import com.example.simplemacrotracking.databinding.FragmentSettingsBinding
import com.example.simplemacrotracking.util.CsvExporter
import com.example.simplemacrotracking.util.CsvImporter
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    @Inject lateinit var foodRepository: FoodRepository
    @Inject lateinit var diaryRepository: DiaryRepository
    @Inject lateinit var weightRepository: WeightRepository

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            val result = CsvImporter.importCsv(
                requireContext(), uri, foodRepository, diaryRepository, weightRepository
            )
            Snackbar.make(
                binding.root,
                "Imported ${result.imported} entries, ${result.skipped} rows skipped",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSaveGoals.setOnClickListener {
            val cal  = binding.etCalories.text.toString().toIntOrNull() ?: return@setOnClickListener
            val prot = binding.etProtein.text.toString().toIntOrNull() ?: 0
            val carbs = binding.etCarbs.text.toString().toIntOrNull() ?: 0
            val fat  = binding.etFat.text.toString().toIntOrNull() ?: 0
            viewModel.saveGoals(cal, prot, carbs, fat)
            Snackbar.make(requireView(), "Goals saved ✓", Snackbar.LENGTH_SHORT).show()
        }

        binding.rgWeightUnit.setOnCheckedChangeListener { _, checkedId ->
            val unit = if (checkedId == binding.rbLb.id) WeightUnit.LB else WeightUnit.KG
            viewModel.setWeightUnit(unit)
        }


        binding.btnTestApiKey.setOnClickListener {
            viewModel.testApiKey(binding.etApiKey.text.toString())
        }

        binding.btnExportDiary.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val entries = viewModel.getAllDiaryEntries()
                CsvExporter.exportDiary(requireContext(), entries)
            }
        }

        binding.btnExportWeight.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val entries = viewModel.getAllWeightEntries()
                CsvExporter.exportWeight(requireContext(), entries)
            }
        }

        binding.btnImportCsv.setOnClickListener {
            importLauncher.launch("*/*")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.etCalories.setText(state.calorieGoal.toString())
                    binding.etProtein.setText(if (state.proteinGoal > 0) state.proteinGoal.toString() else "")
                    binding.etCarbs.setText(if (state.carbsGoal > 0) state.carbsGoal.toString() else "")
                    binding.etFat.setText(if (state.fatGoal > 0) state.fatGoal.toString() else "")
                    binding.rgWeightUnit.check(
                        if (state.weightUnit == WeightUnit.LB) binding.rbLb.id else binding.rbKg.id
                    )
                    binding.etApiKey.setText(state.aiApiKey)
                    binding.progressConverting.visibility =
                        if (state.isConverting) View.VISIBLE else View.GONE

                    // Test button: show spinner text while in-flight, disable while testing
                    binding.btnTestApiKey.isEnabled = !state.isTesting
                    binding.btnTestApiKey.text = if (state.isTesting) "Testing..." else "Test"

                    // Show result dialog when result arrives
                    state.apiKeyTestResult?.let { message ->
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("API Key Test")
                            .setMessage(message)
                            .setPositiveButton("OK") { _, _ -> viewModel.clearApiKeyTestResult() }
                            .setOnCancelListener { viewModel.clearApiKeyTestResult() }
                            .show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
