package com.liftpath.activities

import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.liftpath.R
import com.liftpath.databinding.ActivityReadinessDashboardBinding
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
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

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

        setupBackgroundAnimation()
        setupClickListeners()
        setupHealthConnectToggle()
        loadReadinessData()
    }

    override fun onResume() {
        super.onResume()
        // Reload data when returning (e.g., from calibration settings)
        loadReadinessData()
        startCountdownUpdates()
    }

    override fun onPause() {
        super.onPause()
        stopCountdownUpdates()
    }

    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is Animatable) {
            drawable.start()
        }
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
        val config = ReadinessConfig.fromSettings(settings)

        // Use mock data if no real data exists
        if (trainingData.trainings.isEmpty()) {
            trainingData = ReadinessHelper.createMockTrainingData()
        }

        // Load external activities from stored JSON (if enabled)
        lifecycleScope.launch(Dispatchers.IO) {
            val useHealthConnect = healthConnectPrefs.getBoolean(HEALTH_CONNECT_ENABLED_KEY, false)
            val externalActivities = if (useHealthConnect) {
                try {
                    // Use stored activities from JSON instead of fetching from Health Connect
                    // This way we use the persisted data with deduplication and overlap filtering
                    HealthConnectHelper.getStoredActivities(applicationContext)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            // Calculate continuous fatigue timeline with external activities
            val timeline = ReadinessHelper.calculateContinuousFatigueTimeline(
                trainingData,
                config,
                externalActivities
            )

            withContext(Dispatchers.Main) {
                fatigueTimeline = timeline
                updateCalendarWithTimeline(timeline)
                // Update activity readiness tiles with timeline that includes Health Connect data
                val settings = settingsManager.getSettings()
                val config = ReadinessConfig.fromSettings(settings)
                val currentFatigue = ReadinessHelper.getCurrentFatigueFromTimeline(timeline, config)
                updateActivityReadiness(currentFatigue, config)
            }
        }
    }

    private fun updateCalendarWithTimeline(timeline: FatigueTimeline) {
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
        days.forEach { pair ->
            val dateStr = pair.first
            val fatigue = pair.second
            val dayCell = createDayCell(dateStr, fatigue)
            binding.layoutCalendarDays.addView(dayCell)
        }

        // Always show calendar (even if no workouts, shows empty days)
        binding.cardCalendar.visibility = View.VISIBLE
    }

    private fun createDayCell(dateStr: String, fatigue: Float): View {
        // Parse date to get day of week and day number
        val date = dateFormat.parse(dateStr) ?: Date()
        val cal = Calendar.getInstance().apply { time = date }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val dayNumber = cal.get(Calendar.DAY_OF_MONTH)
        val isToday = dateStr == dateFormat.format(Date())

        // Day abbreviation
        val dayAbbr = when (dayOfWeek) {
            Calendar.SUNDAY -> "S"
            Calendar.MONDAY -> "M"
            Calendar.TUESDAY -> "T"
            Calendar.WEDNESDAY -> "W"
            Calendar.THURSDAY -> "T"
            Calendar.FRIDAY -> "F"
            Calendar.SATURDAY -> "S"
            else -> ""
        }

        // Create layout for the cell
        val cellLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(4, 8, 4, 8)
            }
        }

        // Day abbreviation
        val dayText = TextView(this).apply {
            text = dayAbbr
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@ReadinessDashboardActivity, if (isToday) R.color.fitness_primary else R.color.fitness_text_secondary))
            gravity = android.view.Gravity.CENTER
        }
        cellLayout.addView(dayText)

        // Day number
        val dayNumText = TextView(this).apply {
            text = dayNumber.toString()
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@ReadinessDashboardActivity, if (isToday) R.color.fitness_text_primary else R.color.fitness_text_secondary))
            gravity = android.view.Gravity.CENTER
        }
        cellLayout.addView(dayNumText)

        // Fatigue number
        val fatigueText = TextView(this).apply {
            text = String.format("%.0f", fatigue)
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@ReadinessDashboardActivity, R.color.fitness_text_secondary))
            gravity = android.view.Gravity.CENTER
        }
        cellLayout.addView(fatigueText)

        // Colored circle based on fatigue (red to green)
        val circleView = View(this).apply {
            val size = resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 3
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = android.view.Gravity.CENTER
                setMargins(0, 4, 0, 0)
            }
            background = createFatigueCircle(fatigue)
        }
        cellLayout.addView(circleView)

        return cellLayout
    }

    private fun createFatigueCircle(fatigue: Float): android.graphics.drawable.Drawable {
        // Map fatigue to color: 0 = green, 80+ = red
        val normalized = (fatigue / 80f).coerceIn(0f, 1f)
        
        // Detect dark mode
        val isDarkMode = (resources.configuration.uiMode and 
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        // Use HSV color space for better control
        // Hue: 0 (red) to 120 (green)
        // Saturation: 80-100% for visibility
        // Value/Brightness: Adjust based on theme
        val hue = 120f * (1f - normalized) // 120 = green, 0 = red
        val saturation = if (isDarkMode) 0.85f else 0.75f // More saturated in dark mode
        val brightness = if (isDarkMode) 0.85f else 0.70f // Brighter in dark mode
        
        val hsv = floatArrayOf(hue, saturation, brightness)
        val color = Color.HSVToColor(hsv)
        
        val drawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
        }
        
        return drawable
    }

    private fun toggleCalendarChart() {
        isCalendarExpanded = !isCalendarExpanded
        binding.layoutChartExpanded.visibility = if (isCalendarExpanded) View.VISIBLE else View.GONE

        if (isCalendarExpanded) {
            setupFatigueChart()
        }
    }

    private fun setupFatigueChart() {
        val timeline = fatigueTimeline ?: return
        val settings = settingsManager.getSettings()
        val config = ReadinessConfig.fromSettings(settings)

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
        
        // Get external activity timestamps (if Health Connect is enabled)
        val useHealthConnect = healthConnectPrefs.getBoolean(HEALTH_CONNECT_ENABLED_KEY, false)
        if (useHealthConnect) {
            try {
                val externalActivities = HealthConnectHelper.getStoredActivities(applicationContext)
                externalActivities.forEach { activity ->
                    activityTimestamps.add(activity.endTime)
                }
            } catch (e: Exception) {
                // Skip if unable to load external activities
            }
        }
        
        // Use ALL graph points to show calculated decay values
        val sortedGraphPoints = timeline.graphPoints.sortedBy { it.first }
        val allEntries = sortedGraphPoints.map { point ->
            Entry(point.first.toFloat(), point.second.systemicFatigue)
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
        
        // Identify segments that need linear interpolation (2 hours before each activity)
        val linearSegmentIndices = mutableSetOf<Int>()
        val twoHoursMs = 2 * 3600_000L
        activityTimestamps.forEach { activityTime ->
            sortedGraphPoints.forEachIndexed { index, point ->
                val timeDiff = activityTime - point.first
                if (timeDiff > 0 && timeDiff <= twoHoursMs) {
                    linearSegmentIndices.add(index)
                }
            }
        }
        
        if (allEntries.isEmpty()) {
            binding.chartFatigue.visibility = View.GONE
            return
        }

        binding.chartFatigue.visibility = View.VISIBLE

        val primaryColor = ContextCompat.getColor(this, R.color.fitness_primary)
        val lineData = LineData()
        
        // Use a single dataset with all points for perfect continuity
        // Use LINEAR mode which will show all calculated values accurately connected
        // LINEAR mode is appropriate since we're using all calculated hourly points
        val mainDataSet = LineDataSet(allEntries, "Systemic Fatigue").apply {
            color = primaryColor
            valueTextColor = Color.DKGRAY
            setCircleColor(primaryColor)
            circleRadius = 0f
            setDrawCircles(false)
            lineWidth = 3.5f
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR // Linear connects all points continuously
            setDrawFilled(true)
            fillColor = primaryColor
            fillAlpha = 40
            valueTextSize = 11f
            formSize = 12f
        }
        lineData.addDataSet(mainDataSet)

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
        xAxis.textColor = Color.parseColor("#616161")
        xAxis.yOffset = 8f
        xAxis.setLabelCount(minOf(allEntries.size, 8), true)
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
        xAxis.gridColor = Color.parseColor("#E0E0E0")
        xAxis.gridLineWidth = 1.5f
        xAxis.enableGridDashedLine(12f, 8f, 0f)
        xAxis.setDrawAxisLine(true)
        xAxis.axisLineColor = Color.parseColor("#9E9E9E")
        xAxis.axisLineWidth = 1.5f

        // Configure Y-axis - match progression chart styling
        val leftAxis = binding.chartFatigue.axisLeft
        val maxFatigue = allEntries.maxOfOrNull { entry: Entry -> entry.y.toFloat() } ?: 80f
        leftAxis.axisMinimum = 0f
        leftAxis.axisMaximum = (maxFatigue * 1.15f).coerceAtLeast(10f) // Add 15% padding like progression charts
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
        val config = ReadinessConfig.fromSettings(settings)
        
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
            val dateDisplay = displayDateFormat.format(date)
            
            val rawFatigue = rawFatigueByDate[dateStr] ?: 0f
            val endOfDayFatigueValues = timeline.dailyEndValues[dateStr] ?: FatigueValues(0f, 0f, 0f)
            val endOfDayFatigue = endOfDayFatigueValues.systemicFatigue

            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 4, 0, 4)
                }
            }

            // Date
            val dateText = TextView(this).apply {
                text = dateDisplay
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@ReadinessDashboardActivity, R.color.fitness_text_primary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            rowLayout.addView(dateText)

            // Raw fatigue (sum added on that date)
            val rawText = TextView(this).apply {
                text = if (rawFatigue > 0) String.format("Raw: %.0f", rawFatigue) else "--"
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@ReadinessDashboardActivity, R.color.fitness_text_secondary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            rowLayout.addView(rawText)

            // End of day fatigue (from timeline)
            val endText = TextView(this).apply {
                text = String.format("End: %.1f", endOfDayFatigue)
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@ReadinessDashboardActivity, R.color.fitness_accent))
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            rowLayout.addView(endText)

            binding.layoutFatigueList.addView(rowLayout)
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
        val config = ReadinessConfig.fromSettings(settings)
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

    private fun showEmptyState() {
        binding.cardLastWorkout.visibility = View.GONE
        binding.textEmptyState.visibility = View.VISIBLE
        // Hide all activity cards
        binding.cardRunCycle.visibility = View.GONE
        binding.cardSwim.visibility = View.GONE
        binding.cardLowerLift.visibility = View.GONE
        binding.cardUpperLift.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.cardLastWorkout.visibility = View.VISIBLE
        binding.textEmptyState.visibility = View.GONE
        // Show all activity cards
        binding.cardRunCycle.visibility = View.VISIBLE
        binding.cardSwim.visibility = View.VISIBLE
        binding.cardLowerLift.visibility = View.VISIBLE
        binding.cardUpperLift.visibility = View.VISIBLE
    }

    private fun updateLastWorkoutSummary(
        workout: TrainingSession,
        rawFatigueScores: FatigueScores,
        decayedFatigueScores: FatigueScores
    ) {
        binding.textWorkoutDate.text = "Date: ${workout.date}"
        // Show decayed scores (current effective fatigue)
        binding.textLowerFatigue.text = String.format("%.1f", decayedFatigueScores.lowerFatigue)
        binding.textUpperFatigue.text = String.format("%.1f", decayedFatigueScores.upperFatigue)
        binding.textSystemicFatigue.text = String.format("%.1f", decayedFatigueScores.systemicFatigue)
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
        val runCycleReadiness = ReadinessHelper.getRunCycleStatus(fatigueScores, config)
        updateActivityCard(
            binding.cardRunCycle,
            binding.textRunCycleStatus,
            binding.textRunCycleMessage,
            binding.textRunCycleCountdown,
            runCycleReadiness
        )

        // Swim
        val swimReadiness = ReadinessHelper.getSwimStatus(fatigueScores, config)
        updateActivityCard(
            binding.cardSwim,
            binding.textSwimStatus,
            binding.textSwimMessage,
            binding.textSwimCountdown,
            swimReadiness
        )

        // Lower Body Lift
        val lowerLiftReadiness = ReadinessHelper.getLowerLiftStatus(fatigueScores, config)
        updateActivityCard(
            binding.cardLowerLift,
            binding.textLowerLiftStatus,
            binding.textLowerLiftMessage,
            binding.textLowerLiftCountdown,
            lowerLiftReadiness
        )

        // Upper Body Lift
        val upperLiftReadiness = ReadinessHelper.getUpperLiftStatus(fatigueScores, config)
        updateActivityCard(
            binding.cardUpperLift,
            binding.textUpperLiftStatus,
            binding.textUpperLiftMessage,
            binding.textUpperLiftCountdown,
            upperLiftReadiness
        )
    }

    private fun updateActivityCard(
        card: androidx.cardview.widget.CardView,
        statusText: android.widget.TextView,
        messageText: android.widget.TextView,
        countdownText: android.widget.TextView,
        readiness: ActivityReadiness
    ) {
        // Update status text and color
        val (statusLabel, statusColor, cardBackgroundColor) = when (readiness.status) {
            ActivityStatus.GREEN -> {
                Triple("Ready", R.color.fitness_highlight_border, R.color.fitness_highlight_background)
            }
            ActivityStatus.YELLOW -> {
                Triple("Caution", R.color.fitness_warning_border, R.color.fitness_warning_background)
            }
            ActivityStatus.RED -> {
                Triple("Blocked", R.color.fitness_error_border, R.color.fitness_error_background)
            }
        }

        statusText.text = statusLabel
        statusText.setTextColor(ContextCompat.getColor(this, statusColor))
        card.setCardBackgroundColor(ContextCompat.getColor(this, cardBackgroundColor))

        // Update message - use primary text color for better contrast on colored backgrounds
        messageText.text = readiness.message
        messageText.setTextColor(ContextCompat.getColor(this, R.color.fitness_text_primary))

        // Update countdown if recovery time is set - use primary text color for better contrast
        if (readiness.timeUntilFresh != null) {
            updateCountdown(countdownText, readiness.timeUntilFresh)
            countdownText.visibility = View.VISIBLE
            countdownText.setTextColor(ContextCompat.getColor(this, R.color.fitness_text_primary))
        } else {
            countdownText.visibility = View.GONE
        }
    }

    private fun updateCountdown(textView: android.widget.TextView, timeUntilFresh: Long) {
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
                textView.text = "Ready now"
                textView.visibility = View.GONE
                return
            }

            val hours = (remaining / 3600_000L).toInt()
            val minutes = ((remaining % 3600_000L) / 60_000L).toInt()

            textView.text = when {
                hours > 0 -> "Ready in ${hours}h ${minutes}m"
                minutes > 0 -> "Ready in ${minutes}m"
                else -> "Ready now"
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
                val config = ReadinessConfig.fromSettings(settings)
                
                // Load external activities from stored JSON (if enabled)
                val useHealthConnect = healthConnectPrefs.getBoolean(HEALTH_CONNECT_ENABLED_KEY, false)
                val externalActivities = if (useHealthConnect) {
                    withContext(Dispatchers.IO) {
                        try {
                            // Use stored activities from JSON
                            HealthConnectHelper.getStoredActivities(applicationContext)
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                } else {
                    emptyList()
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
            val config = ReadinessConfig.fromSettings(settings)

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
}

