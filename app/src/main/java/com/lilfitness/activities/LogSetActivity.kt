package com.lilfitness.activities

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lilfitness.R
import androidx.appcompat.app.AppCompatActivity
import com.lilfitness.databinding.ActivityLogSetBinding
import com.lilfitness.helpers.DialogHelper
import com.lilfitness.helpers.JsonHelper
import com.lilfitness.helpers.ProgressionHelper
import com.lilfitness.helpers.ProgressionSettingsManager
import com.lilfitness.helpers.showWithTransparentWindow
import com.lilfitness.models.ExerciseEntry
import com.lilfitness.services.RestTimerService
import java.util.Locale

class LogSetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogSetBinding
    private lateinit var jsonHelper: JsonHelper
    private var exerciseId: Int = 0
    private var exerciseName: String = ""
    private var setNumber: Int = 1
    private var workoutType: String = "heavy"
    private var previousSetReps: Int? = null

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

        // Setup RPE help button
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
            
            // Pre-fill RPE if it exists and field is blank
            lastSet.rpe?.let {
                if (binding.editTextRpe.text.isNullOrBlank()) {
                    binding.editTextRpe.setText(it.toString())
                    // Don't update hint for last set fallback - it's from previous session
                }
            }
            
            // Pre-fill note if exists
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
            settings = userSettings,
            userLevel = userSettings.userLevel
        )

        if (!suggestion.isFirstTime) {
            val suggestedWeight = when (workoutType) {
                "heavy" -> suggestion.proposedHeavyWeight
                "light" -> suggestion.proposedLightWeight
                else -> null
            }

            if (suggestedWeight != null) {
                // Auto-fill weight for all sets if field is blank
                if (binding.editTextKg.text.isNullOrBlank()) {
                    binding.editTextKg.setText(suggestedWeight.toString())
                }
                
                val suggestedReps = when (workoutType) {
                    "heavy" -> userSettings.heavyReps
                    "light" -> userSettings.lightReps
                    else -> null
                }

                // Get suggested RPE based on user level and workout type
                val suggestedRpe = ProgressionHelper.suggestRpe(userSettings.userLevel, workoutType)

                // Build hint text with set number, total sets, and reps
                val hintText = buildString {
                    val totalSets = when (workoutType) {
                        "heavy" -> userSettings.heavySets
                        "light" -> userSettings.lightSets
                        else -> 1
                    }
                    val suggestedReps = when (workoutType) {
                        "heavy" -> userSettings.heavyReps
                        "light" -> userSettings.lightReps
                        else -> null
                    }
                    
                    append("Suggested: ${suggestedWeight}kg")
                    
                    if (suggestedReps != null && suggestedReps > 0) {
                        append(" for Set $setNumber of $totalSets ($suggestedReps reps)")
                    } else {
                        append(" for Set $setNumber of $totalSets")
                    }
                    
                    if (suggestedRpe != null) {
                        append(" @ RPE $suggestedRpe")
                    }
                    
                    suggestion.badge?.let {
                        append(" $it")
                    }
                }
                
                binding.textSuggestionContent.text = hintText

                // Auto-fill reps for all sets if field is blank
                if (binding.editTextReps.text.isNullOrBlank() && suggestedReps != null && suggestedReps > 0) {
                    binding.editTextReps.setText(suggestedReps.toString())
                }
                
                // Auto-fill RPE for all sets if field is blank
                if (suggestedRpe != null && binding.editTextRpe.text.isNullOrBlank()) {
                    binding.editTextRpe.setText(suggestedRpe.toString())
                    updateRpeHint(suggestedRpe)
                }
                
                // The card now has a fixed accent color background, no need to change it
                
                binding.tvSuggestionHint.visibility = View.VISIBLE
            }
        } else {
            // First time - still suggest RPE if available
            val suggestedRpe = ProgressionHelper.suggestRpe(userSettings.userLevel, workoutType)
            if (suggestedRpe != null && binding.editTextRpe.text.isNullOrBlank()) {
                binding.editTextRpe.setText(suggestedRpe.toString())
                updateRpeHint(suggestedRpe)
            }
        }
    }
    
    private fun updateRpeHint(rpe: Float) {
        val rpeDescription = when {
            rpe <= 6.0f -> "Very easy - Could do 4+ more reps"
            rpe <= 7.0f -> "Easy - Could do 3 more reps"
            rpe <= 7.5f -> "Moderate - Could do 2-3 more reps"
            rpe <= 8.0f -> "Moderate-Hard - Could do 2 more reps"
            rpe <= 8.5f -> "Hard - Could do 1-2 more reps"
            rpe <= 9.0f -> "Very Hard - Could do 1 more rep"
            rpe <= 9.5f -> "Extremely Hard - Maybe 1 more rep"
            else -> "Maximal - No reps left in tank"
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
        // Validate inputs
        val kg = binding.editTextKg.text.toString().toFloatOrNull()
        val reps = binding.editTextReps.text.toString().toIntOrNull()

        if (kg == null || reps == null) {
            Toast.makeText(this, getString(R.string.toast_please_enter_weight_reps), Toast.LENGTH_SHORT).show()
            return
        }

        // Get RPE (validate 6-10 range)
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
            null  // User didn't enter RPE
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
            rating = null,  // Don't use rating anymore
            workoutType = workoutType,
            rpe = rpe,
            completed = completed
        )

        val resultIntent = Intent().apply {
            putExtra(EXTRA_LOGGED_SET, newEntry)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        
        // Start rest timer if enabled
        startRestTimer(rpe)
        
        finish()
    }
    
    private fun startRestTimer(rpe: Float?) {
        val settings = ProgressionSettingsManager(this).getSettings()
        
        if (!settings.restTimerEnabled) {
            return
        }
        
        // Calculate rest duration based on workout type
        var restSeconds = when (workoutType) {
            "heavy" -> settings.heavyRestSeconds
            "light" -> settings.lightRestSeconds
            "custom" -> settings.customRestSeconds
            else -> settings.customRestSeconds  // Default to custom for unknown types
        }
        
        // Apply RPE adjustments if RPE is provided
        if (settings.rpeAdjustmentEnabled && rpe != null) {
            // Original high RPE threshold logic
            if (rpe >= settings.rpeHighThreshold) {
                restSeconds += settings.rpeHighBonusSeconds
            }
            
            // New RPE deviation adjustment: compare logged RPE to suggested RPE
            val suggestedRpe = ProgressionHelper.suggestRpe(settings.userLevel, workoutType)
            if (suggestedRpe != null) {
                val rpeDifference = rpe - suggestedRpe
                
                // If logged RPE is threshold or more higher than suggested, add positive adjustment
                if (rpeDifference >= settings.rpeDeviationThreshold) {
                    restSeconds += settings.rpePositiveAdjustmentSeconds
                }
                // If logged RPE is threshold or more lower than suggested, subtract negative adjustment
                else if (rpeDifference <= -settings.rpeDeviationThreshold) {
                    restSeconds = maxOf(0, restSeconds - settings.rpeNegativeAdjustmentSeconds) // Don't go below 0
                }
            }
        }
        
        // Start the timer service without showing dialog (use permanent UI instead)
        com.lilfitness.services.RestTimerService.startTimer(this, restSeconds, exerciseName, showDialog = false)
    }

    private fun formatTypeLabel(type: String): String {
        return type.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }
}

