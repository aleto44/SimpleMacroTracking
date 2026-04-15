package com.example.simplemacrotracking.ui.shared

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.simplemacrotracking.R
import com.example.simplemacrotracking.data.model.DiaryEntry
import com.example.simplemacrotracking.data.model.FoodItem
import com.example.simplemacrotracking.data.repository.DiaryRepository
import com.example.simplemacrotracking.data.repository.FoodRepository
import com.example.simplemacrotracking.databinding.FragmentItemActionSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class ItemActionSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentItemActionSheetBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var foodRepository: FoodRepository
    @Inject lateinit var diaryRepository: DiaryRepository

    private var foodItem: FoodItem? = null
    private var viewCreated = false

    private val foodItemId: Long by lazy { arguments?.getLong("foodItemId", -1L) ?: -1L }
    private val targetDate: String by lazy {
        arguments?.getString("targetDate")?.ifBlank { null } ?: LocalDate.now().toString()
    }
    private val diaryEntryId: Long by lazy { arguments?.getLong("diaryEntryId", -1L) ?: -1L }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentItemActionSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val isEditing = diaryEntryId > 0
        viewCreated = true

        binding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val amount = s?.toString()?.toFloatOrNull()
                binding.btnAction.isEnabled = amount != null && amount > 0f
                updatePreview(amount ?: 0f)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })

        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnEditFood.setOnClickListener {
            val food = foodItem ?: return@setOnClickListener
            findNavController().navigate(
                R.id.action_itemActionSheet_to_addEntryBottomSheet,
                bundleOf("editFoodId" to food.id, "targetDate" to targetDate)
            )
        }

        binding.btnAction.setOnClickListener {
            val amount = binding.etAmount.text.toString().toFloatOrNull() ?: return@setOnClickListener
            val food = foodItem ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                if (isEditing) {
                    val existing = diaryRepository.getDiaryEntryById(diaryEntryId)
                    existing?.let { diaryRepository.updateDiaryEntry(it.copy(actualAmount = amount)) }
                } else {
                    diaryRepository.insertDiaryEntry(
                        DiaryEntry(
                            date = LocalDate.parse(targetDate),
                            foodItemId = food.id,
                            actualAmount = amount,
                            measurementType = food.measurementType
                        )
                    )
                }
                parentFragmentManager.setFragmentResult(
                    "item_action_result",
                    Bundle().apply { putBoolean("saved", true) }
                )
                // Pop all the way back to the diary when adding a new entry
                // (covers the path: Diary → FoodDatabase → ItemActionSheet)
                val poppedToDiary = !isEditing &&
                    findNavController().popBackStack(R.id.diaryFragment, false)
                if (!poppedToDiary) findNavController().popBackStack()
            }
        }

        loadFoodData(isEditing, populateAmountField = true)
    }

    override fun onStart() {
        super.onStart()
        // Refresh food info when returning from edit (pencil navigation)
        if (viewCreated) loadFoodData(diaryEntryId > 0, populateAmountField = false)
    }

    private fun loadFoodData(isEditing: Boolean, populateAmountField: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val food = foodRepository.getFoodItemById(foodItemId) ?: return@launch
            foodItem = food
            binding.tvFoodName.text = food.name
            binding.tvFoodInfo.text = buildString {
                if (!food.brand.isNullOrBlank()) append("${food.brand} · ")
                append("per %.4g %s".format(food.baseAmount, food.measurementType)
                    .replace(Regex("(\\d)0+ "), "$1 "))
            }
            binding.tvUnit.text = food.measurementType

            if (populateAmountField) {
                binding.btnAction.text = if (isEditing) "Save Changes" else "Add to Diary"
                val amount = if (isEditing) {
                    diaryRepository.getDiaryEntryById(diaryEntryId)?.actualAmount ?: food.baseAmount
                } else food.baseAmount
                binding.etAmount.setText(formatFloat(amount))
                updatePreview(amount)
            } else {
                // Just refresh the displayed macros with current amount
                val current = binding.etAmount.text.toString().toFloatOrNull() ?: food.baseAmount
                updatePreview(current)
            }
        }
    }

    private fun formatFloat(v: Float): String {
        return if (v == v.toLong().toFloat()) "%.0f".format(v) else "%.2f".format(v).trimEnd('0')
    }

    private fun updatePreview(amount: Float) {
        val food = foodItem ?: return
        val scale = if (food.baseAmount > 0) amount / food.baseAmount else 0f
        binding.tvPreviewCalories.text = "Calories: %.0f".format(food.calories * scale)
        binding.tvPreviewProtein.text = "Protein: %.1f g".format(food.proteinG * scale)
        binding.tvPreviewCarbs.text = "Carbs: %.1f g".format(food.carbsG * scale)
        binding.tvPreviewFat.text = "Fat: %.1f g".format(food.fatG * scale)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        viewCreated = false
    }
}
