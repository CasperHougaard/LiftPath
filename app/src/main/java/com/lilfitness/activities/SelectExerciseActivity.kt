package com.lilfitness.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.lilfitness.R
import com.lilfitness.adapters.SelectExerciseWithPlanAdapter
import com.lilfitness.databinding.ActivitySelectExerciseBinding
import com.lilfitness.helpers.DialogHelper
import com.lilfitness.helpers.JsonHelper
import com.lilfitness.helpers.ProgressionHelper
import com.lilfitness.helpers.ProgressionSettingsManager
import com.lilfitness.helpers.showWithTransparentWindow
import com.lilfitness.models.BodyRegion
import com.lilfitness.models.ExerciseLibraryItem
import java.util.Locale

class SelectExerciseActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySelectExerciseBinding
    private lateinit var jsonHelper: JsonHelper
    
    // Data sources
    private var allExercises: List<ExerciseLibraryItem> = emptyList()
    private var displayedExercises: List<ExerciseLibraryItem> = emptyList()
    
    private lateinit var adapter: SelectExerciseWithPlanAdapter
    
    // Intent / Context Data
    private var sessionWorkoutType: String = "heavy"
    private var planId: String? = null
    private var planExerciseIds: Set<Int> = emptySet()
    private var alreadyAddedExerciseIds: Set<Int> = emptySet()
    
    // Filter States
    private var filterUnaddedOnly: Boolean = true
    private var searchQuery: String = ""
    private var selectedRegion: BodyRegion? = null // Future: Filter by UPPER/LOWER

    companion object {
        const val EXTRA_EXERCISE_ID = "extra_exercise_id"
        const val EXTRA_EXERCISE_NAME = "extra_exercise_name"
        const val EXTRA_WORKOUT_TYPE = "extra_workout_type"
        const val EXTRA_SELECTED_WORKOUT_TYPE = "extra_selected_workout_type"
        const val EXTRA_PLAN_ID = "extra_plan_id"
        const val EXTRA_ALREADY_ADDED_EXERCISE_IDS = "extra_already_added_exercise_ids"
    }

    private val createExerciseLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val newId = result.data?.getIntExtra(EditExerciseActivity.EXTRA_EXERCISE_ID, -1) ?: -1
            
            // RELOAD DATA: We must reload from JSON because the EditActivity saved the full object 
            // (with regions/targets) to disk. We cannot manually construct it here safely anymore.
            loadPlanExercises()
            loadExercises() 

            // Find the newly created exercise to auto-select it
            val newExercise = allExercises.find { it.id == newId }
            if (newExercise != null) {
                onExerciseSelected(newExercise, sessionWorkoutType)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectExerciseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBackgroundAnimation()

        jsonHelper = JsonHelper(this)
        
        // Unpack Intent
        sessionWorkoutType = intent.getStringExtra(EXTRA_WORKOUT_TYPE) ?: "heavy"
        planId = intent.getStringExtra(EXTRA_PLAN_ID)
        alreadyAddedExerciseIds = intent.getIntArrayExtra(EXTRA_ALREADY_ADDED_EXERCISE_IDS)?.toSet() ?: emptySet()
        
        // Initial Setup
        loadPlanExercises()
        setupRecyclerView()
        setupFilterToggle()
        
        // Initial Load
        loadExercises()

        binding.buttonCreateNewExercise.setOnClickListener {
            val intent = Intent(this, EditExerciseActivity::class.java)
            createExerciseLauncher.launch(intent)
        }

        binding.buttonBack.setOnClickListener {
            onBackPressed()
        }
        
        // OPTIONAL: If you add an EditText with id 'searchEditText' to your XML later, 
        // this logic is ready to go.
        /*
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s.toString()
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        */
    }
    
    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is android.graphics.drawable.Animatable) {
            drawable.start()
        }
    }

    private fun loadPlanExercises() {
        if (planId != null) {
            val trainingData = jsonHelper.readTrainingData()
            val plan = trainingData.workoutPlans.find { it.id == planId }
            planExerciseIds = plan?.exerciseIds?.toSet() ?: emptySet()
        }
    }

    private fun setupRecyclerView() {
        adapter = SelectExerciseWithPlanAdapter(
            exercises = emptyList(),
            planExerciseIds = planExerciseIds,
            onExerciseClicked = { exercise ->
                onExerciseSelected(exercise, sessionWorkoutType)
            }
        )
        binding.recyclerViewSelectExercise.adapter = adapter
        binding.recyclerViewSelectExercise.layoutManager = LinearLayoutManager(this)
    }

    private fun setupFilterToggle() {
        binding.switchFilterUnadded.isChecked = filterUnaddedOnly
        binding.switchFilterUnadded.setOnCheckedChangeListener { _, isChecked ->
            filterUnaddedOnly = isChecked
            applyFilters()
        }
    }

    private fun loadExercises() {
        // Load Raw Data
        allExercises = jsonHelper.readTrainingData().exerciseLibrary
        applyFilters()
    }

    private fun applyFilters() {
        var result = allExercises

        // 1. Filter by "Not Added Yet"
        if (filterUnaddedOnly) {
            result = result.filter { it.id !in alreadyAddedExerciseIds }
        }

        // 2. Filter by Search Text (Name or Target Muscle)
        if (searchQuery.isNotEmpty()) {
            val query = searchQuery.lowercase()
            result = result.filter { 
                it.name.lowercase().contains(query) || 
                it.primaryTargets.any { muscle -> muscle.name.lowercase().contains(query) }
            }
        }

        // 3. Filter by Body Region (If you add buttons for this later)
        selectedRegion?.let { region ->
            result = result.filter { it.region == region }
        }

        // 4. Smart Sort: Region first, then Name
        // This groups "LOWER" body exercises together and "UPPER" together
        result = result.sortedWith(
            compareBy<ExerciseLibraryItem> { it.region } // Group by Region
            .thenBy { it.name }                          // Then Alphabetical
        )

        displayedExercises = result
        
        // Update Adapter
        adapter = SelectExerciseWithPlanAdapter(
            exercises = displayedExercises,
            planExerciseIds = planExerciseIds,
            onExerciseClicked = { exercise ->
                onExerciseSelected(exercise, sessionWorkoutType)
            }
        )
        binding.recyclerViewSelectExercise.adapter = adapter
    }

    private fun onExerciseSelected(exercise: ExerciseLibraryItem, requestedType: String? = null) {
        val workoutType = requestedType ?: sessionWorkoutType
        val trainingData = jsonHelper.readTrainingData()
        val settingsManager = ProgressionSettingsManager(this)
        val userSettings = settingsManager.getSettings()
        
        val suggestion = ProgressionHelper.getSuggestion(
            exerciseId = exercise.id,
            requestedType = workoutType,
            trainingData = trainingData,
            settings = userSettings
        )

        if (suggestion.isFirstTime) {
            showFirstTimeDialog(exercise, workoutType)
        } else {
            showSuggestionDialog(exercise, suggestion, workoutType)
        }
    }

    // --- DIALOGS (Unchanged Logic, just ensuring compatibility) ---

    private fun showFirstTimeDialog(exercise: ExerciseLibraryItem, workoutType: String) {
        DialogHelper.createBuilder(this)
            .setTitle(exercise.name)
            .setMessage(getString(R.string.dialog_message_first_time_exercise, formatTypeLabel(workoutType)))
            .setPositiveButton(getString(R.string.button_add_exercise)) { _, _ ->
                returnExercise(exercise, workoutType)
            }
            .setNeutralButton(getString(R.string.button_change_type)) { _, _ ->
                showTypeOverrideDialog(exercise, workoutType)
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    private fun showSuggestionDialog(
        exercise: ExerciseLibraryItem,
        suggestion: ProgressionHelper.ProgressionSuggestion,
        workoutType: String
    ) {
        val suggestedWeight = suggestion.proposedWeight

        val message = buildString {
            suggestion.badge?.let { append("$it\n\n") }
            suggestion.lastHeavyRpe?.let {
                append(getString(R.string.dialog_message_last_rpe, it))
                append("\n")
            }
            suggestion.daysSinceLastWorkout?.let { days ->
                if (days >= 14) {
                    append(getString(R.string.dialog_message_days_since_last, days))
                    append("\n")
                }
            }
            append(suggestion.humanExplanation)
            append("\n\n")
            
            if (suggestedWeight != null) {
                append(getString(R.string.dialog_message_suggested_weight, suggestedWeight))
            } else {
                append(getString(R.string.dialog_message_custom_workout))
            }
        }

        DialogHelper.createBuilder(this)
            .setTitle(exercise.name)
            .setMessage(message)
            .setPositiveButton(getString(R.string.button_add_exercise)) { _, _ ->
                returnExercise(exercise, workoutType)
            }
            .setNeutralButton(getString(R.string.button_change_type)) { _, _ ->
                showTypeOverrideDialog(exercise, workoutType)
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    private fun showTypeOverrideDialog(exercise: ExerciseLibraryItem, currentType: String) {
        val types = arrayOf("Heavy", "Light", "Custom")
        val currentIndex = when (currentType) {
            "heavy" -> 0
            "light" -> 1
            else -> 2
        }

        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.dialog_title_override_workout_type, exercise.name))
            .setSingleChoiceItems(types, currentIndex) { dialog, which ->
                val newType = when (which) {
                    0 -> "heavy"
                    1 -> "light"
                    else -> "custom"
                }
                dialog.dismiss()
                onExerciseSelected(exercise, newType)
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    private fun returnExercise(exercise: ExerciseLibraryItem, workoutType: String) {
        val intent = Intent().apply {
            putExtra(EXTRA_EXERCISE_ID, exercise.id)
            putExtra(EXTRA_EXERCISE_NAME, exercise.name)
            putExtra(EXTRA_SELECTED_WORKOUT_TYPE, workoutType)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun formatTypeLabel(type: String): String {
        return type.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }
}