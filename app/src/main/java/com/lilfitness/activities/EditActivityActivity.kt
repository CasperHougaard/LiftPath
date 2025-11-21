package com.lilfitness.activities

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lilfitness.R
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.lilfitness.adapters.EditActivityAdapter
import com.lilfitness.databinding.ActivityEditActivityBinding
import com.lilfitness.helpers.DialogHelper
import com.lilfitness.helpers.JsonHelper
import com.lilfitness.helpers.showWithTransparentWindow
import com.lilfitness.models.ExerciseEntry
import com.lilfitness.models.TrainingSession
import java.util.Locale

class EditActivityActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditActivityBinding
    private lateinit var jsonHelper: JsonHelper
    private lateinit var trainingSession: TrainingSession
    private var exerciseId: Int = 0
    private var exerciseName: String = ""
    private lateinit var sets: MutableList<ExerciseEntry>

    companion object {
        const val EXTRA_TRAINING_SESSION_ID = "extra_training_session_id"
        const val EXTRA_EXERCISE_ID = "extra_exercise_id"
        const val EXTRA_EXERCISE_NAME = "extra_exercise_name"
        const val EXTRA_IS_ACTIVE_WORKOUT = "extra_is_active_workout"
        const val EXTRA_SETS = "extra_sets"
        const val EXTRA_UPDATED_SETS = "extra_updated_sets"
    }

    private var isActiveWorkout = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jsonHelper = JsonHelper(this)

        isActiveWorkout = intent.getBooleanExtra(EXTRA_IS_ACTIVE_WORKOUT, false)
        exerciseId = intent.getIntExtra(EXTRA_EXERCISE_ID, 0)
        exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: "Exercise"

        if (isActiveWorkout) {
            // Active workout mode - sets are passed directly
            val setsFromIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(EXTRA_SETS, ExerciseEntry::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(EXTRA_SETS)
            }
            
            if (setsFromIntent == null || setsFromIntent.isEmpty()) {
                Toast.makeText(this, getString(R.string.toast_no_sets_to_edit), Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            
            sets = setsFromIntent.sortedBy { it.setNumber }.toMutableList()
        } else {
            // Saved workout mode - load from database
            val sessionId = intent.getStringExtra(EXTRA_TRAINING_SESSION_ID)
            if (sessionId == null) {
                Toast.makeText(this, getString(R.string.toast_invalid_training_session), Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            // Load training session
            val trainingData = jsonHelper.readTrainingData()
            trainingSession = trainingData.trainings.find { it.id == sessionId }
                ?: run {
                    Toast.makeText(this, getString(R.string.toast_training_session_not_found), Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }

            // Get all sets for this exercise
            sets = trainingSession.exercises
                .filter { it.exerciseId == exerciseId }
                .sortedBy { it.setNumber }
                .toMutableList()
        }

        title = "Edit $exerciseName"

        setupBackgroundAnimation()
        setupRecyclerView()
        setupClickListeners()
    }

    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is Animatable) {
            drawable.start()
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerViewSets.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewSets.adapter = EditActivityAdapter(
            sets,
            onKgChanged = { position, kg ->
                if (position < sets.size) {
                    sets[position] = sets[position].copy(kg = kg)
                }
            },
            onRepsChanged = { position, reps ->
                if (position < sets.size) {
                    sets[position] = sets[position].copy(reps = reps)
                }
            },
            onRpeChanged = { position, rpe ->
                if (position < sets.size) {
                    sets[position] = sets[position].copy(rpe = rpe)
                }
            },
            onNoteClicked = { position ->
                showNoteDialog(position)
            }
        )
    }

    private fun showNoteDialog(position: Int) {
        if (position >= sets.size) return

        val currentNote = sets[position].note ?: ""
        val editText = EditText(this).apply {
            setText(currentNote)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            hint = "Note (optional)"
            minLines = 3
            maxLines = 5
        }

        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.dialog_title_edit_note, sets[position].setNumber))
            .setView(editText)
            .setPositiveButton(getString(R.string.button_save)) { _, _ ->
                val note = editText.text.toString().takeIf { it.isNotBlank() }
                sets[position] = sets[position].copy(note = note)
                binding.recyclerViewSets.adapter?.notifyItemChanged(position)
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .setNeutralButton(getString(R.string.button_clear)) { _, _ ->
                sets[position] = sets[position].copy(note = null)
                binding.recyclerViewSets.adapter?.notifyItemChanged(position)
            }
            .showWithTransparentWindow()
    }

    private fun setupClickListeners() {
        binding.buttonBack.setOnClickListener {
            finish()
        }

        binding.buttonSave.setOnClickListener {
            saveChanges()
        }

        binding.buttonCancel.setOnClickListener {
            finish()
        }
    }

    private fun saveChanges() {
        // Force any focused input fields inside the RecyclerView to lose focus
        // so their onFocusChange listeners commit the latest values to `sets`
        binding.recyclerViewSets.clearFocus()

        // Validate all sets
        for (set in sets) {
            if (set.kg <= 0) {
                Toast.makeText(this, getString(R.string.toast_set_weight_error, set.setNumber), Toast.LENGTH_SHORT).show()
                return
            }
            if (set.reps <= 0) {
                Toast.makeText(this, getString(R.string.toast_set_reps_error, set.setNumber), Toast.LENGTH_SHORT).show()
                return
            }
            set.rpe?.let { rpe ->
                if (rpe < 6.0f || rpe > 10.0f) {
                    Toast.makeText(this, getString(R.string.toast_set_rpe_error, set.setNumber), Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }

        if (isActiveWorkout) {
            // Active workout mode - return updated sets
            val resultIntent = Intent().apply {
                putParcelableArrayListExtra(EXTRA_UPDATED_SETS, ArrayList(sets))
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        } else {
            // Saved workout mode - update and persist to database
            // Update the training session
            // Create a map for quick lookup of edited sets
            val editedSetsMap = sets.associateBy { it.setNumber }
            
            val updatedExercises = trainingSession.exercises.map { entry ->
                if (entry.exerciseId == exerciseId) {
                    // Find the edited version of this set, preserving all original fields if not found
                    editedSetsMap[entry.setNumber] ?: entry
                } else {
                    // Keep other exercises unchanged
                    entry
                }
            }.toMutableList()

            // Create updated training session with all other fields preserved
            trainingSession = trainingSession.copy(exercises = updatedExercises)

            // Persist changes - read fresh data to ensure we have latest state
            val trainingData = jsonHelper.readTrainingData()
            val sessionIndex = trainingData.trainings.indexOfFirst { it.id == trainingSession.id }
            if (sessionIndex != -1) {
                // Replace the session with our updated version, preserving all session-level fields
                trainingData.trainings[sessionIndex] = trainingSession
                jsonHelper.writeTrainingData(trainingData)
            } else {
                Toast.makeText(this, getString(R.string.toast_error_training_session_not_found), Toast.LENGTH_SHORT).show()
                return
            }

            setResult(Activity.RESULT_OK)
            finish()
        }
    }
}

