package com.liftpath.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.liftpath.R
import com.liftpath.databinding.ActivityReadinessDashboardBinding
import com.liftpath.databinding.ItemReadinessChannelRowBinding
import com.liftpath.databinding.ItemReadinessDayBinding
import com.liftpath.databinding.ItemReadinessDriverBinding
import com.liftpath.databinding.ItemReadinessFatigueRowBinding
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.ReadinessConfig
import com.liftpath.helpers.ReadinessHelper
import com.liftpath.helpers.FatigueScores
import com.liftpath.helpers.FatigueValues
import com.liftpath.helpers.ActivityReadiness
import com.liftpath.helpers.ActivityStatus
import com.liftpath.helpers.FatigueTimeline
import com.liftpath.helpers.ReadinessSettingsManager
import com.liftpath.helpers.HealthConnectHelper
import com.liftpath.helpers.ExternalActivity
import com.liftpath.helpers.ExternalLoadProvider
import com.liftpath.helpers.ReadinessPresentation
import com.liftpath.helpers.TriPathConnection
import com.liftpath.helpers.TriPathContract
import com.liftpath.helpers.TriPathDay
import com.liftpath.helpers.TriPathReadiness
import com.liftpath.helpers.TriPathStorage
import com.liftpath.helpers.TriPathStorageHelper
import com.liftpath.helpers.TriPathSyncHelper
import com.liftpath.models.TrainingSession
import com.liftpath.models.TrainingData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import android.content.SharedPreferences
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import com.google.gson.GsonBuilder
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import com.liftpath.helpers.lpColor

class ReadinessDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReadinessDashboardBinding
    private lateinit var jsonHelper: JsonHelper
    private lateinit var settingsManager: ReadinessSettingsManager
    private lateinit var healthConnectPrefs: SharedPreferences
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    private val HEALTH_CONNECT_ENABLED_KEY = "use_health_connect_data"
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private var fatigueTimeline: FatigueTimeline? = null

    private val exportDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            exportFatigueData(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReadinessDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jsonHelper = JsonHelper(this)
        settingsManager = ReadinessSettingsManager(this)
        healthConnectPrefs = getSharedPreferences("health_connect_settings", MODE_PRIVATE)

        setupClickListeners()
        setupHealthConnectToggle()
        loadReadinessData()
        updateTriPathCard()

        // Auto-sync external sources in the background
        autoSyncHealthConnect()
        autoSyncTriPath()
    }

    override fun onResume() {
        super.onResume()
        // Reload data when returning (e.g., from calibration settings)
        loadReadinessData()
        updateTriPathCard()
        startCountdownUpdates()

        // Auto-sync external sources in the background
        autoSyncHealthConnect()
        autoSyncTriPath()
    }

    override fun onPause() {
        super.onPause()
        stopCountdownUpdates()
    }

    private fun setupClickListeners() {
        binding.buttonBack.setOnClickListener {
            finish()
        }

        binding.buttonSettings.setOnClickListener {
            val intent = Intent(this, ReadinessCalibrationActivity::class.java)
            startActivity(intent)
        }

        binding.layoutCalendarDays.setOnClickListener {
            toggleCalendarChart()
        }

        binding.buttonExportFatigueData.setOnClickListener {
            exportFatigueDataToFile()
        }

        binding.buttonHealthConnect.setOnClickListener {
            val intent = Intent(this, HealthConnectActivity::class.java)
            startActivity(intent)
        }

        // The switch is small and the row is 56dp tall; tapping anywhere on it toggles.
        binding.rowUseHealthConnect.setOnClickListener {
            binding.switchUseHealthConnect.toggle()
        }

        binding.switchUseHealthConnect.setOnCheckedChangeListener { _, isChecked ->
            healthConnectPrefs.edit().putBoolean(HEALTH_CONNECT_ENABLED_KEY, isChecked).apply()
            // Reload data when toggle changes
            loadReadinessData()
        }
    }

    private fun setupHealthConnectToggle() {
        val isEnabled = healthConnectPrefs.getBoolean(HEALTH_CONNECT_ENABLED_KEY, false)
        binding.switchUseHealthConnect.isChecked = isEnabled
    }

    private var isCalendarExpanded = false

    private fun setupCalendarView() {
        var trainingData = jsonHelper.readTrainingData()
        val settings = settingsManager.getSettings()
        val config = ExternalLoadProvider.readinessConfig(applicationContext, settings)

        // Use mock data if no real data exists
        if (trainingData.trainings.isEmpty()) {
            trainingData = ReadinessHelper.createMockTrainingData()
        }

        // Load external activities from stored JSON (Health Connect and/or TriPath, deduplicated)
        lifecycleScope.launch(Dispatchers.IO) {
            val externalActivities = ExternalLoadProvider.getExternalActivities(applicationContext)

            // Calculate continuous fatigue timeline with external activities
            val timeline = ReadinessHelper.calculateContinuousFatigueTimeline(
                trainingData,
                config,
                externalActivities
            )

            withContext(Dispatchers.Main) {
                fatigueTimeline = timeline
                // Update activity readiness tiles with timeline that includes Health Connect data
                val settings = settingsManager.getSettings()
                val config = ExternalLoadProvider.readinessConfig(applicationContext, settings)
                updateCalendarWithTimeline(timeline, config)
                val currentFatigue = ReadinessHelper.getCurrentFatigueFromTimeline(timeline, config)
                updateActivityReadiness(currentFatigue, config)
            }
        }
    }

    private fun updateCalendarWithTimeline(timeline: FatigueTimeline, config: ReadinessConfig) {
        // Extract daily end values for calendar
        val dailyEndValues = timeline.dailyEndValues

        // Get last 7 days including today (today is the 7th day)
        val calendar = Calendar.getInstance()
        val days = mutableListOf<Pair<String, Float>>() // Date string to fatigue at end of day

        // 6 days ago to today (7 days total, with today as the 7th)
        for (i in 6 downTo 0) {
            calendar.time = Date()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            val dateStr = dateFormat.format(calendar.time)

            // Get fatigue at end of day from timeline (using systemic fatigue for calendar display)
            val fatigueValues = dailyEndValues[dateStr] ?: FatigueValues(0f, 0f, 0f)
            val fatigue = fatigueValues.systemicFatigue
            days.add(Pair(dateStr, fatigue))
        }

        // Create day cells
        binding.layoutCalendarDays.removeAllViews()
        days.forEach { (dateStr, fatigue) ->
            binding.layoutCalendarDays.addView(createDayCell(dateStr, fatigue, config.thresholds))
        }

        // Always show calendar (even if no workouts, shows empty days)
        binding.cardCalendar.visibility = View.VISIBLE
    }

    /**
     * One day of the 7-day strip.
     *
     * The dot was an HSV interpolation from hue 120 to hue 0, with saturation and brightness
     * branched on dark mode. That produced the same green in all four palettes and read as a
     * different app's colour in three of them — and it scored against a fixed 0-80 range rather
     * than the athlete's calibrated thresholds, so a novice's normal Tuesday came out red. It is
     * now a token, picked by [ReadinessPresentation.fatigueColorAttr] against those thresholds.
     */
    private fun createDayCell(
        dateStr: String,
        fatigue: Float,
        thresholds: ReadinessConfig.Thresholds
    ): View {
        val date = dateFormat.parse(dateStr) ?: Date()
        val cal = Calendar.getInstance().apply { time = date }
        val isToday = dateStr == dateFormat.format(Date())

        val dayAbbr = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "S"
            Calendar.MONDAY -> "M"
            Calendar.TUESDAY -> "T"
            Calendar.WEDNESDAY -> "W"
            Calendar.THURSDAY -> "T"
            Calendar.FRIDAY -> "F"
            Calendar.SATURDAY -> "S"
            else -> ""
        }

        val cell = ItemReadinessDayBinding.inflate(layoutInflater, binding.layoutCalendarDays, false)
        cell.root.layoutParams = LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        )

        cell.textDayLetter.text = dayAbbr
        cell.textDayNumber.text = cal.get(Calendar.DAY_OF_MONTH).toString()
        cell.textDayFatigue.text = String.format(Locale.getDefault(), "%.0f", fatigue)

        // Today is the only cell that gets the accent, and only on its letter — it marks *where*
        // you are, which is a different job from the dot's "how bad is it".
        cell.textDayLetter.setTextColor(lpColor(if (isToday) R.attr.lpAccent else R.attr.lpInkTertiary))
        cell.textDayNumber.setTextColor(lpColor(if (isToday) R.attr.lpInk else R.attr.lpInkSecondary))
        cell.viewDayDot.backgroundTintList = ColorStateList.valueOf(
            lpColor(ReadinessPresentation.fatigueColorAttr(fatigue, thresholds))
        )

        return cell.root
    }

    private fun toggleCalendarChart() {
        isCalendarExpanded = !isCalendarExpanded
        binding.layoutChartExpanded.visibility = if (isCalendarExpanded) View.VISIBLE else View.GONE
        binding.textCalendarHint.setText(
            if (isCalendarExpanded) R.string.readiness_calendar_hint_collapse
            else R.string.readiness_calendar_hint
        )

        if (isCalendarExpanded) {
            setupFatigueChart()
        }
    }

    private fun setupFatigueChart() {
        val timeline = fatigueTimeline ?: return
        val settings = settingsManager.getSettings()
        val config = ExternalLoadProvider.readinessConfig(applicationContext, settings)

        // Get activity timestamps (workouts and external activities)
        val activityTimestamps = mutableSetOf<Long>()
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        
        // Get workout timestamps
        var trainingData = jsonHelper.readTrainingData()
        if (trainingData.trainings.isEmpty()) {
            trainingData = ReadinessHelper.createMockTrainingData()
        }
        
        trainingData.trainings.forEach { workout ->
            try {
                val workoutDate = dateFormat.parse(workout.date) ?: return@forEach
                val calendar = Calendar.getInstance().apply {
                    time = workoutDate
                    set(Calendar.HOUR_OF_DAY, 12) // Noon as workout start
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                // Add duration if available
                val workoutEndTime = calendar.timeInMillis + (workout.durationSeconds?.times(1000) ?: 0L)
                activityTimestamps.add(workoutEndTime)
            } catch (e: Exception) {
                // Skip workouts with invalid dates
            }
        }
        
        // Get external activity timestamps (Health Connect and/or TriPath)
        try {
            ExternalLoadProvider.getExternalActivities(applicationContext).forEach { activity ->
                activityTimestamps.add(activity.endTime)
            }
        } catch (e: Exception) {
            // Skip if unable to load external activities
        }
        
        // Use ALL graph points to show calculated decay values
        val sortedGraphPoints = timeline.graphPoints.sortedBy { it.first }
        val now = System.currentTimeMillis()
        
        // Split entries into past (solid line) and future (dotted line)
        val pastEntries = mutableListOf<Entry>()
        val futureEntries = mutableListOf<Entry>()
        
        sortedGraphPoints.forEach { point ->
            val entry = Entry(point.first.toFloat(), point.second.systemicFatigue)
            if (point.first <= now) {
                pastEntries.add(entry)
            } else {
                futureEntries.add(entry)
            }
        }
        
        // Identify which points correspond to activities (within 1 hour tolerance)
        val activityPointIndices = mutableSetOf<Int>()
        val oneHourMs = 3600_000L
        
        activityTimestamps.forEach { activityTime ->
            sortedGraphPoints.forEachIndexed { index, point ->
                if (kotlin.math.abs(point.first - activityTime) <= oneHourMs) {
                    activityPointIndices.add(index)
                }
            }
        }
        
        if (pastEntries.isEmpty() && futureEntries.isEmpty()) {
            binding.chartFatigue.visibility = View.GONE
            return
        }

        binding.chartFatigue.visibility = View.VISIBLE

        val primaryColor = this.lpColor(R.attr.lpAccent)
        val lineData = LineData()
        
        // Past data: solid line
        if (pastEntries.isNotEmpty()) {
            val pastDataSet = LineDataSet(pastEntries, "Systemic Fatigue").apply {
                color = primaryColor
                valueTextColor = this@ReadinessDashboardActivity.lpColor(R.attr.lpInkSecondary)
                setCircleColor(primaryColor)
                circleRadius = 0f
                setDrawCircles(false)
                lineWidth = 3.5f
                setDrawValues(false)
                mode = LineDataSet.Mode.LINEAR
                setDrawFilled(true)
                fillColor = primaryColor
                fillAlpha = 40
                valueTextSize = 11f
                formSize = 12f
            }
            lineData.addDataSet(pastDataSet)
        }
        
        // Future data: dotted line (2 days forward)
        if (futureEntries.isNotEmpty()) {
            val futureDataSet = LineDataSet(futureEntries, "Projected Fatigue").apply {
                color = primaryColor
                valueTextColor = this@ReadinessDashboardActivity.lpColor(R.attr.lpInkSecondary)
                setCircleColor(primaryColor)
                circleRadius = 0f
                setDrawCircles(false)
                lineWidth = 3.5f
                setDrawValues(false)
                mode = LineDataSet.Mode.LINEAR
                setDrawFilled(false) // No fill for future projection
                enableDashedLine(15f, 10f, 0f) // Dashed line: 15px dash, 10px gap
                valueTextSize = 11f
                formSize = 12f
            }
            lineData.addDataSet(futureDataSet)
        }
        
        // Combine all entries for activity markers
        val allEntries = pastEntries + futureEntries

        // Activity markers: single dataset with all activity points, configured to not draw lines
        val activityEntries = activityPointIndices.mapNotNull { index ->
            if (index < allEntries.size) {
                allEntries[index]
            } else {
                null
            }
        }
        
        if (activityEntries.isNotEmpty()) {
            val activityDataSet = LineDataSet(activityEntries, "Activities").apply {
                color = Color.TRANSPARENT // Transparent line color to prevent line artifacts
                setCircleColor(primaryColor)
                circleRadius = 6f
                setDrawCircles(true)
                lineWidth = 0f
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                cubicIntensity = 0f
                setDrawFilled(false)
            }
            lineData.addDataSet(activityDataSet)
        }
        
        lineData.setValueTextSize(11f)
        binding.chartFatigue.data = lineData

        // Configure chart
        binding.chartFatigue.description.isEnabled = false
        binding.chartFatigue.legend.isEnabled = false
        binding.chartFatigue.setTouchEnabled(true)
        binding.chartFatigue.setDragEnabled(true)
        binding.chartFatigue.setScaleEnabled(true)
        binding.chartFatigue.setPinchZoom(false)

        // Configure X-axis - match progression chart styling
        val xAxis = binding.chartFatigue.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textSize = 12f
        xAxis.textColor = this.lpColor(R.attr.lpInkSecondary)
        xAxis.yOffset = 8f
        xAxis.setLabelCount(minOf((pastEntries + futureEntries).size, 12), true)
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return try {
                    val displayFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
                    displayFormat.format(Date(value.toLong()))
                } catch (e: Exception) {
                    ""
                }
            }
        }
        xAxis.labelRotationAngle = -45f
        xAxis.setDrawGridLines(true)
        xAxis.gridColor = this.lpColor(R.attr.lpChartGrid)
        xAxis.gridLineWidth = 1.5f
        xAxis.enableGridDashedLine(12f, 8f, 0f)
        xAxis.setDrawAxisLine(true)
        xAxis.axisLineColor = this.lpColor(R.attr.lpInkSecondary)
        xAxis.axisLineWidth = 1.5f

        // Configure Y-axis - match progression chart styling
        val leftAxis = binding.chartFatigue.axisLeft
        val maxFatigue = (pastEntries + futureEntries).maxOfOrNull { entry: Entry -> entry.y.toFloat() } ?: 80f
        leftAxis.axisMinimum = 0f
        leftAxis.axisMaximum = (maxFatigue * 1.15f).coerceAtLeast(10f) // Add 15% padding like progression charts
        leftAxis.textSize = 12f
        leftAxis.textColor = this.lpColor(R.attr.lpInkSecondary)
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = this.lpColor(R.attr.lpChartGrid)
        leftAxis.gridLineWidth = 1.5f
        leftAxis.enableGridDashedLine(12f, 8f, 0f)
        leftAxis.setDrawZeroLine(true)
        leftAxis.zeroLineColor = this.lpColor(R.attr.lpInkSecondary)
        leftAxis.zeroLineWidth = 2f
        leftAxis.setLabelCount(6, true)
        leftAxis.setDrawAxisLine(true)
        leftAxis.axisLineColor = this.lpColor(R.attr.lpInkSecondary)
        leftAxis.axisLineWidth = 1.5f
        leftAxis.setDrawLabels(true)
        leftAxis.spaceTop = 5f
        leftAxis.spaceBottom = 0f

        binding.chartFatigue.axisRight.isEnabled = false
        binding.chartFatigue.invalidate()

        // Populate fatigue list with raw fatigue values
        populateFatigueList(timeline)
        
        // Calculate current status from nearest graph point
        calculateCurrentStatusFromTimeline(timeline, config)
    }
    
    private fun calculateCurrentStatusFromTimeline(timeline: FatigueTimeline, config: ReadinessConfig) {
        val now = System.currentTimeMillis()
        
        // Find graph point closest to now
        val nearestPoint = timeline.graphPoints.minByOrNull { point: Pair<Long, FatigueValues> ->
            kotlin.math.abs(point.first - now) 
        } ?: return
        
        val currentFatigue = nearestPoint.second.systemicFatigue
        
        // Determine status based on thresholds
        val status = when {
            currentFatigue > config.thresholds.high -> ActivityStatus.RED
            currentFatigue >= config.thresholds.moderate -> ActivityStatus.YELLOW
            else -> ActivityStatus.GREEN
        }
        
        // You could update a status indicator here if needed
        // For now, this is just calculated but not displayed separately
        // (The main readiness tiles already show status)
    }

    private fun populateFatigueList(timeline: FatigueTimeline) {
        binding.layoutFatigueList.removeAllViews()

        val displayDateFormat = SimpleDateFormat("MM/dd (EEE)", Locale.getDefault())
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        
        // Get raw fatigue values for each day (sum of workouts on that day)
        var trainingData = jsonHelper.readTrainingData()
        if (trainingData.trainings.isEmpty()) {
            trainingData = ReadinessHelper.createMockTrainingData()
        }
        val settings = settingsManager.getSettings()
        val config = ExternalLoadProvider.readinessConfig(applicationContext, settings)
        
        val workoutsByDate = trainingData.trainings.groupBy { it.date }
        val rawFatigueByDate = workoutsByDate.mapValues { (_, workouts) ->
            workouts.sumOf { workout ->
                val rawScores = ReadinessHelper.calculateFatigueScores(workout, trainingData, config)
                rawScores.systemicFatigue.toDouble()
            }.toFloat()
        }

        // Show last 7 days
        val calendar = Calendar.getInstance()
        for (i in 6 downTo 0) {
            calendar.time = Date()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            val dateStr = dateFormat.format(calendar.time)
            val date = dateFormat.parse(dateStr) ?: Date()

            val rawFatigue = rawFatigueByDate[dateStr] ?: 0f
            val endOfDayFatigueValues = timeline.dailyEndValues[dateStr] ?: FatigueValues(0f, 0f, 0f)
            val endOfDayFatigue = endOfDayFatigueValues.systemicFatigue

            val row = ItemReadinessFatigueRowBinding.inflate(
                layoutInflater, binding.layoutFatigueList, false
            )
            row.textRowDate.text = displayDateFormat.format(date)
            row.textRowRaw.text = if (rawFatigue > 0) {
                getString(R.string.readiness_fatigue_row_raw, String.format(Locale.getDefault(), "%.0f", rawFatigue))
            } else {
                getString(R.string.placeholder_dash)
            }
            row.textRowEnd.text = getString(
                R.string.readiness_fatigue_row_end,
                String.format(Locale.getDefault(), "%.1f", endOfDayFatigue)
            )
            row.textRowEnd.setTextColor(
                lpColor(ReadinessPresentation.fatigueColorAttr(endOfDayFatigue, config.thresholds))
            )

            binding.layoutFatigueList.addView(row.root)
        }
    }

    private fun loadReadinessData() {
        val trainingData = jsonHelper.readTrainingData()
        val lastWorkout = getLastCompletedWorkout(trainingData.trainings)

        if (lastWorkout == null) {
            showEmptyState()
            return
        }

        hideEmptyState()

        // Calculate fatigue scores using settings
        val settings = settingsManager.getSettings()
        val config = ExternalLoadProvider.readinessConfig(applicationContext, settings)
        val rawFatigueScores = ReadinessHelper.calculateFatigueScores(
            lastWorkout,
            trainingData,
            config
        )

        // Apply decay based on time elapsed since workout
        val elapsedTime = calculateElapsedTime(lastWorkout.date)
        val decayedFatigueScores = FatigueScores(
            lowerFatigue = ReadinessHelper.getDecayedScore(
                rawFatigueScores.lowerFatigue,
                elapsedTime,
                config
            ),
            upperFatigue = ReadinessHelper.getDecayedScore(
                rawFatigueScores.upperFatigue,
                elapsedTime,
                config
            ),
            systemicFatigue = ReadinessHelper.getDecayedScore(
                rawFatigueScores.systemicFatigue,
                elapsedTime,
                config
            )
        )

        // Update last workout summary (show both raw and decayed)
        updateLastWorkoutSummary(lastWorkout, rawFatigueScores, decayedFatigueScores)

        // Setup calendar view (calculates fatigue timeline)
        // Note: setupCalendarView() is async and will update activity readiness tiles when timeline is ready
        setupCalendarView()

        // For immediate display, use decayed scores as fallback
        // The timeline will update the tiles when it's ready (includes Health Connect data if enabled)
        updateActivityReadiness(decayedFatigueScores, config)
    }

    private fun getLastCompletedWorkout(trainings: List<TrainingSession>): TrainingSession? {
        if (trainings.isEmpty()) return null

        // Sort by date descending and get the most recent
        return trainings.sortedByDescending { it.date }.firstOrNull()
    }

    private fun showEmptyState() = setEmptyState(empty = true)

    private fun hideEmptyState() = setEmptyState(empty = false)

    /**
     * The four activity cards and the last-workout summary have nothing to say before the first
     * session, so the empty-state card replaces them rather than joining them — four dashes and an
     * explanation underneath is worse than one sentence.
     */
    private fun setEmptyState(empty: Boolean) {
        val contentVisibility = if (empty) View.GONE else View.VISIBLE
        binding.cardEmptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.cardLastWorkout.visibility = contentVisibility
        binding.cardRunCycle.visibility = contentVisibility
        binding.cardSwim.visibility = contentVisibility
        binding.cardLowerLift.visibility = contentVisibility
        binding.cardUpperLift.visibility = contentVisibility
    }

    private fun updateLastWorkoutSummary(
        workout: TrainingSession,
        rawFatigueScores: FatigueScores,
        decayedFatigueScores: FatigueScores
    ) {
        binding.textWorkoutDate.text = workout.date
        // Show decayed scores (current effective fatigue)
        binding.textLowerFatigue.text = String.format(Locale.getDefault(), "%.1f", decayedFatigueScores.lowerFatigue)
        binding.textUpperFatigue.text = String.format(Locale.getDefault(), "%.1f", decayedFatigueScores.upperFatigue)
        binding.textSystemicFatigue.text = String.format(Locale.getDefault(), "%.1f", decayedFatigueScores.systemicFatigue)
    }

    /**
     * Calculates elapsed time since workout in milliseconds.
     */
    private fun calculateElapsedTime(workoutDate: String): Long {
        return try {
            val workoutTime = dateFormat.parse(workoutDate)?.time ?: return 0L
            val now = System.currentTimeMillis()
            (now - workoutTime).coerceAtLeast(0L)
        } catch (e: Exception) {
            0L
        }
    }

    private fun updateActivityReadiness(
        fatigueScores: FatigueScores,
        config: ReadinessConfig
    ) {
        // Run / Cycle
        updateActivityCard(
            binding.dotRunCycle,
            binding.textRunCycleStatus,
            binding.textRunCycleMessage,
            binding.textRunCycleCountdown,
            ReadinessHelper.getRunCycleStatus(fatigueScores, config)
        )

        // Swim
        updateActivityCard(
            binding.dotSwim,
            binding.textSwimStatus,
            binding.textSwimMessage,
            binding.textSwimCountdown,
            ReadinessHelper.getSwimStatus(fatigueScores, config)
        )

        // Lower Body Lift
        updateActivityCard(
            binding.dotLowerLift,
            binding.textLowerLiftStatus,
            binding.textLowerLiftMessage,
            binding.textLowerLiftCountdown,
            ReadinessHelper.getLowerLiftStatus(fatigueScores, config)
        )

        // Upper Body Lift
        updateActivityCard(
            binding.dotUpperLift,
            binding.textUpperLiftStatus,
            binding.textUpperLiftMessage,
            binding.textUpperLiftCountdown,
            ReadinessHelper.getUpperLiftStatus(fatigueScores, config)
        )
    }

    /**
     * One activity verdict.
     *
     * The card used to be recoloured per status on top of an already-coloured status label. A
     * coloured panel plus a coloured label is the same signal twice, and at four cards it made the
     * grid the loudest thing on the page — louder than the verdict card that actually decides the
     * day. The panel stays the neutral hairline card every other surface uses; the dot and the
     * status word carry the state.
     */
    private fun updateActivityCard(
        statusDot: View,
        statusText: TextView,
        messageText: TextView,
        countdownText: TextView,
        readiness: ActivityReadiness
    ) {
        val statusColor = lpColor(ReadinessPresentation.statusColorAttr(readiness.status))

        statusText.setText(ReadinessPresentation.statusLabelRes(readiness.status))
        statusText.setTextColor(statusColor)
        statusDot.backgroundTintList = ColorStateList.valueOf(statusColor)

        messageText.text = readiness.message

        if (readiness.timeUntilFresh != null) {
            updateCountdown(countdownText, readiness.timeUntilFresh)
        } else {
            countdownText.visibility = View.GONE
        }
    }

    private fun updateCountdown(textView: TextView, timeUntilFresh: Long) {
        val now = System.currentTimeMillis()
        val trainingData = jsonHelper.readTrainingData()
        val lastWorkout = getLastCompletedWorkout(trainingData.trainings)
            ?: return

        try {
            val workoutTime = dateFormat.parse(lastWorkout.date)?.time ?: return
            // Calculate when recovery will be complete: workout time + recovery duration
            val recoveryCompleteTime = workoutTime + timeUntilFresh
            val remaining = recoveryCompleteTime - now

            if (remaining <= 0) {
                textView.setText(R.string.readiness_ready_now)
                textView.visibility = View.GONE
                return
            }

            val hours = (remaining / 3600_000L).toInt()
            val minutes = ((remaining % 3600_000L) / 60_000L).toInt()

            textView.text = when {
                hours > 0 -> getString(R.string.readiness_ready_in_hours, hours, minutes)
                minutes > 0 -> getString(R.string.readiness_ready_in_minutes, minutes)
                else -> getString(R.string.readiness_ready_now)
            }
            textView.visibility = View.VISIBLE
        } catch (e: Exception) {
            textView.text = ""
            textView.visibility = View.GONE
        }
    }

    private fun startCountdownUpdates() {
        stopCountdownUpdates()
        
        // Use coroutine scope for async operations
        lifecycleScope.launch {
            while (true) {
                val trainingData = jsonHelper.readTrainingData()
                val settings = settingsManager.getSettings()
                val config = ExternalLoadProvider.readinessConfig(applicationContext, settings)
                
                // Load external activities from stored JSON (Health Connect and/or TriPath)
                val externalActivities = withContext(Dispatchers.IO) {
                    ExternalLoadProvider.getExternalActivities(applicationContext)
                }
                
                val timeline = ReadinessHelper.calculateContinuousFatigueTimeline(
                    trainingData,
                    config,
                    externalActivities
                )
                
                fatigueTimeline = timeline
                
                // Get current fatigue from timeline (accounts for all workouts in last 7 days)
                val currentFatigue = ReadinessHelper.getCurrentFatigueFromTimeline(timeline, config)

                // Update countdowns for all activities using timeline values
                val runCycleReadiness = ReadinessHelper.getRunCycleStatus(currentFatigue, config)
                if (runCycleReadiness.timeUntilFresh != null) {
                    updateCountdown(binding.textRunCycleCountdown, runCycleReadiness.timeUntilFresh)
                }

                val swimReadiness = ReadinessHelper.getSwimStatus(currentFatigue, config)
                if (swimReadiness.timeUntilFresh != null) {
                    updateCountdown(binding.textSwimCountdown, swimReadiness.timeUntilFresh)
                }

                val lowerLiftReadiness = ReadinessHelper.getLowerLiftStatus(currentFatigue, config)
                if (lowerLiftReadiness.timeUntilFresh != null) {
                    updateCountdown(binding.textLowerLiftCountdown, lowerLiftReadiness.timeUntilFresh)
                }

                val upperLiftReadiness = ReadinessHelper.getUpperLiftStatus(currentFatigue, config)
                if (upperLiftReadiness.timeUntilFresh != null) {
                    updateCountdown(binding.textUpperLiftCountdown, upperLiftReadiness.timeUntilFresh)
                }

                // Wait 1 minute before next update
                kotlinx.coroutines.delay(60_000L)
            }
        }
    }

    private fun stopCountdownUpdates() {
        // Coroutine cancellation is handled automatically by lifecycleScope
        // No explicit cancellation needed as lifecycleScope cancels on onPause
    }

    private fun exportFatigueDataToFile() {
        val timeline = fatigueTimeline
        if (timeline == null || timeline.graphPoints.isEmpty()) {
            Toast.makeText(this, "No fatigue data available to export", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = "fatigue_data_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
        exportDocumentLauncher.launch(fileName)
    }

    private fun exportFatigueData(destinationUri: Uri) {
        val timeline = fatigueTimeline
        if (timeline == null || timeline.graphPoints.isEmpty()) {
            Toast.makeText(this, "No fatigue data available to export", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val settings = settingsManager.getSettings()
            val config = ExternalLoadProvider.readinessConfig(applicationContext, settings)

            // Create data structure for export
            val exportData = FatigueExportData(
                metadata = FatigueMetadata(
                    exportDate = dateTimeFormat.format(Date()),
                    totalDataPoints = timeline.graphPoints.size,
                    dateRange = DateRange(
                        start = isoDateFormat.format(Date(timeline.graphPoints.first().first)),
                        end = isoDateFormat.format(Date(timeline.graphPoints.last().first))
                    ),
                    settings = ExportSettings(
                        recoverySpeedMultiplier = config.recoverySpeedMultiplier,
                        defaultRPE = config.defaultRPE,
                        allowRunningOnTiredLegs = config.allowRunningOnTiredLegs,
                        thresholds = ThresholdsData(
                            high = config.thresholds.high,
                            moderate = config.thresholds.moderate,
                            cnsMax = config.thresholds.cnsMax
                        ),
                        ignoreWeekends = config.ignoreWeekends
                    )
                ),
                graphPoints = timeline.graphPoints.map { (timestamp, fatigueValues) ->
                    GraphPoint(
                        timestamp = timestamp,
                        timestampISO = isoDateFormat.format(Date(timestamp)),
                        timestampReadable = dateTimeFormat.format(Date(timestamp)),
                        fatigueValue = fatigueValues.systemicFatigue,
                        lowerFatigue = fatigueValues.lowerFatigue,
                        upperFatigue = fatigueValues.upperFatigue
                    )
                },
                dailyEndValues = timeline.dailyEndValues.mapValues { (_, fatigueValues) ->
                    mapOf(
                        "systemic" to fatigueValues.systemicFatigue,
                        "lower" to fatigueValues.lowerFatigue,
                        "upper" to fatigueValues.upperFatigue
                    )
                }
            )

            // Serialize to JSON with pretty printing
            val gson = GsonBuilder().setPrettyPrinting().create()
            val jsonString = gson.toJson(exportData)

            // Write to file
            val resolver = contentResolver
            resolver.openOutputStream(destinationUri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray())
                outputStream.flush()
            } ?: throw IOException("Unable to open destination")

            Toast.makeText(this, "Fatigue data exported successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("ReadinessDashboard", "Failed to export fatigue data", e)
            Toast.makeText(
                this,
                "Export failed: ${e.localizedMessage ?: "Unknown error"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Data classes for JSON export
    private data class FatigueExportData(
        val metadata: FatigueMetadata,
        val graphPoints: List<GraphPoint>,
        val dailyEndValues: Map<String, Map<String, Float>>
    )

    private data class FatigueMetadata(
        val exportDate: String,
        val totalDataPoints: Int,
        val dateRange: DateRange,
        val settings: ExportSettings
    )

    private data class DateRange(
        val start: String,
        val end: String
    )

    private data class ExportSettings(
        val recoverySpeedMultiplier: Float,
        val defaultRPE: Float,
        val allowRunningOnTiredLegs: Boolean,
        val thresholds: ThresholdsData,
        val ignoreWeekends: Boolean
    )

    private data class ThresholdsData(
        val high: Float,
        val moderate: Float,
        val cnsMax: Float
    )

    private data class GraphPoint(
        val timestamp: Long,
        val timestampISO: String,
        val timestampReadable: String,
        val fatigueValue: Float, // Systemic fatigue (for backward compatibility)
        val lowerFatigue: Float,
        val upperFatigue: Float
    )

    private fun autoSyncHealthConnect() {
        // Check if Health Connect is enabled
        val isEnabled = healthConnectPrefs.getBoolean(HEALTH_CONNECT_ENABLED_KEY, false)
        
        if (!isEnabled) {
            return // Health Connect sync is disabled, skip
        }
        
        // Check if Health Connect is available
        if (!HealthConnectHelper.isAvailable(this)) {
            return // Health Connect not available, skip
        }
        
        // Perform sync in background (silently, no UI feedback)
        lifecycleScope.launch {
            HealthConnectHelper.autoSyncActivities(applicationContext).fold(
                onSuccess = { newCount ->
                    // Sync successful - reload data to include new activities
                    loadReadinessData()
                },
                onFailure = { error ->
                    // Sync failed silently (already logged in helper)
                }
            )
        }
    }

    /**
     * Pulls TriPath's cardio load and recovery data, then redraws. Silent: TriPath being absent,
     * disabled or unreachable is the normal case, not an error worth a toast.
     */
    private fun autoSyncTriPath() {
        if (!TriPathConnection.isEnabled(this)) return

        lifecycleScope.launch {
            TriPathSyncHelper.autoSync(applicationContext).onSuccess {
                loadReadinessData()
                updateTriPathCard()
            }
        }
    }

    /**
     * Renders TriPath's readiness verdict, or says plainly that it is showing LiftPath's own.
     *
     * TriPath owns readiness now: it sees every discipline, sleep, fuelling and body composition,
     * where LiftPath sees lifting. So this displays a verdict rather than computing one — and the
     * source line is not optional, because an athlete must never be quietly switched between two
     * models that can disagree.
     *
     * When TriPath is absent, disconnected, or too old to advertise
     * [TriPathContract.CAP_READINESS_V1], the local model still runs and the card says so.
     */
    private fun updateTriPathCard() {
        val card = binding.cardTripath
        if (!TriPathConnection.isActive(this)) {
            card.visibility = View.GONE
            return
        }

        lifecycleScope.launch {
            val storage = withContext(Dispatchers.IO) {
                TriPathStorageHelper(applicationContext).read()
            }
            val readiness = storage.readiness
            if (readiness == null) {
                showLocalFallbackCard(storage)
                return@launch
            }

            card.visibility = View.VISIBLE
            binding.textTripathScore.text = readiness.score.toString()
            binding.textTripathScore.setTextColor(
                lpColor(ReadinessPresentation.bandColorAttr(readiness.band))
            )
            binding.textTripathBand.text = ReadinessPresentation.humanise(readiness.band)
            binding.textTripathGuidance.text =
                readiness.guidance ?: ReadinessPresentation.humanise(readiness.action)

            renderChannels(readiness)
            renderDrivers(readiness)
            updateDetailVisibility()

            binding.textTripathEffect.text = readiness.weeklyLoadRampPct
                ?.let { getString(R.string.readiness_ramp, it) }
                .orEmpty()
            binding.textTripathEffect.visibility =
                if (readiness.weeklyLoadRampPct == null) View.GONE else View.VISIBLE

            binding.textTripathSource.text = getString(
                R.string.readiness_source_tripath,
                DateUtils.getRelativeTimeSpanString(
                    readiness.computedAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
            )
        }
    }

    /**
     * TriPath is connected but has not handed over a verdict — an older build, or a sync that has
     * not run yet. Show what it did send and label the readiness numbers as locally computed.
     *
     * Reuses the verdict card's shape rather than a second layout: form takes the headline slot,
     * fitness and fatigue take two of the channel rows. The card must not change size or position
     * between the two modes, or a TriPath update would rearrange the page under the reader.
     */
    private fun showLocalFallbackCard(storage: TriPathStorage) {
        val card = binding.cardTripath
        val day = storage.days.maxByOrNull { it.date }
        if (day == null) {
            card.visibility = View.GONE
            return
        }

        card.visibility = View.VISIBLE
        binding.textTripathScore.text = String.format(Locale.getDefault(), "%+.0f", day.tsb)
        binding.textTripathScore.setTextColor(lpColor(R.attr.lpInk))
        binding.textTripathBand.setText(R.string.readiness_form_label)
        binding.textTripathGuidance.text = recoverySummary(day)

        binding.layoutTripathChannels.removeAllViews()
        addChannelRow(
            label = getString(R.string.readiness_fitness_label),
            value = String.format(Locale.getDefault(), "%.0f", day.ctl),
            colorAttr = R.attr.lpInk,
            clears = null
        )
        addChannelRow(
            label = getString(R.string.readiness_fatigue_label),
            value = String.format(Locale.getDefault(), "%.0f", day.atl),
            colorAttr = R.attr.lpInk,
            clears = null
        )

        binding.layoutTripathDrivers.removeAllViews()
        updateDetailVisibility()
        binding.textTripathEffect.visibility = View.GONE
        binding.textTripathSource.setText(R.string.readiness_source_local)
    }

    /**
     * Hides the channel strip, the driver list and the rule above them when they are empty.
     *
     * Each carries a top margin, so an empty container is not free — it leaves a gap that reads as
     * a row that failed to load, which is exactly the wrong impression for an integration whose
     * whole failure mode is "TriPath sent less than expected".
     */
    private fun updateDetailVisibility() {
        val hasChannels = binding.layoutTripathChannels.childCount > 0
        val hasDrivers = binding.layoutTripathDrivers.childCount > 0
        binding.layoutTripathChannels.visibility = if (hasChannels) View.VISIBLE else View.GONE
        binding.layoutTripathDrivers.visibility = if (hasDrivers) View.VISIBLE else View.GONE
        binding.viewTripathDivider.visibility =
            if (hasChannels || hasDrivers) View.VISIBLE else View.GONE
    }

    /** "Sleep 82 · HRV 46 · Soreness 3/10" — whichever of the three TriPath actually has. */
    private fun recoverySummary(day: TriPathDay): String = buildList {
        day.sleepScore?.let { add(getString(R.string.readiness_sleep_score, it)) }
            ?: day.sleepMinutes?.let {
                add(getString(R.string.readiness_sleep_duration, it / 60, it % 60))
            }
        day.hrvRmssd?.let { add(getString(R.string.readiness_hrv, it)) }
        day.soreness?.let { add(getString(R.string.readiness_soreness, it)) }
    }.joinToString(" · ").ifEmpty { getString(R.string.readiness_no_recovery_data) }

    /**
     * One row per strain channel. The point of showing four rather than one number is that they
     * disagree usefully: legs can be wrecked while the upper body is untouched.
     */
    private fun renderChannels(readiness: TriPathReadiness) {
        binding.layoutTripathChannels.removeAllViews()

        ReadinessPresentation.CHANNELS.forEach { channel ->
            val freshness = channel.freshness(readiness) ?: return@forEach
            addChannelRow(
                label = getString(channel.labelRes),
                value = getString(R.string.readiness_percent, freshness),
                colorAttr = ReadinessPresentation.freshnessColorAttr(freshness),
                clears = readiness.hoursToFresh[channel.key]
                    ?.takeIf { it > 0 }
                    ?.let { getString(R.string.readiness_channel_clears, ReadinessPresentation.formatHours(it)) }
            )
        }
    }

    private fun addChannelRow(label: String, value: String, colorAttr: Int, clears: String?) {
        val row = ItemReadinessChannelRowBinding.inflate(
            layoutInflater, binding.layoutTripathChannels, false
        )
        row.textChannelLabel.text = label
        row.textChannelValue.text = value
        row.textChannelValue.setTextColor(lpColor(colorAttr))
        row.viewChannelDot.backgroundTintList =
            ColorStateList.valueOf(lpColor(colorAttr))
        row.textChannelClears.text = clears.orEmpty()
        row.textChannelClears.visibility = if (clears == null) View.GONE else View.VISIBLE
        binding.layoutTripathChannels.addView(row.root)
    }

    /** The ranked reasons behind the score — worst first, because that is what to act on. */
    private fun renderDrivers(readiness: TriPathReadiness) {
        val container = binding.layoutTripathDrivers
        container.removeAllViews()

        readiness.drivers
            .filter { it.impact < 0 }
            .sortedBy { it.impact }
            .take(MAX_DRIVERS_SHOWN)
            .forEach { driver ->
            val row = ItemReadinessDriverBinding.inflate(layoutInflater, container, false)
            row.textDriver.text = driver.detail
            container.addView(row.root)
        }
    }

    private companion object {
        /** Enough drivers to explain the score without turning the card into a report. */
        const val MAX_DRIVERS_SHOWN = 3
    }
}
