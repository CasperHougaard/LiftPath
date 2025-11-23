package com.liftpath.activities

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.liftpath.R // <--- FIXED: ENSURE THIS IMPORT IS HERE
import com.liftpath.databinding.ActivityLogSetBinding
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.ProgressionHelper
import com.liftpath.helpers.ProgressionSettingsManager
import com.liftpath.helpers.showWithTransparentWindow
import com.liftpath.models.ExerciseEntry
import java.util.Locale
import kotlin.math.max // <--- FIXED: IMPORT ADDED

class LogSetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogSetBinding
    private lateinit var jsonHelper: JsonHelper
    private var exerciseId: Int = 0
    private var exerciseName: String = ""
    private var setNumber: Int = 1
    private var workoutType: String = "heavy"
    private var previousSetReps: Int? = null

    private var pendingTimerRpe: Float? = null
    private var pendingTimerWorkoutType: String? = null
    private var pendingTimerExerciseName: String? = null
    private var shouldFinishAfterTimer = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, start the timer
            startRestTimerAfterPermissionCheck()
        } else {
            // Permission denied, finish the activity
            if (shouldFinishAfterTimer) {
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_EXERCISE_ID = "extra_exercise_id"
        const val EXTRA_EXERCISE_NAME = "extra_exercise_name"
        const val EXTRA_SET_NUMBER = "extra_set_number"
        const val EXTRA_LOGGED_SET = "extra_logged_set"
        const val EXTRA_WORKOUT_TYPE = "extra_workout_type"
        const val EXTRA_PREVIOUS_SET_REPS = "extra_previous_set_reps"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogSetBinding.inflate(layoutInflater)
        setContentView(binding.root)
        jsonHelper = JsonHelper(this)

        exerciseId = intent.getIntExtra(EXTRA_EXERCISE_ID, 0)
        exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: "Exercise"
        setNumber = intent.getIntExtra(EXTRA_SET_NUMBER, 1)
        workoutType = intent.getStringExtra(EXTRA_WORKOUT_TYPE) ?: "heavy"
        previousSetReps = intent.getIntExtra(EXTRA_PREVIOUS_SET_REPS, -1).takeIf { it > 0 }
        binding.textLogSetTitle.text = "$exerciseName (${formatTypeLabel(workoutType)})"

        setupBackgroundAnimation()

        binding.btnRpeHelp.setOnClickListener {
            showRpeHelpDialog()
        }

        showWeightSuggestion()
        prefillLastSetFallback()
        prefillRepsFromPreviousSet()

        binding.buttonSaveSet.setOnClickListener {
            saveSet()
        }

        binding.buttonBack.setOnClickListener {
            finish()
        }
    }

    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is Animatable) {
            drawable.start()
        }
    }

    private fun prefillLastSetFallback() {
        if (binding.editTextKg.text?.isNotBlank() == true) return
        val trainingData = jsonHelper.readTrainingData()
        val lastSet = trainingData.trainings
            .flatMap { it.exercises }
            .filter { it.exerciseName == exerciseName }
            .lastOrNull()

        if (lastSet != null) {
            binding.editTextKg.setText(lastSet.kg.toString())
            binding.editTextReps.setText(lastSet.reps.toString())

            lastSet.rpe?.let {
                if (binding.editTextRpe.text.isNullOrBlank()) {
                    binding.editTextRpe.setText(it.toString())
                }
            }
            lastSet.note?.let {
                binding.editTextNote.setText(it)
            }
        }
    }

    private fun prefillRepsFromPreviousSet() {
        if (setNumber <= 1) return
        val reps = previousSetReps ?: return
        binding.editTextReps.setText(reps.toString())
    }

    private fun showWeightSuggestion() {
        val trainingData = jsonHelper.readTrainingData()
        val settingsManager = ProgressionSettingsManager(this)
        val userSettings = settingsManager.getSettings()

        val suggestion = ProgressionHelper.getSuggestion(
            exerciseId = exerciseId,
            requestedType = workoutType,
            trainingData = trainingData,
            settings = userSettings
        )

        if (!suggestion.isFirstTime) {
            val suggestedWeight = suggestion.proposedWeight

            if (suggestedWeight != null) {
                if (binding.editTextKg.text.isNullOrBlank()) {
                    binding.editTextKg.setText(suggestedWeight.toString())
                }

                val suggestedReps = suggestion.proposedReps ?: 5

                val suggestedRpe = ProgressionHelper.suggestRpe(userSettings.userLevel, workoutType)

                val hintText = buildString {
                    append("Suggested: ${suggestedWeight}kg")

                    if (suggestedReps > 0) {
                        append(" ($suggestedReps reps)")
                    }

                    append(" @ RPE $suggestedRpe")

                    suggestion.badge?.let {
                        append(" $it")
                    }
                }

                binding.textSuggestionContent.text = hintText

                if (binding.editTextReps.text.isNullOrBlank() && suggestedReps > 0) {
                    binding.editTextReps.setText(suggestedReps.toString())
                }

                if (binding.editTextRpe.text.isNullOrBlank()) {
                    binding.editTextRpe.setText(suggestedRpe.toString())
                    updateRpeHint(suggestedRpe)
                }

                binding.tvSuggestionHint.visibility = View.VISIBLE
            }
        } else {
            val suggestedRpe = ProgressionHelper.suggestRpe(userSettings.userLevel, workoutType)
            if (binding.editTextRpe.text.isNullOrBlank()) {
                binding.editTextRpe.setText(suggestedRpe.toString())
                updateRpeHint(suggestedRpe)
            }
        }
    }

    private fun updateRpeHint(rpe: Float) {
        val rpeDescription = when {
            rpe <= 6.0f -> "Very easy"
            rpe <= 7.0f -> "Easy"
            rpe <= 8.0f -> "Moderate"
            rpe <= 9.0f -> "Hard"
            else -> "Maximal"
        }
        val hintText = "Suggested RPE $rpe: $rpeDescription"
        binding.textRpeHint.text = hintText
        binding.textRpeHint.visibility = View.VISIBLE
    }

    private fun showRpeHelpDialog() {
        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.dialog_title_rpe_scale))
            .setMessage(getString(R.string.dialog_message_rpe_scale))
            .setPositiveButton(getString(R.string.button_got_it), null)
            .showWithTransparentWindow()
    }

    private fun saveSet() {
        val kg = binding.editTextKg.text.toString().toFloatOrNull()
        val reps = binding.editTextReps.text.toString().toIntOrNull()

        if (kg == null || reps == null) {
            Toast.makeText(this, getString(R.string.toast_please_enter_weight_reps), Toast.LENGTH_SHORT).show()
            return
        }

        val rpeText = binding.editTextRpe.text.toString()
        val rpe = if (rpeText.isNotEmpty()) {
            val value = rpeText.toFloatOrNull()
            if (value != null && value in 6.0f..10.0f) {
                value
            } else {
                Toast.makeText(this, getString(R.string.toast_rpe_range), Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            null
        }

        val note = binding.editTextNote.text.toString()
        val completed = binding.cbCompleted.isChecked

        val newEntry = ExerciseEntry(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            setNumber = setNumber,
            kg = kg,
            reps = reps,
            note = note.takeIf { it.isNotBlank() },
            rating = null,
            workoutType = workoutType,
            rpe = rpe,
            completed = completed
        )

        val resultIntent = Intent().apply {
            putExtra(EXTRA_LOGGED_SET, newEntry)
        }
        setResult(Activity.RESULT_OK, resultIntent)

        // Start timer - this will handle permission request if needed
        shouldFinishAfterTimer = true
        val needsPermission = startRestTimer(rpe)
        
        // Only finish if timer was started immediately (no permission request needed)
        // Otherwise, finish will be called in the permission callback
        if (!needsPermission) {
            finish()
        }
    }

    private fun startRestTimer(rpe: Float?): Boolean {
        val settings = ProgressionSettingsManager(this).getSettings()

        if (!settings.restTimerEnabled) {
            return false // Timer disabled, no permission needed
        }

        // Check and request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Store the timer parameters to use after permission is granted
                pendingTimerRpe = rpe
                pendingTimerWorkoutType = workoutType
                pendingTimerExerciseName = exerciseName
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return true // Permission request needed
            }
        }

        startRestTimerAfterPermissionCheck(rpe)
        return false // No permission request needed
    }

    private fun startRestTimerAfterPermissionCheck(rpe: Float? = null) {
        val settings = ProgressionSettingsManager(this).getSettings()
        
        // Use pending values if available (from permission request), otherwise use current values
        val actualRpe = pendingTimerRpe ?: rpe
        val actualWorkoutType = pendingTimerWorkoutType ?: workoutType
        val actualExerciseName = pendingTimerExerciseName ?: exerciseName
        
        // Clear pending values
        pendingTimerRpe = null
        pendingTimerWorkoutType = null
        pendingTimerExerciseName = null

        // Calculate base rest time based on workout type (heavy/light/custom)
        var restSeconds = when (actualWorkoutType) {
            "heavy" -> settings.heavyRestSeconds
            "light" -> settings.lightRestSeconds
            "custom" -> settings.customRestSeconds
            else -> settings.customRestSeconds
        }

        // Apply RPE-based adjustments if enabled
        if (settings.rpeAdjustmentEnabled && actualRpe != null) {
            // Add bonus time if RPE is very high
            if (actualRpe >= settings.rpeHighThreshold) {
                restSeconds += settings.rpeHighBonusSeconds
            }

            // Adjust based on deviation from suggested RPE
            val suggestedRpe = ProgressionHelper.suggestRpe(settings.userLevel, actualWorkoutType)
            val rpeDifference = actualRpe - suggestedRpe

            if (rpeDifference >= settings.rpeDeviationThreshold) {
                // Higher RPE than suggested = add more rest
                restSeconds += settings.rpePositiveAdjustmentSeconds
            }
            else if (rpeDifference <= -settings.rpeDeviationThreshold) {
                // Lower RPE than suggested = reduce rest
                restSeconds = max(0, restSeconds - settings.rpeNegativeAdjustmentSeconds)
            }
        }

        com.liftpath.services.RestTimerService.startTimer(this, restSeconds, actualExerciseName, showDialog = false)
        
        // Finish activity after timer is started
        if (shouldFinishAfterTimer) {
            finish()
        }
    }

    private fun formatTypeLabel(type: String): String {
        return type.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }
}