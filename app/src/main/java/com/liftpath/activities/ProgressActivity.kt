package com.liftpath.activities

import android.graphics.Color
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.liftpath.R
import com.liftpath.databinding.ActivityProgressBinding
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.ProgressSettingsManager
import com.liftpath.helpers.OneRMEstimationHelper
import com.liftpath.helpers.SessionMetrics
import com.liftpath.helpers.TrendResult
import com.liftpath.helpers.TrendDirection
import com.liftpath.helpers.DialogHelper
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.TargetMuscle
import com.liftpath.models.MovementPattern
import com.liftpath.helpers.showWithTransparentWindow
import com.liftpath.models.ExerciseSet
import com.liftpath.utils.WorkoutTypeFormatter
import android.view.LayoutInflater
import android.widget.TextView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.BarChart
import java.util.Calendar
import android.content.Intent
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

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
    private lateinit var settingsManager: ProgressSettingsManager
    private var currentChartType = ChartType.WEIGHT
    private var currentExerciseSets: List<ExerciseSet> = emptyList()
    private var currentSessionWorkoutTypes: Map<String, String> = emptyMap() // date -> workoutType from TrainingSession
    private lateinit var dateFormat: SimpleDateFormat
    private var currentMetricView: String = "Strength" // "Strength" or "Volume"
    private var showAllSessions: Boolean = false
    private var cachedSessionMetrics: List<SessionMetrics> = emptyList()
    private var currentTimeRangeMonths: Int = 3 // Default: 3 months
    private var isGroupSelected: Boolean = false
    private var selectedGroupName: String? = null
    private var exerciseLibrary: List<ExerciseLibraryItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "View Progress"

        jsonHelper = JsonHelper(this)
        settingsManager = ProgressSettingsManager(this)
        dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

        setupBackgroundAnimation()
        setupTabs()
        setupChipGroup()
        setupTimeRangeSpinner()
        setupSpinner()
        setupEstimationPeriodSpinner()
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

        binding.buttonSettings.setOnClickListener {
            val intent = Intent(this, ProgressSettingsActivity::class.java)
            startActivity(intent)
        }

        binding.buttonInfoEstimation.setOnClickListener {
            showEstimationLogicDialog()
        }

        binding.buttonExtendedProjection.setOnClickListener {
            showExtendedProjectionDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh estimation if settings changed
        if (currentExerciseSets.isNotEmpty()) {
            val selectedExercise = binding.spinnerExercise.selectedItem?.toString()
            if (selectedExercise != null) {
                updateStatsForExercise(selectedExercise)
            }
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
                // When TabLayout is used, hide ChipGroup and show TabLayout
                binding.chipGroupMetric.visibility = View.GONE
                binding.layoutShowAllToggle.visibility = View.GONE
                binding.tabChartType.visibility = View.VISIBLE
                binding.textChartModeIndicator.visibility = View.GONE
                
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

    private fun setupChipGroup() {
        // Hide TabLayout by default, show ChipGroup (new 3D view mode)
        binding.tabChartType.visibility = View.GONE
        binding.chipGroupMetric.visibility = View.VISIBLE

        binding.chipGroupMetric.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull()
            when (checkedId) {
                binding.chipStrength.id -> {
                    currentMetricView = "Strength"
                    binding.layoutShowAllToggle.visibility = View.VISIBLE
                    updateChartModeIndicator()
                    if (currentExerciseSets.isNotEmpty()) {
                        refreshChartView()
                    }
                }
                binding.chipVolume.id -> {
                    currentMetricView = "Volume"
                    binding.layoutShowAllToggle.visibility = View.GONE
                    updateChartModeIndicator()
                    if (currentExerciseSets.isNotEmpty()) {
                        refreshChartView()
                    }
                }
            }
        }

        // Set default to Strength
        binding.chipStrength.isChecked = true
        binding.layoutShowAllToggle.visibility = View.VISIBLE

        // Setup Show All toggle with better feedback
        binding.switchShowAll.setOnCheckedChangeListener { _, isChecked ->
            showAllSessions = isChecked
            updateChartModeIndicator()
            if (currentExerciseSets.isNotEmpty() && currentMetricView == "Strength") {
                refreshChartView()
            }
        }
        
        // Initial indicator update
        updateChartModeIndicator()

        // Setup Coach's Summary button
        binding.buttonCoachSummary.setOnClickListener {
            showCoachReportDialog()
        }
    }

    private fun setupTimeRangeSpinner() {
        val timeRanges = arrayOf("1 month", "3 months", "12 months")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, timeRanges)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTimeRange.adapter = adapter

        // Set default to 3 months (index 1)
        binding.spinnerTimeRange.setSelection(1)
        currentTimeRangeMonths = 3

        binding.spinnerTimeRange.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                currentTimeRangeMonths = when (position) {
                    0 -> 1
                    1 -> 3
                    2 -> 12
                    else -> 3
                }
                updateChartModeIndicator()
                if (currentExerciseSets.isNotEmpty()) {
                    refreshChartView()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun updateChartModeIndicator() {
        if (binding.chipGroupMetric.visibility != View.VISIBLE) {
            binding.textChartModeIndicator.visibility = View.GONE
            return
        }

        // Calculate filtered session count based on time range
        val cutoffDate = Calendar.getInstance().apply {
            add(Calendar.MONTH, -currentTimeRangeMonths)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val groupPrefix = if (isGroupSelected && selectedGroupName != null) {
            "$selectedGroupName • "
        } else {
            ""
        }

        when (currentMetricView) {
            "Strength" -> {
                val filterText = if (showAllSessions) "Heavy + Light" else "Heavy only"
                val sessionCount = if (currentExerciseSets.isNotEmpty()) {
                    val oneRMPerSession = OneRMEstimationHelper.calculateOneRMPerSession(
                        currentExerciseSets,
                        currentSessionWorkoutTypes,
                        showAllSessions
                    )
                    oneRMPerSession.keys.count { dateStr ->
                        try {
                            val date = dateFormat.parse(dateStr)
                            date != null && date.time >= cutoffDate.time
                        } catch (e: Exception) {
                            false
                        }
                    }
                } else {
                    0
                }
                val timeRangeText = when (currentTimeRangeMonths) {
                    1 -> "1 month"
                    3 -> "3 months"
                    12 -> "12 months"
                    else -> "$currentTimeRangeMonths months"
                }
                binding.textChartModeIndicator.text = "$groupPrefix 1RM • $filterText • $timeRangeText • $sessionCount sessions"
                binding.textChartModeIndicator.visibility = View.VISIBLE
                binding.textFilterStatus.text = if (showAllSessions) {
                    "Showing: Heavy + Light sessions"
                } else {
                    "Showing: Heavy sessions only"
                }
            }
            "Volume" -> {
                val sessionCount = if (currentExerciseSets.isNotEmpty()) {
                    val volumePerSession = OneRMEstimationHelper.calculateVolumePerSession(
                        currentExerciseSets,
                        currentSessionWorkoutTypes
                    )
                    volumePerSession.keys.count { dateStr ->
                        try {
                            val date = dateFormat.parse(dateStr)
                            date != null && date.time >= cutoffDate.time
                        } catch (e: Exception) {
                            false
                        }
                    }
                } else {
                    0
                }
                val timeRangeText = when (currentTimeRangeMonths) {
                    1 -> "1 month"
                    3 -> "3 months"
                    12 -> "12 months"
                    else -> "$currentTimeRangeMonths months"
                }
                val groupText = if (isGroupSelected) "Group Volume" else "Volume"
                binding.textChartModeIndicator.text = "$groupPrefix $groupText • All sessions • $timeRangeText • $sessionCount sessions"
                binding.textChartModeIndicator.visibility = View.VISIBLE
                binding.textFilterStatus.text = if (isGroupSelected) {
                    "Showing: Aggregated volume for group"
                } else {
                    "Showing: All session types"
                }
            }
            else -> {
                binding.textChartModeIndicator.visibility = View.GONE
            }
        }
    }

    private fun setupSpinner() {
        val trainingData = jsonHelper.readTrainingData()
        exerciseLibrary = trainingData.exerciseLibrary
        
        val exerciseNames = trainingData.trainings
            .flatMap { it.exercises }
            .map { it.exerciseName }
            .distinct()
            .sorted()

        // Create group options
        val groupOptions = mutableListOf<String>()
        groupOptions.add("[ALL CHEST]")
        groupOptions.add("[ALL LEGS]")
        groupOptions.add("[ALL PUSH]")
        groupOptions.add("[ALL PULL]")
        
        // Combine groups and exercises
        val allOptions = mutableListOf<String>()
        allOptions.addAll(groupOptions)
        allOptions.add("─────────") // Divider
        allOptions.addAll(exerciseNames)

        val adapter = ArrayAdapter(
            this,
            R.layout.item_progress_spinner_selected,
            allOptions
        )
        adapter.setDropDownViewResource(R.layout.item_progress_spinner_dropdown)
        binding.spinnerExercise.adapter = adapter

        binding.spinnerExercise.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val selected = allOptions[position]
                if (selected == "─────────") {
                    // Skip divider - select first exercise instead
                    if (exerciseNames.isNotEmpty()) {
                        val firstExerciseIndex = groupOptions.size + 1 // +1 for divider
                        binding.spinnerExercise.setSelection(firstExerciseIndex)
                    }
                    return
                }
                if (selected.startsWith("[ALL")) {
                    // Group selected
                    isGroupSelected = true
                    selectedGroupName = selected
                    updateStatsForGroup(selected)
                } else {
                    // Individual exercise selected
                    isGroupSelected = false
                    selectedGroupName = null
                    updateStatsForExercise(selected)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
    }

    private fun updateStatsForGroup(groupName: String) {
        val trainingData = jsonHelper.readTrainingData()
        
        // Get exercises in the group
        val exercisesInGroup = getExercisesInGroup(groupName)
        if (exercisesInGroup.isEmpty()) {
            // No exercises found in group
            binding.textEmptyState.text = "No exercises found in this group"
            binding.textEmptyState.visibility = View.VISIBLE
            binding.chart.visibility = View.GONE
            binding.chartBar.visibility = View.GONE
            return
        }

        // Get all sets from exercises in the group
        val allSets = mutableListOf<ExerciseSet>()
        val sessionWorkoutTypes = mutableMapOf<String, String>()
        
        trainingData.trainings.forEach { session ->
            session.exercises.forEach { entry ->
                // Check if this exercise is in the group
                val exerciseInGroup = exercisesInGroup.any { exercise ->
                    exercise.id == entry.exerciseId || exercise.name == entry.exerciseName
                }
                
                if (exerciseInGroup) {
                    allSets.add(ExerciseSet(
                        date = session.date,
                        setNumber = entry.setNumber,
                        kg = entry.kg,
                        reps = entry.reps,
                        rpe = entry.rpe
                    ))
                    if (session.defaultWorkoutType != null) {
                        sessionWorkoutTypes[session.date] = session.defaultWorkoutType
                    }
                }
            }
        }

        if (allSets.isEmpty()) {
            binding.textEmptyState.text = "No training data for this group"
            binding.textEmptyState.visibility = View.VISIBLE
            binding.chart.visibility = View.GONE
            binding.chartBar.visibility = View.GONE
            return
        }

        currentExerciseSets = allSets
        currentSessionWorkoutTypes = sessionWorkoutTypes

        // For groups, always show volume as BarChart
        currentMetricView = "Volume"
        // Uncheck strength and check volume
        binding.chipStrength.isChecked = false
        binding.chipVolume.isChecked = true
        binding.layoutShowAllToggle.visibility = View.GONE // Hide show all toggle for groups

        // Calculate and display metrics
        calculateSessionMetrics(allSets)
        calculateAndDisplayStats(allSets)
        updateChartModeIndicator()
        refreshChartView()
        calculateAndDisplayInsights(allSets)
    }

    private fun getExercisesInGroup(groupName: String): List<ExerciseLibraryItem> {
        return when (groupName) {
            "[ALL CHEST]" -> {
                exerciseLibrary.filter { exercise ->
                    exercise.primaryTargets.any { target ->
                        target == TargetMuscle.CHEST_UPPER ||
                        target == TargetMuscle.CHEST_MIDDLE ||
                        target == TargetMuscle.CHEST_LOWER
                    }
                }
            }
            "[ALL LEGS]" -> {
                exerciseLibrary.filter { exercise ->
                    exercise.primaryTargets.any { target ->
                        target == TargetMuscle.QUADS ||
                        target == TargetMuscle.HAMSTRINGS ||
                        target == TargetMuscle.GLUTES ||
                        target == TargetMuscle.CALVES
                    }
                }
            }
            "[ALL PUSH]" -> {
                exerciseLibrary.filter { exercise ->
                    exercise.pattern == MovementPattern.PUSH_HORIZONTAL ||
                    exercise.pattern == MovementPattern.PUSH_VERTICAL
                }
            }
            "[ALL PULL]" -> {
                exerciseLibrary.filter { exercise ->
                    exercise.pattern == MovementPattern.PULL_HORIZONTAL ||
                    exercise.pattern == MovementPattern.PULL_VERTICAL
                }
            }
            else -> emptyList()
        }
    }

    private fun updateStatsForExercise(exerciseName: String) {
        val allSets = mutableListOf<ExerciseSet>()
        val sessionWorkoutTypes = mutableMapOf<String, String>() // date -> workoutType
        val trainingData = jsonHelper.readTrainingData()
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

        for (training in trainingData.trainings) {
            val workoutType = WorkoutTypeFormatter.normalize(training.defaultWorkoutType)
            
            // Store session workout type by date (from TrainingSession.defaultWorkoutType)
            sessionWorkoutTypes[training.date] = training.defaultWorkoutType ?: "heavy"
            
            // Rule B: Filtering based on workout type and RPE availability
            // - Heavy workouts: Always include (can infer from weight/reps if no RPE)
            // - Light/Deload/Warmup workouts: Only include if RPE data is available
            val shouldIncludeWorkout = when (workoutType) {
                WorkoutTypeFormatter.HEAVY -> {
                    // Heavy workouts always included
                    true
                }
                WorkoutTypeFormatter.LIGHT -> {
                    // Light workouts only included if they have RPE data (Rule B)
                    // Check if any exercise sets for this exercise have RPE
                    training.exercises
                        .filter { it.exerciseName == exerciseName }
                        .any { it.rpe != null }
                }
                else -> {
                    // Custom/other workouts: Include if RPE data is available (conservative approach)
                    training.exercises
                        .filter { it.exerciseName == exerciseName }
                        .any { it.rpe != null }
                }
            }
            
            if (shouldIncludeWorkout) {
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
        }

        allSets.sortBy {
            try {
                dateFormat.parse(it.date)
            } catch (e: Exception) {
                Date(0)
            }
        }

        currentExerciseSets = allSets
        currentSessionWorkoutTypes = sessionWorkoutTypes // Store for use in estimation
        calculateAndDisplayStats(allSets)
        calculateSessionMetrics(allSets)
        updateChartModeIndicator()
        refreshChartView()
        calculateAndDisplayEstimation(allSets)
        calculateAndDisplayInsights(allSets)
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


    private fun calculateSessionMetrics(sets: List<ExerciseSet>) {
        if (sets.isEmpty()) {
            cachedSessionMetrics = emptyList()
            return
        }

        val volumePerSession = OneRMEstimationHelper.calculateVolumePerSession(sets, currentSessionWorkoutTypes)
        val efficiencyPerSession = OneRMEstimationHelper.calculateEfficiencyPerSession(sets, currentSessionWorkoutTypes)
        val oneRMPerSession = OneRMEstimationHelper.calculateOneRMPerSession(sets, currentSessionWorkoutTypes, showAllSessions)

        // Get all unique session dates
        val allDates = sets.map { it.date }.distinct().sorted()

        cachedSessionMetrics = allDates.map { date ->
            val workoutType = currentSessionWorkoutTypes[date]
            SessionMetrics(
                date = date,
                workoutType = workoutType,
                oneRM = oneRMPerSession[date],
                volume = volumePerSession[date] ?: 0f,
                efficiency = efficiencyPerSession[date]
            )
        }
    }

    private fun refreshChartView() {
        if (currentExerciseSets.isEmpty()) return

        when (currentMetricView) {
            "Strength" -> {
                binding.chart.visibility = View.VISIBLE
                binding.chartBar.visibility = View.GONE
                setupLineChartForStrength()
            }
            "Volume" -> {
                binding.chart.visibility = View.GONE
                binding.chartBar.visibility = View.VISIBLE
                setupBarChart()
            }
        }
    }

    private fun setupLineChartForStrength() {
        if (currentExerciseSets.isEmpty()) {
            binding.textEmptyState.text = getString(R.string.progress_empty_state)
            binding.textEmptyState.visibility = View.VISIBLE
            binding.chart.clear()
            binding.chart.invalidate()
            return
        }

        val oneRMPerSession = OneRMEstimationHelper.calculateOneRMPerSession(
            currentExerciseSets,
            currentSessionWorkoutTypes,
            showAllSessions
        )

        // Filter by time range
        val cutoffDate = Calendar.getInstance().apply {
            add(Calendar.MONTH, -currentTimeRangeMonths)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val entries = mutableListOf<Entry>()
        oneRMPerSession.forEach { (dateStr, oneRM) ->
            val date = try {
                dateFormat.parse(dateStr)
            } catch (e: Exception) {
                null
            }
            if (date != null && date.time >= cutoffDate.time) {
                entries.add(Entry(date.time.toFloat(), oneRM))
            }
        }

        if (entries.isEmpty()) {
            binding.textEmptyState.text = getString(R.string.progress_empty_state)
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

        val dataSet = LineDataSet(entries, "1RM (kg)")
        dataSet.color = Color.parseColor("#FF9800") // Orange
        dataSet.valueTextColor = Color.DKGRAY
        dataSet.setCircleColor(Color.parseColor("#FF9800"))
        dataSet.circleRadius = 6f
        dataSet.lineWidth = 3.5f
        dataSet.setDrawValues(false)
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.cubicIntensity = 0.2f
        dataSet.setDrawFilled(true)
        dataSet.fillColor = Color.parseColor("#FF9800")
        dataSet.fillAlpha = 40

        val lineData = LineData(dataSet)
        lineData.setValueTextSize(11f)
        binding.chart.data = lineData

        configureChartAxes(binding.chart, entries, niceMaximum)
        binding.chart.animateX(800)
        binding.chart.invalidate()
    }

    private fun setupBarChart() {
        if (currentExerciseSets.isEmpty()) {
            binding.textEmptyState.text = getString(R.string.progress_empty_state)
            binding.textEmptyState.visibility = View.VISIBLE
            binding.chartBar.clear()
            binding.chartBar.invalidate()
            return
        }

        val volumePerSession = OneRMEstimationHelper.calculateVolumePerSession(
            currentExerciseSets,
            currentSessionWorkoutTypes
        )

        // Filter by time range
        val cutoffDate = Calendar.getInstance().apply {
            add(Calendar.MONTH, -currentTimeRangeMonths)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val filteredDates = volumePerSession.keys.filter { dateStr ->
            try {
                val date = dateFormat.parse(dateStr)
                date != null && date.time >= cutoffDate.time
            } catch (e: Exception) {
                false
            }
        }.sorted()

        val barEntries = mutableListOf<BarEntry>()
        filteredDates.forEachIndexed { index, dateStr ->
            val volume = volumePerSession[dateStr] ?: 0f
            barEntries.add(BarEntry(index.toFloat(), volume))
        }

        if (barEntries.isEmpty()) {
            binding.textEmptyState.text = getString(R.string.progress_empty_state)
            binding.textEmptyState.visibility = View.VISIBLE
            binding.chartBar.clear()
            binding.chartBar.invalidate()
            return
        } else {
            binding.textEmptyState.visibility = View.GONE
        }

        val maxValue = barEntries.maxOfOrNull { it.y } ?: 0f
        val niceMaximum = calculateNiceMaximum(maxValue)

        val dataSet = BarDataSet(barEntries, "Volume (kg)")
        dataSet.color = Color.parseColor("#4CAF50") // Green
        dataSet.valueTextColor = Color.DKGRAY
        dataSet.setDrawValues(false)

        val barData = BarData(dataSet)
        barData.barWidth = 0.6f
        binding.chartBar.data = barData

        configureBarChartAxes(binding.chartBar, filteredDates, niceMaximum)
        binding.chartBar.animateY(800)
        binding.chartBar.invalidate()
    }

    private fun configureChartAxes(chart: LineChart, entries: List<Entry>, niceMaximum: Float) {
        val xAxis = chart.xAxis
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

        val leftAxis = chart.axisLeft
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

        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false
        chart.setBackgroundColor(Color.WHITE)
        chart.setDrawGridBackground(false)
        chart.setBorderColor(Color.parseColor("#E0E0E0"))
        chart.setBorderWidth(1f)

        val legend = chart.legend
        legend.isEnabled = true
        legend.textSize = 13f
        legend.textColor = Color.parseColor("#424242")
        legend.formSize = 12f
        legend.xEntrySpace = 15f
        legend.yEntrySpace = 8f

        chart.setTouchEnabled(true)
        chart.setDragEnabled(true)
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.setDoubleTapToZoomEnabled(true)
    }

    private fun configureBarChartAxes(chart: BarChart, sortedDates: List<String>, niceMaximum: Float) {
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textSize = 12f
        xAxis.textColor = Color.parseColor("#616161")
        xAxis.yOffset = 8f
        xAxis.setLabelCount(minOf(sortedDates.size, 8), true)
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                if (index >= 0 && index < sortedDates.size) {
                    val dateStr = sortedDates[index]
                    return try {
                        val date = dateFormat.parse(dateStr)
                        SimpleDateFormat("MM/dd", Locale.getDefault()).format(date)
                    } catch (e: Exception) {
                        ""
                    }
                }
                return ""
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

        val leftAxis = chart.axisLeft
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
                } else {
                    String.format(Locale.US, "%.0f", value)
                }
            }
        }

        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false
        chart.setBackgroundColor(Color.WHITE)
        chart.setDrawGridBackground(false)
        chart.setBorderColor(Color.parseColor("#E0E0E0"))
        chart.setBorderWidth(1f)

        val legend = chart.legend
        legend.isEnabled = true
        legend.textSize = 13f
        legend.textColor = Color.parseColor("#424242")
        legend.formSize = 12f
        legend.xEntrySpace = 15f
        legend.yEntrySpace = 8f

        chart.setTouchEnabled(true)
        chart.setDragEnabled(true)
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.setDoubleTapToZoomEnabled(true)
    }

    private fun calculateAndDisplayInsights(sets: List<ExerciseSet>) {
        if (cachedSessionMetrics.isEmpty() || cachedSessionMetrics.size < 2) {
            binding.cardInsight.visibility = View.GONE
            return
        }

        binding.cardInsight.visibility = View.VISIBLE

        // Get last session (most recent) - sessions are sorted by date (oldest to newest)
        val lastSession = cachedSessionMetrics.last()
        
        // For efficiency: find matching workout type session
        // Function expects sessions sorted newest to oldest, so reverse the list
        val normalizedLastWorkoutType = WorkoutTypeFormatter.normalize(lastSession.workoutType)
        val previousSessionForEfficiency = OneRMEstimationHelper.findMatchingSessionForComparison(
            cachedSessionMetrics.reversed(), // Reverse to newest to oldest for comparison function
            normalizedLastWorkoutType,
            lastSession.date
        )

        // For volume and strength: use immediate previous session
        val previousSession = if (cachedSessionMetrics.size >= 2) {
            cachedSessionMetrics[cachedSessionMetrics.size - 2]
        } else {
            null
        }

        // Display comparisons
        if (previousSession != null) {
            val volumeDiff = lastSession.volume - previousSession.volume
            val volumeText = if (volumeDiff >= 0) {
                String.format(Locale.US, "+%.0f kg", volumeDiff)
            } else {
                String.format(Locale.US, "%.0f kg", volumeDiff)
            }
            binding.textVolumeComparison.text = volumeText

            val strengthDiff = (lastSession.oneRM ?: 0f) - (previousSession.oneRM ?: 0f)
            val strengthText = if (strengthDiff >= 0) {
                String.format(Locale.US, "+%.1f kg", strengthDiff)
            } else {
                String.format(Locale.US, "%.1f kg", strengthDiff)
            }
            binding.textStrengthComparison.text = strengthText
        } else {
            binding.textVolumeComparison.text = "--"
            binding.textStrengthComparison.text = "--"
        }

        // Efficiency comparison (apples to apples)
        if (previousSessionForEfficiency != null && 
            lastSession.efficiency != null && 
            previousSessionForEfficiency.efficiency != null) {
            val efficiencyDiff = lastSession.efficiency - previousSessionForEfficiency.efficiency
            val efficiencyText = if (efficiencyDiff >= 0) {
                String.format(Locale.US, "+%.1f", efficiencyDiff)
            } else {
                String.format(Locale.US, "%.1f", efficiencyDiff)
            }
            val contextText = if (normalizedLastWorkoutType == WorkoutTypeFormatter.normalize(previousSessionForEfficiency.workoutType)) {
                " ($normalizedLastWorkoutType vs $normalizedLastWorkoutType)"
            } else {
                " (Mixed)"
            }
            binding.textEfficiencyComparison.text = efficiencyText + contextText
            binding.textComparisonContext.text = "Comparing: ${normalizedLastWorkoutType ?: "Unknown"} vs ${WorkoutTypeFormatter.normalize(previousSessionForEfficiency.workoutType) ?: "Unknown"}"
        } else {
            binding.textEfficiencyComparison.text = "--"
            binding.textComparisonContext.text = ""
        }

        // Determine badges
        val badges = determineBadges(lastSession, previousSession, previousSessionForEfficiency)
        
        if (badges.isNotEmpty()) {
            binding.textBadge.text = badges.first()
            binding.textBadge.visibility = View.VISIBLE
        } else {
            binding.textBadge.visibility = View.GONE
        }

        // Calculate and display trends
        calculateAndDisplayTrends()
    }

    private fun calculateAndDisplayTrends() {
        if (cachedSessionMetrics.size < 4) {
            // Insufficient data
            binding.cardTrendAnalysis.visibility = View.GONE
            return
        }

        binding.cardTrendAnalysis.visibility = View.VISIBLE

        // Sort sessions by date (oldest to newest) for trend calculation
        val sortedSessions = cachedSessionMetrics.sortedBy { session ->
            try {
                dateFormat.parse(session.date)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }

        // Calculate trends
        val volumeTrend = OneRMEstimationHelper.calculateTrend(sortedSessions, "volume")
        val strengthTrend = OneRMEstimationHelper.calculateTrend(sortedSessions, "strength")
        val efficiencyTrend = OneRMEstimationHelper.calculateTrend(sortedSessions, "efficiency")

        // Display volume trend
        if (volumeTrend != null) {
            val trendText = when (volumeTrend.trendDirection) {
                TrendDirection.UP -> {
                    val weeks = (volumeTrend.sessionCount / 2f).toInt().coerceAtLeast(1)
                    String.format(Locale.US, "📈 Trending Up (+%.0f%% over %d weeks)", 
                        volumeTrend.percentageChange, weeks)
                }
                TrendDirection.DOWN -> {
                    val weeks = (volumeTrend.sessionCount / 2f).toInt().coerceAtLeast(1)
                    String.format(Locale.US, "📉 Deload Needed? (%.0f%% over %d weeks)", 
                        volumeTrend.percentageChange, weeks)
                }
                TrendDirection.STABLE -> "➡️ Stable"
            }
            binding.textVolumeTrend.text = trendText
            binding.textVolumeTrend.setTextColor(when (volumeTrend.trendDirection) {
                TrendDirection.UP -> Color.parseColor("#4CAF50")
                TrendDirection.DOWN -> Color.parseColor("#FF9800")
                TrendDirection.STABLE -> Color.parseColor("#757575")
            })
        } else {
            binding.textVolumeTrend.text = "--"
            binding.textVolumeTrend.setTextColor(Color.parseColor("#757575"))
        }

        // Display strength trend
        if (strengthTrend != null) {
            val trendText = when (strengthTrend.trendDirection) {
                TrendDirection.UP -> {
                    val weeks = (strengthTrend.sessionCount / 2f).toInt().coerceAtLeast(1)
                    String.format(Locale.US, "📈 Trending Up (+%.0f%% over %d weeks)", 
                        strengthTrend.percentageChange, weeks)
                }
                TrendDirection.DOWN -> {
                    val weeks = (strengthTrend.sessionCount / 2f).toInt().coerceAtLeast(1)
                    String.format(Locale.US, "📉 Declining (%.0f%% over %d weeks)", 
                        strengthTrend.percentageChange, weeks)
                }
                TrendDirection.STABLE -> "➡️ Stable"
            }
            binding.textStrengthTrend.text = trendText
            binding.textStrengthTrend.setTextColor(when (strengthTrend.trendDirection) {
                TrendDirection.UP -> Color.parseColor("#4CAF50")
                TrendDirection.DOWN -> Color.parseColor("#FF9800")
                TrendDirection.STABLE -> Color.parseColor("#757575")
            })
        } else {
            binding.textStrengthTrend.text = "--"
            binding.textStrengthTrend.setTextColor(Color.parseColor("#757575"))
        }

        // Display efficiency trend
        if (efficiencyTrend != null) {
            val trendText = when (efficiencyTrend.trendDirection) {
                TrendDirection.UP -> {
                    val weeks = (efficiencyTrend.sessionCount / 2f).toInt().coerceAtLeast(1)
                    String.format(Locale.US, "📈 Trending Up (+%.0f%% over %d weeks)", 
                        efficiencyTrend.percentageChange, weeks)
                }
                TrendDirection.DOWN -> {
                    val weeks = (efficiencyTrend.sessionCount / 2f).toInt().coerceAtLeast(1)
                    String.format(Locale.US, "📉 Declining (%.0f%% over %d weeks)", 
                        efficiencyTrend.percentageChange, weeks)
                }
                TrendDirection.STABLE -> "➡️ Stable"
            }
            binding.textEfficiencyTrend.text = trendText
            binding.textEfficiencyTrend.setTextColor(when (efficiencyTrend.trendDirection) {
                TrendDirection.UP -> Color.parseColor("#4CAF50")
                TrendDirection.DOWN -> Color.parseColor("#FF9800")
                TrendDirection.STABLE -> Color.parseColor("#757575")
            })
        } else {
            binding.textEfficiencyTrend.text = "--"
            binding.textEfficiencyTrend.setTextColor(Color.parseColor("#757575"))
        }
    }

    private fun showCoachReportDialog() {
        if (cachedSessionMetrics.size < 4) {
            DialogHelper.createBuilder(this)
                .setTitle("Insufficient Data")
                .setMessage("Need at least 4 sessions to generate a coach's report.")
                .setPositiveButton("OK", null)
                .showWithTransparentWindow()
            return
        }

        // Sort sessions by date (oldest to newest) for trend calculation
        val sortedSessions = cachedSessionMetrics.sortedBy { session ->
            try {
                dateFormat.parse(session.date)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }

        // Calculate trends
        val volumeTrend = OneRMEstimationHelper.calculateTrend(sortedSessions, "volume")
        val strengthTrend = OneRMEstimationHelper.calculateTrend(sortedSessions, "strength")
        val efficiencyTrend = OneRMEstimationHelper.calculateTrend(sortedSessions, "efficiency")

        // Generate report
        val report = OneRMEstimationHelper.generateCoachReport(
            volumeTrend,
            strengthTrend,
            efficiencyTrend,
            cachedSessionMetrics.size
        )

        DialogHelper.createBuilder(this)
            .setTitle("🧠 IronBrain Report")
            .setMessage(report)
            .setPositiveButton("Got it", null)
            .showWithTransparentWindow()
    }

    private fun determineBadges(
        lastSession: SessionMetrics,
        previousSession: SessionMetrics?,
        previousSessionForEfficiency: SessionMetrics?
    ): List<String> {
        val badges = mutableListOf<String>()

        // Strength PR: Compare against all-time maximum
        val allTimeMax1RM = cachedSessionMetrics.mapNotNull { it.oneRM }.maxOrNull() ?: 0f
        if (lastSession.oneRM != null && lastSession.oneRM > allTimeMax1RM) {
            badges.add("🥇 Strength PR")
        }

        // Volume PR: Compare immediate previous session
        if (previousSession != null && lastSession.volume > previousSession.volume) {
            badges.add("🛡️ Volume PR")
        }

        // Efficiency Gain: Only if same workout type and similar load
        if (previousSessionForEfficiency != null &&
            lastSession.efficiency != null &&
            previousSessionForEfficiency.efficiency != null &&
            WorkoutTypeFormatter.normalize(lastSession.workoutType) == WorkoutTypeFormatter.normalize(previousSessionForEfficiency.workoutType)) {
            
            // Check if load is similar (within 5% or same weight)
            val lastTopSet = currentExerciseSets
                .filter { it.date == lastSession.date }
                .maxByOrNull { it.kg * it.reps }
            val prevTopSet = currentExerciseSets
                .filter { it.date == previousSessionForEfficiency.date }
                .maxByOrNull { it.kg * it.reps }
            
            val isSimilarLoad = if (lastTopSet != null && prevTopSet != null) {
                val weightDiff = kotlin.math.abs(lastTopSet.kg - prevTopSet.kg)
                val avgWeight = (lastTopSet.kg + prevTopSet.kg) / 2f
                weightDiff / avgWeight <= 0.05f || lastTopSet.kg == prevTopSet.kg
            } else {
                false
            }

            if (lastSession.efficiency > previousSessionForEfficiency.efficiency && isSimilarLoad) {
                badges.add("🧠 Efficiency Gain")
            }
        }

        // Priority: Strength PR > Volume PR > Efficiency Gain
        return badges.sortedBy {
            when (it) {
                "🥇 Strength PR" -> 0
                "🛡️ Volume PR" -> 1
                "🧠 Efficiency Gain" -> 2
                else -> 3
            }
        }
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
                        // Use hybrid formula from helper (filters out reps > 15)
                        val valid1RMs = sessionSets.mapNotNull { OneRMEstimationHelper.calculateOneRM(it.kg, it.reps, it.rpe) }
                        valid1RMs.maxOrNull() ?: 0f
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

        // Add projection line for 1RM chart
        val projectionEntries = mutableListOf<Entry>()
        if (currentChartType == ChartType.ONE_RM && entries.isNotEmpty()) {
            val settings = settingsManager.getSettings()
            val estimation = OneRMEstimationHelper.estimate1RMProgression(
                sets = currentExerciseSets,
                sessionWorkoutTypes = currentSessionWorkoutTypes,
                projectionMonths = currentProjectionMonths,
                minDataPoints = settings.minimumDataPoints,
                recentDataWindowDays = settings.recentDataWindowDays
            )

            if (estimation != null && estimation.isQualified) {
                val lastEntry = entries.last()
                val projectionDate = estimation.projectionDate
                
                // Add point at projection date
                projectionEntries.add(Entry(projectionDate.time.toFloat(), estimation.expected1RM))
                
                // Create projection line from last data point to projection
                projectionEntries.add(0, Entry(lastEntry.x, lastEntry.y))
            }
        }

        val maxEntryValue = entries.maxOfOrNull { it.y } ?: 0f
        val maxProjectionValue = projectionEntries.maxOfOrNull { it.y } ?: 0f
        val niceMaximum = calculateNiceMaximum(max(maxEntryValue, maxProjectionValue))

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
        
        // Add projection line if available
        if (projectionEntries.size >= 2) {
            val projectionDataSet = LineDataSet(projectionEntries, "Projection")
            projectionDataSet.color = Color.parseColor("#9E9E9E") // Gray for projection
            projectionDataSet.setCircleColor(Color.parseColor("#9E9E9E"))
            projectionDataSet.circleRadius = 4f
            projectionDataSet.lineWidth = 2f
            projectionDataSet.setDrawValues(false)
            projectionDataSet.mode = LineDataSet.Mode.LINEAR
            projectionDataSet.enableDashedLine(10f, 5f, 0f) // Dashed line
            projectionDataSet.setDrawCircles(true)
            lineData.addDataSet(projectionDataSet)
        }
        
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

    private var currentProjectionMonths = 3

    private fun setupEstimationPeriodSpinner() {
        val periods = arrayOf("1 month", "2 months", "3 months", "6 months")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, periods)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerProjectionPeriod.adapter = adapter

        // Set default from settings
        val settings = settingsManager.getSettings()
        val defaultMonths = settings.defaultEstimationPeriodMonths.coerceIn(1, 6)
        currentProjectionMonths = defaultMonths
        
        // Find closest match in spinner options
        val defaultIndex = when {
            defaultMonths <= 1 -> 0
            defaultMonths <= 2 -> 1
            defaultMonths <= 3 -> 2
            else -> 3
        }
        binding.spinnerProjectionPeriod.setSelection(defaultIndex)

        binding.spinnerProjectionPeriod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                currentProjectionMonths = when (position) {
                    0 -> 1
                    1 -> 2
                    2 -> 3
                    3 -> 6
                    else -> 3
                }
                if (currentExerciseSets.isNotEmpty()) {
                    calculateAndDisplayEstimation(currentExerciseSets)
                    // Refresh chart if on 1RM tab to show updated projection
                    if (currentChartType == ChartType.ONE_RM) {
                        setupChart(currentExerciseSets, dateFormat)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun calculateAndDisplayEstimation(sets: List<ExerciseSet>) {
        val settings = settingsManager.getSettings()

        val estimation = OneRMEstimationHelper.estimate1RMProgression(
            sets = sets,
            sessionWorkoutTypes = currentSessionWorkoutTypes,
            projectionMonths = currentProjectionMonths,
            minDataPoints = settings.minimumDataPoints,
            recentDataWindowDays = settings.recentDataWindowDays
        )

        if (estimation == null) {
            binding.cardEstimation.visibility = View.GONE
            return
        }

        binding.cardEstimation.visibility = View.VISIBLE

        // Display current 1RM
        binding.textCurrent1rm.text = String.format(Locale.US, "%.1f kg", estimation.current1RM)

        // Display expected 1RM
        binding.textExpected1rm.text = String.format(Locale.US, "%.1f kg", estimation.expected1RM)

        // Display improvement
        val improvementText = if (estimation.improvementKg >= 0) {
            String.format(Locale.US, "+%.1f kg (%.1f%%)", estimation.improvementKg, estimation.improvementPercent)
        } else {
            String.format(Locale.US, "%.1f kg (%.1f%%)", estimation.improvementKg, estimation.improvementPercent)
        }
        binding.textImprovement.text = improvementText

        // Display projection date
        val projectionDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        binding.textProjectionDate.text = "Projected for ${projectionDateFormat.format(estimation.projectionDate)}"

        // Show warnings if enabled and warnings exist
        if (settings.showWarnings && estimation.warnings.isNotEmpty()) {
            binding.cardWarning.visibility = View.VISIBLE
            binding.textWarning.text = estimation.warnings.joinToString("\n")
            binding.buttonDismissWarning.setOnClickListener {
                binding.cardWarning.visibility = View.GONE
            }
        } else {
            binding.cardWarning.visibility = View.GONE
        }
    }

    private fun showEstimationLogicDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_estimation_logic, null)

        DialogHelper.createBuilder(this)
            .setView(dialogView)
            .setPositiveButton("Got it", null)
            .showWithTransparentWindow()
    }

    private fun showExtendedProjectionDialog() {
        if (currentExerciseSets.isEmpty()) {
            DialogHelper.createBuilder(this)
                .setTitle("No Data")
                .setMessage("Please select an exercise with training history to view the extended projection.")
                .setPositiveButton("OK", null)
                .showWithTransparentWindow()
            return
        }

        val settings = settingsManager.getSettings()
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_extended_projection, null)
        val chart = dialogView.findViewById<LineChart>(R.id.chart_extended_projection)
        val textTitle = dialogView.findViewById<TextView>(R.id.text_dialog_title)

        // Create 6-month projection
        val estimation = OneRMEstimationHelper.estimate1RMProgression(
            sets = currentExerciseSets,
            sessionWorkoutTypes = currentSessionWorkoutTypes,
            projectionMonths = 6,
            minDataPoints = settings.minimumDataPoints,
            recentDataWindowDays = settings.recentDataWindowDays
        )

        if (estimation == null) {
            DialogHelper.createBuilder(this)
                .setTitle("Insufficient Data")
                .setMessage("Not enough data points to generate a 6-month projection.")
                .setPositiveButton("OK", null)
                .showWithTransparentWindow()
            return
        }

        textTitle.text = "6-Month 1RM Projection"

        // Build extended projection data (monthly points)
        val entries = mutableListOf<Entry>()
        val projectionEntries = mutableListOf<Entry>()

        // Historical data points
        val oneRMPerSession = currentExerciseSets.groupBy { it.date }
            .mapNotNull { (dateStr, sessionSets) ->
                val date = try {
                    dateFormat.parse(dateStr)
                } catch (e: Exception) {
                    null
                }
                if (date != null) {
                    val valid1RMs = sessionSets.mapNotNull { OneRMEstimationHelper.calculateOneRM(it.kg, it.reps) }
                    if (valid1RMs.isNotEmpty()) {
                        val max1RM = valid1RMs.maxOrNull() ?: 0f
                        Pair(date, max1RM)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            .sortedBy { it.first }

        val firstDate = oneRMPerSession.first().first
        val lastDate = oneRMPerSession.last().first

        // Add historical points
        oneRMPerSession.forEach { (date, oneRM) ->
            entries.add(Entry(date.time.toFloat(), oneRM))
        }

        // Add projection points (monthly intervals for 6 months)
        val calendar = Calendar.getInstance()
        calendar.time = lastDate
        val today = Date()
        
        // Project current point
        projectionEntries.add(Entry(lastDate.time.toFloat(), estimation.current1RM))

        // Project monthly for 6 months
        for (month in 1..6) {
            calendar.time = lastDate
            calendar.add(Calendar.MONTH, month)
            val projectionDate = calendar.time

            val monthlyEstimation = OneRMEstimationHelper.estimate1RMProgression(
                sets = currentExerciseSets,
                sessionWorkoutTypes = currentSessionWorkoutTypes,
                projectionMonths = month,
                minDataPoints = settings.minimumDataPoints,
                recentDataWindowDays = settings.recentDataWindowDays
            )

            if (monthlyEstimation != null) {
                projectionEntries.add(Entry(projectionDate.time.toFloat(), monthlyEstimation.expected1RM))
            }
        }

        // Configure chart
        setupExtendedProjectionChart(chart, entries, projectionEntries, firstDate)

        DialogHelper.createBuilder(this)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .showWithTransparentWindow()
    }

    private fun setupExtendedProjectionChart(
        chart: LineChart,
        historicalEntries: List<Entry>,
        projectionEntries: List<Entry>,
        firstDate: Date
    ) {
        // Historical data
        val historicalDataSet = LineDataSet(historicalEntries, "Historical 1RM")
        historicalDataSet.color = Color.parseColor("#FF9800")
        historicalDataSet.setCircleColor(Color.parseColor("#FF9800"))
        historicalDataSet.circleRadius = 5f
        historicalDataSet.lineWidth = 3f
        historicalDataSet.setDrawValues(false)
        historicalDataSet.mode = LineDataSet.Mode.CUBIC_BEZIER

        // Projection data
        val projectionDataSet = LineDataSet(projectionEntries, "Projected 1RM")
        projectionDataSet.color = Color.parseColor("#9E9E9E")
        projectionDataSet.setCircleColor(Color.parseColor("#9E9E9E"))
        projectionDataSet.circleRadius = 4f
        projectionDataSet.lineWidth = 2.5f
        projectionDataSet.setDrawValues(false)
        projectionDataSet.mode = LineDataSet.Mode.LINEAR
        projectionDataSet.enableDashedLine(10f, 5f, 0f)

        val lineData = LineData(historicalDataSet, projectionDataSet)
        chart.data = lineData

        // Configure chart appearance
        chart.description.isEnabled = false
        chart.setBackgroundColor(Color.WHITE)
        chart.setDrawGridBackground(false)
        chart.legend.isEnabled = true
        chart.setTouchEnabled(true)
        chart.setDragEnabled(true)
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textSize = 11f
        xAxis.textColor = Color.parseColor("#616161")
        xAxis.setDrawGridLines(true)
        xAxis.gridColor = Color.parseColor("#E0E0E0")
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return try {
                    val date = Date(value.toLong())
                    SimpleDateFormat("MMM\nyyyy", Locale.getDefault()).format(date)
                } catch (e: Exception) {
                    ""
                }
            }
        }

        val leftAxis = chart.axisLeft
        leftAxis.textSize = 11f
        leftAxis.textColor = Color.parseColor("#616161")
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.parseColor("#E0E0E0")
        leftAxis.axisMinimum = 0f

        chart.axisRight.isEnabled = false
        chart.invalidate()
    }
}
