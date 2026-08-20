package com.liftpath.activities

import android.app.Activity
import android.graphics.drawable.Animatable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.liftpath.R
import com.liftpath.adapters.ExerciseTrendAdapter
import com.liftpath.databinding.ActivityWorkoutReportBinding
import com.liftpath.helpers.DurationHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.MuscleMapColorResolver
import com.liftpath.helpers.MuscleMapRenderer
import com.liftpath.helpers.RestTimerHelper
import com.liftpath.helpers.WorkoutComparisonHelper
import com.liftpath.models.TargetMuscle
import com.liftpath.models.TrainingSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorkoutReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorkoutReportBinding
    private lateinit var jsonHelper: JsonHelper
    private lateinit var trainingSession: TrainingSession

    companion object {
        const val EXTRA_TRAINING_SESSION = "extra_training_session"
        private const val TAG = "WorkoutReportActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkoutReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jsonHelper = JsonHelper(this)

        // Get training session from intent
        val session = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_TRAINING_SESSION, TrainingSession::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_TRAINING_SESSION)
        }

        if (session == null) {
            Log.e(TAG, "No training session provided")
            finish()
            return
        }

        trainingSession = session

        setupBackgroundAnimation()
        setupClickListeners()
        loadReportData()
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

        binding.buttonDone.setOnClickListener {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun loadReportData() {
        try {
            // Load training data
            val trainingData = jsonHelper.readTrainingData()
            val allSessions = trainingData.trainings
            val exerciseLibrary = trainingData.exerciseLibrary

            // Calculate summary
            val summary = WorkoutComparisonHelper.calculateSessionSummary(
                trainingSession,
                allSessions
            )

            // Display header
            binding.textDate.text = trainingSession.date

            // Display summary stats
            binding.textTotalVolume.text = String.format("%,dkg", summary.totalVolume.toInt())
            binding.textTotalSets.text = summary.totalSets.toString()
            binding.textTotalReps.text = summary.totalReps.toString()
            binding.textExercisesCount.text = summary.exerciseCount.toString()
            binding.textPrsCount.text = summary.prCount.toString()

            // Timed holds contribute no volume or reps, so their work is reported on its own tile —
            // only for sessions that actually contain one.
            if (summary.holdSetCount > 0) {
                binding.cardTotalHoldTime.visibility = View.VISIBLE
                binding.textTotalHoldTime.text =
                    RestTimerHelper.formatHoldTotal(summary.totalHoldSeconds)
            } else {
                binding.cardTotalHoldTime.visibility = View.GONE
            }

            val durationText = summary.durationSeconds?.let { 
                DurationHelper.formatDuration(it) 
            } ?: "--"
            binding.textDuration.text = durationText

            // Calculate exercise trends
            val exerciseTrends = WorkoutComparisonHelper.calculateExerciseTrends(
                trainingSession,
                allSessions,
                exerciseLibrary
            )

            // Setup exercise trends RecyclerView
            binding.recyclerExerciseTrends.layoutManager = LinearLayoutManager(this)
            binding.recyclerExerciseTrends.adapter = ExerciseTrendAdapter(exerciseTrends)

            // Calculate muscle progress
            val muscleProgress = WorkoutComparisonHelper.calculateMuscleProgress(
                trainingSession,
                allSessions,
                exerciseLibrary
            )

            // Render the illustrated muscle map
            updateMuscleMap(muscleProgress)

        } catch (e: Exception) {
            Log.e(TAG, "Error loading report data", e)
        }
    }

    // Preserves the pre-existing approximation: muscles that improved are shown as "primary"
    // (dark), everything else as "secondary" (light) — this is a binary approximation of the
    // true progress gradient, matching the behavior already in place before this migration.
    private fun updateMuscleMap(muscleProgress: Map<TargetMuscle, Float?>) {
        if (muscleProgress.isEmpty()) return

        val improvedMuscles = muscleProgress
            .filter { (_, progress) -> progress != null && progress > 0 }
            .keys
        val otherMuscles = muscleProgress
            .filter { (_, progress) -> progress == null || progress <= 0 }
            .keys

        lifecycleScope.launch {
            val muscleRoles = MuscleMapColorResolver.resolveHighlightColors(improvedMuscles, otherMuscles)
            val maskRoles = MuscleMapColorResolver.flattenToMaskCategories(
                muscleRoles, rank = MuscleMapColorResolver::highlightRank
            )
            val maskColors = maskRoles.map { (maskResId, role) ->
                maskResId to MuscleMapColorResolver.colorFor(this@WorkoutReportActivity, role)
            }
            val bitmap = withContext(Dispatchers.Default) {
                MuscleMapRenderer.render(this@WorkoutReportActivity, maskColors)
            }
            binding.imageMuscleMap.setImageBitmap(bitmap)
            binding.progressMuscleMap.visibility = View.GONE
        }
    }
}
