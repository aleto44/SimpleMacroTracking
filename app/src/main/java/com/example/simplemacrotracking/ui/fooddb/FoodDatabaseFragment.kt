package com.example.simplemacrotracking.ui.fooddb

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.simplemacrotracking.MainActivity
import com.example.simplemacrotracking.R
import com.example.simplemacrotracking.data.model.FoodItem
import com.example.simplemacrotracking.databinding.FragmentFoodDatabaseBinding
import com.example.simplemacrotracking.databinding.ItemFoodBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FoodDatabaseFragment : Fragment() {

    private var _binding: FragmentFoodDatabaseBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FoodDatabaseViewModel by viewModels()

    private val pickerMode: Boolean by lazy { arguments?.getBoolean("pickerMode", false) ?: false }
    private val targetDate: String? by lazy { arguments?.getString("targetDate") }

    private lateinit var adapter: FoodAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFoodDatabaseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Update toolbar title in picker mode
        if (pickerMode) {
            (requireActivity() as? androidx.appcompat.app.AppCompatActivity)
                ?.supportActionBar?.title = "Add Food to Diary"
        }

        adapter = FoodAdapter(
            onItemClick = { food ->
                findNavController().navigate(
                    R.id.action_foodDatabase_to_itemActionSheet,
                    bundleOf(
                        "foodItemId" to food.id,
                        "targetDate" to (targetDate ?: java.time.LocalDate.now().toString())
                    )
                )
            },
            onItemLongClick = { food ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete food item?")
                    .setMessage("\"${food.name}\" will be removed from your food database. Diary entries using it will also be deleted.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteFoodItem(food)
                    }
                    .show()
            }
        )
        binding.recyclerView.adapter = adapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = viewModel.setQuery(s?.toString() ?: "")
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })

        (activity as MainActivity).getFab().setOnClickListener {
            findNavController().navigate(
                R.id.action_foodDatabase_to_addEntryBottomSheet,
                bundleOf("targetDate" to (targetDate ?: java.time.LocalDate.now().toString()))
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.items)
                    val isEmpty = state.items.isEmpty()
                    binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
                    binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                    binding.tvEmptyHint.text = if (state.query.isBlank())
                        "Tap \u002B to add your first food"
                    else
                        "No results for \"${state.query}\""
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class FoodAdapter(
    private val onItemClick: (FoodItem) -> Unit,
    private val onItemLongClick: (FoodItem) -> Unit
) : ListAdapter<FoodItem, FoodAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemFoodBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FoodItem) {
            binding.tvFoodName.text = item.name
            binding.tvFoodSource.text = item.source.name
            binding.tvFoodSubtitle.text = buildString {
                if (!item.brand.isNullOrBlank()) append("${item.brand} · ")
                append("per %.0f %s".format(item.baseAmount, item.measurementType))
            }
            binding.tvFoodMacros.text =
                "Cal %.0f · P %.0fg · C %.0fg · F %.0fg".format(
                    item.calories, item.proteinG, item.carbsG, item.fatG
                )
            binding.root.setOnClickListener { onItemClick(item) }
            binding.root.setOnLongClickListener { onItemLongClick(item); true }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFoodBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FoodItem>() {
            override fun areItemsTheSame(a: FoodItem, b: FoodItem) = a.id == b.id
            override fun areContentsTheSame(a: FoodItem, b: FoodItem) = a == b
        }
    }
}

