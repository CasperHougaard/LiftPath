package com.lilfitness.activities

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.lilfitness.R
import com.lilfitness.databinding.ActivityEditExerciseBinding
import com.lilfitness.helpers.DialogHelper
import com.lilfitness.helpers.JsonHelper
import com.lilfitness.helpers.showWithTransparentWindow
import com.lilfitness.models.ExerciseLibraryItem
import com.lilfitness.models.Mechanics
import com.lilfitness.models.MovementPattern
import com.lilfitness.models.Tier

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

        setupBackgroundAnimation()
        setupDropdowns()
        loadExerciseData()
        setupClickListeners()
    }

    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is Animatable) {
            drawable.start()
        }
    }

    private fun setupDropdowns() {
        // 1. Movement Pattern (Show Human Name)
        val patterns = MovementPattern.values().map { it.displayName }
        val patternAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, patterns)
        binding.dropdownPattern.setAdapter(patternAdapter)

        // 2. Tier
        val tiers = Tier.values().map { it.displayName }
        val tierAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, tiers)
        binding.dropdownTier.setAdapter(tierAdapter)

        // 3. Mechanics
        val mechanics = Mechanics.values().map { it.displayName }
        val mechanicsAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mechanics)
        binding.dropdownMechanics.setAdapter(mechanicsAdapter)
    }

    private fun loadExerciseData() {
        if (exerciseId != -1) {
            binding.textEditExerciseTitle.text = "Edit Exercise"
            binding.cardDelete.visibility = View.VISIBLE

            val trainingData = jsonHelper.readTrainingData()
            val exercise = trainingData.exerciseLibrary.find { it.id == exerciseId }

            if (exercise != null) {
                binding.editTextExerciseName.setText(exercise.name)

                // Set Dropdown Values (Using displayName)
                exercise.pattern?.let { binding.dropdownPattern.setText(it.displayName, false) }
                exercise.tier?.let { binding.dropdownTier.setText(it.displayName, false) }
                exercise.mechanics?.let { binding.dropdownMechanics.setText(it.displayName, false) }
            }
        } else {
            binding.textEditExerciseTitle.text = "Create New Exercise"
            binding.cardDelete.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        binding.buttonSaveExercise.setOnClickListener { saveExercise() }
        binding.cardDelete.setOnClickListener { showDeleteConfirmationDialog() }
        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonCancel.setOnClickListener { finish() }
    }

    private fun showDeleteConfirmationDialog() {
        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.dialog_title_delete_exercise))
            .setMessage(getString(R.string.dialog_message_delete_exercise))
            .setPositiveButton(getString(R.string.button_delete)) { _, _ -> deleteExercise() }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
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

        // Get Display Strings
        val patternStr = binding.dropdownPattern.text.toString()
        val tierStr = binding.dropdownTier.text.toString()
        val mechanicsStr = binding.dropdownMechanics.text.toString()

        // Reverse Lookup: Find Enum by displayName
        val selectedPattern = MovementPattern.values().find { it.displayName == patternStr }
        val selectedTier = Tier.values().find { it.displayName == tierStr }
        val selectedMechanics = Mechanics.values().find { it.displayName == mechanicsStr }

        val trainingData = jsonHelper.readTrainingData()

        if (exerciseId != -1) {
            val existingExercise = trainingData.exerciseLibrary.find { it.id == exerciseId }
            if (existingExercise != null) {
                val index = trainingData.exerciseLibrary.indexOf(existingExercise)
                if (index != -1) {
                    trainingData.exerciseLibrary[index] = existingExercise.copy(
                        name = newName,
                        pattern = selectedPattern,
                        tier = selectedTier,
                        mechanics = selectedMechanics
                    )
                }
                // Legacy name update
                trainingData.trainings.forEach { session ->
                    session.exercises.forEach { entry ->
                        if (entry.exerciseId == exerciseId) entry.exerciseName = newName
                    }
                }
            }
        } else {
            val nextId = (trainingData.exerciseLibrary.maxOfOrNull { it.id } ?: 0) + 1
            val newExercise = ExerciseLibraryItem(
                id = nextId,
                name = newName,
                pattern = selectedPattern,
                tier = selectedTier,
                mechanics = selectedMechanics
            )
            trainingData.exerciseLibrary.add(newExercise)
            exerciseId = nextId
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