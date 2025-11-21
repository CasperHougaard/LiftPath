package com.lilfitness.activities

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.lilfitness.databinding.ActivityEditExerciseBinding
import com.lilfitness.helpers.JsonHelper
import com.lilfitness.models.ExerciseLibraryItem

class EditExerciseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditExerciseBinding
    private lateinit var jsonHelper: JsonHelper
    private var exerciseId: Int = -1

    companion object {
        const val EXTRA_EXERCISE_ID = "extra_exercise_id"
        const val EXTRA_EXERCISE_NAME = "extra_exercise_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditExerciseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jsonHelper = JsonHelper(this)
        exerciseId = intent.getIntExtra(EXTRA_EXERCISE_ID, -1)

        if (exerciseId != -1) {
            // Edit mode
            binding.textEditExerciseTitle.text = "Edit Exercise"
            val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME)
            binding.editTextExerciseName.setText(exerciseName)
            binding.cardDelete.visibility = View.VISIBLE
        } else {
            // Create mode
            binding.textEditExerciseTitle.text = "Create New Exercise"
            binding.cardDelete.visibility = View.GONE
        }

        setupBackgroundAnimation()
        setupClickListeners()
    }

    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is Animatable) {
            drawable.start()
        }
    }

    private fun setupClickListeners() {
        binding.buttonSaveExercise.setOnClickListener {
            saveExercise()
        }

        binding.cardDelete.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        binding.buttonBack.setOnClickListener {
            finish()
        }

        binding.buttonCancel.setOnClickListener {
            finish()
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete Exercise")
            .setMessage("Are you sure you want to delete this exercise? This will remove all logged sets for this exercise from your history. This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteExercise()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteExercise() {
        val trainingData = jsonHelper.readTrainingData()
        trainingData.exerciseLibrary.removeAll { it.id == exerciseId }
        trainingData.trainings.forEach { session ->
            session.exercises.removeAll { it.exerciseId == exerciseId }
        }
        jsonHelper.writeTrainingData(trainingData)

        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun saveExercise() {
        val newName = binding.editTextExerciseName.text.toString().trim()
        if (newName.isEmpty()) {
            binding.editTextExerciseName.error = "Exercise name cannot be empty"
            return
        }

        val trainingData = jsonHelper.readTrainingData()

        if (exerciseId != -1) {
            // Update existing exercise
            val existingExercise = trainingData.exerciseLibrary.find { it.id == exerciseId }
            if (existingExercise != null) {
                // Update name in the library
                val index = trainingData.exerciseLibrary.indexOf(existingExercise)
                if (index != -1) {
                    trainingData.exerciseLibrary[index] = existingExercise.copy(name = newName)
                }

                // Update name in all past training sessions for data integrity
                trainingData.trainings.forEach { session ->
                    session.exercises.forEach { entry ->
                        if (entry.exerciseId == exerciseId) {
                            entry.exerciseName = newName
                        }
                    }
                }
            }
        } else {
            // Create new exercise
            val nextId = (trainingData.exerciseLibrary.maxOfOrNull { it.id } ?: 0) + 1
            val newExercise = ExerciseLibraryItem(id = nextId, name = newName)
            trainingData.exerciseLibrary.add(newExercise)
            exerciseId = nextId // So we can return it
        }

        jsonHelper.writeTrainingData(trainingData)

        val resultIntent = Intent().apply {
            putExtra(EXTRA_EXERCISE_ID, exerciseId)
            putExtra(EXTRA_EXERCISE_NAME, newName)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
