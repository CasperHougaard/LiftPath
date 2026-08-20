package com.liftpath.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.liftpath.R
import com.liftpath.helpers.DialogHelper
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.liftpath.adapters.WithingsScanAdapter
import com.liftpath.databinding.FragmentProgressWithingsBinding
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.StrengthVsWeightHelper
import com.liftpath.helpers.WithingsStorageHelper
import com.liftpath.helpers.lpColor
import com.liftpath.models.WithingsScanEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProgressWithingsFragment : Fragment() {

    private var _binding: FragmentProgressWithingsBinding? = null
    private val binding get() = _binding!!

    private var editMode = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProgressWithingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonEditScans.setOnClickListener {
            editMode = !editMode
            binding.buttonEditScans.setText(
                if (editMode) R.string.withings_done_editing else R.string.withings_edit_scans
            )
            loadData()
        }
        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        val storage = WithingsStorageHelper(requireContext()).read()
        val entries = storage.entries  // sorted newest-first by sync helper

        setupSummaryCards(entries)
        setupStrengthVsWeightCard(entries)
        setupChart(binding.chartWeight, entries, "Weight (kg)") { it.weightKg }
        setupChart(binding.chartBodyFat, entries, "Body Fat (%)") { it.bodyFatPct }
        setupChart(binding.chartLeanMass, entries, "Lean Mass (kg)") { it.leanBodyMassKg }
        setupBodyCompositionChart(binding.chartBodyComposition, entries)
        setupRecyclerView(entries)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Summary cards
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupSummaryCards(entries: List<WithingsScanEntry>) {
        if (entries.isEmpty()) return

        val latest = entries.first()
        val previous = entries.getOrNull(1)

        // Weight
        latest.weightKg?.let { w ->
            binding.textLatestWeight.text = String.format(Locale.US, "%.1f kg", w)
            binding.textWeightDelta.text = previous?.weightKg?.let { p ->
                formatDelta(w - p, "kg")
            } ?: ""
        }

        // Body Fat
        latest.bodyFatPct?.let { f ->
            binding.textLatestBodyFat.text = String.format(Locale.US, "%.1f%%", f)
            binding.textBodyFatDelta.text = previous?.bodyFatPct?.let { p ->
                formatDelta(f - p, "%")
            } ?: ""
        }

        // BMR
        latest.bmrKcal?.let { b ->
            binding.textLatestBmr.text = String.format(Locale.US, "%.0f kcal", b)
            binding.textBmrDelta.text = previous?.bmrKcal?.let { p ->
                formatDelta(b - p, " kcal")
            } ?: ""
        }
    }

    private fun formatDelta(delta: Double, unit: String): String {
        if (delta == 0.0) return ""
        val sign = if (delta > 0) "+" else ""
        return "$sign${String.format(Locale.US, "%.1f", delta)}$unit"
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Strength vs weight indicator
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupStrengthVsWeightCard(scans: List<WithingsScanEntry>) {
        val trainings = JsonHelper(requireContext()).readTrainingData().trainings
        val result = StrengthVsWeightHelper.compute(trainings, scans)
        if (result == null) {
            binding.cardStrengthVsWeight.visibility = View.GONE
            return
        }
        binding.cardStrengthVsWeight.visibility = View.VISIBLE

        binding.textStrengthChange.apply {
            text = formatSignedPct(result.strengthChangePct)
            // Rising strength is the good outcome; falling strength is the warning.
            setTextColor(changeColor(result.strengthChangePct))
        }
        binding.textWeightChange.apply {
            text = formatSignedPct(result.weightChangePct)
            // Weight direction isn't inherently good or bad — the verdict interprets it.
            setTextColor(requireContext().lpColor(R.attr.lpNeutral))
        }
        binding.textStrengthWeightWindow.text =
            if (result.windowDays > 0) getString(R.string.withings_window_days, result.windowDays) else ""
        binding.textStrengthWeightVerdict.text = result.verdict
    }

    private fun formatSignedPct(pct: Float): String {
        val sign = if (pct > 0) "+" else ""
        return "$sign${String.format(Locale.US, "%.1f", pct)}%"
    }

    /** Positive when strength is up, negative when down, neutral when essentially flat (±1.5%). */
    private fun changeColor(pct: Float): Int = when {
        pct > 1.5f -> requireContext().lpColor(R.attr.lpPositive)
        pct < -1.5f -> requireContext().lpColor(R.attr.lpNegative)
        else -> requireContext().lpColor(R.attr.lpNeutral)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Charts
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupChart(
        chart: LineChart,
        entries: List<WithingsScanEntry>,
        label: String,
        selector: (WithingsScanEntry) -> Double?
    ) {
        // Oldest-first for chart X axis. Keep only scans that have a value for this metric,
        // each paired with its real timestamp.
        val valued = entries.reversed().mapNotNull { scan ->
            selector(scan)?.let { scan.dateMs to it }
        }

        if (valued.isEmpty()) {
            chart.visibility = View.GONE
            return
        }
        chart.visibility = View.VISIBLE

        // Position each point by how many days after the first scan it was taken, so a longer
        // gap between measurements shows as proportionally more horizontal space (a week off
        // reads as a week-wide gap, not the same step as consecutive days).
        val firstMs = valued.first().first
        val labelFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val chartEntries = valued.map { (dateMs, value) ->
            Entry(daysSince(firstMs, dateMs), value.toFloat())
        }

        val ctx = chart.context
        val dataSet = LineDataSet(chartEntries, label).apply {
            color = ctx.lpColor(R.attr.lpAccent)
            valueTextColor = ctx.lpColor(R.attr.lpInk)
            lineWidth = 2.5f
            circleRadius = 4f
            setCircleColor(ctx.lpColor(R.attr.lpAccent))
            setDrawCircleHole(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = ctx.lpColor(R.attr.lpAccent)
            fillAlpha = 30
        }

        chart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            setPinchZoom(false)
            setBackgroundColor(Color.TRANSPARENT)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f  // at most one label per day
                setDrawGridLines(false)
                textColor = ctx.lpColor(R.attr.lpInkSecondary)
                textSize = 10f
                valueFormatter = object : ValueFormatter() {
                    // value is days-since-firstMs; render it back as a calendar date.
                    override fun getFormattedValue(value: Float): String =
                        labelFormat.format(Date(dayOffsetToMs(firstMs, value)))
                }
                labelRotationAngle = -30f
            }

            axisLeft.apply {
                textColor = ctx.lpColor(R.attr.lpInkSecondary)
                setDrawGridLines(true)
                gridColor = ctx.lpColor(R.attr.lpChartGrid)
            }
            axisRight.isEnabled = false

            animateX(600)
            invalidate()
        }
    }

    private fun setupBodyCompositionChart(chart: BarChart, entries: List<WithingsScanEntry>) {
        val labelFormat = SimpleDateFormat("MMM d", Locale.getDefault())

        // Oldest-first, keeping only scans with the fields needed for a composition breakdown,
        // each paired with its real timestamp.
        val valued = entries.reversed().mapNotNull { scan ->
            val weight = scan.weightKg ?: return@mapNotNull null
            val fatPct = scan.bodyFatPct ?: return@mapNotNull null
            val fatKg = (weight * fatPct / 100.0).toFloat()
            val lean = scan.leanBodyMassKg?.toFloat() ?: (weight - fatKg).toFloat()
            val bone = (scan.boneMassKg ?: 0.0).toFloat()
            val water = (scan.bodyWaterMassKg ?: 0.0).toFloat()
            val muscle = maxOf(0f, lean - bone - water)
            // stack order bottom→top: muscle, water, bone, fat
            scan.dateMs to floatArrayOf(muscle, water, bone, fatKg)
        }

        if (valued.isEmpty()) {
            chart.visibility = View.GONE
            return
        }
        chart.visibility = View.VISIBLE

        // Same real-date X axis as the line charts: bars sit at their true day-offset so gaps
        // between scans are visible. Bar width is derived from the smallest gap between two
        // scans so bars never overlap, then clamped to stay legible over long, sparse ranges.
        val firstMs = valued.first().first
        val barEntries = valued.map { (dateMs, stack) ->
            BarEntry(daysSince(firstMs, dateMs), stack)
        }
        val minGapDays = barEntries.map { it.x }.zipWithNext { a, b -> b - a }
            .filter { it > 0f }.minOrNull() ?: 1f
        val barWidthDays = (minGapDays * 0.7f).coerceIn(0.5f, 10f)

        val ctx = chart.context
        val dataSet = BarDataSet(barEntries, "").apply {
            // Same four roles as the legend row in fragment_progress_withings.xml, in the
            // same stack order (muscle, water, bone, fat) so the swatches match the bars.
            setColors(
                ctx.lpColor(R.attr.lpChartVolume),  // muscle
                ctx.lpColor(R.attr.lpChartTime),    // water
                ctx.lpColor(R.attr.lpChartRpe),     // bone
                ctx.lpColor(R.attr.lpChartFatigue)  // fat
            )
            setDrawValues(false)
            stackLabels = arrayOf("Muscle", "Water", "Bone", "Fat")
        }

        chart.apply {
            data = BarData(dataSet).apply { barWidth = barWidthDays }
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            setPinchZoom(false)
            setBackgroundColor(Color.TRANSPARENT)
            setFitBars(true)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f  // at most one label per day
                setDrawGridLines(false)
                textColor = ctx.lpColor(R.attr.lpInkSecondary)
                textSize = 10f
                labelRotationAngle = -30f
                valueFormatter = object : ValueFormatter() {
                    // value is days-since-firstMs; render it back as a calendar date.
                    override fun getFormattedValue(value: Float): String =
                        labelFormat.format(Date(dayOffsetToMs(firstMs, value)))
                }
            }

            axisLeft.apply {
                textColor = ctx.lpColor(R.attr.lpInkSecondary)
                setDrawGridLines(true)
                gridColor = ctx.lpColor(R.attr.lpChartGrid)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) = "${value.toInt()} kg"
                }
            }
            axisRight.isEnabled = false

            animateY(600)
            invalidate()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Scan list
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupRecyclerView(entries: List<WithingsScanEntry>) {
        binding.recyclerWithingsScans.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = WithingsScanAdapter(entries, editMode) { confirmIgnoreScan(it) }
            isNestedScrollingEnabled = false
        }
    }

    private fun confirmIgnoreScan(entry: WithingsScanEntry) {
        val dateLabel = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(entry.dateMs))
        DialogHelper.createBuilder(requireContext())
            .setTitle(R.string.withings_delete_scan_title)
            .setMessage(getString(R.string.withings_delete_scan_message, dateLabel))
            .setPositiveButton(R.string.withings_delete_scan_confirm) { _, _ ->
                WithingsStorageHelper(requireContext()).ignoreScan(entry.dateMs)
                loadData()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val MS_PER_DAY = 86_400_000.0

        /** Days between [firstMs] and [dateMs], as a fractional chart X coordinate. */
        private fun daysSince(firstMs: Long, dateMs: Long): Float =
            ((dateMs - firstMs) / MS_PER_DAY).toFloat()

        /** Inverse of [daysSince]: turn a day-offset axis value back into epoch millis. */
        private fun dayOffsetToMs(firstMs: Long, dayOffset: Float): Long =
            firstMs + (dayOffset * MS_PER_DAY).toLong()
    }
}
