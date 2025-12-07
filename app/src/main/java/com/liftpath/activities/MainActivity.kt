package com.liftpath.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.liftpath.R
import androidx.appcompat.app.AppCompatActivity
import com.liftpath.databinding.ActivityMainBinding
import com.liftpath.helpers.ActiveWorkoutDraftManager
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.HealthConnectHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.showWithTransparentWindow
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.liftpath.models.ActiveWorkoutDraft
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.TrainingData
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.liftpath.adapters.ChartCarouselAdapter
import com.liftpath.adapters.ChartType
import com.liftpath.adapters.ChartData
import com.liftpath.helpers.ReadinessHelper
import com.liftpath.helpers.ReadinessConfig
import com.google.android.material.tabs.TabLayoutMediator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var jsonHelper: JsonHelper
    private lateinit var prefs: SharedPreferences
    private lateinit var draftManager: ActiveWorkoutDraftManager
    
    companion object {
        private const val PREFS_NAME = "main_activity_prefs"
        private const val KEY_LEFT_EXERCISE = "left_exercise"
        private const val KEY_RIGHT_EXERCISE = "right_exercise"
        private const val DEFAULT_LEFT_EXERCISE = "Bench Press (Barbell)"
        private const val DEFAULT_RIGHT_EXERCISE = "Back Squat (Barbell)"
    }

    private val startWorkoutForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // This block is called when ActiveTrainingActivity finishes.
        // We can now update the stats on the main screen.
        updateStats()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply Window Insets to Root View
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        jsonHelper = JsonHelper(this)
        draftManager = ActiveWorkoutDraftManager(this)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setupDefaultExercises()
        setupClickListeners()
        setupBackgroundAnimation()
        runEntranceAnimations()
        updateStats()
        
        // Auto-sync Health Connect in the background
        autoSyncHealthConnect()
    }

    override fun onResume() {
        super.onResume()
        // Refresh stats when returning from other activities (e.g., after deleting a training session)
        updateStats()
        
        // Auto-sync Health Connect in the background
        autoSyncHealthConnect()
    }

    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is Animatable) {
            drawable.start()
        }
    }

    private fun runEntranceAnimations() {
        // 1. Header Elements (Title, Subtitle, Settings)
        val fadeDown = AnimationUtils.loadAnimation(this, com.liftpath.R.anim.fade_in_up)
        binding.textWelcomeTitle.startAnimation(fadeDown)
        binding.textWelcomeSubtitle.startAnimation(fadeDown)
        binding.cardSettings.startAnimation(fadeDown)

        // 2. Main Hero Card (Pop In)
        val popIn = AnimationUtils.loadAnimation(this, com.liftpath.R.anim.pop_in)
        popIn.startOffset = 200
        binding.cardStartWorkout.startAnimation(popIn)

        // 3. Grid Items (Fade Up Staggered)
        val fadeUp1 = AnimationUtils.loadAnimation(this, com.liftpath.R.anim.fade_in_up)
        fadeUp1.startOffset = 300
        binding.cardViewProgress.startAnimation(fadeUp1)
        binding.cardViewHistory.startAnimation(fadeUp1)

        val fadeUp2 = AnimationUtils.loadAnimation(this, com.liftpath.R.anim.fade_in_up)
        fadeUp2.startOffset = 400
        binding.cardExercises.startAnimation(fadeUp2)
        binding.cardPlans.startAnimation(fadeUp2)
        binding.cardReadiness.startAnimation(fadeUp2)

        // 4. Stats Section (Fade Up)
        val fadeUpStats = AnimationUtils.loadAnimation(this, com.liftpath.R.anim.fade_in_up)
        fadeUpStats.startOffset = 500
        binding.textTodayStats.startAnimation(fadeUpStats)
        binding.cardBenchPress.startAnimation(fadeUpStats)
        binding.cardSquat.startAnimation(fadeUpStats)
        binding.cardDaysSince.startAnimation(fadeUpStats)

        // 5. Chart Carousel (Fade Up Last)
        val fadeUpChart = AnimationUtils.loadAnimation(this, com.liftpath.R.anim.fade_in_up)
        fadeUpChart.startOffset = 600
        binding.cardChartsCarousel.startAnimation(fadeUpChart)
    }

    private fun setupDefaultExercises() {
        val trainingData = jsonHelper.readTrainingData()
        if (trainingData.exerciseLibrary.isEmpty()) {
            val defaultExercises = listOf(
                ExerciseLibraryItem(
                    id = 1,
                    name = "Deadlift",
                    pattern = com.liftpath.models.MovementPattern.HINGE,
                    manualMechanics = com.liftpath.models.Mechanics.COMPOUND,
                    tier = com.liftpath.models.Tier.TIER_1
                ),
                ExerciseLibraryItem(
                    id = 2,
                    name = "Squat",
                    pattern = com.liftpath.models.MovementPattern.SQUAT,
                    manualMechanics = com.liftpath.models.Mechanics.COMPOUND,
                    tier = com.liftpath.models.Tier.TIER_1
                ),
                ExerciseLibraryItem(
                    id = 3,
                    name = "Bench Press",
                    pattern = com.liftpath.models.MovementPattern.PUSH_HORIZONTAL,
                    manualMechanics = com.liftpath.models.Mechanics.COMPOUND,
                    tier = com.liftpath.models.Tier.TIER_1
                ),
                ExerciseLibraryItem(
                    id = 4,
                    name = "Biceps Curl",
                    pattern = com.liftpath.models.MovementPattern.ISOLATION_ARMS,
                    manualMechanics = com.liftpath.models.Mechanics.ISOLATION,
                    tier = com.liftpath.models.Tier.TIER_3
                ),
                ExerciseLibraryItem(
                    id = 5,
                    name = "Triceps Pushdown",
                    pattern = com.liftpath.models.MovementPattern.ISOLATION_ARMS,
                    manualMechanics = com.liftpath.models.Mechanics.ISOLATION,
                    tier = com.liftpath.models.Tier.TIER_3
                )
            )
            trainingData.exerciseLibrary.addAll(defaultExercises)
            jsonHelper.writeTrainingData(trainingData)
        }
    }

    private fun setupClickListeners() {
        binding.cardStartWorkout.setOnClickListener {
            handleStartWorkout()
        }

        binding.cardViewProgress.setOnClickListener {
            val intent = Intent(this, ProgressActivity::class.java)
            startActivity(intent)
        }

        binding.cardViewHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        binding.cardExercises.setOnClickListener {
            val intent = Intent(this, ExercisesActivity::class.java)
            startActivity(intent)
        }

        binding.cardSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        binding.cardPlans.setOnClickListener {
            val intent = Intent(this, WorkoutPlansActivity::class.java)
            startActivity(intent)
        }

        binding.cardReadiness.setOnClickListener {
            val intent = Intent(this, ReadinessDashboardActivity::class.java)
            startActivity(intent)
        }
        
        // Add click listeners to exercise cards
        binding.cardBenchPress.setOnClickListener {
            showExerciseSelectionDialog(true) // true = left card
        }
        
        binding.cardSquat.setOnClickListener {
            showExerciseSelectionDialog(false) // false = right card
        }
    }

    private fun handleStartWorkout() {
        val existingDraft = draftManager.loadDraft()
        if (existingDraft == null) {
            showWorkoutModeDialog()
            return
        }

        if (existingDraft.entries.isEmpty()) {
            draftManager.clearDraft()
            showWorkoutModeDialog()
            return
        }

        showDraftPromptBeforeWorkoutType(existingDraft)
    }

    /**
     * Detects the last registered workout type and determines the next type.
     * Alternates between heavy and light for periodized progression.
     * If last was heavy, next is light; if last was light, next is heavy.
     * If no history exists, defaults to heavy.
     */
    private fun detectNextWorkoutType(): String {
        val trainingData = jsonHelper.readTrainingData()
        
        // Find the most recent workout that has a workout type (heavy or light, not custom)
        val lastWorkout = trainingData.trainings
            .filter { session ->
                val type = session.defaultWorkoutType
                type == "heavy" || type == "light"
            }
            .maxByOrNull { it.date }
        
        if (lastWorkout == null) {
            // No history, default to heavy
            return "heavy"
        }
        
        val lastType = lastWorkout.defaultWorkoutType ?: "heavy"
        
        // Alternate for periodized progression: if last was heavy, next is light; if last was light, next is heavy
        return when (lastType) {
            "heavy" -> "light"
            "light" -> "heavy"
            else -> "heavy"
        }
    }

    private fun showWorkoutModeDialog() {
        val detectedType = detectNextWorkoutType()
        val typeLabel = detectedType.replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
        }
        
        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.dialog_title_select_workout_mode))
            .setMessage("Detected next workout: $typeLabel\n\nContinue with plan progression or create a custom workout?")
            .setPositiveButton("Continue Plan") { _, _ ->
                // Launch with auto-generate enabled for "Continue Plan"
                launchActiveWorkout(detectedType, resumeDraft = false, autoGenerate = true)
            }
            .setNeutralButton("Custom") { _, _ ->
                // Custom workouts don't auto-generate
                launchActiveWorkout("custom", resumeDraft = false, autoGenerate = false)
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    private fun startWorkoutWithType(workoutType: String, skipDraftPrompt: Boolean = false, autoGenerate: Boolean = false) {
        if (!skipDraftPrompt) {
            val existingDraft = draftManager.loadDraft()
            if (existingDraft != null && existingDraft.entries.isNotEmpty()) {
                showResumeDraftDialog(workoutType, existingDraft, autoGenerate)
                return
            }
        }
        launchActiveWorkout(workoutType, resumeDraft = false, autoGenerate = autoGenerate)
    }

    private fun showDraftPromptBeforeWorkoutType(draft: ActiveWorkoutDraft) {
        val message = getString(R.string.dialog_message_resume_workout_simple, draft.workoutType, draft.date)

        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.dialog_title_resume_workout))
            .setMessage(message)
            .setPositiveButton(getString(R.string.button_resume)) { _, _ ->
                launchActiveWorkout(draft.workoutType, resumeDraft = true)
            }
            .setNegativeButton(getString(R.string.button_start_new)) { _, _ ->
                draftManager.clearDraft()
                showWorkoutModeDialog()
            }
            .setNeutralButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    private fun showResumeDraftDialog(requestedType: String, draft: ActiveWorkoutDraft, autoGenerate: Boolean = false) {
        val message = getString(R.string.dialog_message_resume_workout, draft.workoutType, draft.date)

        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.dialog_title_resume_workout))
            .setMessage(message)
            .setPositiveButton(getString(R.string.button_resume)) { _, _ ->
                launchActiveWorkout(draft.workoutType, resumeDraft = true, autoGenerate = false)
            }
            .setNegativeButton(getString(R.string.button_discard)) { _, _ ->
                draftManager.clearDraft()
                launchActiveWorkout(requestedType, resumeDraft = false, autoGenerate = autoGenerate)
            }
            .setNeutralButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    private fun launchActiveWorkout(workoutType: String, resumeDraft: Boolean, autoGenerate: Boolean = false) {
        val intent = Intent(this, ActiveTrainingActivity::class.java).apply {
            putExtra(ActiveTrainingActivity.EXTRA_WORKOUT_TYPE, workoutType)
            putExtra(ActiveTrainingActivity.EXTRA_RESUME_DRAFT, resumeDraft)
            putExtra(ActiveTrainingActivity.EXTRA_AUTO_GENERATE, autoGenerate)
        }
        startWorkoutForResult.launch(intent)
    }

    private fun updateStats() {
        val trainingData = jsonHelper.readTrainingData()
        
        // Get selected exercises from preferences
        val leftExercise = prefs.getString(KEY_LEFT_EXERCISE, DEFAULT_LEFT_EXERCISE) ?: DEFAULT_LEFT_EXERCISE
        val rightExercise = prefs.getString(KEY_RIGHT_EXERCISE, DEFAULT_RIGHT_EXERCISE) ?: DEFAULT_RIGHT_EXERCISE
        
        // Update card labels
        binding.textLeftExerciseName.text = leftExercise
        binding.textRightExerciseName.text = rightExercise
        
        // Enable marquee scrolling for long exercise names
        enableMarqueeScrolling(binding.textLeftExerciseName)
        enableMarqueeScrolling(binding.textRightExerciseName)
        
        // Calculate and display left exercise 1RM
        val leftExercise1RM = calculateCurrent1RM(leftExercise, trainingData)
        val leftExerciseTrend = calculateProgressionTrend(leftExercise, trainingData)
        update1RMDisplay(binding.textBenchPress1rm, binding.textBenchPressIndicator, leftExercise1RM, leftExerciseTrend)
        
        // Calculate and display right exercise 1RM
        val rightExercise1RM = calculateCurrent1RM(rightExercise, trainingData)
        val rightExerciseTrend = calculateProgressionTrend(rightExercise, trainingData)
        update1RMDisplay(binding.textSquat1rm, binding.textSquatIndicator, rightExercise1RM, rightExerciseTrend)
        
        // Calculate days since last heavy and light workouts
        val daysSinceHeavy = calculateDaysSinceLastWorkout(trainingData, "heavy")
        val daysSinceLight = calculateDaysSinceLastWorkout(trainingData, "light")
        
        binding.textDaysHeavy.text = if (daysSinceHeavy != null) daysSinceHeavy.toString() else "--"
        binding.textDaysLight.text = if (daysSinceLight != null) daysSinceLight.toString() else "--"
        
        // Setup charts carousel
        setupChartsCarousel(trainingData)
    }
    
    private fun showExerciseSelectionDialog(isLeftCard: Boolean) {
        val trainingData = jsonHelper.readTrainingData()
        val exerciseNames = trainingData.exerciseLibrary.map { it.name }.sorted()
        
        if (exerciseNames.isEmpty()) {
            DialogHelper.createBuilder(this)
                .setTitle(getString(R.string.dialog_title_no_exercises))
                .setMessage(getString(R.string.dialog_message_no_exercises))
                .setPositiveButton(getString(R.string.button_ok), null)
                .showWithTransparentWindow()
            return
        }
        
        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.dialog_title_select_exercise))
            .setItems(exerciseNames.toTypedArray()) { _, which ->
                val selectedExercise = exerciseNames[which]
                val key = if (isLeftCard) KEY_LEFT_EXERCISE else KEY_RIGHT_EXERCISE
                prefs.edit().putString(key, selectedExercise).apply()
                updateStats() // Refresh the display
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }
    
    private fun calculateCurrent1RM(exerciseName: String, trainingData: TrainingData): Float? {
        val allSets = trainingData.trainings.flatMap { session ->
            session.exercises.filter { entry ->
                entry.exerciseName == exerciseName && 
                (entry.workoutType == "heavy" || (entry.workoutType == null && session.defaultWorkoutType == "heavy"))
            }
        }
        
        if (allSets.isEmpty()) return null
        
        // Calculate 1RM for each set and return the maximum
        val oneRMs = allSets.map { calculateOneRM(it.kg, it.reps) }
        return oneRMs.maxOrNull()
    }
    
    private fun calculateOneRM(weight: Float, reps: Int): Float {
        // Epley's formula: 1RM = weight × (1 + reps/30)
        if (reps <= 0) return weight
        if (reps == 1) return weight
        return weight * (1 + reps / 30f)
    }
    
    private fun calculateProgressionTrend(exerciseName: String, trainingData: TrainingData): String {
        // Get all sessions with this exercise (only heavy workouts), sorted by date
        val sessionsWithExercise = trainingData.trainings
            .filter { session ->
                session.exercises.any { entry ->
                    entry.exerciseName == exerciseName &&
                    (entry.workoutType == "heavy" || (entry.workoutType == null && session.defaultWorkoutType == "heavy"))
                }
            }
            .sortedBy { it.date }
        
        if (sessionsWithExercise.size < 2) return "steady"
        
        // Get last 3 sessions for trend analysis
        val recentSessions = sessionsWithExercise.takeLast(3)
        
        // Calculate max 1RM per session (only from heavy sets)
        val oneRMsPerSession = recentSessions.mapNotNull { session ->
            val exerciseSets = session.exercises.filter { entry ->
                entry.exerciseName == exerciseName &&
                (entry.workoutType == "heavy" || (entry.workoutType == null && session.defaultWorkoutType == "heavy"))
            }
            exerciseSets.maxOfOrNull { calculateOneRM(it.kg, it.reps) }
        }
        
        if (oneRMsPerSession.size < 2) return "steady"
        
        // Compare last two 1RM values
        val last1RM = oneRMsPerSession.last()
        val previous1RM = oneRMsPerSession[oneRMsPerSession.size - 2]
        
        val difference = last1RM - previous1RM
        val threshold = 1.0f // Consider 1kg difference as significant
        
        return when {
            difference > threshold -> "increasing"
            difference < -threshold -> "decreasing"
            else -> "steady"
        }
    }
    
    private fun update1RMDisplay(valueTextView: android.widget.TextView, indicatorTextView: android.widget.TextView, oneRM: Float?, trend: String) {
        if (oneRM != null) {
            valueTextView.text = String.format(Locale.US, "%.1f", oneRM)
        } else {
            valueTextView.text = "--"
        }
        
        // Set indicator based on trend
        when (trend) {
            "increasing" -> {
                indicatorTextView.text = "↑"
                indicatorTextView.setTextColor(Color.parseColor("#4CAF50")) // Green
            }
            "decreasing" -> {
                indicatorTextView.text = "↓"
                indicatorTextView.setTextColor(Color.parseColor("#F44336")) // Red
            }
            else -> {
                indicatorTextView.text = "○"
                indicatorTextView.setTextColor(Color.parseColor("#2196F3")) // Blue
            }
        }
    }
    
    private fun calculateDaysSinceLastWorkout(trainingData: TrainingData, workoutType: String): Int? {
        // Find the most recent workout of the specified type
        val lastWorkout = trainingData.trainings
            .filter { session ->
                session.defaultWorkoutType == workoutType || 
                session.exercises.any { it.workoutType == workoutType }
            }
            .maxByOrNull { it.date }
        
        if (lastWorkout == null) return null
        
        return try {
            val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US)
            val lastDate = dateFormat.parse(lastWorkout.date) ?: return null
            val today = Date()
            val diffMillis = today.time - lastDate.time
            val days = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
            maxOf(0, days)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun setupChartsCarousel(trainingData: TrainingData) {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US)
        
        // Calculate volume chart data
        val volumeEntries = trainingData.trainings
            .mapNotNull { session ->
                val totalVolume = session.exercises.sumOf { (it.kg * it.reps).toDouble() }.toFloat()
                try {
                    val date = dateFormat.parse(session.date) ?: return@mapNotNull null
                    Entry(date.time.toFloat(), totalVolume)
                } catch (e: Exception) {
                    null
                }
            }
            .sortedBy { it.x }
        
        // Calculate average RPE per session
        val rpeEntries = trainingData.trainings
            .mapNotNull { session ->
                val rpeValues = session.exercises.mapNotNull { it.rpe }
                if (rpeValues.isEmpty()) return@mapNotNull null
                val avgRpe = rpeValues.average().toFloat()
                try {
                    val date = dateFormat.parse(session.date) ?: return@mapNotNull null
                    Entry(date.time.toFloat(), avgRpe)
                } catch (e: Exception) {
                    null
                }
            }
            .sortedBy { it.x }
        
        // Calculate time consumption per session (in minutes)
        val timeEntries = trainingData.trainings
            .mapNotNull { session ->
                val durationSeconds = session.durationSeconds
                if (durationSeconds == null || durationSeconds <= 0) return@mapNotNull null
                val durationMinutes = (durationSeconds / 60f)
                try {
                    val date = dateFormat.parse(session.date) ?: return@mapNotNull null
                    Entry(date.time.toFloat(), durationMinutes)
                } catch (e: Exception) {
                    null
                }
            }
            .sortedBy { it.x }
        
        // Calculate raw fatigue per session - show all dates from last 28 days
        val config = ReadinessConfig() // Use default config
        
        // Create a map of date -> (fatigue, workoutType)
        val fatigueByDate = mutableMapOf<String, Pair<Float, String?>>()
        
        trainingData.trainings.forEach { session ->
            try {
                val sessionDate = dateFormat.parse(session.date) ?: return@forEach
                val fatigueScores = ReadinessHelper.calculateFatigueScores(session, trainingData, config)
                val rawFatigue = fatigueScores.systemicFatigue
                if (rawFatigue > 0) {
                    fatigueByDate[session.date] = Pair(rawFatigue, session.defaultWorkoutType)
                }
            } catch (e: Exception) {
                // Skip invalid dates
            }
        }
        
        // Generate all dates for the last 28 days
        val calendar = java.util.Calendar.getInstance()
        val today = calendar.time
        val allDates = mutableListOf<Pair<Long, Pair<Float, String?>>>()
        
        for (i in 0 until 28) {
            calendar.time = today
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -i)
            val date = calendar.time
            val dateStr = dateFormat.format(date)
            val dateMillis = date.time
            
            val (fatigue, workoutType) = fatigueByDate[dateStr] ?: Pair(0f, null)
            allDates.add(Pair(dateMillis, Pair(fatigue, workoutType)))
        }
        
        // Sort by date (oldest first)
        allDates.sortBy { it.first }
        
        val fatigueEntries = allDates.map { (dateMillis, fatigueData) ->
            Entry(dateMillis.toFloat(), fatigueData.first)
        }
        
        val workoutTypes = allDates.map { it.second.second }
        
        // Create chart data list
        val charts = listOf(
            ChartData(
                type = ChartType.VOLUME,
                entries = volumeEntries,
                title = "Volume Trends",
                color = Color.parseColor("#4CAF50"), // Green
                yAxisLabel = "Volume (kg)"
            ),
            ChartData(
                type = ChartType.AVG_RPE,
                entries = rpeEntries,
                title = "Average RPE",
                color = Color.parseColor("#FF9800"), // Orange
                yAxisLabel = "RPE"
            ),
            ChartData(
                type = ChartType.TIME_CONSUMPTION,
                entries = timeEntries,
                title = "Time Consumption",
                color = Color.parseColor("#2196F3"), // Blue
                yAxisLabel = "Time (min)"
            ),
            ChartData(
                type = ChartType.FATIGUE,
                entries = fatigueEntries,
                title = "Raw Fatigue",
                color = Color.parseColor("#F44336"), // Red (default, but will be overridden by color coding)
                yAxisLabel = "Fatigue",
                workoutTypes = workoutTypes
            )
        )
        
        // Setup ViewPager2
        val adapter = ChartCarouselAdapter(charts)
        binding.viewpagerCharts.adapter = adapter
        
        // Setup TabLayout
        TabLayoutMediator(binding.tabLayoutCharts, binding.viewpagerCharts) { tab, position ->
            tab.text = when (position) {
                0 -> "Volume"
                1 -> "RPE"
                2 -> "Time"
                3 -> "Fatigue"
                else -> ""
            }
        }.attach()
    }
    
    private fun autoSyncHealthConnect() {
        // Check if Health Connect is enabled
        val healthConnectPrefs = getSharedPreferences("health_connect_settings", Context.MODE_PRIVATE)
        val isEnabled = healthConnectPrefs.getBoolean("use_health_connect_data", false)
        
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
                    // Sync successful, no UI feedback needed for auto-sync
                },
                onFailure = { error ->
                    // Sync failed silently (already logged in helper)
                }
            )
        }
    }
    
    private fun enableMarqueeScrolling(textView: android.widget.TextView) {
        textView.isSelected = true
        textView.isSingleLine = true
        textView.ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
        textView.marqueeRepeatLimit = -1 // Infinite scrolling
    }
}

