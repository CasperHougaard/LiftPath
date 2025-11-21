package com.lilfitness.activities

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.lilfitness.databinding.ActivityTrainingDetailBinding
import com.lilfitness.helpers.JsonHelper
import com.lilfitness.adapters.TrainingDetailAdapter
import com.lilfitness.models.ExerciseEntry
import com.lilfitness.models.GroupedExercise
import com.lilfitness.models.TrainingSession
import com.lilfitness.utils.WorkoutTypeFormatter

class TrainingDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrainingDetailBinding
    private lateinit var jsonHelper: JsonHelper
    private lateinit var trainingSession: TrainingSession
    private val workoutTypeKeys = listOf("heavy", "light", "custom")
    private val workoutTypeLabels = listOf("Heavy", "Light", "Custom")

    private var currentEditingEntry: ExerciseEntry? = null

    private val editSetLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val updatedEntry = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.data?.getParcelableExtra(EditSetActivity.EXTRA_EXERCISE_ENTRY, ExerciseEntry::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    result.data?.getParcelableExtra(EditSetActivity.EXTRA_EXERCISE_ENTRY)
                }

                if (updatedEntry != null) {
                    updateTrainingSession(updatedEntry)
                }
            }
            EditSetActivity.RESULT_DELETE -> {
                // Delete the set
                currentEditingEntry?.let { entryToDelete ->
                    deleteSet(entryToDelete)
                }
            }
        }
        currentEditingEntry = null
    }

    private val editActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Reload the training session and refresh the view
            val trainingData = jsonHelper.readTrainingData()
            val updatedSession = trainingData.trainings.find { it.id == trainingSession.id }
            if (updatedSession != null) {
                trainingSession = updatedSession
                setupRecyclerView()
            }
        }
    }

    companion object {
        const val EXTRA_TRAINING_SESSION = "extra_training_session"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrainingDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jsonHelper = JsonHelper(this)

        val session = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_TRAINING_SESSION, TrainingSession::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_TRAINING_SESSION)
        }

        if (session != null) {
            trainingSession = session
            title = "Training #${trainingSession.trainingNumber} - ${trainingSession.date}"
            setupSessionTypeControls()
            setupRecyclerView()
            setupClickListeners()
        } else {
            title = "Training Details"
        }
    }

    private fun setupClickListeners() {
        binding.buttonBack.setOnClickListener {
            onBackPressed()
        }

        binding.buttonDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Training")
                .setMessage("Are you sure you want to delete this training permanently?")
                .setPositiveButton("Delete") { _, _ ->
                    val trainingData = jsonHelper.readTrainingData()
                    val updatedTrainings = trainingData.trainings.toMutableList()
                    updatedTrainings.remove(trainingSession)
                    jsonHelper.writeTrainingData(trainingData.copy(trainings = updatedTrainings))
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupRecyclerView() {
        val groupedExercises = trainingSession.exercises
            .groupBy { it.exerciseId }
            .map { (exerciseId, sets) ->
                val sortedSets = sets.sortedBy { it.setNumber }
                GroupedExercise(exerciseId, sortedSets.first().exerciseName, sortedSets)
            }
            .sortedBy { it.exerciseName }

        binding.recyclerViewTrainingDetail.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewTrainingDetail.adapter = TrainingDetailAdapter(
            groupedExercises,
            trainingSession.defaultWorkoutType,
            onEditSetClicked = {
                currentEditingEntry = it
                val intent = Intent(this, EditSetActivity::class.java).apply {
                    putExtra(EditSetActivity.EXTRA_EXERCISE_ENTRY, it)
                    putExtra(EditSetActivity.EXTRA_IS_EDIT_MODE, true)
                }
                editSetLauncher.launch(intent)
            },
            onChangeTypeClicked = { groupedExercise ->
                showExerciseTypeDialog(groupedExercise)
            },
            onEditActivityClicked = { groupedExercise ->
                val intent = Intent(this, com.lilfitness.activities.EditActivityActivity::class.java).apply {
                    putExtra(com.lilfitness.activities.EditActivityActivity.EXTRA_TRAINING_SESSION_ID, trainingSession.id)
                    putExtra(com.lilfitness.activities.EditActivityActivity.EXTRA_EXERCISE_ID, groupedExercise.exerciseId)
                    putExtra(com.lilfitness.activities.EditActivityActivity.EXTRA_EXERCISE_NAME, groupedExercise.exerciseName)
                }
                editActivityLauncher.launch(intent)
            }
        )
    }

    private fun updateTrainingSession(updatedEntry: ExerciseEntry) {
        val exerciseIndex = trainingSession.exercises.indexOfFirst { it.setNumber == updatedEntry.setNumber && it.exerciseId == updatedEntry.exerciseId }
        if (exerciseIndex != -1) {
            trainingSession.exercises[exerciseIndex] = updatedEntry
            persistTrainingSession()
            setupRecyclerView()
        }
    }

    private fun deleteSet(entryToDelete: ExerciseEntry) {
        val exerciseIndex = trainingSession.exercises.indexOfFirst { 
            it.setNumber == entryToDelete.setNumber && it.exerciseId == entryToDelete.exerciseId 
        }
        if (exerciseIndex != -1) {
            trainingSession.exercises.removeAt(exerciseIndex)
            persistTrainingSession()
            setupRecyclerView()
            Toast.makeText(this, "Set deleted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSessionTypeControls() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, workoutTypeLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSessionType.adapter = adapter

        val normalizedType = WorkoutTypeFormatter.normalize(trainingSession.defaultWorkoutType)
        val selectionIndex = workoutTypeKeys.indexOf(normalizedType).takeIf { it >= 0 } ?: 0
        binding.spinnerSessionType.setSelection(selectionIndex)

        binding.buttonApplySessionType.setOnClickListener {
            val selectedType = workoutTypeKeys[binding.spinnerSessionType.selectedItemPosition]
            applyTypeToEntireSession(selectedType)
        }
    }

    private fun applyTypeToEntireSession(type: String) {
        val updatedExercises = trainingSession.exercises.map { it.copy(workoutType = type) }.toMutableList()
        trainingSession = trainingSession.copy(
            exercises = updatedExercises,
            defaultWorkoutType = type
        )
        persistTrainingSession()
        setupRecyclerView()
        Toast.makeText(this, "Applied ${WorkoutTypeFormatter.label(type)} to all exercises.", Toast.LENGTH_SHORT).show()
    }

    private fun showExerciseTypeDialog(groupedExercise: GroupedExercise) {
        val currentType = groupedExercise.sets.firstOrNull()?.workoutType ?: trainingSession.defaultWorkoutType
        val normalized = WorkoutTypeFormatter.normalize(currentType)
        val currentIndex = workoutTypeKeys.indexOf(normalized).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(this)
            .setTitle("Set type for ${groupedExercise.exerciseName}")
            .setSingleChoiceItems(workoutTypeLabels.toTypedArray(), currentIndex) { dialog, which ->
                val selectedType = workoutTypeKeys[which]
                dialog.dismiss()
                applyTypeToExercise(groupedExercise.exerciseId, selectedType)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyTypeToExercise(exerciseId: Int, type: String) {
        val updatedExercises = trainingSession.exercises.map { entry ->
            if (entry.exerciseId == exerciseId) entry.copy(workoutType = type) else entry
        }.toMutableList()
        trainingSession = trainingSession.copy(exercises = updatedExercises)
        persistTrainingSession()
        setupRecyclerView()
        Toast.makeText(this, "${WorkoutTypeFormatter.label(type)} applied to exercise.", Toast.LENGTH_SHORT).show()
    }

    private fun persistTrainingSession() {
        val trainingData = jsonHelper.readTrainingData()
        val sessionIndex = trainingData.trainings.indexOfFirst { it.id == trainingSession.id }
        if (sessionIndex != -1) {
            trainingData.trainings[sessionIndex] = trainingSession
            jsonHelper.writeTrainingData(trainingData)
        }
    }
}