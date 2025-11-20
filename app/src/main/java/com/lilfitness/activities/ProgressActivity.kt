package com.lilfitness.activities

import android.graphics.Color
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.lilfitness.R
import com.lilfitness.databinding.ActivityProgressBinding
import com.lilfitness.helpers.JsonHelper
import com.lilfitness.models.ExerciseSet
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ChartType {
    WEIGHT,
    VOLUME,
    ONE_RM,
    AVG_WEIGHT,
    AVG_RPE
}

class ProgressActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProgressBinding
    private lateinit var jsonHelper: JsonHelper
    private var currentChartType = ChartType.WEIGHT
    private var currentExerciseSets: List<ExerciseSet> = emptyList()
    private lateinit var dateFormat: SimpleDateFormat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "View Progress"

        jsonHelper = JsonHelper(this)
        dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

        setupBackgroundAnimation()
        setupTabs()
        setupSpinner()
        setupClickListeners()
    }

    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is Animatable) {
            drawable.start()
        }
    }

    private fun setupClickListeners() {
        binding.buttonBack.setOnClickListener {
            onBackPressed()
        }
    }

    private fun setupTabs() {
        binding.tabChartType.addTab(binding.tabChartType.newTab().setText("Weight"))
        binding.tabChartType.addTab(binding.tabChartType.newTab().setText("Volume"))
        binding.tabChartType.addTab(binding.tabChartType.newTab().setText("1RM"))
        binding.tabChartType.addTab(binding.tabChartType.newTab().setText("Avg Weight"))
        binding.tabChartType.addTab(binding.tabChartType.newTab().setText("Avg RPE"))

        binding.tabChartType.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> currentChartType = ChartType.WEIGHT
                    1 -> currentChartType = ChartType.VOLUME
                    2 -> currentChartType = ChartType.ONE_RM
                    3 -> currentChartType = ChartType.AVG_WEIGHT
                    4 -> currentChartType = ChartType.AVG_RPE
                }
                if (currentExerciseSets.isNotEmpty()) {
                    setupChart(currentExerciseSets, dateFormat)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupSpinner() {
        val trainingData = jsonHelper.readTrainingData()
        val exerciseNames = trainingData.trainings
            .flatMap { it.exercises }
            .map { it.exerciseName }
            .distinct()

        val adapter = ArrayAdapter(
            this,
            R.layout.item_progress_spinner_selected,
            exerciseNames
        )
        adapter.setDropDownViewResource(R.layout.item_progress_spinner_dropdown)
        binding.spinnerExercise.adapter = adapter

        binding.spinnerExercise.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val selectedExercise = exerciseNames[position]
                updateStatsForExercise(selectedExercise)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
    }

    private fun updateStatsForExercise(exerciseName: String) {
        val allSets = mutableListOf<ExerciseSet>()
        val trainingData = jsonHelper.readTrainingData()
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

        for (training in trainingData.trainings) {
            for (exercise in training.exercises) {
                if (exercise.exerciseName == exerciseName) {
                    allSets.add(
                        ExerciseSet(
                            training.date,
                            exercise.setNumber,
                            exercise.kg,
                            exercise.reps,
                            exercise.rpe
                        )
                    )
                }
            }
        }

        allSets.sortBy {
            try {
                dateFormat.parse(it.date)
            } catch (e: Exception) {
                Date(0)
            }
        }

        currentExerciseSets = allSets
        calculateAndDisplayStats(allSets)
        setupChart(allSets, dateFormat)
    }

    private fun calculateAndDisplayStats(sets: List<ExerciseSet>) {
        if (sets.isEmpty()) {
            binding.textMaxVolume.text = "--"
            binding.textMaxWeight.text = "--"
            binding.textAvgWeight.text = "--"
            binding.textAvgRpe.text = "--"
            binding.textTotalReps.text = "--"
            return
        }

        val maxWeight = sets.maxOfOrNull { it.kg } ?: 0f
        val totalReps = sets.sumOf { it.reps }
        val totalVolume = sets.sumOf { (it.kg * it.reps).toDouble() }
        val avgWeight = if (totalReps > 0) totalVolume / totalReps else 0.0

        // Calculate max volume per session
        val volumePerSession = sets.groupBy { it.date }
            .mapValues { (_, sessionSets) ->
                sessionSets.sumOf { (it.kg * it.reps).toDouble() }
            }
        val maxVolume = volumePerSession.values.maxOrNull() ?: 0.0

        val rpeValues = sets.mapNotNull { it.rpe?.toDouble() }
        val avgRpe = if (rpeValues.isNotEmpty()) rpeValues.average() else null

        binding.textMaxVolume.text = String.format(Locale.US, "%.0f kg", maxVolume)
        binding.textMaxWeight.text = String.format(Locale.US, "%.1f kg", maxWeight)
        binding.textAvgWeight.text = String.format(Locale.US, "%.1f kg", avgWeight)
        binding.textAvgRpe.text = avgRpe?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
        binding.textTotalReps.text = totalReps.toString()
    }

    private fun calculateOneRM(weight: Float, reps: Int): Float {
        // Epley's formula: 1RM = weight × (1 + reps/30)
        if (reps <= 0) return weight
        if (reps == 1) return weight
        return weight * (1 + reps / 30f)
    }

    /**
     * Calculate a nice maximum value for Y-axis that rounds up to a sensible number
     */
    private fun calculateNiceMaximum(maxValue: Float): Float {
        if (maxValue <= 0) return 100f
        
        // Add 15% padding
        val paddedValue = maxValue * 1.15f
        
        // Round up to nice numbers based on magnitude
        return when {
            paddedValue < 10 -> {
                // For values < 10, round to nearest 2
                ((paddedValue / 2).toInt() * 2 + 2).toFloat().coerceAtLeast(5f)
            }
            paddedValue < 50 -> {
                // For values < 50, round to nearest 5
                ((paddedValue / 5).toInt() * 5 + 5).toFloat().coerceAtLeast(10f)
            }
            paddedValue < 100 -> {
                // For values < 100, round to nearest 10
                ((paddedValue / 10).toInt() * 10 + 10).toFloat().coerceAtLeast(50f)
            }
            paddedValue < 500 -> {
                // For values < 500, round to nearest 25
                ((paddedValue / 25).toInt() * 25 + 25).toFloat().coerceAtLeast(100f)
            }
            paddedValue < 1000 -> {
                // For values < 1000, round to nearest 50
                ((paddedValue / 50).toInt() * 50 + 50).toFloat().coerceAtLeast(500f)
            }
            else -> {
                // For large values, round to nearest 100
                ((paddedValue / 100).toInt() * 100 + 100).toFloat().coerceAtLeast(1000f)
            }
        }
    }

    private fun setupChart(sets: List<ExerciseSet>, dateFormat: SimpleDateFormat) {
        if (sets.isEmpty()) {
            binding.textEmptyState.text = getString(R.string.progress_empty_state)
            binding.textEmptyState.visibility = View.VISIBLE
            binding.chart.clear()
            binding.chart.invalidate()
            return
        }

        val entries = mutableListOf<Entry>()
        val label: String
        val color: Int

        when (currentChartType) {
            ChartType.WEIGHT -> {
                val maxWeightPerSession = sets.groupBy { it.date }
                    .mapValues { (_, sessionSets) ->
                        sessionSets.maxOfOrNull { it.kg } ?: 0f
                    }

                maxWeightPerSession.forEach { (dateStr, maxWeight) ->
                    val date = try {
                        dateFormat.parse(dateStr)
                    } catch (e: Exception) {
                        null
                    }
                    if (date != null) {
                        entries.add(Entry(date.time.toFloat(), maxWeight))
                    }
                }
                label = "Max Weight (kg)"
                color = Color.parseColor("#2196F3") // Blue
            }
            ChartType.VOLUME -> {
                val volumePerSession = sets.groupBy { it.date }
                    .mapValues { (_, sessionSets) ->
                        sessionSets.sumOf { (it.kg * it.reps).toDouble() }.toFloat()
                    }

                volumePerSession.forEach { (dateStr, volume) ->
                    val date = try {
                        dateFormat.parse(dateStr)
                    } catch (e: Exception) {
                        null
                    }
                    if (date != null) {
                        entries.add(Entry(date.time.toFloat(), volume))
                    }
                }
                label = "Volume (kg)"
                color = Color.parseColor("#4CAF50") // Green
            }
            ChartType.ONE_RM -> {
                val oneRMPerSession = sets.groupBy { it.date }
                    .mapValues { (_, sessionSets) ->
                        sessionSets.maxOfOrNull { calculateOneRM(it.kg, it.reps) } ?: 0f
                    }

                oneRMPerSession.forEach { (dateStr, oneRM) ->
                    val date = try {
                        dateFormat.parse(dateStr)
                    } catch (e: Exception) {
                        null
                    }
                    if (date != null) {
                        entries.add(Entry(date.time.toFloat(), oneRM))
                    }
                }
                label = "1RM (kg)"
                color = Color.parseColor("#FF9800") // Orange
            }
            ChartType.AVG_WEIGHT -> {
                val avgWeightPerSession = sets.groupBy { it.date }
                    .mapValues { (_, sessionSets) ->
                        val totalVolume = sessionSets.sumOf { (it.kg * it.reps).toDouble() }
                        val totalReps = sessionSets.sumOf { it.reps }
                        if (totalReps > 0) (totalVolume / totalReps).toFloat() else 0f
                    }

                avgWeightPerSession.forEach { (dateStr, avgWeight) ->
                    val date = try {
                        dateFormat.parse(dateStr)
                    } catch (e: Exception) {
                        null
                    }
                    if (date != null) {
                        entries.add(Entry(date.time.toFloat(), avgWeight))
                    }
                }
                label = "Avg Weight (kg)"
                color = Color.parseColor("#9C27B0") // Purple
            }
            ChartType.AVG_RPE -> {
                val avgRpePerSession = sets.groupBy { it.date }
                    .mapValues { (_, sessionSets) ->
                        val rpeValues = sessionSets.mapNotNull { it.rpe }
                        if (rpeValues.isNotEmpty()) {
                            rpeValues.average().toFloat()
                        } else {
                            null
                        }
                    }

                avgRpePerSession.forEach { (dateStr, avgRpe) ->
                    val date = try {
                        dateFormat.parse(dateStr)
                    } catch (e: Exception) {
                        null
                    }
                    if (date != null && avgRpe != null) {
                        entries.add(Entry(date.time.toFloat(), avgRpe))
                    }
                }
                label = "Avg RPE"
                color = Color.parseColor("#F59E0B") // Amber
            }
        }

        if (entries.isEmpty()) {
            val message = if (currentChartType == ChartType.AVG_RPE) {
                getString(R.string.progress_empty_state_rpe)
            } else {
                getString(R.string.progress_empty_state)
            }
            binding.textEmptyState.text = message
            binding.textEmptyState.visibility = View.VISIBLE
            binding.chart.clear()
            binding.chart.invalidate()
            return
        } else {
            binding.textEmptyState.visibility = View.GONE
        }

        entries.sortBy { it.x }

        val maxEntryValue = entries.maxOfOrNull { it.y } ?: 0f
        val niceMaximum = calculateNiceMaximum(maxEntryValue)

        val dataSet = LineDataSet(entries, label)
        dataSet.color = color
        dataSet.valueTextColor = Color.DKGRAY
        dataSet.setCircleColor(color)
        dataSet.circleRadius = 6f
        dataSet.lineWidth = 3.5f
        dataSet.setDrawValues(false)
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.cubicIntensity = 0.2f
        dataSet.setDrawFilled(true)
        dataSet.fillColor = color
        dataSet.fillAlpha = 40
        dataSet.valueTextSize = 11f
        dataSet.formSize = 12f

        val lineData = LineData(dataSet)
        lineData.setValueTextSize(11f)
        binding.chart.data = lineData

        val xAxis = binding.chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textSize = 12f
        xAxis.textColor = Color.parseColor("#616161")
        xAxis.yOffset = 8f
        xAxis.setLabelCount(minOf(entries.size, 8), true)
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return try {
                    val date = Date(value.toLong())
                    SimpleDateFormat("MM/dd", Locale.getDefault()).format(date)
                } catch (e: Exception) {
                    ""
                }
            }
        }
        xAxis.labelRotationAngle = -45f
        xAxis.setDrawGridLines(true)
        xAxis.gridColor = Color.parseColor("#E0E0E0")
        xAxis.gridLineWidth = 1.5f
        xAxis.enableGridDashedLine(12f, 8f, 0f)
        xAxis.setDrawAxisLine(true)
        xAxis.axisLineColor = Color.parseColor("#9E9E9E")
        xAxis.axisLineWidth = 1.5f

        val leftAxis = binding.chart.axisLeft
        leftAxis.axisMinimum = 0f
        leftAxis.axisMaximum = niceMaximum
        leftAxis.textSize = 12f
        leftAxis.textColor = Color.parseColor("#616161")
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.parseColor("#E0E0E0")
        leftAxis.gridLineWidth = 1.5f
        leftAxis.enableGridDashedLine(12f, 8f, 0f)
        leftAxis.setDrawZeroLine(true)
        leftAxis.zeroLineColor = Color.parseColor("#9E9E9E")
        leftAxis.zeroLineWidth = 2f
        leftAxis.setLabelCount(6, true)
        leftAxis.setDrawAxisLine(true)
        leftAxis.axisLineColor = Color.parseColor("#9E9E9E")
        leftAxis.axisLineWidth = 1.5f
        leftAxis.setDrawLabels(true)
        leftAxis.spaceTop = 5f
        leftAxis.spaceBottom = 0f

        leftAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return if (value >= 1000) {
                    String.format(Locale.US, "%.0f", value)
                } else if (value >= 100) {
                    String.format(Locale.US, "%.0f", value)
                } else {
                    String.format(Locale.US, "%.1f", value)
                }
            }
        }

        binding.chart.axisRight.isEnabled = false

        binding.chart.description.isEnabled = false
        binding.chart.setBackgroundColor(Color.WHITE)
        binding.chart.setDrawGridBackground(false)
        binding.chart.setBorderColor(Color.parseColor("#E0E0E0"))
        binding.chart.setBorderWidth(1f)

        val legend = binding.chart.legend
        legend.isEnabled = true
        legend.textSize = 13f
        legend.textColor = Color.parseColor("#424242")
        legend.formSize = 12f
        legend.xEntrySpace = 15f
        legend.yEntrySpace = 8f

        binding.chart.setTouchEnabled(true)
        binding.chart.setDragEnabled(true)
        binding.chart.setScaleEnabled(true)
        binding.chart.setPinchZoom(true)
        binding.chart.setDoubleTapToZoomEnabled(true)

        binding.chart.animateX(800)
        binding.chart.invalidate()
    }
}
