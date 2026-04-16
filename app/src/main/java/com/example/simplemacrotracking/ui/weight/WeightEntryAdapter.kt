package com.example.simplemacrotracking.ui.weight

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.simplemacrotracking.data.model.WeightEntry
import com.example.simplemacrotracking.databinding.ItemWeightEntryBinding
import java.time.format.DateTimeFormatter

class WeightEntryAdapter(
    private val onEdit: (WeightEntry) -> Unit,
    private val onDelete: (WeightEntry) -> Unit
) : ListAdapter<WeightEntry, WeightEntryAdapter.ViewHolder>(DIFF) {

    private val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy")

    inner class ViewHolder(private val binding: ItemWeightEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: WeightEntry) {
            binding.tvEntryDate.text = entry.date.format(fmt)
            binding.tvEntryValue.text = "%.1f %s".format(entry.value, entry.unit.name.lowercase())
            binding.btnEditEntry.setOnClickListener { onEdit(entry) }
            binding.btnDeleteEntry.setOnClickListener { onDelete(entry) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWeightEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<WeightEntry>() {
            override fun areItemsTheSame(a: WeightEntry, b: WeightEntry) = a.id == b.id
            override fun areContentsTheSame(a: WeightEntry, b: WeightEntry) = a == b
        }
    }
}

