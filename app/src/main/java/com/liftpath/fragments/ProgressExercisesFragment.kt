package com.liftpath.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.liftpath.R
import com.liftpath.databinding.FragmentProgressExercisesBinding
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.OneRMEstimationHelper
import com.liftpath.helpers.ProgressSettingsManager
import com.liftpath.models.ExerciseSet
import com.liftpath.models.SetIntent
import java.text.SimpleDateFormat
import java.util.*

class ProgressExercisesFragment : Fragment() {

    private var _binding: FragmentProgressExercisesBinding? = null
    private val binding get() = _binding!!
    private lateinit var jsonHelper: JsonHelper
    private lateinit var settingsManager: ProgressSettingsManager
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    
    private var currentTimeRangeMonths = 3
    private var currentProjectionMonths = 3
    private var showStrength = true
    private var showBuild = true
    private var showFlush = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProgressExercisesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        jsonHelper = JsonHelper(requireContext())
        settingsManager = ProgressSettingsManager(requireContext())
        
        setupSpinners()
        setupIntentFilterChips()
    }

    private fun setupSpinners() {
        // Exercise spinner
        val trainingData = jsonHelper.readTrainingData()
        val exerciseNames = trainingData.trainings
            .flatMap { it.exercises }
            .map { it.exerciseName }
            .distinct()
            .sorted()

        if (exerciseNames.isEmpty()) {
            binding.textEmptyState.visibility = View.VISIBLE
            binding.chartCombined.visibility = View.GONE
            return
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, exerciseNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerExercise.adapter = adapter

        binding.spinnerExercise.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedExercise = exerciseNames[position]
                loadExerciseData(selectedExercise)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Time range spinner
        val timeRanges = arrayOf("1 month", "3 months", "12 months")
        val timeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, timeRanges)
        timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTimeRange.adapter = timeAdapter
        binding.spinnerTimeRange.setSelection(1) // Default to 3 months

        binding.spinnerTimeRange.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                currentTimeRangeMonths = when (position) {
                    0 -> 1
                    1 -> 3
                    2 -> 12
                    else -> 3
                }
                val selectedExercise = binding.spinnerExercise.selectedItem?.toString()
                if (selectedExercise != null) {
                    loadExerciseData(selectedExercise)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Projection period spinner
        val projectionPeriods = arrayOf("1 month", "2 months", "3 months", "6 months")
        val projAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, projectionPeriods)
        projAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerProjectionPeriod.adapter = projAdapter
        binding.spinnerProjectionPeriod.setSelection(2) // Default to 3 months

        binding.spinnerProjectionPeriod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                currentProjectionMonths = when (position) {
                    0 -> 1
                    1 -> 2
                    2 -> 3
                    3 -> 6
                    else -> 3
                }
                val selectedExercise = binding.spinnerExercise.selectedItem?.toString()
                if (selectedExercise != null) {
                    updateEstimation(selectedExercise)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupIntentFilterChips() {
        binding.chipStrength.setOnCheckedChangeListener { _, isChecked ->
            showStrength = isChecked
            refreshChart()
        }
        binding.chipBuild.setOnCheckedChangeListener { _, isChecked ->
            showBuild = isChecked
            refreshChart()
        }
        binding.chipFlush.setOnCheckedChangeListener { _, isChecked ->
            showFlush = isChecked
            refreshChart()
        }
    }

    private fun refreshChart() {
        val selectedExercise = binding.spinnerExercise.selectedItem?.toString()
        if (selectedExercise != null) {
            loadExerciseData(selectedExercise)
        }
    }

    private fun loadExerciseData(exerciseName: String) {
        val trainingData = jsonHelper.readTrainingData()
        
        // Group sets by date and intent
        val metricsByDateAndIntent = mutableMapOf<String, MutableMap<SetIntent, MutableList<ExerciseSet>>>()
        val sessionWorkoutTypes = mutableMapOf<String, String>()

        trainingData.trainings.forEach { session ->
            sessionWorkoutTypes[session.date] = session.defaultWorkoutType ?: "heavy"
            
            session.exercises
                .filter { it.exerciseName == exerciseName && !it.isWarmup }
                .forEach { entry ->
                    val intent = entry.getEffectiveIntent(session.defaultWorkoutType)
                    val exerciseSet = ExerciseSet(
                        date = session.date,
                        setNumber = entry.setNumber,
                        kg = entry.kg,
                        reps = entry.reps,
                        rpe = entry.rpe
                    )
                    
                    metricsByDateAndIntent
                        .getOrPut(session.date) { mutableMapOf() }
                        .getOrPut(intent) { mutableListOf() }
                        .add(exerciseSet)
                }
        }

        if (metricsByDateAndIntent.isEmpty()) {
            binding.textEmptyState.visibility = View.VISIBLE
            binding.chartCombined.visibility = View.GONE
            return
        }

        binding.textEmptyState.visibility = View.GONE
        binding.chartCombined.visibility = View.VISIBLE

        // Apply time range filter
        val cutoffDate = Calendar.getInstance().apply {
            add(Calendar.MONTH, -currentTimeRangeMonths)
        }.time

        val filteredDates = metricsByDateAndIntent.keys.filter { dateStr ->
            try {
                val date = dateFormat.parse(dateStr)
                date != null && date.time >= cutoffDate.time
            } catch (e: Exception) {
                false
            }
        }.sorted()

        // Calculate stats per intent
        updateIntentStats(metricsByDateAndIntent, filteredDates)
        
        // Setup combined chart
        setupCombinedChart(metricsByDateAndIntent, filteredDates, sessionWorkoutTypes)
        
        // Update estimation
        updateEstimation(exerciseName)
        
        // Update trends
        updateTrends(metricsByDateAndIntent, filteredDates, sessionWorkoutTypes)
    }

    private fun updateIntentStats(
        metrics: Map<String, Map<SetIntent, List<ExerciseSet>>>,
        filteredDates: List<String>
    ) {
        // Strength stats
        val strengthSessions = filteredDates.count { date ->
            metrics[date]?.containsKey(SetIntent.STRENGTH) == true
        }
        val strength1RMs = filteredDates.mapNotNull { date ->
            metrics[date]?.get(SetIntent.STRENGTH)?.mapNotNull { set ->
                OneRMEstimationHelper.calculateOneRM(set.kg, set.reps, set.rpe)
            }?.maxOrNull()
        }
        val currentStrength1RM = strength1RMs.lastOrNull()
        
        if (strengthSessions > 0 && currentStrength1RM != null) {
            binding.cardStrengthStats.visibility = View.VISIBLE
            binding.textStrength1rm.text = String.format(Locale.US, "%.1fkg", currentStrength1RM)
            binding.textStrengthSessions.text = "$strengthSessions sessions"
        } else {
            binding.cardStrengthStats.visibility = View.GONE
        }

        // Build stats
        val buildSessions = filteredDates.count { date ->
            metrics[date]?.containsKey(SetIntent.BUILD) == true
        }
        val buildVolumes = filteredDates.mapNotNull { date ->
            metrics[date]?.get(SetIntent.BUILD)?.sumOf { (it.kg * it.reps).toDouble() }?.toFloat()
        }
        val avgBuildVolume = if (buildVolumes.isNotEmpty()) buildVolumes.average() else null

        if (buildSessions > 0 && avgBuildVolume != null) {
            binding.cardBuildStats.visibility = View.VISIBLE
            binding.textBuildVolume.text = String.format(Locale.US, "%,.0fkg", avgBuildVolume)
            binding.textBuildSessions.text = "$buildSessions sessions"
        } else {
            binding.cardBuildStats.visibility = View.GONE
        }

        // Flush stats
        val flushSessions = filteredDates.count { date ->
            metrics[date]?.containsKey(SetIntent.FLUSH) == true
        }
        val flushReps = filteredDates.mapNotNull { date ->
            metrics[date]?.get(SetIntent.FLUSH)?.sumOf { it.reps }
        }
        val totalFlushReps = flushReps.sum()

        if (flushSessions > 0 && totalFlushReps > 0) {
            binding.cardFlushStats.visibility = View.VISIBLE
            binding.textFlushReps.text = totalFlushReps.toString()
            binding.textFlushSessions.text = "$flushSessions sessions"
        } else {
            binding.cardFlushStats.visibility = View.GONE
        }
    }

    private fun setupCombinedChart(
        metrics: Map<String, Map<SetIntent, List<ExerciseSet>>>,
        filteredDates: List<String>,
        sessionWorkoutTypes: Map<String, String>
    ) {
        // Clear old state before building new data so stale highlights/animations are removed.
        binding.chartCombined.clear()

        val lineDataSets = mutableListOf<com.github.mikephil.charting.interfaces.datasets.ILineDataSet>()
        val barEntries = mutableListOf<BarEntry>()
        var hasLeftAxisData = false
        var hasRightAxisData = false

        // Strength 1RM Line (if enabled)
        if (showStrength) {
            val strengthEntries = mutableListOf<Entry>()
            filteredDates.forEachIndexed { index, dateStr ->
                val strengthSets = metrics[dateStr]?.get(SetIntent.STRENGTH)
                if (strengthSets != null && strengthSets.isNotEmpty()) {
                    val max1RM = strengthSets.mapNotNull { set ->
                        OneRMEstimationHelper.calculateOneRM(set.kg, set.reps, set.rpe)
                    }.maxOrNull()
                    if (max1RM != null) {
                        strengthEntries.add(Entry(index.toFloat(), max1RM))
                    }
                }
            }
            if (strengthEntries.isNotEmpty()) {
                hasLeftAxisData = true
                val strengthDataSet = LineDataSet(strengthEntries, "1RM (Strength)").apply {
                    color = Color.parseColor("#DC2626")
                    setCircleColor(Color.parseColor("#DC2626"))
                    circleRadius = 4f
                    lineWidth = 2.5f
                    setDrawValues(false)
                    axisDependency = YAxis.AxisDependency.LEFT
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                }
                lineDataSets.add(strengthDataSet)
            }
        }

        // Build Volume Bars (if enabled)
        if (showBuild) {
            filteredDates.forEachIndexed { index, dateStr ->
                val buildSets = metrics[dateStr]?.get(SetIntent.BUILD)
                if (buildSets != null && buildSets.isNotEmpty()) {
                    val volume = buildSets.sumOf { (it.kg * it.reps).toDouble() }.toFloat()
                    barEntries.add(BarEntry(index.toFloat(), volume))
                }
            }
            if (barEntries.isNotEmpty()) hasRightAxisData = true
        }

        // Flush Reps Line (if enabled)
        if (showFlush) {
            val flushEntries = mutableListOf<Entry>()
            filteredDates.forEachIndexed { index, dateStr ->
                val flushSets = metrics[dateStr]?.get(SetIntent.FLUSH)
                if (flushSets != null && flushSets.isNotEmpty()) {
                    val totalReps = flushSets.sumOf { it.reps }.toFloat()
                    flushEntries.add(Entry(index.toFloat(), totalReps))
                }
            }
            if (flushEntries.isNotEmpty()) {
                hasRightAxisData = true
                val flushDataSet = LineDataSet(flushEntries, "Reps (Flush)").apply {
                    color = Color.parseColor("#10B981")
                    setCircleColor(Color.parseColor("#10B981"))
                    circleRadius = 4f
                    lineWidth = 2.5f
                    setDrawValues(false)
                    axisDependency = YAxis.AxisDependency.RIGHT
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                }
                lineDataSets.add(flushDataSet)
            }
        }

        if (lineDataSets.isEmpty() && barEntries.isEmpty()) {
            binding.chartCombined.visibility = View.GONE
            binding.textEmptyState.visibility = View.VISIBLE
            binding.textEmptyState.text = "No data available for selected intents"
            return
        }

        binding.chartCombined.visibility = View.VISIBLE
        binding.textEmptyState.visibility = View.GONE

        val combinedData = CombinedData()

        // Always set LineData (even empty) so LineChartRenderer.drawData() never gets null lineData.
        combinedData.setData(LineData(lineDataSets.toList()))

        // CRITICAL: BarLineChartBase.notifyDataSetChanged() calls mRenderer.initBuffers() BEFORE
        // CombinedChart.setData() calls createRenderers(). If the old mRenderers still contains a
        // BarChartRenderer from a previous load but the new CombinedData has no BarData, then
        // BarChartRenderer.initBuffers() calls chart.getBarData().getDataSetCount() → NPE crash.
        // Fix: always provide a non-null BarData (empty when no bars) so getBarData() ≠ null.
        if (barEntries.isNotEmpty()) {
            combinedData.setData(BarData(BarDataSet(barEntries, "Volume (Build)").apply {
                color = Color.parseColor("#F59E0B")
                setDrawValues(false)
                axisDependency = YAxis.AxisDependency.RIGHT
            }).apply { barWidth = 0.4f })
        } else {
            combinedData.setData(BarData())
        }

        binding.chartCombined.apply {
            data = combinedData
            description.isEnabled = false
            setBackgroundColor(Color.WHITE)
            setDrawGridBackground(false)
            legend.isEnabled = true
            legend.textSize = 11f

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
                labelRotationAngle = -45f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        if (index in filteredDates.indices) {
                            return try {
                                val date = dateFormat.parse(filteredDates[index])
                                SimpleDateFormat("MM/dd", Locale.getDefault()).format(date!!)
                            } catch (e: Exception) { "" }
                        }
                        return ""
                    }
                }
            }

            // notifyDataSetChanged() (called inside setData above) unconditionally calls
            // computeAxis(mAxisMinimum, mAxisMaximum) on both axes regardless of isEnabled.
            // If an axis has no data its range is (Float.MAX_VALUE, -Float.MAX_VALUE) — reversed.
            // That reversed range propagates to NaN/overflow inside computeAxisValues(), which can
            // cause a NegativeArraySizeException when allocating the label array.
            // Fix: when an axis carries no real data, give it a valid dummy range [0, 100].
            axisLeft.isEnabled = hasLeftAxisData
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
                axisMinimum = 0f
                if (hasLeftAxisData) resetAxisMaximum() else axisMaximum = 100f
                setScaleEnabled(false)
            }

            axisRight.isEnabled = hasRightAxisData
            axisRight.apply {
                setDrawGridLines(false)
                axisMinimum = 0f
                if (hasRightAxisData) resetAxisMaximum() else axisMaximum = 100f
                setScaleEnabled(false)
            }

            setTouchEnabled(false)
            setDragEnabled(false)
            setScaleEnabled(false)
            setPinchZoom(false)
            setDoubleTapToZoomEnabled(false)

            if (filteredDates.isNotEmpty()) {
                val dataCount = filteredDates.size.toFloat()
                val barWidth = 0.4f
                val extraSpace = (barWidth * 2f) + 0.2f
                xAxis.axisMinimum = -extraSpace
                xAxis.axisMaximum = dataCount - 1f + extraSpace
                fitScreen()
            }

            animateX(800)
            invalidate()
        }
    }

    private fun updateEstimation(exerciseName: String) {
        val trainingData = jsonHelper.readTrainingData()
        val allSets = mutableListOf<ExerciseSet>()
        val sessionWorkoutTypes = mutableMapOf<String, String>()

        trainingData.trainings.forEach { session ->
            sessionWorkoutTypes[session.date] = session.defaultWorkoutType ?: "heavy"
            session.exercises
                .filter { it.exerciseName == exerciseName && !it.isWarmup }
                .filter { it.getEffectiveIntent(session.defaultWorkoutType) == SetIntent.STRENGTH }
                .forEach { entry ->
                    allSets.add(ExerciseSet(
                        date = session.date,
                        setNumber = entry.setNumber,
                        kg = entry.kg,
                        reps = entry.reps,
                        rpe = entry.rpe
                    ))
                }
        }

        val settings = settingsManager.getSettings()
        val estimation = OneRMEstimationHelper.estimate1RMProgression(
            sets = allSets,
            sessionWorkoutTypes = sessionWorkoutTypes,
            projectionMonths = currentProjectionMonths,
            minDataPoints = settings.minimumDataPoints,
            recentDataWindowDays = settings.recentDataWindowDays
        )

        if (estimation == null) {
            binding.cardEstimation.visibility = View.GONE
            return
        }

        binding.cardEstimation.visibility = View.VISIBLE
        binding.textCurrent1rm.text = String.format(Locale.US, "%.1f kg", estimation.current1RM)
        binding.textExpected1rm.text = String.format(Locale.US, "%.1f kg", estimation.expected1RM)

        val improvementText = if (estimation.improvementKg >= 0) {
            String.format(Locale.US, "+%.1f kg (%.1f%%)", estimation.improvementKg, estimation.improvementPercent)
        } else {
            String.format(Locale.US, "%.1f kg (%.1f%%)", estimation.improvementKg, estimation.improvementPercent)
        }
        binding.textImprovement.text = improvementText

        val projectionDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        binding.textProjectionDate.text = "Projected for ${projectionDateFormat.format(estimation.projectionDate)}"
    }

    private fun updateTrends(
        metrics: Map<String, Map<SetIntent, List<ExerciseSet>>>,
        filteredDates: List<String>,
        sessionWorkoutTypes: Map<String, String>
    ) {
        if (filteredDates.size < 4) {
            binding.cardTrendAnalysis.visibility = View.GONE
            return
        }

        binding.cardTrendAnalysis.visibility = View.VISIBLE

        // Calculate volume trend
        val volumes = filteredDates.mapNotNull { date ->
            metrics[date]?.values?.flatten()?.sumOf { (it.kg * it.reps).toDouble() }?.toFloat()
        }
        
        if (volumes.size >= 4) {
            val volumeTrend = calculateSimpleTrend(volumes)
            binding.textVolumeTrend.text = formatTrend(volumeTrend)
            binding.textVolumeTrend.setTextColor(getTrendColor(volumeTrend))
        } else {
            binding.textVolumeTrend.text = "--"
        }

        // Calculate strength trend (1RM)
        val strength1RMs = filteredDates.mapNotNull { date ->
            metrics[date]?.get(SetIntent.STRENGTH)?.mapNotNull { set ->
                OneRMEstimationHelper.calculateOneRM(set.kg, set.reps, set.rpe)
            }?.maxOrNull()
        }

        if (strength1RMs.size >= 4) {
            val strengthTrend = calculateSimpleTrend(strength1RMs)
            binding.textStrengthTrend.text = formatTrend(strengthTrend)
            binding.textStrengthTrend.setTextColor(getTrendColor(strengthTrend))
        } else {
            binding.textStrengthTrend.text = "--"
        }

        // Efficiency trend (Build intent: volume/RPE per session)
        val buildSets = filteredDates.flatMap { date ->
            metrics[date]?.get(SetIntent.BUILD) ?: emptyList()
        }
        val efficiencyByDate = OneRMEstimationHelper.calculateEfficiencyPerSession(buildSets, sessionWorkoutTypes)
        val efficiencies = filteredDates.mapNotNull { efficiencyByDate[it] }

        if (efficiencies.size >= 4) {
            val efficiencyTrend = calculateSimpleTrend(efficiencies)
            binding.textEfficiencyTrend.text = formatTrend(efficiencyTrend)
            binding.textEfficiencyTrend.setTextColor(getTrendColor(efficiencyTrend))
        } else {
            binding.textEfficiencyTrend.text = "--"
        }
    }

    private fun calculateSimpleTrend(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val firstHalf = values.take(values.size / 2).average().toFloat()
        val secondHalf = values.drop(values.size / 2).average().toFloat()
        return if (firstHalf > 0) ((secondHalf - firstHalf) / firstHalf) * 100f else 0f
    }

    private fun formatTrend(percentage: Float): String {
        return when {
            percentage > 5 -> String.format(Locale.US, "Up +%.0f%%", percentage)
            percentage < -5 -> String.format(Locale.US, "Down %.0f%%", percentage)
            else -> "Stable"
        }
    }

    private fun getTrendColor(percentage: Float): Int {
        return when {
            percentage > 5 -> Color.parseColor("#10B981")
            percentage < -5 -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#6B7280")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
