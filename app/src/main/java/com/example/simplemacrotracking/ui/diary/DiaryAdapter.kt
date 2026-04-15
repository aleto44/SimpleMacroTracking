package com.example.simplemacrotracking.ui.diary

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.simplemacrotracking.data.model.DiaryEntryWithFood
import com.example.simplemacrotracking.databinding.ItemDiaryEntryBinding

class DiaryAdapter(
    private val onItemClick: (DiaryEntryWithFood) -> Unit,
    private val onItemLongClick: (DiaryEntryWithFood) -> Unit
) : ListAdapter<DiaryEntryWithFood, DiaryAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemDiaryEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DiaryEntryWithFood) {
            val scale = item.entry.actualAmount / item.food.baseAmount
            val calories = item.food.calories * scale
            val protein = item.food.proteinG * scale
            val carbs = item.food.carbsG * scale
            val fat = item.food.fatG * scale

            binding.tvFoodName.text = item.food.name
            binding.tvFoodDetails.text = buildString {
                append("%.0f %s".format(item.entry.actualAmount, item.entry.measurementType))
                append(" · Cal %.0f".format(calories))
                append(" · P %.0fg".format(protein))
                if (fat > carbs) append(" · F %.0fg".format(fat))
                else append(" · C %.0fg".format(carbs))
            }

            binding.root.setOnClickListener { onItemClick(item) }
            binding.root.setOnLongClickListener { onItemLongClick(item); true }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDiaryEntryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DiaryEntryWithFood>() {
            override fun areItemsTheSame(a: DiaryEntryWithFood, b: DiaryEntryWithFood) =
                a.entry.id == b.entry.id

            override fun areContentsTheSame(a: DiaryEntryWithFood, b: DiaryEntryWithFood) =
                a == b
        }
    }
}

