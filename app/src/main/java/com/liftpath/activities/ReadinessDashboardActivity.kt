package com.liftpath.activities

import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.liftpath.helpers.ActivityReadiness
import com.liftpath.helpers.ActivityStatus
import com.liftpath.helpers.FatigueTimeline
import com.liftpath.helpers.ReadinessSettingsManager
import com.liftpath.models.TrainingSession
import com.liftpath.models.TrainingData
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
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val handler = Handler(Looper.getMainLooper())
    private var fatigueTimeline: FatigueTimeline? = null
    private var updateRunnable: Runnable? = null

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

        setupBackgroundAnimation()
        setupClickListeners()
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

        // Calculate continuous fatigue timeline
        fatigueTimeline = ReadinessHelper.calculateContinuousFatigueTimeline(trainingData, config)
        val timeline = fatigueTimeline ?: return

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

            // Get fatigue at end of day from timeline
            val fatigue = dailyEndValues[dateStr] ?: 0f
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

        // Convert timeline graph points to chart entries
        val entries = timeline.graphPoints.map { point: Pair<Long, Float> ->
            Entry(point.first.toFloat(), point.second)
        }

        if (entries.isEmpty()) {
            binding.chartFatigue.visibility = View.GONE
            return
        }

        binding.chartFatigue.visibility = View.VISIBLE

        val dataSet = LineDataSet(entries, "Systemic Fatigue")
        val primaryColor = ContextCompat.getColor(this, R.color.fitness_primary)
        dataSet.color = primaryColor
        dataSet.valueTextColor = ContextCompat.getColor(this, R.color.fitness_text_secondary)
        dataSet.setCircleColor(primaryColor)
        dataSet.circleRadius = 3f // Smaller for hourly data
        dataSet.lineWidth = 2f
        dataSet.setDrawValues(false)
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.cubicIntensity = 0.2f
        dataSet.setDrawFilled(true)
        dataSet.fillColor = primaryColor
        dataSet.fillAlpha = 30

        val lineData = LineData(dataSet)
        binding.chartFatigue.data = lineData

        // Configure chart
        binding.chartFatigue.description.isEnabled = false
        binding.chartFatigue.legend.isEnabled = false
        binding.chartFatigue.setTouchEnabled(true)
        binding.chartFatigue.setDragEnabled(true)
        binding.chartFatigue.setScaleEnabled(true)
        binding.chartFatigue.setPinchZoom(false)

        // Configure X-axis
        val xAxis = binding.chartFatigue.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textSize = 10f
        xAxis.textColor = ContextCompat.getColor(this, R.color.fitness_text_secondary)
        xAxis.setLabelCount(7, true)
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
        xAxis.setDrawGridLines(true)
        xAxis.gridColor = ContextCompat.getColor(this, R.color.fitness_chart_grid)
        xAxis.setDrawAxisLine(true)

        // Configure Y-axis
        val leftAxis = binding.chartFatigue.axisLeft
        val maxFatigue = entries.maxOfOrNull { entry: Entry -> entry.y.toFloat() } ?: 80f
        leftAxis.axisMinimum = 0f
        leftAxis.axisMaximum = (maxFatigue * 1.1f).coerceAtLeast(10f) // Add 10% padding
        leftAxis.textSize = 10f
        leftAxis.textColor = ContextCompat.getColor(this, R.color.fitness_text_secondary)
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = ContextCompat.getColor(this, R.color.fitness_chart_grid)
        leftAxis.setDrawZeroLine(true)

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
        val nearestPoint = timeline.graphPoints.minByOrNull { point: Pair<Long, Float> ->
            kotlin.math.abs(point.first - now) 
        } ?: return
        
        val currentFatigue = nearestPoint.second
        
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
            val endOfDayFatigue = timeline.dailyEndValues[dateStr] ?: 0f

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

        // Calculate and update activity readiness using decayed scores
        updateActivityReadiness(decayedFatigueScores, config)

        // Setup calendar view
        setupCalendarView()
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
        updateRunnable = object : Runnable {
            override fun run() {
                val trainingData = jsonHelper.readTrainingData()
                val lastWorkout = getLastCompletedWorkout(trainingData.trainings)
                if (lastWorkout != null) {
                    val settings = settingsManager.getSettings()
                    val config = ReadinessConfig.fromSettings(settings)
                    val rawFatigueScores = ReadinessHelper.calculateFatigueScores(
                        lastWorkout,
                        trainingData,
                        config
                    )
                    
                    // Apply decay
                    val elapsedTime = calculateElapsedTime(lastWorkout.date)
                    val fatigueScores = FatigueScores(
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

                    // Update countdowns for all activities
                    val runCycleReadiness = ReadinessHelper.getRunCycleStatus(fatigueScores, config)
                    if (runCycleReadiness.timeUntilFresh != null) {
                        updateCountdown(binding.textRunCycleCountdown, runCycleReadiness.timeUntilFresh)
                    }

                    val swimReadiness = ReadinessHelper.getSwimStatus(fatigueScores, config)
                    if (swimReadiness.timeUntilFresh != null) {
                        updateCountdown(binding.textSwimCountdown, swimReadiness.timeUntilFresh)
                    }

                    val lowerLiftReadiness = ReadinessHelper.getLowerLiftStatus(fatigueScores, config)
                    if (lowerLiftReadiness.timeUntilFresh != null) {
                        updateCountdown(binding.textLowerLiftCountdown, lowerLiftReadiness.timeUntilFresh)
                    }

                    val upperLiftReadiness = ReadinessHelper.getUpperLiftStatus(fatigueScores, config)
                    if (upperLiftReadiness.timeUntilFresh != null) {
                        updateCountdown(binding.textUpperLiftCountdown, upperLiftReadiness.timeUntilFresh)
                    }
                }

                // Schedule next update in 1 minute
                handler.postDelayed(this, 60_000L)
            }
        }
        handler.post(updateRunnable!!)
    }

    private fun stopCountdownUpdates() {
        updateRunnable?.let {
            handler.removeCallbacks(it)
            updateRunnable = null
        }
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
                graphPoints = timeline.graphPoints.map { (timestamp, fatigueValue) ->
                    GraphPoint(
                        timestamp = timestamp,
                        timestampISO = isoDateFormat.format(Date(timestamp)),
                        timestampReadable = dateTimeFormat.format(Date(timestamp)),
                        fatigueValue = fatigueValue
                    )
                },
                dailyEndValues = timeline.dailyEndValues
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
        val dailyEndValues: Map<String, Float>
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
        val fatigueValue: Float
    )
}

