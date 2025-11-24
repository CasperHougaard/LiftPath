package com.liftpath.activities

import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
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
import com.liftpath.helpers.ReadinessSettingsManager
import com.liftpath.models.TrainingSession
import java.text.SimpleDateFormat
import java.util.*

class ReadinessDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReadinessDashboardBinding
    private lateinit var jsonHelper: JsonHelper
    private lateinit var settingsManager: ReadinessSettingsManager
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

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
        val fatigueScores = ReadinessHelper.calculateFatigueScores(
            lastWorkout,
            trainingData,
            config
        )

        // Update last workout summary
        updateLastWorkoutSummary(lastWorkout, fatigueScores)

        // Calculate and update activity readiness
        updateActivityReadiness(fatigueScores, config)
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
        fatigueScores: FatigueScores
    ) {
        binding.textWorkoutDate.text = "Date: ${workout.date}"
        binding.textLowerFatigue.text = String.format("%.1f", fatigueScores.lowerFatigue)
        binding.textUpperFatigue.text = String.format("%.1f", fatigueScores.upperFatigue)
        binding.textSystemicFatigue.text = String.format("%.1f", fatigueScores.systemicFatigue)
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

        // Update message
        messageText.text = readiness.message

        // Update countdown if recovery time is set
        if (readiness.timeUntilFresh != null) {
            updateCountdown(countdownText, readiness.timeUntilFresh)
            countdownText.visibility = View.VISIBLE
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
                    val fatigueScores = ReadinessHelper.calculateFatigueScores(
                        lastWorkout,
                        trainingData,
                        config
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
}

