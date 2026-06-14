package com.example.simplemacrotracking.ui.weight

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.simplemacrotracking.data.model.WeightEntry
import com.example.simplemacrotracking.databinding.ItemWeightEntryBinding
import java.time.format.DateTimeFormatter

class WeightEntryAdapter(
    private val onEdit: (WeightEntry) -> Unit,
    private val onDelete: (WeightEntry) -> Unit,
    private val onLoadMore: (() -> Unit)? = null
) : RecyclerView.Adapter<WeightEntryAdapter.ViewHolder>() {

    private val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
    private val displayedEntries = mutableListOf<WeightEntry>()
    private var allEntries = listOf<WeightEntry>()
    private var loadedCount = 0
    private var isLoadingMore = false
    private val batchSize = 50

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
        holder.bind(displayedEntries[position])
        if (position >= displayedEntries.size - 10 && !isLoadingMore && loadedCount < allEntries.size) {
            holder.itemView.post { loadMore() }
        }
    }

    override fun getItemCount() = displayedEntries.size

    fun submitList(entries: List<WeightEntry>) {
        allEntries = entries
        displayedEntries.clear()
        loadedCount = 0
        isLoadingMore = false
        loadMore()
    }

    fun updateItem(entry: WeightEntry) {
        val index = displayedEntries.indexOfFirst { it.id == entry.id }
        if (index >= 0) {
            displayedEntries[index] = entry
            notifyItemChanged(index)
        }
    }

    fun removeItem(entry: WeightEntry) {
        val index = displayedEntries.indexOfFirst { it.id == entry.id }
        if (index >= 0) {
            displayedEntries.removeAt(index)
            allEntries = allEntries.filterNot { it.id == entry.id }
            loadedCount = (loadedCount - 1).coerceAtLeast(0)
            notifyItemRemoved(index)
        }
    }

    private fun loadMore() {
        if (isLoadingMore || loadedCount >= allEntries.size) return
        isLoadingMore = true
        val endIndex = minOf(loadedCount + batchSize, allEntries.size)
        val newEntries = allEntries.subList(loadedCount, endIndex)
        val oldSize = displayedEntries.size
        displayedEntries.addAll(newEntries)
        loadedCount = endIndex
        isLoadingMore = false
        notifyItemRangeInserted(oldSize, newEntries.size)
        onLoadMore?.invoke()
    }
}

