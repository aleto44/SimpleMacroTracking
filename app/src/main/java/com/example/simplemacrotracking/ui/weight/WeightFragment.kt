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
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import android.view.MotionEvent

@AndroidEntryPoint
class WeightFragment : Fragment() {

    private var _binding: FragmentWeightBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WeightViewModel by viewModels()
    private lateinit var entryAdapter: WeightEntryAdapter
    /** True while we are programmatically pushing data into the chart — suppresses gesture callbacks. */
    private var isUpdatingChart = false
    /** Data-space X value where the user's drag started. NaN when no drag is in progress. */
    private var dragStartDataX: Float = Float.NaN
    /** Pixel X where the drag started (for the selection overlay). NaN when idle. */
    private var dragStartPixelX: Float = Float.NaN

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

        // Cog wheel: toggle chart settings panel
        binding.btnChartSettings.setOnClickListener {
            val panel = binding.layoutChartSettings
            panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // MA days slider
        binding.sliderMaDays.addOnChangeListener(Slider.OnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val days = value.toInt()
                binding.tvMaDaysLabel.text = "$days days"
                viewModel.setMovingAverageDays(days)
            }
        })

        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val range = when (checkedIds.firstOrNull()) {
                R.id.chip_1w  -> TimeRange.W1
                R.id.chip_1m  -> TimeRange.M1
                R.id.chip_3m  -> TimeRange.M3
                R.id.chip_1y  -> TimeRange.Y1
                R.id.chip_3y  -> TimeRange.Y3
                R.id.chip_5y  -> TimeRange.Y5
                R.id.chip_all -> TimeRange.ALL
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
            setScaleEnabled(false)
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
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = borderColor
                textColor = textSecondary
                axisLineColor = borderColor
            }

            axisRight.apply {
                gridColor = borderColor
                axisLineColor = borderColor
            }

            // Capture drag start position in data-space for drag-to-zoom
            // and pixel position for the selection overlay
            var touchDownX = 0f
            var touchDownY = 0f
            var verticalScrollLocked = false
            setOnTouchListener { v, event ->
                if (!isUpdatingChart) {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            touchDownX = event.x
                            touchDownY = event.y
                            verticalScrollLocked = false
                            val pt = getTransformer(YAxis.AxisDependency.LEFT)
                                .getValuesByTouchPoint(event.x, event.y)
                            dragStartDataX = pt.x.toFloat()
                            dragStartPixelX = event.x
                            binding.chartSelectionOverlay.clearSelection()
                            // Allow parent to decide until we know the direction
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = Math.abs(event.x - touchDownX)
                            val dy = Math.abs(event.y - touchDownY)
                            if (!verticalScrollLocked) {
                                val minSlop = 12f  // px before we commit to a direction
                                if (dx > minSlop || dy > minSlop) {
                                    if (dy > dx * 2f) {
                                        // Clearly vertical — let the parent ScrollView scroll
                                        dragStartDataX = Float.NaN
                                        dragStartPixelX = Float.NaN
                                        binding.chartSelectionOverlay.clearSelection()
                                        v.parent?.requestDisallowInterceptTouchEvent(false)
                                        return@setOnTouchListener false
                                    } else {
                                        // Horizontal or ambiguous — lock scroll and handle as chart drag
                                        verticalScrollLocked = true
                                        v.parent?.requestDisallowInterceptTouchEvent(true)
                                    }
                                }
                            }
                            if (verticalScrollLocked && !dragStartPixelX.isNaN()) {
                                binding.chartSelectionOverlay.updateSelection(dragStartPixelX, event.x)
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            verticalScrollLocked = false
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                            binding.chartSelectionOverlay.clearSelection()
                        }
                    }
                }
                false // let the chart handle all touch events normally
            }

            // Drag-to-zoom: on drag end, filter data to the dragged range
            onChartGestureListener = object : OnChartGestureListener {
                override fun onChartGestureStart(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {}
                override fun onChartLongPressed(me: MotionEvent?) {}
                override fun onChartDoubleTapped(me: MotionEvent?) {}
                override fun onChartSingleTapped(me: MotionEvent?) {}
                override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {}
                override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {}
                override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {}

                override fun onChartGestureEnd(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {
                    if (isUpdatingChart) return
                    if (lastPerformedGesture == ChartTouchListener.ChartGesture.DRAG && me != null) {
                        val endPt = binding.lineChart.getTransformer(YAxis.AxisDependency.LEFT)
                            .getValuesByTouchPoint(me.x, me.y)
                        val endDataX = endPt.x.toFloat()
                        val startDataX = dragStartDataX
                        dragStartDataX = Float.NaN
                        dragStartPixelX = Float.NaN
                        binding.chartSelectionOverlay.clearSelection()
                        // Only zoom if the drag covered more than 1 day
                        if (!startDataX.isNaN() && Math.abs(endDataX - startDataX) > 1f) {
                            try {
                                val start = LocalDate.ofEpochDay(minOf(startDataX, endDataX).toLong())
                                val end   = LocalDate.ofEpochDay(maxOf(startDataX, endDataX).toLong())
                                viewModel.setCustomDateRange(start, end)
                            } catch (_: Exception) {}
                        }
                    } else {
                        dragStartDataX = Float.NaN
                        dragStartPixelX = Float.NaN
                        binding.chartSelectionOverlay.clearSelection()
                    }
                }
            }
        }
    }

    private fun render(state: WeightUiState) {
        updateChart(state)
        updateStats(state.filteredEntries, state.allEntries)
        entryAdapter.submitList(state.allEntries.sortedByDescending { it.date })
    }

    private fun movingAverage(entries: List<Pair<Float, Float>>, window: Int): List<Entry> {
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

        val today = LocalDate.now()

        // Compute the calorie cutoff from the active range, NOT from weight entries.
        // This way calories are always filtered to the same window even when weight data is sparse.
        val calCutoff: LocalDate? = state.customRange?.first ?: when (state.timeRange) {
            TimeRange.W1  -> today.minusWeeks(1)
            TimeRange.M1  -> today.minusMonths(1)
            TimeRange.M3  -> today.minusMonths(3)
            TimeRange.Y1  -> today.minusYears(1)
            TimeRange.Y3  -> today.minusYears(3)
            TimeRange.Y5  -> today.minusYears(5)
            TimeRange.ALL -> null
        }
        val calEnd: LocalDate? = state.customRange?.second

        val calEntries: List<Pair<Float, Float>> = state.dailyCalories.entries
            .filter { (date, _) ->
                date != today &&
                (calCutoff == null || !date.isBefore(calCutoff)) &&
                (calEnd   == null || !date.isAfter(calEnd))
            }
            .sortedBy { it.key }
            .map { it.key.toEpochDay().toFloat() to it.value }

        val weightPairs: List<Pair<Float, Float>> = state.filteredEntries
            .map { it.date.toEpochDay().toFloat() to it.value }

        val dataSets = mutableListOf<LineDataSet>()

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
                val maEntries = movingAverage(weightPairs, state.movingAverageDays)
                dataSets += LineDataSet(maEntries, "Weight MA").apply {
                    color = weightMaColor
                    setCircleColor(weightMaColor)
                    lineWidth = 2f
                    circleRadius = 2f
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    axisDependency = YAxis.AxisDependency.LEFT
                    setDrawHighlightIndicators(false)
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
                    setDrawHighlightIndicators(false)
                }
            }
        }

        if (state.showCalories && calEntries.isNotEmpty()) {
            val axisDep = if (bothShown) YAxis.AxisDependency.RIGHT else YAxis.AxisDependency.LEFT
            if (state.showMovingAverage) {
                val maEntries = movingAverage(calEntries, state.movingAverageDays)
                dataSets += LineDataSet(maEntries, "Calories MA").apply {
                    color = calMaColor
                    setCircleColor(calMaColor)
                    lineWidth = 2f
                    circleRadius = 2f
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    axisDependency = axisDep
                    setDrawHighlightIndicators(false)
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
                    setDrawHighlightIndicators(false)
                }
            }
        }

        if (dataSets.isEmpty()) {
            binding.lineChart.clear()
            binding.lineChart.invalidate()
            return
        }

        binding.lineChart.data = LineData(dataSets.toList())

        // Dynamic x-axis label format based on visible span
        val allXValues = (weightPairs.map { it.first } + calEntries.map { it.first })
        val spanDays = if (allXValues.size >= 2) allXValues.max() - allXValues.min() else 0f
        val useYearOnly = spanDays > 548f  // > ~1.5 years
        binding.lineChart.xAxis.valueFormatter = object : ValueFormatter() {
            private val fmtShort = DateTimeFormatter.ofPattern("MMM d")
            private val fmtYear  = DateTimeFormatter.ofPattern("yyyy")
            override fun getFormattedValue(value: Float): String = try {
                val date = LocalDate.ofEpochDay(value.toLong())
                if (useYearOnly) date.format(fmtYear) else date.format(fmtShort)
            } catch (e: Exception) { "" }
        }

        isUpdatingChart = true
        binding.lineChart.fitScreen()
        binding.lineChart.invalidate()
        isUpdatingChart = false
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
