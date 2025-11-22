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
import com.google.android.material.chip.Chip
import com.lilfitness.models.BodyRegion
import com.lilfitness.models.ExerciseLibraryItem
import com.lilfitness.models.Mechanics
import com.lilfitness.models.MovementPattern
import com.lilfitness.models.TargetMuscle
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
        // 1. Body Region
        val regions = BodyRegion.values().map { it.displayName }
        val regionAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, regions)
        binding.dropdownRegion.setAdapter(regionAdapter)

        // 2. Movement Pattern (Show Human Name)
        val patterns = MovementPattern.values().map { it.displayName }
        val patternAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, patterns)
        binding.dropdownPattern.setAdapter(patternAdapter)

        // 3. Tier
        val tiers = Tier.values().map { it.displayName }
        val tierAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, tiers)
        binding.dropdownTier.setAdapter(tierAdapter)

        // 4. Mechanics
        val manualMechanics = Mechanics.values().map { it.displayName }
        val mechanicsAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, manualMechanics)
        binding.dropdownMechanics.setAdapter(mechanicsAdapter)

        // 5. Setup Target Muscle Chips
        setupTargetMuscleChips()
    }

    private fun setupTargetMuscleChips() {
        // Create chips for all TargetMuscle values
        val allMuscles = TargetMuscle.values()

        // Primary Targets ChipGroup
        binding.chipGroupPrimaryTargets.removeAllViews()
        allMuscles.forEach { muscle ->
            val chip = Chip(this)
            chip.text = muscle.displayName
            chip.isCheckable = true
            chip.tag = muscle
            chip.chipStrokeWidth = 1f
            chip.chipStrokeColor = getColorStateList(R.color.fitness_primary)
            chip.setTextColor(getColorStateList(R.color.fitness_text_primary))
            binding.chipGroupPrimaryTargets.addView(chip)
        }

        // Secondary Targets ChipGroup
        binding.chipGroupSecondaryTargets.removeAllViews()
        allMuscles.forEach { muscle ->
            val chip = Chip(this)
            chip.text = muscle.displayName
            chip.isCheckable = true
            chip.tag = muscle
            chip.chipStrokeWidth = 1f
            chip.chipStrokeColor = getColorStateList(R.color.fitness_primary)
            chip.setTextColor(getColorStateList(R.color.fitness_text_primary))
            binding.chipGroupSecondaryTargets.addView(chip)
        }
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
                exercise.region?.let { binding.dropdownRegion.setText(it.displayName, false) }
                exercise.pattern?.let { binding.dropdownPattern.setText(it.displayName, false) }
                exercise.tier?.let { binding.dropdownTier.setText(it.displayName, false) }
                // Use manualMechanics if set, otherwise use computed mechanics
                val mechanicsToDisplay = exercise.manualMechanics ?: exercise.mechanics
                binding.dropdownMechanics.setText(mechanicsToDisplay.displayName, false)

                // Set Target Muscle Chips
                setSelectedTargetMuscles(exercise.primaryTargets, exercise.secondaryTargets)
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

    private fun setSelectedTargetMuscles(primaryTargets: List<TargetMuscle>, secondaryTargets: List<TargetMuscle>) {
        // Set primary targets
        for (i in 0 until binding.chipGroupPrimaryTargets.childCount) {
            val chip = binding.chipGroupPrimaryTargets.getChildAt(i) as Chip
            val muscle = chip.tag as? TargetMuscle
            chip.isChecked = muscle != null && primaryTargets.contains(muscle)
        }

        // Set secondary targets
        for (i in 0 until binding.chipGroupSecondaryTargets.childCount) {
            val chip = binding.chipGroupSecondaryTargets.getChildAt(i) as Chip
            val muscle = chip.tag as? TargetMuscle
            chip.isChecked = muscle != null && secondaryTargets.contains(muscle)
        }
    }

    private fun getSelectedTargetMuscles(chipGroup: com.google.android.material.chip.ChipGroup): List<TargetMuscle> {
        val selectedMuscles = mutableListOf<TargetMuscle>()
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as Chip
            if (chip.isChecked) {
                val muscle = chip.tag as? TargetMuscle
                muscle?.let { selectedMuscles.add(it) }
            }
        }
        return selectedMuscles
    }

    private fun saveExercise() {
        val newName = binding.editTextExerciseName.text.toString().trim()
        if (newName.isEmpty()) {
            binding.editTextExerciseName.error = "Exercise name cannot be empty"
            return
        }

        // Get Display Strings
        val regionStr = binding.dropdownRegion.text.toString()
        val patternStr = binding.dropdownPattern.text.toString()
        val tierStr = binding.dropdownTier.text.toString()
        val mechanicsStr = binding.dropdownMechanics.text.toString()

        // Reverse Lookup: Find Enum by displayName
        val selectedRegion = BodyRegion.values().find { it.displayName == regionStr }
        val selectedPattern = MovementPattern.values().find { it.displayName == patternStr }
        val selectedTier = Tier.values().find { it.displayName == tierStr }
        val selectedMechanics = Mechanics.values().find { it.displayName == mechanicsStr }

        // Get selected target muscles
        val selectedPrimaryTargets = getSelectedTargetMuscles(binding.chipGroupPrimaryTargets)
        val selectedSecondaryTargets = getSelectedTargetMuscles(binding.chipGroupSecondaryTargets)

        val trainingData = jsonHelper.readTrainingData()

        if (exerciseId != -1) {
            val existingExercise = trainingData.exerciseLibrary.find { it.id == exerciseId }
            if (existingExercise != null) {
                val index = trainingData.exerciseLibrary.indexOf(existingExercise)
                if (index != -1) {
                    trainingData.exerciseLibrary[index] = existingExercise.copy(
                        name = newName,
                        region = selectedRegion,
                        pattern = selectedPattern,
                        tier = selectedTier,
                        manualMechanics = selectedMechanics,
                        primaryTargets = selectedPrimaryTargets,
                        secondaryTargets = selectedSecondaryTargets
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
                region = selectedRegion,
                pattern = selectedPattern,
                tier = selectedTier,
                manualMechanics = selectedMechanics,
                primaryTargets = selectedPrimaryTargets,
                secondaryTargets = selectedSecondaryTargets
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