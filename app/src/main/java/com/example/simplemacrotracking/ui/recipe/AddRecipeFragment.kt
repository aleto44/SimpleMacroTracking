package com.example.simplemacrotracking.ui.recipe

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.simplemacrotracking.R
import com.example.simplemacrotracking.data.model.FoodItem
import com.example.simplemacrotracking.data.repository.FoodRepository
import com.example.simplemacrotracking.data.repository.IngredientDraft
import com.example.simplemacrotracking.data.repository.RecipeRepository
import com.example.simplemacrotracking.databinding.DialogIngredientPickerBinding
import com.example.simplemacrotracking.databinding.FragmentAddRecipeBinding
import com.example.simplemacrotracking.databinding.ItemRecipeIngredientBinding
import com.example.simplemacrotracking.ui.entry.AddEntryBottomSheet
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AddRecipeFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentAddRecipeBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var foodRepository: FoodRepository
    @Inject lateinit var recipeRepository: RecipeRepository

    private val ingredientDrafts = mutableListOf<IngredientDraft>()
    private lateinit var ingredientListAdapter: IngredientListAdapter
    private var pickerDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddRecipeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val units = listOf("g", "oz", "ml", "serving", "piece", "tbsp", "tsp", "cup")
        binding.actvServingUnit.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, units)
        )
        binding.actvServingUnit.setText("serving", false)

        ingredientListAdapter = IngredientListAdapter { draft ->
            ingredientDrafts.remove(draft)
            ingredientListAdapter.submitList(ingredientDrafts.toList())
            updateTotals()
        }
        binding.rvIngredients.adapter = ingredientListAdapter

        binding.btnAddIngredient.setOnClickListener { showIngredientPicker() }
        binding.btnCancelRecipe.setOnClickListener { dismiss() }
        binding.btnSaveRecipe.setOnClickListener { saveRecipe() }

        // Listen for new food created via inline add-food sheet (recipeMode=true)
        childFragmentManager.setFragmentResultListener("ingredient_food_saved", viewLifecycleOwner) { _, bundle ->
            val foodId = bundle.getLong("foodItemId", -1L)
            if (foodId < 0) return@setFragmentResultListener
            viewLifecycleOwner.lifecycleScope.launch {
                val food = foodRepository.getFoodItemById(foodId) ?: return@launch
                showAmountDialog(food)
            }
        }
    }

    private fun showIngredientPicker() {
        val pickerView = DialogIngredientPickerBinding.inflate(layoutInflater)
        var searchJob: Job? = null

        val pickerAdapter = FoodPickerAdapter { food ->
            pickerDialog?.dismiss()
            showAmountDialog(food)
        }
        pickerView.rvIngredientResults.adapter = pickerAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            pickerAdapter.submitList(foodRepository.searchBaseFoods(""))
        }

        pickerView.etIngredientSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(200)
                    pickerAdapter.submitList(foodRepository.searchBaseFoods(s?.toString() ?: ""))
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })

        pickerView.btnCreateNewIngredient.setOnClickListener {
            pickerDialog?.dismiss()
            AddEntryBottomSheet().apply {
                arguments = bundleOf("recipeMode" to true, "targetDate" to "")
            }.show(childFragmentManager, "add_ingredient_food")
        }

        pickerDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Ingredient")
            .setView(pickerView.root)
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAmountDialog(food: FoodItem) {
        val outerContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 20, 48, 16)
        }

        // Food details (name, brand, base amount)
        val detailsText = TextView(requireContext()).apply {
            text = buildString {
                append(food.name)
                if (!food.brand.isNullOrBlank()) append(" · ${food.brand}")
            }
            textSize = 14f
            setTextColor(requireContext().getColor(R.color.color_text_secondary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        outerContainer.addView(detailsText)

        // "AMOUNT" label
        val amountLabel = TextView(requireContext()).apply {
            text = "AMOUNT"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(requireContext().getColor(R.color.color_text_secondary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }
        outerContainer.addView(amountLabel)

        // Amount input row (input + unit)
        val inputRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }
        val amountInput = EditText(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(formatFloat(food.baseAmount))
            textSize = 16f
            selectAll()
        }
        val unitLabel = TextView(requireContext()).apply {
            text = food.measurementType
            textSize = 16f
            setTextColor(requireContext().getColor(R.color.color_text_secondary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = 12 }
        }
        inputRow.addView(amountInput)
        inputRow.addView(unitLabel)
        outerContainer.addView(inputRow)

        // Macro preview
        val scale = if (food.baseAmount > 0f) food.baseAmount / food.baseAmount else 1f
        val macrosText = TextView(requireContext()).apply {
            text = buildString {
                append("Calories: %.0f\n".format(food.calories * scale))
                append("Protein: %.1f g\n".format(food.proteinG * scale))
                append("Carbs: %.1f g\n".format(food.carbsG * scale))
                append("Fat: %.1f g".format(food.fatG * scale))
            }
            textSize = 13f
            setTextColor(requireContext().getColor(R.color.color_text_secondary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }
        outerContainer.addView(macrosText)

        MaterialAlertDialogBuilder(requireContext())
            .setView(outerContainer)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add") { _, _ ->
                val amount = amountInput.text.toString().toFloatOrNull()
                if (amount != null && amount > 0f) {
                    ingredientDrafts.add(IngredientDraft(food, amount))
                    ingredientListAdapter.submitList(ingredientDrafts.toList())
                    updateTotals()
                }
            }
            .show()

        amountInput.requestFocus()
    }

    private fun updateTotals() {
        var cal = 0f; var p = 0f; var c = 0f; var f = 0f
        for (d in ingredientDrafts) {
            val scale = if (d.food.baseAmount > 0f) d.amount / d.food.baseAmount else 0f
            cal += d.food.calories * scale
            p   += d.food.proteinG * scale
            c   += d.food.carbsG   * scale
            f   += d.food.fatG     * scale
        }
        binding.tvTotals.text = "Cal %.0f · P %.1fg · C %.1fg · F %.1fg".format(cal, p, c, f)
    }

    private fun saveRecipe() {
        val name = binding.etRecipeName.text.toString().trim()
        if (name.isBlank()) {
            binding.tilRecipeName.error = "Name is required"
            return
        }
        binding.tilRecipeName.error = null

        val baseAmount = binding.etServingAmount.text.toString().toFloatOrNull()
        if (baseAmount == null || baseAmount <= 0f) {
            binding.tilServingAmount.error = "Enter a valid serving size"
            return
        }
        binding.tilServingAmount.error = null

        if (ingredientDrafts.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage("Add at least one ingredient to create a recipe.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val unit = binding.actvServingUnit.text.toString().trim().ifBlank { "serving" }
        viewLifecycleOwner.lifecycleScope.launch {
            recipeRepository.saveRecipe(name, baseAmount, unit, ingredientDrafts.toList())
            dismiss()
        }
    }

    private fun formatFloat(v: Float): String =
        if (v == v.toLong().toFloat()) "%.0f".format(v) else "%.2f".format(v).trimEnd('0')

    override fun onDestroyView() {
        super.onDestroyView()
        pickerDialog?.dismiss()
        pickerDialog = null
        _binding = null
    }
}

private class FoodPickerAdapter(
    private val onClick: (FoodItem) -> Unit
) : ListAdapter<FoodItem, FoodPickerAdapter.VH>(DIFF) {

    inner class VH(private val root: View) : RecyclerView.ViewHolder(root) {
        fun bind(item: FoodItem) {
            (root as TextView).text = buildString {
                append(item.name)
                if (!item.brand.isNullOrBlank()) append(" · ${item.brand}")
                append("  (%.4g %s)".format(item.baseAmount, item.measurementType)
                    .replace(Regex("(\\d)0+ "), "$1 "))
            }
            root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(48, 28, 48, 28)
            textSize = 14f
            setTextColor(parent.context.getColor(R.color.color_text_primary))
            val typedValue = android.util.TypedValue()
            parent.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
            setBackgroundResource(typedValue.resourceId)
        }
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FoodItem>() {
            override fun areItemsTheSame(a: FoodItem, b: FoodItem) = a.id == b.id
            override fun areContentsTheSame(a: FoodItem, b: FoodItem) = a == b
        }
    }
}

private class IngredientListAdapter(
    private val onRemove: (IngredientDraft) -> Unit
) : ListAdapter<IngredientDraft, IngredientListAdapter.VH>(DIFF) {

    inner class VH(private val binding: ItemRecipeIngredientBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(draft: IngredientDraft) {
            binding.tvIngredientName.text = draft.food.name
            val scale = if (draft.food.baseAmount > 0f) draft.amount / draft.food.baseAmount else 0f
            binding.tvIngredientMacros.text = "Cal %.0f · P %.1fg · C %.1fg · F %.1fg".format(
                draft.food.calories * scale,
                draft.food.proteinG * scale,
                draft.food.carbsG * scale,
                draft.food.fatG * scale
            )
            binding.tvIngredientAmount.text = "${formatFloat(draft.amount)} ${draft.food.measurementType}"
            binding.btnRemoveIngredient.setOnClickListener { onRemove(draft) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemRecipeIngredientBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<IngredientDraft>() {
            override fun areItemsTheSame(a: IngredientDraft, b: IngredientDraft) = a.food.id == b.food.id
            override fun areContentsTheSame(a: IngredientDraft, b: IngredientDraft) = a == b
        }

        private fun formatFloat(v: Float): String =
            if (v == v.toLong().toFloat()) "%.0f".format(v) else "%.2f".format(v).trimEnd('0')
    }
}
