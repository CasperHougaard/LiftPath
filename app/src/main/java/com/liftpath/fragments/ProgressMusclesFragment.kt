package com.liftpath.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.liftpath.R
import com.liftpath.databinding.FragmentProgressMusclesBinding
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.SetMetrics
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.TargetMuscle
import com.liftpath.models.TrainingSession
import java.text.SimpleDateFormat
import java.util.*
import com.liftpath.helpers.lpColor

class ProgressMusclesFragment : Fragment() {

    private var _binding: FragmentProgressMusclesBinding? = null
    private val binding get() = _binding!!
    private lateinit var jsonHelper: JsonHelper
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    /** Week bucket key: sortable and unique across years (Monday start of week). */
    private val weekKeyFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    private val weekAxisLabelFormat = SimpleDateFormat("MMM d", Locale.getDefault())

    private var selectedMuscleGroup = "All"
    private var currentTimeRangeMonths = 3
    private var useWeightedVolume = false

    private val muscleGroupMap = mapOf(
        "All" to TargetMuscle.values().toList(),
        "Chest" to listOf(TargetMuscle.CHEST_UPPER, TargetMuscle.CHEST_MIDDLE, TargetMuscle.CHEST_LOWER),
        "Back" to listOf(TargetMuscle.LATS, TargetMuscle.TRAPS_MID, TargetMuscle.TRAPS_UPPER, TargetMuscle.LOWER_BACK),
        "Shoulders" to listOf(TargetMuscle.DELT_FRONT, TargetMuscle.DELT_SIDE, TargetMuscle.DELT_REAR),
        "Arms" to listOf(TargetMuscle.BICEPS, TargetMuscle.TRICEPS_LONG, TargetMuscle.TRICEPS_LATERAL, TargetMuscle.FOREARMS),
        "Legs" to listOf(TargetMuscle.QUADS, TargetMuscle.HAMSTRINGS, TargetMuscle.GLUTES, TargetMuscle.CALVES),
        "Core" to listOf(TargetMuscle.ABS, TargetMuscle.OBLIQUES)
    )

    /**
     * One distinct hue per body-region category for the grouped bar chart. Resolved lazily
     * (not a top-level constant map) because the actual ARGB values come from the active
     * palette's tokens, not from fixed hex.
     *
     * Every palette aliases several roles to the same literal colour (lpChartVolume ==
     * lpPositive == lpIntentFlush; lpChartFatigue == lpNegative == lpIntentStrength;
     * lpChartRpe == lpIntentBuild; lpNeutral == lpIntentWarmup — see lp_palette_*.xml), so
     * there are only six truly distinct hue families in any palette: green, red, amber,
     * blue, grey, and the palette's own accent. This picks exactly one attr per family;
     * picking two aliases of the same family (an earlier version of this code did) makes
     * two of the six bars in the "All" view visually indistinguishable.
     */
    private fun muscleGroupColors(context: android.content.Context): Map<String, Int> = mapOf(
        "Chest" to context.lpColor(R.attr.lpChartVolume),   // green family
        "Back" to context.lpColor(R.attr.lpChartTime),      // blue family
        "Shoulders" to context.lpColor(R.attr.lpChartRpe),  // amber family
        "Arms" to context.lpColor(R.attr.lpNegative),       // red family
        "Legs" to context.lpColor(R.attr.lpNeutral),        // grey family
        "Core" to context.lpColor(R.attr.lpAccent)          // palette accent
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProgressMusclesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        jsonHelper = JsonHelper(requireContext())

        setupMuscleChips()
        setupTimeRangeSpinner()
        setupWeightedVolumeToggle()
        loadMuscleData()
    }

    private fun setupMuscleChips() {
        binding.chipGroupMuscles.setOnCheckedStateChangeListener { _, checkedIds ->
            // With singleSelection, Material may emit an empty set briefly while switching chips;
            // applying "All" here caused redundant work; ignoring avoids inconsistent chart state.
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            selectedMuscleGroup = when (checkedIds.first()) {
                R.id.chip_all -> "All"
                R.id.chip_chest -> "Chest"
                R.id.chip_back -> "Back"
                R.id.chip_shoulders -> "Shoulders"
                R.id.chip_arms -> "Arms"
                R.id.chip_legs -> "Legs"
                R.id.chip_core -> "Core"
                else -> "All"
            }
            loadMuscleData()
        }
    }

    private fun setupWeightedVolumeToggle() {
        binding.switchWeightedVolume.setOnCheckedChangeListener { _, isChecked ->
            useWeightedVolume = isChecked
            loadMuscleData()
        }
    }

    private fun setupTimeRangeSpinner() {
        val timeRanges = arrayOf("1 month", "3 months", "12 months")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, timeRanges)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTimeRange.adapter = adapter
        binding.spinnerTimeRange.setSelection(1)

        binding.spinnerTimeRange.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                currentTimeRangeMonths = when (position) {
                    0 -> 1
                    1 -> 3
                    2 -> 12
                    else -> 3
                }
                loadMuscleData()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun loadMuscleData() {
        val trainingData = jsonHelper.readTrainingData()
        val sessions = trainingData.trainings
        val exerciseLibrary = trainingData.exerciseLibrary

        val targetMuscles = muscleGroupMap[selectedMuscleGroup] ?: return

        val volumeType = if (useWeightedVolume) getString(R.string.progress_weighted_prefix) else ""
        binding.textMuscleTitle.text = getString(R.string.progress_muscle_volume_title, volumeType, selectedMuscleGroup)
        binding.textMuscleSubtitle.text = getString(R.string.progress_weekly_volume_subtitle, currentTimeRangeMonths)

        // Filter sessions by time range
        val cutoffDate = Calendar.getInstance().apply {
            add(Calendar.MONTH, -currentTimeRangeMonths)
        }.time

        val filteredSessions = sessions.filter { session ->
            try {
                val date = dateFormat.parse(session.date)
                date != null && date.time >= cutoffDate.time
            } catch (e: Exception) {
                false
            }
        }.sortedBy { it.date }

        if (filteredSessions.isEmpty()) {
            binding.textEmptyState.visibility = View.VISIBLE
            binding.chartMuscleVolume.visibility = View.GONE
            binding.cardMuscleStats.visibility = View.GONE
            binding.recyclerContributingExercises.visibility = View.GONE
            binding.textNoExercises.visibility = View.GONE
            return
        }

        binding.textEmptyState.visibility = View.GONE
        binding.chartMuscleVolume.visibility = View.VISIBLE
        binding.cardMuscleStats.visibility = View.VISIBLE

        // Calculate weekly volumes
        if (selectedMuscleGroup == "All") {
            // Calculate volumes for each muscle group separately
            val allMuscleGroups = listOf("Chest", "Back", "Shoulders", "Arms", "Legs", "Core")
            val weeklyVolumesByGroup = allMuscleGroups.associateWith { groupName ->
                val groupMuscles = muscleGroupMap[groupName] ?: emptyList()
                calculateWeeklyVolumes(filteredSessions, groupMuscles, exerciseLibrary, useWeightedVolume)
            }
            setupGroupedMusclesBarChart(weeklyVolumesByGroup)
            // For stats, sum all volumes
            val combinedVolumes = mutableMapOf<String, Float>()
            weeklyVolumesByGroup.values.forEach { volumes ->
                volumes.forEach { (week, volume) ->
                    combinedVolumes[week] = (combinedVolumes[week] ?: 0f) + volume
                }
            }
            updateStats(combinedVolumes)
            // For contributing exercises, use all muscles
            updateContributingExercises(filteredSessions, targetMuscles, exerciseLibrary, useWeightedVolume)
        } else {
            val weeklyVolumes = calculateWeeklyVolumes(filteredSessions, targetMuscles, exerciseLibrary, useWeightedVolume)
            
            if (weeklyVolumes.isEmpty()) {
                binding.textEmptyState.visibility = View.VISIBLE
                binding.chartMuscleVolume.visibility = View.GONE
                binding.cardMuscleStats.visibility = View.GONE
                binding.recyclerContributingExercises.visibility = View.GONE
                binding.textNoExercises.visibility = View.VISIBLE
                return
            }

            setupVolumeChart(weeklyVolumes)
            updateStats(weeklyVolumes)
            updateContributingExercises(filteredSessions, targetMuscles, exerciseLibrary, useWeightedVolume)
        }
    }

    private fun calculateWeeklyVolumes(
        sessions: List<TrainingSession>,
        targetMuscles: List<TargetMuscle>,
        exerciseLibrary: List<ExerciseLibraryItem>,
        useWeighted: Boolean = false
    ): Map<String, Float> {
        val weeklyVolumes = mutableMapOf<String, Float>()
        val calendar = Calendar.getInstance()

        sessions.forEach { session ->
            try {
                val date = dateFormat.parse(session.date) ?: return@forEach
                calendar.time = date
                // Set to start of week (Monday)
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
                calendar.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val weekKey = weekKeyFormat.format(calendar.time)

                val sessionVolume = session.exercises
                    .filterNot { it.isWarmup }
                    .filter { entry ->
                        val exercise = exerciseLibrary.find { it.id == entry.exerciseId }
                        exercise?.let { ex ->
                            ex.primaryTargets.any { it in targetMuscles } ||
                            ex.secondaryTargets.any { it in targetMuscles }
                        } ?: false
                    }
                    .sumOf { entry ->
                        // Timed holds carry no reps; RPE-weighting them would multiply by RIR
                        // alone and invent volume out of nothing.
                        if (entry.isTimedEntry()) {
                            0.0
                        } else if (useWeighted && entry.rpe != null) {
                            val rir = 10f - entry.rpe
                            val effectiveReps = entry.reps + rir
                            (entry.kg * effectiveReps).toDouble()
                        } else {
                            SetMetrics.volumeKg(entry).toDouble()
                        }
                    }
                    .toFloat()

                weeklyVolumes[weekKey] = (weeklyVolumes[weekKey] ?: 0f) + sessionVolume
            } catch (e: Exception) {
                // Skip invalid dates
            }
        }

        return weeklyVolumes
    }

    private fun formatWeekAxisLabel(weekKey: String): String =
        try {
            weekAxisLabelFormat.format(weekKeyFormat.parse(weekKey)!!)
        } catch (_: Exception) {
            weekKey
        }

    private fun setupVolumeChart(weeklyVolumes: Map<String, Float>) {
        val sortedWeeks = weeklyVolumes.keys.sorted()
        val entries = sortedWeeks.mapIndexed { index, week ->
            BarEntry(index.toFloat(), weeklyVolumes[week] ?: 0f)
        }

        val ctx = requireContext()
        val dataSet = BarDataSet(entries, "Volume (kg)").apply {
            color = ctx.lpColor(R.attr.lpAccent)
            setDrawValues(false)
        }

        val barData = BarData(dataSet).apply {
            barWidth = 0.7f
        }

        binding.chartMuscleVolume.apply {
            clear()
            data = barData
            description.isEnabled = false
            setBackgroundColor(ctx.lpColor(R.attr.lpSurface))
            setDrawGridBackground(false)
            legend.isEnabled = false

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = ctx.lpColor(R.attr.lpInkTertiary)
                labelRotationAngle = -45f
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        if (index !in sortedWeeks.indices) return ""
                        return formatWeekAxisLabel(sortedWeeks[index])
                    }
                }
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = ctx.lpColor(R.attr.lpChartGrid)
                textColor = ctx.lpColor(R.attr.lpInkTertiary)
                axisMinimum = 0f
            }

            axisRight.isEnabled = false

            setTouchEnabled(true)
            setDragEnabled(true)
            setScaleEnabled(true)
            animateY(800)
            invalidate()
        }
    }

    private fun setupGroupedMusclesBarChart(weeklyVolumesByGroup: Map<String, Map<String, Float>>) {
        val allWeeks = weeklyVolumesByGroup.values.flatMap { it.keys }.distinct().sorted()
        if (allWeeks.isEmpty()) {
            binding.textEmptyState.visibility = View.VISIBLE
            binding.chartMuscleVolume.visibility = View.GONE
            return
        }

        val ctx = requireContext()
        val groupColors = muscleGroupColors(ctx)
        val muscleGroups = listOf("Chest", "Back", "Shoulders", "Arms", "Legs", "Core")
        val barDataSets = muscleGroups.map { groupName ->
            val volumes = weeklyVolumesByGroup[groupName] ?: emptyMap()
            val entries = allWeeks.mapIndexed { index, week ->
                BarEntry(index.toFloat(), volumes[week] ?: 0f)
            }
            val color = groupColors[groupName] ?: ctx.lpColor(R.attr.lpAccent)
            BarDataSet(entries, groupName).apply {
                this.color = color
                setDrawValues(false)
                axisDependency = YAxis.AxisDependency.LEFT
            }
        }

        val barData = BarData(barDataSets).apply {
            // 6 * barWidth + 5 * barSpace + groupSpace == 1 per MPAndroidChart grouped bars.
            barWidth = 0.11f
            groupBars(0f, 0.24f, 0.02f)
        }

        binding.chartMuscleVolume.apply {
            clear()
            data = barData
            description.isEnabled = false
            setBackgroundColor(ctx.lpColor(R.attr.lpSurface))
            setDrawGridBackground(false)
            legend.isEnabled = true
            legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
            legend.orientation = Legend.LegendOrientation.HORIZONTAL
            legend.setDrawInside(false)
            legend.textSize = 11f
            legend.textColor = ctx.lpColor(R.attr.lpInkSecondary)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = ctx.lpColor(R.attr.lpInkTertiary)
                labelRotationAngle = -45f
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        if (index !in allWeeks.indices) return ""
                        return formatWeekAxisLabel(allWeeks[index])
                    }
                }
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = ctx.lpColor(R.attr.lpChartGrid)
                textColor = ctx.lpColor(R.attr.lpInkTertiary)
                axisMinimum = 0f
            }

            axisRight.isEnabled = false

            setTouchEnabled(true)
            setDragEnabled(true)
            setScaleEnabled(true)
            animateY(800)
            invalidate()
        }
    }

    private fun updateStats(weeklyVolumes: Map<String, Float>) {
        val totalVolume = weeklyVolumes.values.sum()
        val avgWeekly = if (weeklyVolumes.isNotEmpty()) totalVolume / weeklyVolumes.size else 0f

        binding.textTotalVolume.text = String.format(Locale.US, "%,.0f", totalVolume)
        binding.textAvgWeekly.text = String.format(Locale.US, "%,.0f", avgWeekly)

        // Calculate trend
        val volumes = weeklyVolumes.values.toList()
        if (volumes.size >= 2) {
            val firstHalf = volumes.take(volumes.size / 2).average()
            val secondHalf = volumes.drop(volumes.size / 2).average()
            val trend = if (firstHalf > 0) ((secondHalf - firstHalf) / firstHalf) * 100 else 0.0
            binding.textTrend.text = String.format(Locale.US, "%+.0f%%", trend)
            val ctx = requireContext()
            binding.textTrend.setTextColor(when {
                trend > 5 -> ctx.lpColor(R.attr.lpPositive)
                trend < -5 -> ctx.lpColor(R.attr.lpNegative)
                else -> ctx.lpColor(R.attr.lpInkTertiary)
            })
        } else {
            binding.textTrend.text = "--"
        }
    }

    private fun updateContributingExercises(
        sessions: List<TrainingSession>,
        targetMuscles: List<TargetMuscle>,
        exerciseLibrary: List<ExerciseLibraryItem>,
        useWeighted: Boolean = false
    ) {
        // Calculate total volume per exercise for this muscle group
        // Map: exerciseId -> (exerciseName, totalVolume)
        val exerciseVolumes = mutableMapOf<Int, Pair<String, Float>>()
        
        sessions.forEach { session ->
            session.exercises
                .filterNot { it.isWarmup }
                .forEach { entry ->
                    val exercise = exerciseLibrary.find { it.id == entry.exerciseId }
                    if (exercise != null) {
                        val targetsMuscle = exercise.primaryTargets.any { it in targetMuscles } ||
                                exercise.secondaryTargets.any { it in targetMuscles }
                        
                        if (targetsMuscle) {
                            val volume = if (entry.isTimedEntry()) {
                                0f
                            } else if (useWeighted && entry.rpe != null) {
                                val rir = 10f - entry.rpe
                                val effectiveReps = entry.reps + rir
                                entry.kg * effectiveReps
                            } else {
                                SetMetrics.volumeKg(entry)
                            }
                            val current = exerciseVolumes[entry.exerciseId]
                            exerciseVolumes[entry.exerciseId] = Pair(
                                exercise.name,
                                (current?.second ?: 0f) + volume
                            )
                        }
                    }
                }
        }
        
        // Sort by volume descending and take top 5
        // Convert to list of (exerciseId, exerciseName, volume) tuples
        val topExercises = exerciseVolumes.entries
            .sortedByDescending { it.value.second }
            .take(5)
            .map { (exerciseId, nameVolume) -> Triple(exerciseId, nameVolume.first, nameVolume.second) }
        
        if (topExercises.isEmpty()) {
            binding.recyclerContributingExercises.visibility = View.GONE
            binding.textNoExercises.visibility = View.VISIBLE
        } else {
            binding.recyclerContributingExercises.visibility = View.VISIBLE
            binding.textNoExercises.visibility = View.GONE
            
            // Use ExerciseTrendAdapter with simplified data
            val trends = topExercises.map { (exerciseId, name, volume) ->
                com.liftpath.models.ExerciseTrendData(
                    exerciseId = exerciseId,
                    exerciseName = name,
                    intent = com.liftpath.models.SetIntent.BUILD,
                    currentVolume = volume,
                    previousVolume = null,
                    currentEstimated1RM = null,
                    previousEstimated1RM = null,
                    currentTopSet = null,
                    previousTopSet = null,
                    hasNewAllTimePR = false,
                    intentSessionCount = 0,
                    prWeight = null,
                    prWeightDate = 0L,
                    prVolume = null,
                    prVolumeDate = 0L,
                    pr1RM = null,
                    pr1RMDate = 0L
                )
            }
            
            val adapter = com.liftpath.adapters.ExerciseTrendAdapter(trends)
            binding.recyclerContributingExercises.layoutManager = LinearLayoutManager(requireContext())
            binding.recyclerContributingExercises.adapter = adapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
