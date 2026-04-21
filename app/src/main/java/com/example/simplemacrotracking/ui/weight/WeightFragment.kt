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
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
            AddWeightDialogFragment().show(parentFragmentManager, "add_weight")
        }

        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val range = when (checkedIds.firstOrNull()) {
                R.id.chip_1w  -> TimeRange.W1
                R.id.chip_1m  -> TimeRange.M1
                R.id.chip_3m  -> TimeRange.M3
                R.id.chip_1y  -> TimeRange.Y1
                R.id.chip_3y  -> TimeRange.Y3
                R.id.chip_5y  -> TimeRange.Y5
                else          -> TimeRange.M3
            }
            viewModel.setTimeRange(range)
        }

        binding.chipShowWeight.setOnCheckedChangeListener { _, checked ->
            viewModel.setShowWeight(checked)
        }
        binding.chipShowCalories.setOnCheckedChangeListener { _, checked ->
            viewModel.setShowCalories(checked)
        }
        binding.chipShowMovingAvg.setOnCheckedChangeListener { _, checked ->
            viewModel.setShowMovingAverage(checked)
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
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Entry")
                    .setMessage("Delete the weight entry for ${entry.date}?")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deleteWeightEntry(entry) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.rvWeightEntries.adapter = entryAdapter
    }

    private fun setupChart() {
        val textSecondary = resources.getColor(R.color.color_text_secondary, null)
        val borderColor   = resources.getColor(R.color.color_border, null)

        binding.lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(false)
            setDrawGridBackground(false)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

            legend.apply {
                isEnabled = true
                textColor = textSecondary
                form = Legend.LegendForm.LINE
                horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
            }

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

            // Right axis will be configured per-render
            axisRight.apply {
                gridColor = borderColor
                axisLineColor = borderColor
            }
        }
    }

    private fun render(state: WeightUiState) {
        updateChart(state)
        updateStats(state.filteredEntries, state.allEntries)
        entryAdapter.submitList(state.allEntries.sortedByDescending { it.date })
    }

    private fun movingAverage(entries: List<Pair<Float, Float>>, window: Int = 7): List<Entry> {
        return entries.mapIndexed { i, (x, _) ->
            val slice = entries.subList(maxOf(0, i - window + 1), i + 1)
            Entry(x, slice.map { it.second }.average().toFloat())
        }
    }

    private fun updateChart(state: WeightUiState) {
        val weightGreen    = resources.getColor(R.color.color_accent_green, null)
        val weightGreenDim = resources.getColor(R.color.color_accent_green_dim, null)
        val weightMaColor  = resources.getColor(R.color.color_accent_green_ma, null)
        val calOrange      = resources.getColor(R.color.color_accent_orange, null)
        val calOrangeDim   = resources.getColor(R.color.color_accent_orange_dim, null)
        val calMaColor     = resources.getColor(R.color.color_accent_orange_ma, null)
        val textSecondary  = resources.getColor(R.color.color_text_secondary, null)

        // Determine date range from filtered weight entries + matching calorie dates
        val cutoff = state.filteredEntries.minOfOrNull { it.date }
        val calEntries: List<Pair<Float, Float>> = if (cutoff != null) {
            state.dailyCalories.entries
                .filter { !it.key.isBefore(cutoff) }
                .sortedBy { it.key }
                .map { it.key.toEpochDay().toFloat() to it.value }
        } else {
            state.dailyCalories.entries
                .sortedBy { it.key }
                .map { it.key.toEpochDay().toFloat() to it.value }
        }

        val weightPairs: List<Pair<Float, Float>> = state.filteredEntries
            .map { it.date.toEpochDay().toFloat() to it.value }

        val dataSets = mutableListOf<LineDataSet>()

        // Both weight & calories shown → enable right axis for calories
        val bothShown = state.showWeight && state.showCalories
        binding.lineChart.axisRight.apply {
            isEnabled = bothShown
            if (bothShown) {
                textColor = calOrange
                axisLineColor = calOrange
            }
        }
        binding.lineChart.axisLeft.apply {
            textColor = if (state.showWeight) weightGreen else textSecondary
        }

        if (state.showWeight) {
            if (state.showMovingAverage) {
                // Show MA line instead of raw
                val maEntries = movingAverage(weightPairs)
                dataSets += LineDataSet(maEntries, "Weight MA").apply {
                    color = weightMaColor
                    setCircleColor(weightMaColor)
                    lineWidth = 2f
                    circleRadius = 2f
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    axisDependency = YAxis.AxisDependency.LEFT
                }
            } else {
                val entries = weightPairs.map { Entry(it.first, it.second) }
                dataSets += LineDataSet(entries, "Weight").apply {
                    color = weightGreen
                    setCircleColor(weightGreen)
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    setDrawFilled(true)
                    fillColor = weightGreenDim
                    axisDependency = YAxis.AxisDependency.LEFT
                }
            }
        }

        if (state.showCalories && calEntries.isNotEmpty()) {
            val axisDep = if (bothShown) YAxis.AxisDependency.RIGHT else YAxis.AxisDependency.LEFT
            if (state.showMovingAverage) {
                val maEntries = movingAverage(calEntries)
                dataSets += LineDataSet(maEntries, "Calories MA").apply {
                    color = calMaColor
                    setCircleColor(calMaColor)
                    lineWidth = 2f
                    circleRadius = 2f
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    axisDependency = axisDep
                }
            } else {
                val entries = calEntries.map { Entry(it.first, it.second) }
                dataSets += LineDataSet(entries, "Calories").apply {
                    color = calOrange
                    setCircleColor(calOrange)
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    setDrawFilled(true)
                    fillColor = calOrangeDim
                    axisDependency = axisDep
                }
            }
        }

        if (dataSets.isEmpty()) {
            binding.lineChart.clear()
            binding.lineChart.invalidate()
            return
        }

        binding.lineChart.data = LineData(dataSets.toList())
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
