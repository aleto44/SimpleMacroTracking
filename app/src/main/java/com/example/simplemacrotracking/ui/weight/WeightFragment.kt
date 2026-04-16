package com.example.simplemacrotracking.ui.weight

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.simplemacrotracking.R
import com.example.simplemacrotracking.data.model.WeightEntry
import com.example.simplemacrotracking.databinding.FragmentWeightBinding
import com.example.simplemacrotracking.ui.shared.AddWeightDialogFragment
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class WeightFragment : Fragment() {

    private var _binding: FragmentWeightBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WeightViewModel by viewModels()
    private lateinit var entryAdapter: WeightEntryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWeightBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupChart()
        setupEntriesList()

        binding.btnAddWeight.setOnClickListener {
            AddWeightDialogFragment()
                .show(parentFragmentManager, "add_weight")
        }

        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val range = when (checkedIds.firstOrNull()) {
                R.id.chip_1w  -> TimeRange.W1
                R.id.chip_1m  -> TimeRange.M1
                R.id.chip_3m  -> TimeRange.M3
                R.id.chip_1y  -> TimeRange.Y1
                R.id.chip_all -> TimeRange.ALL
                else          -> TimeRange.M3
            }
            viewModel.setTimeRange(range)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun setupEntriesList() {
        entryAdapter = WeightEntryAdapter(
            onEdit = { entry ->
                AddWeightDialogFragment.newEditInstance(entry)
                    .show(parentFragmentManager, "edit_weight")
            },
            onDelete = { entry ->
                viewModel.deleteWeightEntry(entry)
            }
        )
        binding.rvWeightEntries.adapter = entryAdapter
    }

    private fun setupChart() {
        val textSecondary = resources.getColor(R.color.color_text_secondary, null)
        val borderColor  = resources.getColor(R.color.color_border, null)

        binding.lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(false)
            setDrawGridBackground(false)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            legend.isEnabled = false

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = textSecondary
                axisLineColor = borderColor
                valueFormatter = object : ValueFormatter() {
                    private val fmt = DateTimeFormatter.ofPattern("MMM d")
                    override fun getFormattedValue(value: Float): String = try {
                        LocalDate.ofEpochDay(value.toLong()).format(fmt)
                    } catch (e: Exception) { "" }
                }
            }
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = borderColor
                textColor = textSecondary
                axisLineColor = borderColor
            }
            axisRight.isEnabled = false
        }
    }

    private fun render(state: WeightUiState) {
        updateChart(state.filteredEntries)
        updateStats(state.filteredEntries, state.allEntries)
        // Show all entries newest-first in the list
        entryAdapter.submitList(state.allEntries.sortedByDescending { it.date })
    }

    private fun updateChart(entries: List<WeightEntry>) {
        if (entries.isEmpty()) {
            binding.lineChart.clear()
            binding.lineChart.invalidate()
            return
        }
        val chartEntries = entries.map { Entry(it.date.toEpochDay().toFloat(), it.value) }
        val accentGreen = resources.getColor(R.color.color_accent_green, null)
        val dataSet = LineDataSet(chartEntries, "Weight").apply {
            color = accentGreen
            setCircleColor(accentGreen)
            lineWidth = 2f
            circleRadius = 3f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = resources.getColor(R.color.color_accent_green_dim, null)
        }
        binding.lineChart.data = LineData(dataSet)
        binding.lineChart.invalidate()
    }

    private fun updateStats(filtered: List<WeightEntry>, all: List<WeightEntry>) {
        val latest = all.lastOrNull()
        if (latest != null) {
            binding.tvLatestWeight.text =
                "Latest:  %.1f %s  (%s)".format(latest.value, latest.unit.name, latest.date)
            val first = filtered.firstOrNull()
            if (first != null && first.id != latest.id) {
                val diff = latest.value - first.value
                val arrow = if (diff < 0) "↓" else "↑"
                binding.tvWeightChange.text =
                    "Change:  $arrow %.1f %s in range".format(Math.abs(diff), latest.unit.name)
            } else {
                binding.tvWeightChange.text = "Change:  —"
            }
        } else {
            binding.tvLatestWeight.text = "Latest:  No entries yet"
            binding.tvWeightChange.text = "Change:  —"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
