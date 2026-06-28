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
import androidx.core.content.ContextCompat
import com.liftpath.databinding.ActivityMainBinding
import com.liftpath.helpers.ActiveWorkoutDraftManager
import com.liftpath.helpers.BodyWeightDialogs
import com.liftpath.helpers.BodyWeightHelper
import com.liftpath.helpers.CatalogMergeHelper
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.HealthConnectHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.WithingsHealthConnectHelper
import com.liftpath.helpers.showWithTransparentWindow
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.liftpath.models.ActiveWorkoutDraft
import com.liftpath.models.TrainingData
import com.liftpath.models.SetIntent
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.liftpath.adapters.ChartCarouselAdapter
import com.liftpath.adapters.ChartType
import com.liftpath.adapters.ChartData
import com.liftpath.helpers.ProgressAnalysisHelper
import com.liftpath.helpers.ReadinessHelper
import com.liftpath.helpers.ReadinessConfig
import com.google.android.material.tabs.TabLayoutMediator
import com.liftpath.components.SelectWorkoutModeBottomSheet
import java.text.SimpleDateFormat
import java.util.Calendar
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

        // Apply Window Insets to handle system bars (status/nav)
        // We apply padding only to the content container, not the root,
        // so the background animation can bleed under the system bars.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Apply padding to the ScrollView content or the ScrollView itself
            // but NOT the root view (which contains the background)
            binding.scrollView.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            
            windowInsets
        }

        jsonHelper = JsonHelper(this)
        draftManager = ActiveWorkoutDraftManager(this)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setupClickListeners()
        setupBackgroundAnimation()
        runEntranceAnimations()
        updateStats()

        binding.root.post {
            CatalogMergeHelper.checkAndOfferIfNeeded(this, jsonHelper, supportFragmentManager)
        }
        
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
        binding.cardHomeMomentum.startAnimation(fadeUpStats)

        // 4b. Insights row (same wave, slightly later)
        val fadeUpInsights = AnimationUtils.loadAnimation(this, com.liftpath.R.anim.fade_in_up)
        fadeUpInsights.startOffset = 550
        binding.layoutHomeInsights.startAnimation(fadeUpInsights)

        // 5. Chart Carousel (Fade Up Last)
        val fadeUpChart = AnimationUtils.loadAnimation(this, com.liftpath.R.anim.fade_in_up)
        fadeUpChart.startOffset = 600
        binding.cardChartsCarousel.startAnimation(fadeUpChart)
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


    private fun showWorkoutModeDialog() {
        showWorkoutModeBottomSheet()
    }

    private fun showWorkoutModeBottomSheet() {
        val bottomSheet = SelectWorkoutModeBottomSheet.newInstance(
            onCustomSelected = {
                launchActiveWorkout("custom", resumeDraft = false, autoGenerate = false, planId = null)
            },
            onPlanSelected = { plan, planSet ->
                launchActiveWorkout(plan.workoutType, resumeDraft = false, autoGenerate = false, planId = plan.id, planSetId = planSet?.id)
            }
        )
        bottomSheet.show(supportFragmentManager, "SelectWorkoutModeBottomSheet")
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
                showWorkoutModeBottomSheet()
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

    private fun launchActiveWorkout(workoutType: String, resumeDraft: Boolean, autoGenerate: Boolean = false, planId: String? = null, planSetId: String? = null) {
        val intent = Intent(this, ActiveTrainingActivity::class.java).apply {
            putExtra(ActiveTrainingActivity.EXTRA_WORKOUT_TYPE, workoutType)
            putExtra(ActiveTrainingActivity.EXTRA_RESUME_DRAFT, resumeDraft)
            putExtra(ActiveTrainingActivity.EXTRA_AUTO_GENERATE, autoGenerate)
            if (planId != null) putExtra(ActiveTrainingActivity.EXTRA_PLAN_ID, planId)
            if (planSetId != null) putExtra(ActiveTrainingActivity.EXTRA_PLAN_SET_ID, planSetId)
        }
        startWorkoutForResult.launch(intent)
    }

    private fun updateStats() {
        val trainingData = jsonHelper.readTrainingData()

        // Preferences store exercise names; look up IDs so session filtering is ID-based
        val leftExerciseName = prefs.getString(KEY_LEFT_EXERCISE, DEFAULT_LEFT_EXERCISE) ?: DEFAULT_LEFT_EXERCISE
        val rightExerciseName = prefs.getString(KEY_RIGHT_EXERCISE, DEFAULT_RIGHT_EXERCISE) ?: DEFAULT_RIGHT_EXERCISE

        val leftLibItem = trainingData.exerciseLibrary.find { it.name == leftExerciseName }
        val rightLibItem = trainingData.exerciseLibrary.find { it.name == rightExerciseName }

        // Update card labels
        binding.textLeftExerciseName.text = leftLibItem?.name ?: leftExerciseName
        binding.textRightExerciseName.text = rightLibItem?.name ?: rightExerciseName

        // Enable marquee scrolling for long exercise names
        enableMarqueeScrolling(binding.textLeftExerciseName)
        enableMarqueeScrolling(binding.textRightExerciseName)

        // Calculate and display left exercise 1RM
        val leftExercise1RM = leftLibItem?.let { calculateCurrent1RM(it.id, trainingData) }
        val leftExerciseTrend = leftLibItem?.let { calculateProgressionTrend(it.id, trainingData) } ?: "steady"
        update1RMDisplay(binding.textBenchPress1rm, binding.textBenchPressIndicator, leftExercise1RM, leftExerciseTrend)

        // Calculate and display right exercise 1RM
        val rightExercise1RM = rightLibItem?.let { calculateCurrent1RM(it.id, trainingData) }
        val rightExerciseTrend = rightLibItem?.let { calculateProgressionTrend(it.id, trainingData) } ?: "steady"
        update1RMDisplay(binding.textSquat1rm, binding.textSquatIndicator, rightExercise1RM, rightExerciseTrend)
        
        updateHomeMomentumCard(trainingData)
        updateHomeInsights(trainingData)

        // Setup charts carousel
        setupChartsCarousel(trainingData)
    }

    private fun updateHomeMomentumCard(trainingData: TrainingData) {
        val summary = ProgressAnalysisHelper.getRollingDaysSummary(trainingData.trainings, dayCount = 21)

        if (summary.sessionCount == 0) {
            binding.textHomeWeekSummary.text = getString(R.string.home_week_no_sessions)
            binding.textHomeWeekInsight.text = getString(R.string.home_week_insight_empty)
        } else {
            binding.textHomeWeekSummary.text = resources.getQuantityString(
                R.plurals.home_week_sessions_volume,
                summary.sessionCount,
                summary.sessionCount,
                summary.totalVolume.toInt()
            )
            binding.textHomeWeekInsight.text = when (summary.dominantIntent) {
                SetIntent.STRENGTH -> getString(R.string.home_week_style_strength)
                SetIntent.FLUSH    -> getString(R.string.home_week_style_flush)
                else               -> getString(R.string.home_week_style_build)
            }
        }
    }

    private fun updateHomeInsights(trainingData: TrainingData) {
        updateBuildStrengthRpeCard(trainingData)
        updateLastWorkoutCard(trainingData)
    }

    private fun updateBuildStrengthRpeCard(trainingData: TrainingData) {
        val (buildAvg, strengthAvg) = ProgressAnalysisHelper.getBuildStrengthRpeAverages(
            trainingData.trainings,
            dayCount = 21
        )
        val sub = getString(R.string.home_rpe_sub)
        when {
            buildAvg != null && strengthAvg != null -> {
                binding.textHomeWinsHeadline.text = String.format(
                    Locale.US,
                    "%.1f · %.1f",
                    buildAvg,
                    strengthAvg
                )
                binding.textHomeWinsSub.text = sub
            }
            buildAvg != null -> {
                binding.textHomeWinsHeadline.text = String.format(Locale.US, "%.1f", buildAvg)
                binding.textHomeWinsSub.text = sub
            }
            strengthAvg != null -> {
                binding.textHomeWinsHeadline.text = String.format(Locale.US, "%.1f", strengthAvg)
                binding.textHomeWinsSub.text = sub
            }
            else -> {
                binding.textHomeWinsHeadline.text = getString(R.string.home_rpe_none_headline)
                binding.textHomeWinsSub.text = getString(R.string.home_rpe_none_sub)
            }
        }
    }

    private fun updateLastWorkoutCard(trainingData: TrainingData) {
        val lastSession = trainingData.trainings.maxByOrNull { it.date }
        if (lastSession == null) {
            binding.textHomeLastWorkoutWhen.text = getString(R.string.home_last_workout_never)
            binding.textHomeLastWorkoutDetail.text = getString(R.string.home_last_workout_never_sub)
            binding.textHomeLastWorkoutWhen.setTextColor(ContextCompat.getColor(this, R.color.fitness_primary))
            return
        }

        val daysBetween = calendarDaysBetweenSessionDateAndToday(lastSession.date)

        binding.textHomeLastWorkoutWhen.text = when (daysBetween) {
            null -> "—"
            0    -> getString(R.string.home_last_workout_today)
            1    -> getString(R.string.home_last_workout_yesterday)
            else -> getString(R.string.home_last_workout_days_ago, daysBetween)
        }

        val whenColor = when (daysBetween) {
            null, in 0..2 -> R.color.fitness_primary
            3             -> R.color.pr_fresh
            4             -> R.color.fitness_accent
            else          -> R.color.fitness_error
        }
        binding.textHomeLastWorkoutWhen.setTextColor(ContextCompat.getColor(this, whenColor))

        val workingSets = lastSession.exercises.filterNot { it.isWarmup }
        val exerciseCount = lastSession.exercises.map { it.exerciseId }.distinct().size
        val volume = workingSets.sumOf { (it.kg * it.reps).toDouble() }.toInt()
        binding.textHomeLastWorkoutDetail.text = getString(
            R.string.home_last_workout_detail,
            exerciseCount,
            volume
        )
    }

    /** Whole calendar days from session local date to today (0 = same calendar day). */
    private fun calendarDaysBetweenSessionDateAndToday(sessionDateStr: String): Int? {
        return try {
            val fmt = SimpleDateFormat("yyyy/MM/dd", Locale.US)
            val sessionDay = Calendar.getInstance().apply {
                time = fmt.parse(sessionDateStr) ?: return null
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val diff = ((today.timeInMillis - sessionDay.timeInMillis) / 86_400_000L).toInt()
            diff.coerceAtLeast(0)
        } catch (_: Exception) {
            null
        }
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
    
    private fun calculateCurrent1RM(exerciseId: Int, trainingData: TrainingData): Float? {
        val allSets = trainingData.trainings.flatMap { session ->
            session.exercises.filter { entry ->
                entry.exerciseId == exerciseId &&
                entry.getEffectiveIntent(session.defaultWorkoutType) == SetIntent.STRENGTH
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
    
    private fun calculateProgressionTrend(exerciseId: Int, trainingData: TrainingData): String {
        // Get all sessions with this exercise (only STRENGTH intent sets), sorted by date
        val sessionsWithExercise = trainingData.trainings
            .filter { session ->
                session.exercises.any { entry ->
                    entry.exerciseId == exerciseId &&
                    entry.getEffectiveIntent(session.defaultWorkoutType) == SetIntent.STRENGTH
                }
            }
            .sortedBy { it.date }

        if (sessionsWithExercise.size < 2) return "steady"

        // Get last 3 sessions for trend analysis
        val recentSessions = sessionsWithExercise.takeLast(3)

        // Calculate max 1RM per session (only from STRENGTH intent sets)
        val oneRMsPerSession = recentSessions.mapNotNull { session ->
            val exerciseSets = session.exercises.filter { entry ->
                entry.exerciseId == exerciseId &&
                entry.getEffectiveIntent(session.defaultWorkoutType) == SetIntent.STRENGTH
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
        
        // Create a map of date -> (fatigue, dominantIntent)
        val fatigueByDate = mutableMapOf<String, Pair<Float, SetIntent>>()
        
        trainingData.trainings.forEach { session ->
            try {
                val sessionDate = dateFormat.parse(session.date) ?: return@forEach
                val fatigueScores = ReadinessHelper.calculateFatigueScores(session, trainingData, config)
                val rawFatigue = fatigueScores.systemicFatigue
                if (rawFatigue > 0) {
                    val dominantIntent = session.getDominantIntent()
                    fatigueByDate[session.date] = Pair(rawFatigue, dominantIntent)
                }
            } catch (e: Exception) {
                // Skip invalid dates
            }
        }
        
        // Generate all dates for the last 28 days
        val calendar = java.util.Calendar.getInstance()
        val today = calendar.time
        val allDates = mutableListOf<Pair<Long, Pair<Float, SetIntent>>>()
        
        for (i in 0 until 28) {
            calendar.time = today
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -i)
            val date = calendar.time
            val dateStr = dateFormat.format(date)
            val dateMillis = date.time
            
            val (fatigue, intent) = fatigueByDate[dateStr] ?: Pair(0f, SetIntent.BUILD)
            allDates.add(Pair(dateMillis, Pair(fatigue, intent)))
        }
        
        // Sort by date (oldest first)
        allDates.sortBy { it.first }
        
        val fatigueEntries = allDates.map { (dateMillis, fatigueData) ->
            Entry(dateMillis.toFloat(), fatigueData.first)
        }
        
        val dominantIntents = allDates.map { it.second.second }
        
        // Create chart data list
        val charts = listOf(
            ChartData(
                type = ChartType.VOLUME,
                entries = volumeEntries,
                title = getString(R.string.home_chart_volume_trends),
                color = Color.parseColor("#4CAF50"), // Green
                yAxisLabel = getString(R.string.home_chart_axis_volume)
            ),
            ChartData(
                type = ChartType.AVG_RPE,
                entries = rpeEntries,
                title = getString(R.string.home_chart_avg_rpe),
                color = Color.parseColor("#FF9800"), // Orange
                yAxisLabel = getString(R.string.home_chart_axis_rpe)
            ),
            ChartData(
                type = ChartType.TIME_CONSUMPTION,
                entries = timeEntries,
                title = getString(R.string.home_chart_time_consumption),
                color = Color.parseColor("#2196F3"), // Blue
                yAxisLabel = getString(R.string.home_chart_axis_time)
            ),
            ChartData(
                type = ChartType.FATIGUE,
                entries = fatigueEntries,
                title = getString(R.string.home_chart_fatigue),
                color = Color.parseColor("#F44336"), // Red (default, but will be overridden by color coding)
                yAxisLabel = getString(R.string.home_chart_axis_fatigue),
                dominantIntents = dominantIntents
            )
        )
        
        // Setup ViewPager2
        val adapter = ChartCarouselAdapter(charts)
        binding.viewpagerCharts.adapter = adapter
        
        // Setup TabLayout
        TabLayoutMediator(binding.tabLayoutCharts, binding.viewpagerCharts) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.home_chart_tab_volume)
                1 -> getString(R.string.home_chart_tab_rpe)
                2 -> getString(R.string.home_chart_tab_time)
                3 -> getString(R.string.home_chart_tab_fatigue)
                else -> ""
            }
        }.attach()
    }
    
    private fun autoSyncHealthConnect() {
        // Check if Health Connect is enabled
        val healthConnectPrefs = getSharedPreferences("health_connect_settings", Context.MODE_PRIVATE)
        val isEnabled = healthConnectPrefs.getBoolean("use_health_connect_data", false)

        if (!isEnabled || !HealthConnectHelper.isAvailable(this)) {
            // No Withings sync will run; check the body-weight prompt against current data.
            maybePromptBodyWeight()
            return
        }

        // Perform sync in background (silently, no UI feedback)
        lifecycleScope.launch {
            HealthConnectHelper.autoSyncActivities(applicationContext).fold(
                onSuccess = { _ -> },
                onFailure = { }  // logged in helper
            )
        }

        // Sync Withings body-scan data silently in the background, then evaluate the body-weight
        // prompt so it reflects the freshest Withings reading (the sync is otherwise fire-and-forget).
        // lifecycleScope resumes on the main thread after the suspend call, so UI access is safe.
        lifecycleScope.launch {
            WithingsHealthConnectHelper.autoSync(applicationContext)
            maybePromptBodyWeight()
        }
    }

    private var bodyWeightDialogVisible = false

    /** Show a body-weight prompt if due. No-op for fresh-Withings / first-time cases. */
    private fun maybePromptBodyWeight() {
        if (isFinishing || isDestroyed || bodyWeightDialogVisible) return
        when (BodyWeightHelper.evaluateBodyWeightPrompt(this)) {
            BodyWeightHelper.BodyWeightPromptType.MANUAL_RECURRING -> {
                val current = BodyWeightHelper.getCurrentBodyweightKg(this) ?: return
                bodyWeightDialogVisible = true
                BodyWeightDialogs.showRecurringManualPrompt(this, current) {
                    bodyWeightDialogVisible = false
                }
            }
            BodyWeightHelper.BodyWeightPromptType.WITHINGS_STALE -> {
                val latest = BodyWeightHelper.latestWithingsWeight(this) ?: return
                bodyWeightDialogVisible = true
                BodyWeightDialogs.showWithingsStalePrompt(this, latest.first, latest.second) {
                    bodyWeightDialogVisible = false
                }
            }
            else -> { /* NONE / NEEDS_INITIAL: nothing on app open */ }
        }
    }
    
    private fun enableMarqueeScrolling(textView: android.widget.TextView) {
        textView.isSelected = true
        textView.isSingleLine = true
        textView.ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
        textView.marqueeRepeatLimit = -1 // Infinite scrolling
    }
}

