package com.lilfitness.activities

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Animatable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.lilfitness.R
import com.lilfitness.databinding.ActivityLogSetBinding
import com.lilfitness.helpers.JsonHelper
import com.lilfitness.helpers.ProgressionHelper
import com.lilfitness.helpers.ProgressionSettingsManager
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
        
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
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
            
            // Pre-fill RPE if it exists
            lastSet.rpe?.let {
                binding.editTextRpe.setText(it.toString())
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
            settings = userSettings
        )

        if (!suggestion.isFirstTime) {
            val suggestedWeight = when (workoutType) {
                "heavy" -> suggestion.proposedHeavyWeight
                "light" -> suggestion.proposedLightWeight
                else -> null
            }

            if (suggestedWeight != null && setNumber == 1) {
                if (binding.editTextKg.text.isNullOrBlank()) {
                    binding.editTextKg.setText(suggestedWeight.toString())
                }
                
                val suggestedReps = when (workoutType) {
                    "heavy" -> userSettings.heavyReps
                    "light" -> userSettings.lightReps
                    else -> null
                }

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
                    
                    suggestion.badge?.let {
                        append(" $it")
                    }
                }
                
                binding.textSuggestionContent.text = hintText

                if (binding.editTextReps.text.isNullOrBlank() && suggestedReps != null && suggestedReps > 0) {
                    binding.editTextReps.setText(suggestedReps.toString())
                }
                
                // The card now has a fixed accent color background, no need to change it
                
                binding.tvSuggestionHint.visibility = View.VISIBLE
            }
        }
    }

    private fun showRpeHelpDialog() {
        val message = """
            RPE Scale (Rate of Perceived Exertion)
            Based on Reps in Reserve (RIR):
            
            10  - Max effort, could not do another rep
            9.5 - Could maybe do 1 more rep
            9   - Could definitely do 1 more rep
            8   - Could do 2 more reps
            7   - Could do 3 more reps
            6   - Could do 4+ reps
            
            For strength (heavy): aim for RPE 7.5-8.5
            For volume (light): aim for RPE 7-8
            
            Leave blank if you're not sure - the app will use the "Completed?" checkbox instead.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("RPE Scale")
            .setMessage(message)
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun saveSet() {
        // Validate inputs
        val kg = binding.editTextKg.text.toString().toFloatOrNull()
        val reps = binding.editTextReps.text.toString().toIntOrNull()

        if (kg == null || reps == null) {
            Toast.makeText(this, "Please enter weight and reps", Toast.LENGTH_SHORT).show()
            return
        }

        // Get RPE (validate 6-10 range)
        val rpeText = binding.editTextRpe.text.toString()
        val rpe = if (rpeText.isNotEmpty()) {
            val value = rpeText.toFloatOrNull()
            if (value != null && value in 6.0f..10.0f) {
                value
            } else {
                Toast.makeText(this, "RPE must be between 6.0 and 10.0", Toast.LENGTH_SHORT).show()
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
        
        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                // Request permission
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
                // Timer will start after permission is granted
                return
            }
        }
        
        // Calculate rest duration based on workout type
        var restSeconds = when (workoutType) {
            "heavy" -> settings.heavyRestSeconds
            "light" -> settings.lightRestSeconds
            "custom" -> settings.customRestSeconds
            else -> settings.customRestSeconds  // Default to custom for unknown types
        }
        
        // Apply RPE adjustment if enabled
        if (settings.rpeAdjustmentEnabled && rpe != null && rpe >= settings.rpeHighThreshold) {
            restSeconds += settings.rpeHighBonusSeconds
        }
        
        // Start the timer service
        com.lilfitness.services.RestTimerService.startTimer(this, restSeconds, exerciseName)
        
        // Show confirmation toast
        val minutes = restSeconds / 60
        val seconds = restSeconds % 60
        val timeText = if (minutes > 0) {
            "${minutes}m ${seconds}s"
        } else {
            "${seconds}s"
        }
        Toast.makeText(this, "⏱️ Rest timer started: $timeText", Toast.LENGTH_SHORT).show()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted. Timer will start next time.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notification permission denied. Rest timer disabled.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatTypeLabel(type: String): String {
        return type.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }
}

