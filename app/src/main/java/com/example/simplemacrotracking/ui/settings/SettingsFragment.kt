package com.example.simplemacrotracking.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.simplemacrotracking.data.model.AiProviderType
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

    private lateinit var aiProviderAdapter: AiProviderAdapter

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        // Use fragment's lifecycleScope (not viewLifecycleOwner) so the import continues
        // even if the user navigates away mid-import, preventing a crash from a destroyed view.
        val appContext = requireContext().applicationContext
        lifecycleScope.launch {
            val result = CsvImporter.importCsv(
                appContext, uri, foodRepository, diaryRepository, weightRepository
            )
            // Only show the Snackbar if the view is still alive
            val root = _binding?.root ?: return@launch
            Snackbar.make(
                root,
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
            val cal   = binding.etCalories.text.toString().toIntOrNull() ?: return@setOnClickListener
            val prot  = binding.etProtein.text.toString().toIntOrNull() ?: 0
            val carbs = binding.etCarbs.text.toString().toIntOrNull() ?: 0
            val fat   = binding.etFat.text.toString().toIntOrNull() ?: 0
            viewModel.saveGoals(cal, prot, carbs, fat)
            Snackbar.make(requireView(), "Goals saved ✓", Snackbar.LENGTH_SHORT).show()
        }

        binding.rgWeightUnit.setOnCheckedChangeListener { _, checkedId ->
            val unit = if (checkedId == binding.rbLb.id) WeightUnit.LB else WeightUnit.KG
            viewModel.setWeightUnit(unit)
        }

        setupAiProviderList()

        binding.btnAddGemini.setOnClickListener {
            viewModel.addProvider(AiProviderType.GEMINI)
            Snackbar.make(binding.root, "Gemini provider added ↓", Snackbar.LENGTH_SHORT).show()
        }
        binding.btnAddGithubModels.setOnClickListener {
            viewModel.addProvider(AiProviderType.GITHUB_MODELS)
            Snackbar.make(binding.root, "GitHub Models provider added ↓", Snackbar.LENGTH_SHORT).show()
        }

        binding.btnExportDiary.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                CsvExporter.exportDiary(requireContext(), viewModel.getAllDiaryEntries())
            }
        }
        binding.btnExportWeight.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                CsvExporter.exportWeight(requireContext(), viewModel.getAllWeightEntries())
            }
        }
        binding.btnImportCsv.setOnClickListener { importLauncher.launch("*/*") }

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
                    binding.progressConverting.visibility =
                        if (state.isConverting) View.VISIBLE else View.GONE

                    // Update provider list
                    aiProviderAdapter.submitList(state.aiProviders)
                    // Force re-measure for wrap_content RecyclerView in NestedScrollView
                    binding.rvAiProviders.requestLayout()

                    // Disable add buttons when that provider type already exists (one of each only)
                    val hasGemini       = state.aiProviders.any { it.type == AiProviderType.GEMINI }
                    val hasGitHubModels = state.aiProviders.any { it.type == AiProviderType.GITHUB_MODELS }
                    binding.btnAddGemini.isEnabled       = !hasGemini
                    binding.btnAddGithubModels.isEnabled = !hasGitHubModels
                    binding.btnAddGemini.alpha       = if (hasGemini)       0.38f else 1.0f
                    binding.btnAddGithubModels.alpha = if (hasGitHubModels) 0.38f else 1.0f

                    // Update per-item testing spinner
                    aiProviderAdapter.testingProviderId = state.testingProviderId

                    // Show test result dialog
                    state.apiKeyTestResult?.let { message ->
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("AI Provider Test")
                            .setMessage(message)
                            .setPositiveButton("OK") { _, _ -> viewModel.clearApiKeyTestResult() }
                            .setOnCancelListener { viewModel.clearApiKeyTestResult() }
                            .show()
                    }
                }
            }
        }

        // Scroll to newly added provider card
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.scrollToProvider.collect { _ ->
                    binding.rvAiProviders.post {
                        binding.scrollView.post {
                            binding.scrollView.smoothScrollTo(0, binding.rvAiProviders.bottom + 200)
                        }
                    }
                }
            }
        }
    }

    private fun setupAiProviderList() {
        aiProviderAdapter = AiProviderAdapter(
            onProvidersChanged = { viewModel.updateProviders(it) },
            onTestProvider = { pos -> viewModel.testProvider(pos) }
        )

        binding.rvAiProviders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = aiProviderAdapter
        }

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                aiProviderAdapter.onItemMove(vh.adapterPosition, target.adapterPosition)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled() = false
            override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(vh, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) vh?.itemView?.alpha = 0.8f
            }
            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                vh.itemView.alpha = 1.0f
            }
        })
        touchHelper.attachToRecyclerView(binding.rvAiProviders)
        aiProviderAdapter.touchHelper = touchHelper
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
