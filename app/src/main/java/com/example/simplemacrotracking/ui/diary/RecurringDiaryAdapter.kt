package com.example.simplemacrotracking.ui.diary

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.simplemacrotracking.data.model.RecurringEntryDisplay
import com.example.simplemacrotracking.databinding.ItemRecurringEntryBinding

class RecurringDiaryAdapter(
    private val onItemLongClick: (RecurringEntryDisplay) -> Unit
) : ListAdapter<RecurringEntryDisplay, RecurringDiaryAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemRecurringEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RecurringEntryDisplay) {
            val scale = item.displayAmount / item.food.baseAmount
            val calories = item.food.calories * scale
            val protein = item.food.proteinG * scale
            val carbs = item.food.carbsG * scale
            val fat = item.food.fatG * scale
            val fiber = item.food.fiberG * scale

            binding.tvFoodName.text = item.food.name
            binding.tvEntryCalories.text = "%.0f kcal".format(calories)
            binding.tvFoodDetails.text = buildString {
                val amt = item.displayAmount
                val amtStr = if (amt == kotlin.math.floor(amt.toDouble()).toFloat()) "%.0f".format(amt) else "%.1f".format(amt)
                append("$amtStr ${item.food.measurementType}")
                append(" · P %.0fg".format(protein))
                append(" · C %.0fg".format(carbs))
                append(" · F %.0fg".format(fat))
                if (fiber > 0f) append(" · Fi %.0fg".format(fiber))
            }

            binding.root.setOnLongClickListener { onItemLongClick(item); true }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecurringEntryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RecurringEntryDisplay>() {
            override fun areItemsTheSame(a: RecurringEntryDisplay, b: RecurringEntryDisplay) =
                a.recurringEntry.id == b.recurringEntry.id
            override fun areContentsTheSame(a: RecurringEntryDisplay, b: RecurringEntryDisplay) =
                a == b
        }
    }
}
