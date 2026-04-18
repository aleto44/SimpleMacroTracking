package com.example.simplemacrotracking.ui.settings

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.simplemacrotracking.R
import com.example.simplemacrotracking.data.model.AiModel
import com.example.simplemacrotracking.data.model.AiModels
import com.example.simplemacrotracking.data.model.AiProviderConfig
import com.example.simplemacrotracking.data.model.AiProviderType
import com.example.simplemacrotracking.databinding.ItemAiProviderBinding
import com.example.simplemacrotracking.databinding.SpinnerItemModelBinding
import java.util.Collections

// ── Custom Spinner Adapter ────────────────────────────────────────────────────

private class ModelSpinnerAdapter(
    context: Context,
    private val models: List<AiModel>
) : ArrayAdapter<AiModel>(context, 0, models) {

    private val green  get() = ContextCompat.getColor(context, R.color.color_accent_green)
    private val orange = Color.parseColor("#FFA040")
    private val white  get() = ContextCompat.getColor(context, R.color.color_text_primary)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        bind(convertView, parent, models[position], isDropdown = false)

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
        bind(convertView, parent, models[position], isDropdown = true)

    private fun bind(convertView: View?, parent: ViewGroup, model: AiModel, isDropdown: Boolean): View {
        val b = if (convertView != null)
            SpinnerItemModelBinding.bind(convertView)
        else
            SpinnerItemModelBinding.inflate(LayoutInflater.from(context), parent, false)

        b.tvModelName.text  = model.id
        b.tvModelName.setTextColor(white)
        b.tvCostBadge.text  = model.costNote
        b.tvCostBadge.setTextColor(if (model.isFree) green else orange)

        // Slightly different background for dropdown rows vs selected item
        if (isDropdown) {
            b.root.setBackgroundColor(ContextCompat.getColor(context, R.color.color_bg_elevated))
        } else {
            b.root.setBackgroundColor(Color.TRANSPARENT)
        }
        return b.root
    }
}

// ── Main adapter ─────────────────────────────────────────────────────────────

class AiProviderAdapter(
    private val onProvidersChanged: (List<AiProviderConfig>) -> Unit,
    private val onTestProvider: (position: Int) -> Unit
) : RecyclerView.Adapter<AiProviderAdapter.ViewHolder>() {

    var touchHelper: ItemTouchHelper? = null
    private val items = mutableListOf<AiProviderConfig>()

    /** ID of the provider currently being tested — drives the per-item spinner. */
    var testingProviderId: String? = null
        set(value) {
            val oldId = field
            field = value
            // Refresh affected rows only
            if (oldId != null) notifyItemChanged(indexOfId(oldId))
            if (value != null) notifyItemChanged(indexOfId(value))
        }

    private fun indexOfId(id: String) = items.indexOfFirst { it.id == id }

    fun submitList(list: List<AiProviderConfig>) {
        val oldSize = items.size
        val newSize = list.size
        items.clear()
        items.addAll(list)
        when {
            oldSize == 0 && newSize == 0 -> { /* nothing to do */ }
            oldSize == 0 -> notifyItemRangeInserted(0, newSize)
            newSize == 0 -> notifyItemRangeRemoved(0, oldSize)
            else -> notifyDataSetChanged()
        }
    }

    fun getItems(): List<AiProviderConfig> = items.toList()

    fun onItemMove(from: Int, to: Int) {
        Collections.swap(items, from, to)
        notifyItemMoved(from, to)
        onProvidersChanged(items.toList())
    }

    fun removeAt(position: Int) {
        items.removeAt(position)
        notifyItemRemoved(position)
        onProvidersChanged(items.toList())
    }

    fun updateItem(position: Int, updated: AiProviderConfig) {
        items[position] = updated
        notifyItemChanged(position)
        onProvidersChanged(items.toList())
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAiProviderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(private val b: ItemAiProviderBinding) : RecyclerView.ViewHolder(b.root) {

        // Prevent spinner selection callback from firing during bind
        private var spinnerReady = false

        fun bind(config: AiProviderConfig) {
            spinnerReady = false

            // Provider name
            b.tvProviderName.text = when (config.type) {
                AiProviderType.GEMINI        -> "Gemini (Google AI)"
                AiProviderType.GITHUB_COPILOT -> "GitHub Copilot"
            }

            // API key hint
            b.tilApiKey.hint = when (config.type) {
                AiProviderType.GEMINI        -> "Gemini API Key"
                AiProviderType.GITHUB_COPILOT -> "GitHub Personal Access Token"
            }

            // API key value (only update if changed to avoid cursor jumping)
            if (b.etApiKey.text.toString() != config.apiKey) {
                b.etApiKey.setText(config.apiKey)
            }
            b.etApiKey.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val pos = adapterPosition
                    if (pos != RecyclerView.NO_ID.toInt()) {
                        updateItem(pos, items[pos].copy(apiKey = b.etApiKey.text.toString().trim()))
                    }
                }
            }

            // Model spinner with custom adapter
            val modelList = AiModels.listFor(config.type)
            val spinnerAdapter = ModelSpinnerAdapter(b.root.context, modelList)
            b.spinnerModel.adapter = spinnerAdapter

            val selectedModel = config.model.ifBlank { AiModels.defaultFor(config.type) }
            val selectedIndex = modelList.indexOfFirst { it.id == selectedModel }.coerceAtLeast(0)
            b.spinnerModel.setSelection(selectedIndex, false)

            b.spinnerModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                    if (!spinnerReady) return
                    val pos = adapterPosition
                    if (pos != RecyclerView.NO_ID.toInt()) {
                        updateItem(pos, items[pos].copy(model = modelList[position].id))
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            // Mark ready after a post so the initial setSelection doesn't trigger the callback
            b.spinnerModel.post { spinnerReady = true }

            // Enabled switch
            b.swEnabled.isChecked = config.enabled
            b.swEnabled.setOnCheckedChangeListener { _, checked ->
                val pos = adapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) updateItem(pos, items[pos].copy(enabled = checked))
            }

            // Remove button
            b.btnRemove.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) removeAt(pos)
            }

            // Drag handle
            b.ivDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) touchHelper?.startDrag(this)
                false
            }

            // Test button + loading state
            val isTesting = testingProviderId == config.id
            b.btnTest.isEnabled = !isTesting
            b.progressTesting.visibility = if (isTesting) View.VISIBLE else View.GONE
            b.btnTest.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) {
                    // Flush key field before testing
                    val key = b.etApiKey.text.toString().trim()
                    if (items[pos].apiKey != key) updateItem(pos, items[pos].copy(apiKey = key))
                    onTestProvider(pos)
                }
            }
        }
    }
}

