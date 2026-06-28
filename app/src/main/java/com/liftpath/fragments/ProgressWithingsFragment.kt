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
import com.liftpath.helpers.WithingsStorageHelper
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
    //  Charts
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupChart(
        chart: LineChart,
        entries: List<WithingsScanEntry>,
        label: String,
        selector: (WithingsScanEntry) -> Double?
    ) {
        // Oldest-first for chart X axis
        val chronological = entries.reversed()
        val chartEntries = mutableListOf<Entry>()
        val xLabels = mutableListOf<String>()
        val labelFormat = SimpleDateFormat("MMM d", Locale.getDefault())

        chronological.forEachIndexed { index, scan ->
            val value = selector(scan) ?: return@forEachIndexed
            chartEntries.add(Entry(index.toFloat(), value.toFloat()))
            xLabels.add(labelFormat.format(Date(scan.dateMs)))
        }

        if (chartEntries.isEmpty()) {
            chart.visibility = View.GONE
            return
        }
        chart.visibility = View.VISIBLE

        val dataSet = LineDataSet(chartEntries, label).apply {
            color = Color.parseColor("#2563EB")
            valueTextColor = Color.parseColor("#111827")
            lineWidth = 2.5f
            circleRadius = 4f
            setCircleColor(Color.parseColor("#2563EB"))
            setDrawCircleHole(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = Color.parseColor("#2563EB")
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
                granularity = 1f
                setDrawGridLines(false)
                textColor = Color.parseColor("#6B7280")
                textSize = 10f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String =
                        xLabels.getOrElse(value.toInt()) { "" }
                }
                labelRotationAngle = -30f
            }

            axisLeft.apply {
                textColor = Color.parseColor("#6B7280")
                setDrawGridLines(true)
                gridColor = Color.parseColor("#F3F4F6")
            }
            axisRight.isEnabled = false

            animateX(600)
            invalidate()
        }
    }

    private fun setupBodyCompositionChart(chart: BarChart, entries: List<WithingsScanEntry>) {
        val chronological = entries.reversed()
        val barEntries = mutableListOf<BarEntry>()
        val xLabels = mutableListOf<String>()
        val labelFormat = SimpleDateFormat("MMM d", Locale.getDefault())

        chronological.forEachIndexed { index, scan ->
            val weight = scan.weightKg ?: return@forEachIndexed
            val fatPct = scan.bodyFatPct ?: return@forEachIndexed
            val fatKg = (weight * fatPct / 100.0).toFloat()
            val lean = scan.leanBodyMassKg?.toFloat() ?: (weight - fatKg).toFloat()
            val bone = (scan.boneMassKg ?: 0.0).toFloat()
            val water = (scan.bodyWaterMassKg ?: 0.0).toFloat()
            val muscle = maxOf(0f, lean - bone - water)
            // stack order bottom→top: muscle, water, bone, fat
            barEntries.add(BarEntry(index.toFloat(), floatArrayOf(muscle, water, bone, fatKg)))
            xLabels.add(labelFormat.format(Date(scan.dateMs)))
        }

        if (barEntries.isEmpty()) {
            chart.visibility = View.GONE
            return
        }
        chart.visibility = View.VISIBLE

        val dataSet = BarDataSet(barEntries, "").apply {
            setColors(
                Color.parseColor("#10B981"), // muscle – green
                Color.parseColor("#3B82F6"), // water  – blue
                Color.parseColor("#F59E0B"), // bone   – amber
                Color.parseColor("#EF4444")  // fat    – red
            )
            setDrawValues(false)
            stackLabels = arrayOf("Muscle", "Water", "Bone", "Fat")
        }

        chart.apply {
            data = BarData(dataSet).apply { barWidth = 0.6f }
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            setPinchZoom(false)
            setBackgroundColor(Color.TRANSPARENT)
            setFitBars(true)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                textColor = Color.parseColor("#6B7280")
                textSize = 10f
                labelRotationAngle = -30f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String =
                        xLabels.getOrElse(value.toInt()) { "" }
                }
            }

            axisLeft.apply {
                textColor = Color.parseColor("#6B7280")
                setDrawGridLines(true)
                gridColor = Color.parseColor("#F3F4F6")
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
}
