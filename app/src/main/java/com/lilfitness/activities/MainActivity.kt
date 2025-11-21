package com.lilfitness.activities

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
import com.lilfitness.R
import androidx.appcompat.app.AppCompatActivity
import com.lilfitness.databinding.ActivityMainBinding
import com.lilfitness.helpers.ActiveWorkoutDraftManager
import com.lilfitness.helpers.DialogHelper
import com.lilfitness.helpers.JsonHelper
import com.lilfitness.helpers.showWithTransparentWindow
import com.lilfitness.models.ActiveWorkoutDraft
import com.lilfitness.models.ExerciseLibraryItem
import com.lilfitness.models.TrainingData
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
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
        private const val DEFAULT_LEFT_EXERCISE = "Bench Press"
        private const val DEFAULT_RIGHT_EXERCISE = "Squat"
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
    }

    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is Animatable) {
            drawable.start()
        }
    }

    private fun runEntranceAnimations() {
        // 1. Header Elements (Title, Subtitle, Settings)
        val fadeDown = AnimationUtils.loadAnimation(this, com.lilfitness.R.anim.fade_in_up)
        binding.textWelcomeTitle.startAnimation(fadeDown)
        binding.textWelcomeSubtitle.startAnimation(fadeDown)
        binding.cardSettings.startAnimation(fadeDown)

        // 2. Main Hero Card (Pop In)
        val popIn = AnimationUtils.loadAnimation(this, com.lilfitness.R.anim.pop_in)
        popIn.startOffset = 200
        binding.cardStartWorkout.startAnimation(popIn)

        // 3. Grid Items (Fade Up Staggered)
        val fadeUp1 = AnimationUtils.loadAnimation(this, com.lilfitness.R.anim.fade_in_up)
        fadeUp1.startOffset = 300
        binding.cardViewProgress.startAnimation(fadeUp1)
        binding.cardViewHistory.startAnimation(fadeUp1)

        val fadeUp2 = AnimationUtils.loadAnimation(this, com.lilfitness.R.anim.fade_in_up)
        fadeUp2.startOffset = 400
        binding.cardExercises.startAnimation(fadeUp2)
        binding.cardPlans.startAnimation(fadeUp2)

        // 4. Stats Section (Fade Up)
        val fadeUpStats = AnimationUtils.loadAnimation(this, com.lilfitness.R.anim.fade_in_up)
        fadeUpStats.startOffset = 500
        binding.textTodayStats.startAnimation(fadeUpStats)
        binding.cardBenchPress.startAnimation(fadeUpStats)
        binding.cardSquat.startAnimation(fadeUpStats)
        binding.cardDaysSince.startAnimation(fadeUpStats)

        // 5. Chart (Fade Up Last)
        val fadeUpChart = AnimationUtils.loadAnimation(this, com.lilfitness.R.anim.fade_in_up)
        fadeUpChart.startOffset = 600
        binding.cardVolumeChart.startAnimation(fadeUpChart)
    }

    private fun setupDefaultExercises() {
        val trainingData = jsonHelper.readTrainingData()
        if (trainingData.exerciseLibrary.isEmpty()) {
            val defaultExercises = listOf(
                ExerciseLibraryItem(id = 1, name = "Deadlift"),
                ExerciseLibraryItem(id = 2, name = "Squat"),
                ExerciseLibraryItem(id = 3, name = "Bench Press"),
                ExerciseLibraryItem(id = 4, name = "Biceps Curl"),
                ExerciseLibraryItem(id = 5, name = "Triceps Pushdown")
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
            showWorkoutTypeDialog()
            return
        }

        if (existingDraft.entries.isEmpty()) {
            draftManager.clearDraft()
            showWorkoutTypeDialog()
            return
        }

        showDraftPromptBeforeWorkoutType(existingDraft)
    }

    private fun showWorkoutTypeDialog() {
        val types = arrayOf("Heavy", "Light", "Custom")

        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.dialog_title_select_workout_type))
            .setItems(types) { _, which ->
                val selectedType = when (which) {
                    0 -> "heavy"
                    1 -> "light"
                    2 -> "custom"
                    else -> "heavy"
                }
                startWorkoutWithType(selectedType, skipDraftPrompt = true)
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    private fun startWorkoutWithType(workoutType: String, skipDraftPrompt: Boolean = false) {
        if (!skipDraftPrompt) {
            val existingDraft = draftManager.loadDraft()
            if (existingDraft != null && existingDraft.entries.isNotEmpty()) {
                showResumeDraftDialog(workoutType, existingDraft)
                return
            }
        }
        launchActiveWorkout(workoutType, resumeDraft = false)
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
                showWorkoutTypeDialog()
            }
            .setNeutralButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    private fun showResumeDraftDialog(requestedType: String, draft: ActiveWorkoutDraft) {
        val message = getString(R.string.dialog_message_resume_workout, draft.workoutType, draft.date)

        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.dialog_title_resume_workout))
            .setMessage(message)
            .setPositiveButton(getString(R.string.button_resume)) { _, _ ->
                launchActiveWorkout(draft.workoutType, resumeDraft = true)
            }
            .setNegativeButton(getString(R.string.button_discard)) { _, _ ->
                draftManager.clearDraft()
                launchActiveWorkout(requestedType, resumeDraft = false)
            }
            .setNeutralButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    private fun launchActiveWorkout(workoutType: String, resumeDraft: Boolean) {
        val intent = Intent(this, ActiveTrainingActivity::class.java).apply {
            putExtra(ActiveTrainingActivity.EXTRA_WORKOUT_TYPE, workoutType)
            putExtra(ActiveTrainingActivity.EXTRA_RESUME_DRAFT, resumeDraft)
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
        
        // Setup volume chart
        setupVolumeChart(trainingData)
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
            session.exercises.filter { it.exerciseName == exerciseName }
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
        // Get all sessions with this exercise, sorted by date
        val sessionsWithExercise = trainingData.trainings
            .filter { session -> session.exercises.any { it.exerciseName == exerciseName } }
            .sortedBy { it.date }
        
        if (sessionsWithExercise.size < 2) return "steady"
        
        // Get last 3 sessions for trend analysis
        val recentSessions = sessionsWithExercise.takeLast(3)
        
        // Calculate max 1RM per session
        val oneRMsPerSession = recentSessions.map { session ->
            val exerciseSets = session.exercises.filter { it.exerciseName == exerciseName }
            exerciseSets.maxOfOrNull { calculateOneRM(it.kg, it.reps) } ?: 0f
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
    
    private fun setupVolumeChart(trainingData: TrainingData) {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US)
        val entries = mutableListOf<Entry>()
        
        // Calculate total volume per session (sum of all kg × reps)
        val volumePerSession = trainingData.trainings.map { session ->
            val totalVolume = session.exercises.sumOf { (it.kg * it.reps).toDouble() }.toFloat()
            Pair(session.date, totalVolume)
        }.sortedBy { it.first } // Sort by date
        
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
        
        if (entries.isEmpty()) {
            binding.chartVolume.visibility = android.view.View.GONE
            return
        }
        
        binding.chartVolume.visibility = android.view.View.VISIBLE
        
        // Sort entries by date
        entries.sortBy { it.x }
        
        // Calculate maximum value for Y-axis
        val maxEntryValue = entries.maxOfOrNull { it.y } ?: 0f
        val niceMaximum = calculateNiceMaximum(maxEntryValue)
        
        val dataSet = LineDataSet(entries, "Volume (kg)")
        dataSet.color = Color.parseColor("#4CAF50") // Green
        dataSet.valueTextColor = Color.DKGRAY
        dataSet.setCircleColor(Color.parseColor("#4CAF50"))
        dataSet.circleRadius = 4f
        dataSet.lineWidth = 2.5f
        dataSet.setDrawValues(false)
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.cubicIntensity = 0.2f
        dataSet.setDrawFilled(true)
        dataSet.fillColor = Color.parseColor("#4CAF50")
        dataSet.fillAlpha = 30
        
        val lineData = LineData(dataSet)
        binding.chartVolume.data = lineData
        
        // Configure X-axis
        val xAxis = binding.chartVolume.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textSize = 10f
        xAxis.textColor = Color.parseColor("#616161")
        xAxis.setLabelCount(minOf(entries.size, 6), true)
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
        xAxis.gridLineWidth = 1f
        xAxis.enableGridDashedLine(8f, 4f, 0f)
        xAxis.setDrawAxisLine(true)
        xAxis.axisLineColor = Color.parseColor("#9E9E9E")
        xAxis.axisLineWidth = 1f
        
        // Configure Y-axis
        val leftAxis = binding.chartVolume.axisLeft
        leftAxis.axisMinimum = 0f
        leftAxis.axisMaximum = niceMaximum
        leftAxis.textSize = 10f
        leftAxis.textColor = Color.parseColor("#616161")
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.parseColor("#E0E0E0")
        leftAxis.gridLineWidth = 1f
        leftAxis.enableGridDashedLine(8f, 4f, 0f)
        leftAxis.setDrawZeroLine(true)
        leftAxis.zeroLineColor = Color.parseColor("#9E9E9E")
        leftAxis.zeroLineWidth = 1f
        leftAxis.setLabelCount(5, true)
        leftAxis.setDrawAxisLine(true)
        leftAxis.axisLineColor = Color.parseColor("#9E9E9E")
        leftAxis.axisLineWidth = 1f
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
        
        binding.chartVolume.axisRight.isEnabled = false
        
        // Configure chart appearance
        binding.chartVolume.description.isEnabled = false
        binding.chartVolume.setBackgroundColor(Color.TRANSPARENT)
        binding.chartVolume.setDrawGridBackground(false)
        
        // Disable legend for compact display
        val legend = binding.chartVolume.legend
        legend.isEnabled = false
        
        // Disable interactions - view only
        binding.chartVolume.setTouchEnabled(false)
        binding.chartVolume.setDragEnabled(false)
        binding.chartVolume.setScaleEnabled(false)
        binding.chartVolume.setPinchZoom(false)
        binding.chartVolume.setDoubleTapToZoomEnabled(false)
        
        // Smooth animation
        binding.chartVolume.animateX(600)
        
        // Refresh chart
        binding.chartVolume.invalidate()
    }
    
    private fun calculateNiceMaximum(maxValue: Float): Float {
        if (maxValue <= 0) return 1000f
        
        // Add 15% padding
        val paddedValue = maxValue * 1.15f
        
        // Round up to nice numbers based on magnitude
        return when {
            paddedValue < 100 -> {
                ((paddedValue / 10).toInt() * 10 + 10).toFloat().coerceAtLeast(50f)
            }
            paddedValue < 500 -> {
                ((paddedValue / 25).toInt() * 25 + 25).toFloat().coerceAtLeast(100f)
            }
            paddedValue < 1000 -> {
                ((paddedValue / 50).toInt() * 50 + 50).toFloat().coerceAtLeast(500f)
            }
            paddedValue < 5000 -> {
                ((paddedValue / 250).toInt() * 250 + 250).toFloat().coerceAtLeast(1000f)
            }
            else -> {
                ((paddedValue / 500).toInt() * 500 + 500).toFloat().coerceAtLeast(5000f)
            }
        }
    }
    
    private fun enableMarqueeScrolling(textView: android.widget.TextView) {
        textView.isSelected = true
        textView.isSingleLine = true
        textView.ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
        textView.marqueeRepeatLimit = -1 // Infinite scrolling
    }
}

