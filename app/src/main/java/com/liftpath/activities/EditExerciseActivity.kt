package com.liftpath.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.liftpath.R
import com.liftpath.databinding.ActivityEditExerciseBinding
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.EquipmentIncrementTable
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.MuscleMapColorResolver
import com.liftpath.helpers.MuscleMapRenderer
import com.liftpath.helpers.WeightIncrementHelper
import com.liftpath.helpers.WeightIncrementSettingsManager
import com.liftpath.helpers.showWithTransparentWindow
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.liftpath.models.BodyRegion
import com.liftpath.models.Equipment
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.ExerciseTargetMetric
import com.liftpath.models.ExerciseType
import com.liftpath.models.Mechanics
import com.liftpath.models.MovementPattern
import com.liftpath.models.TargetMuscle
import com.liftpath.models.Tier
import com.liftpath.models.ExerciseFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.liftpath.helpers.lpColor

class EditExerciseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditExerciseBinding
    private lateinit var jsonHelper: JsonHelper
    private var exerciseId: Int = -1
    private var isFavorite: Boolean = false
    private var availableFamilies: List<ExerciseFamily> = emptyList()
    private var selectedFamilyId: String? = null
    private var selectedEquipment: Equipment? = null
    private lateinit var incrementTable: EquipmentIncrementTable

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

        availableFamilies = jsonHelper.readTrainingData().exerciseFamilies ?: emptyList()
        incrementTable = WeightIncrementSettingsManager(this).getTable()
        setupDropdowns()
        loadExerciseData()
        setupClickListeners()
        updateMuscleMap()
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

        // 5. Exercise Family (optional)
        val familyDisplayNames = listOf("None") + availableFamilies.map { it.name }
        val familyAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, familyDisplayNames)
        binding.dropdownFamily.setAdapter(familyAdapter)
        binding.dropdownFamily.setOnItemClickListener { _, _, position, _ ->
            selectedFamilyId = if (position == 0) null else availableFamilies[position - 1].id
        }

        // 6. Equipment (optional). Tracked in a field rather than reverse-looked-up from the text
        //    at save time like the dropdowns above, because "Not set" maps to no enum value.
        val equipmentNames = listOf(getString(R.string.edit_exercise_equipment_not_set)) +
            Equipment.values().map { it.displayName }
        binding.dropdownEquipment.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, equipmentNames)
        )
        binding.dropdownEquipment.setOnItemClickListener { _, _, position, _ ->
            selectedEquipment = if (position == 0) null else Equipment.values()[position - 1]
            updateInheritedIncrementCaption()
        }

        // 7. Setup Target Muscle Chips
        setupTargetMuscleChips()
    }

    /**
     * Spells out the ladder the override fields would replace, so a blank field reads as
     * "inheriting this" rather than "unset, and who knows what happens".
     */
    private fun updateInheritedIncrementCaption() {
        val rule = selectedEquipment?.let { eq ->
            incrementTable.ruleFor(eq) ?: WeightIncrementHelper.BUILT_IN[eq]
        } ?: WeightIncrementHelper.FALLBACK

        binding.textIncrementInherited.text = when {
            rule == null -> ""
            !rule.hasLadder -> getString(
                R.string.edit_exercise_increment_inherited_no_ladder,
                selectedEquipment?.displayName.orEmpty()
            )
            selectedEquipment == null -> getString(
                R.string.edit_exercise_increment_inherited_unset,
                WeightIncrementHelper.format(rule.incrementKg)
            )
            else -> getString(
                R.string.edit_exercise_increment_inherited,
                selectedEquipment!!.displayName,
                WeightIncrementHelper.format(rule.incrementKg),
                WeightIncrementHelper.format(rule.minimumKg)
            )
        }
    }

    /** Blank means "inherit", so an unparseable or empty field is null rather than zero. */
    private fun parseOverride(text: CharSequence?): Float? =
        text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.toFloatOrNull()?.takeIf { it >= 0f }

    private fun setupTargetMuscleChips() {
        // Create chips for all TargetMuscle values
        val allMuscles = TargetMuscle.values()

        val onChipChecked: (View, Boolean) -> Unit = { _, _ -> updateMuscleMap() }

        // Primary Targets ChipGroup
        binding.chipGroupPrimaryTargets.removeAllViews()
        allMuscles.forEach { muscle ->
            val chip = Chip(this)
            chip.text = muscle.displayName
            chip.isCheckable = true
            chip.tag = muscle
            chip.chipStrokeWidth = 1f
            chip.chipStrokeColor = android.content.res.ColorStateList.valueOf(lpColor(R.attr.lpAccent))
            chip.setTextColor(android.content.res.ColorStateList.valueOf(lpColor(R.attr.lpInk)))
            chip.setOnCheckedChangeListener(onChipChecked)
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
            chip.chipStrokeColor = android.content.res.ColorStateList.valueOf(lpColor(R.attr.lpAccent))
            chip.setTextColor(android.content.res.ColorStateList.valueOf(lpColor(R.attr.lpInk)))
            chip.setOnCheckedChangeListener(onChipChecked)
            binding.chipGroupSecondaryTargets.addView(chip)
        }
    }

    private fun updateMuscleMap() {
        val primaryTargets = getSelectedTargetMuscles(binding.chipGroupPrimaryTargets).toSet()
        val secondaryTargets = getSelectedTargetMuscles(binding.chipGroupSecondaryTargets).toSet()

        lifecycleScope.launch {
            val muscleRoles = MuscleMapColorResolver.resolveHighlightColors(primaryTargets, secondaryTargets)
            val maskRoles = MuscleMapColorResolver.flattenToMaskCategories(
                muscleRoles, rank = MuscleMapColorResolver::highlightRank
            )
            val maskColors = maskRoles.map { (maskResId, role) ->
                maskResId to MuscleMapColorResolver.colorFor(this@EditExerciseActivity, role)
            }
            val bitmap = withContext(Dispatchers.Default) {
                MuscleMapRenderer.render(this@EditExerciseActivity, maskColors)
            }
            binding.imageMuscleMap.setImageBitmap(bitmap)
        }
    }

    private fun loadExerciseData() {
        // Generic image up front so every path (new exercise, missing library entry, or an
        // exercise with no drawing of its own) still shows something.
        binding.imageExerciseIllustration.setImageResource(R.drawable.ic_dumbbell)

        if (exerciseId != -1) {
            binding.textEditExerciseTitle.setText(R.string.title_edit_exercise)
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

                // Set exercise type (default Weighted for legacy/null)
                setSelectedExerciseType(exercise.effectiveType)

                // Set target metric (default Reps for legacy/null)
                setSelectedTargetMetric(exercise.effectiveTargetMetric)

                // Set Target Muscle Chips
                setSelectedTargetMuscles(exercise.primaryTargets, exercise.secondaryTargets)
                
                // Load favorite status
                isFavorite = exercise.isFavorite
                updateFavoriteStarIcon()
                
                // Load note
                binding.editTextNote.setText(exercise.note ?: "")

                // Swap in the exercise's own illustration when it has one
                exercise.illustrationRes?.let { binding.imageExerciseIllustration.setImageResource(it) }

                // Populate family dropdown
                selectedFamilyId = exercise.familyId
                val familyText = if (exercise.familyId != null) {
                    availableFamilies.find { it.id == exercise.familyId }?.name ?: "None"
                } else "None"
                binding.dropdownFamily.setText(familyText, false)

                // Equipment + weight-ladder overrides. saveExercise() now *writes* equipment where
                // it used to preserve it by omission, so this must populate on every path that
                // reaches a save — otherwise a save would null out a catalog exercise's equipment.
                selectedEquipment = exercise.equipment
                binding.dropdownEquipment.setText(
                    exercise.equipment?.displayName
                        ?: getString(R.string.edit_exercise_equipment_not_set),
                    false
                )
                exercise.weightIncrementKgOverride?.let {
                    binding.editTextStepOverride.setText(WeightIncrementHelper.format(it))
                }
                exercise.weightMinimumKgOverride?.let {
                    binding.editTextMinOverride.setText(WeightIncrementHelper.format(it))
                }

                // updateMuscleMap() is called automatically via onPageFinished or chip listener
            }
        } else {
            binding.textEditExerciseTitle.setText(R.string.title_create_exercise)
            binding.cardDelete.visibility = View.GONE
            // New exercises default to Weighted
            setSelectedExerciseType(ExerciseType.WEIGHTED)
            // New exercises default to Reps
            setSelectedTargetMetric(ExerciseTargetMetric.REPS)
            // "Not set" rather than OTHER: behaviourally identical (both resolve to the 2.5 kg
            // fallback) but it keeps the library subtitle clean and asserts nothing untrue.
            binding.dropdownEquipment.setText(
                getString(R.string.edit_exercise_equipment_not_set), false
            )
        }
        updateInheritedIncrementCaption()
    }

    private fun setSelectedExerciseType(type: ExerciseType) {
        when (type) {
            ExerciseType.BODYWEIGHT -> binding.chipTypeBodyweight.isChecked = true
            else -> binding.chipTypeWeighted.isChecked = true
        }
    }

    private fun getSelectedExerciseType(): ExerciseType =
        if (binding.chipTypeBodyweight.isChecked) ExerciseType.BODYWEIGHT else ExerciseType.WEIGHTED

    private fun setSelectedTargetMetric(metric: ExerciseTargetMetric) {
        when (metric) {
            ExerciseTargetMetric.TIME -> binding.chipMetricTime.isChecked = true
            else -> binding.chipMetricReps.isChecked = true
        }
    }

    private fun getSelectedTargetMetric(): ExerciseTargetMetric =
        if (binding.chipMetricTime.isChecked) ExerciseTargetMetric.TIME else ExerciseTargetMetric.REPS

    private fun setupClickListeners() {
        binding.buttonSaveExercise.setOnClickListener { saveExercise() }
        binding.cardDelete.setOnClickListener { showDeleteConfirmationDialog() }
        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonCancel.setOnClickListener { finish() }
        binding.buttonFavorite.setOnClickListener { toggleFavorite() }
    }
    
    private fun toggleFavorite() {
        isFavorite = !isFavorite
        updateFavoriteStarIcon()
    }
    
    private fun updateFavoriteStarIcon() {
        if (isFavorite) {
            binding.imageFavoriteStar.setImageResource(R.drawable.ic_star)
            binding.imageFavoriteStar.setColorFilter(lpColor(R.attr.lpAccent), android.graphics.PorterDuff.Mode.SRC_IN)
        } else {
            binding.imageFavoriteStar.setImageResource(R.drawable.ic_star_outline)
            binding.imageFavoriteStar.setColorFilter(lpColor(R.attr.lpAccent), android.graphics.PorterDuff.Mode.SRC_IN)
        }
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
        
        // Explicitly update muscle map after setting chips
        // (Programmatically setting isChecked may not trigger listeners in some cases)
        updateMuscleMap()
    }

    private fun getSelectedTargetMuscles(chipGroup: ChipGroup): List<TargetMuscle> {
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

        // Get note
        val note = binding.editTextNote.text.toString().trim().takeIf { it.isNotEmpty() }

        val selectedType = getSelectedExerciseType()
        val selectedMetric = getSelectedTargetMetric()

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
                        secondaryTargets = selectedSecondaryTargets,
                        isFavorite = isFavorite,
                        note = note,
                        exerciseType = selectedType,
                        targetMetric = selectedMetric,
                        familyId = selectedFamilyId,
                        equipment = selectedEquipment,
                        weightIncrementKgOverride = parseOverride(binding.editTextStepOverride.text),
                        weightMinimumKgOverride = parseOverride(binding.editTextMinOverride.text)
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
                secondaryTargets = selectedSecondaryTargets,
                isFavorite = isFavorite,
                note = note,
                exerciseType = selectedType,
                targetMetric = selectedMetric,
                familyId = selectedFamilyId,
                equipment = selectedEquipment,
                weightIncrementKgOverride = parseOverride(binding.editTextStepOverride.text),
                weightMinimumKgOverride = parseOverride(binding.editTextMinOverride.text)
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