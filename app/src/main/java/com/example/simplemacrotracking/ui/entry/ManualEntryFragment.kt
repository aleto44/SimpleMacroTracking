package com.example.simplemacrotracking.ui.entry

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.simplemacrotracking.data.model.FoodItem
import com.example.simplemacrotracking.data.model.enums.FoodSource
import com.example.simplemacrotracking.data.repository.FoodRepository
import com.example.simplemacrotracking.databinding.FragmentManualEntryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ManualEntryFragment : Fragment() {

    private var _binding: FragmentManualEntryBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var foodRepository: FoodRepository

    private var editFoodId: Long = -1L
    private var existingFood: FoodItem? = null

    companion object {
        fun newInstance(targetDate: String, editFoodId: Long = -1L) =
            ManualEntryFragment().apply {
                arguments = bundleOf(
                    "targetDate" to targetDate,
                    "editFoodId" to editFoodId
                )
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManualEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        editFoodId = arguments?.getLong("editFoodId", -1L) ?: -1L

        // Unit autocomplete
        val units = listOf("g", "oz", "ml", "serving", "piece", "tbsp", "tsp", "cup")
        val unitAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, units)
        binding.actvUnit.setAdapter(unitAdapter)
        if (savedInstanceState == null) binding.actvUnit.setText("g", false)

        if (editFoodId > 0) {
            viewLifecycleOwner.lifecycleScope.launch {
                val food = foodRepository.getFoodItemById(editFoodId)
                existingFood = food
                food?.let { prefillForm(it) }
            }
        }

        binding.btnSave.setOnClickListener { saveFood() }
    }

    private fun prefillForm(food: FoodItem) {
        binding.etName.setText(food.name)
        binding.etBrand.setText(food.brand ?: "")
        binding.etBaseAmount.setText("%.4g".format(food.baseAmount).trimEnd('0').trimEnd('.'))
        binding.actvUnit.setText(food.measurementType, false)
        binding.etCalories.setText("%.4g".format(food.calories).trimEnd('0').trimEnd('.'))
        binding.etProtein.setText("%.4g".format(food.proteinG).trimEnd('0').trimEnd('.'))
        binding.etCarbs.setText("%.4g".format(food.carbsG).trimEnd('0').trimEnd('.'))
        binding.etFat.setText("%.4g".format(food.fatG).trimEnd('0').trimEnd('.'))
    }

    private fun saveFood() {
        val name = binding.etName.text.toString().trim()
        if (name.isBlank()) {
            binding.tilName.error = "Name is required"
            return
        }
        binding.tilName.error = null

        val baseAmount = binding.etBaseAmount.text.toString().toFloatOrNull()
        if (baseAmount == null || baseAmount <= 0) {
            binding.tilBaseAmount.error = "Enter a valid amount"
            return
        }
        binding.tilBaseAmount.error = null

        val brand = binding.etBrand.text.toString().trim().ifBlank { null }
        val unit = binding.actvUnit.text.toString().trim().ifBlank { "g" }
        val calories = binding.etCalories.text.toString().toFloatOrNull() ?: 0f
        val protein = binding.etProtein.text.toString().toFloatOrNull() ?: 0f
        val carbs = binding.etCarbs.text.toString().toFloatOrNull() ?: 0f
        val fat = binding.etFat.text.toString().toFloatOrNull() ?: 0f

        viewLifecycleOwner.lifecycleScope.launch {
            val foodId: Long
            if (editFoodId > 0 && existingFood != null) {
                val updated = existingFood!!.copy(
                    name = name, brand = brand, baseAmount = baseAmount,
                    measurementType = unit, calories = calories,
                    proteinG = protein, carbsG = carbs, fatG = fat
                )
                foodRepository.updateFoodItem(updated)
                foodId = editFoodId
            } else {
                val food = FoodItem(
                    name = name, brand = brand, baseAmount = baseAmount,
                    measurementType = unit, calories = calories,
                    proteinG = protein, carbsG = carbs, fatG = fat,
                    source = FoodSource.MANUAL
                )
                foodId = foodRepository.saveFoodItem(food)
            }
            parentFragmentManager.setFragmentResult(
                "food_saved",
                bundleOf("foodItemId" to foodId)
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

