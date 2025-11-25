package com.liftpath.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.liftpath.R
import com.liftpath.adapters.ListItem
import com.liftpath.adapters.SelectExerciseWithPlanAdapter
import com.liftpath.databinding.ActivitySelectExerciseBinding
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.isVisible
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.MuscleActivationHelper
import com.liftpath.helpers.ProgressionHelper
import com.liftpath.helpers.ProgressionSettingsManager
import com.liftpath.helpers.showWithTransparentWindow
import com.liftpath.models.BodyRegion
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.MovementPattern
import com.liftpath.models.TargetMuscle
import kotlinx.coroutines.*
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
    private var filterMissingPrimary: Boolean = false
    private var filterMissingSecondary: Boolean = false
    private var searchQuery: String = ""
    private var selectedRegion: BodyRegion? = null // Reused for body area filter
    private var selectedMovementPattern: MovementPattern? = null
    private var selectedMuscleGroups: Set<TargetMuscle> = emptySet()
    private var isAdvancedFiltersExpanded: Boolean = false
    
    // Muscle Activation State
    private var workoutExercises: List<ExerciseLibraryItem> = emptyList()
    private var muscleActivationState: MuscleActivationHelper.MuscleActivationState? = null
    private var missingMusclesState: MuscleActivationHelper.MuscleActivationState? = null
    private var isMuscleOverviewExpanded: Boolean = false
    private var isWebViewReady: Boolean = false
    
    // Coroutine scope for background loading
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
        setupFilterChips()
        setupAdvancedFilters()
        setupMuscleOverview()
        
        // Load workout exercises and calculate muscle activation (on background thread)
        loadWorkoutExercises()
        
        // Initial Load
        loadExercises()

        binding.buttonCreateNewExercise.setOnClickListener {
            val intent = Intent(this, EditExerciseActivity::class.java)
            createExerciseLauncher.launch(intent)
        }

        binding.buttonBack.setOnClickListener {
            finish()
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
            items = emptyList(),
            planExerciseIds = planExerciseIds,
            onExerciseClicked = { exercise ->
                onExerciseSelected(exercise, sessionWorkoutType)
            }
        )
        binding.recyclerViewSelectExercise.adapter = adapter
        binding.recyclerViewSelectExercise.layoutManager = LinearLayoutManager(this)
    }

    private fun setupFilterChips() {
        // Set initial states
        binding.chipFilterUnadded.isChecked = filterUnaddedOnly
        binding.chipFilterMissingPrimary.isChecked = filterMissingPrimary
        binding.chipFilterMissingSecondary.isChecked = filterMissingSecondary
        
        // Set up chip listeners
        binding.chipFilterUnadded.setOnCheckedChangeListener { _, isChecked ->
            filterUnaddedOnly = isChecked
            applyFilters()
        }
        
        binding.chipFilterMissingPrimary.setOnCheckedChangeListener { _, isChecked ->
            filterMissingPrimary = isChecked
            applyFilters()
        }
        
        binding.chipFilterMissingSecondary.setOnCheckedChangeListener { _, isChecked ->
            filterMissingSecondary = isChecked
            applyFilters()
        }
    }

    private fun setupAdvancedFilters() {
        // Set up expandable section toggle
        binding.layoutAdvancedFiltersHeader.setOnClickListener {
            toggleAdvancedFilters()
        }

        // Set up Movement Pattern Spinner
        val movementPatterns = listOf("All") + MovementPattern.values().map { it.displayName }
        val movementAdapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            movementPatterns
        )
        movementAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMovementPattern.adapter = movementAdapter
        binding.spinnerMovementPattern.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selectedMovementPattern = if (position == 0) null else MovementPattern.values()[position - 1]
                applyFilters()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Set up Body Region Spinner
        val bodyRegions = listOf("All") + BodyRegion.values().map { it.displayName }
        val bodyRegionAdapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            bodyRegions
        )
        bodyRegionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerBodyRegion.adapter = bodyRegionAdapter
        binding.spinnerBodyRegion.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selectedRegion = if (position == 0) null else BodyRegion.values()[position - 1]
                applyFilters()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Set up Muscle Groups ChipGroup
        TargetMuscle.values().forEach { muscle ->
            val chip = com.google.android.material.chip.Chip(this)
            chip.id = View.generateViewId()
            chip.text = muscle.displayName
            chip.isCheckable = true
            chip.isChecked = false
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedMuscleGroups = selectedMuscleGroups + muscle
                } else {
                    selectedMuscleGroups = selectedMuscleGroups - muscle
                }
                applyFilters()
            }
            binding.chipGroupMuscleGroups.addView(chip)
        }
    }

    private fun toggleAdvancedFilters() {
        isAdvancedFiltersExpanded = !isAdvancedFiltersExpanded
        
        if (isAdvancedFiltersExpanded) {
            binding.layoutAdvancedFiltersContent.visibility = View.VISIBLE
            binding.imageAdvancedFiltersExpand.rotation = 180f
        } else {
            binding.layoutAdvancedFiltersContent.visibility = View.GONE
            binding.imageAdvancedFiltersExpand.rotation = 0f
        }
    }
    
    private fun loadWorkoutExercises() {
        // Load exercises from workout on background thread to prevent UI stutter
        scope.launch(Dispatchers.IO) {
            val exerciseIds = alreadyAddedExerciseIds.toList()
            val trainingData = jsonHelper.readTrainingData()
            val exercises = exerciseIds.mapNotNull { id ->
                trainingData.exerciseLibrary.find { it.id == id }
            }
            
            // Calculate muscle activation
            val activated = MuscleActivationHelper.getActivatedMuscles(exercises)
            val missing = MuscleActivationHelper.getMissingMuscles(activated)
            
            // Update UI on main thread
            withContext(Dispatchers.Main) {
                workoutExercises = exercises
                muscleActivationState = activated
                missingMusclesState = missing
                updateMuscleOverviewBadge()
                if (isMuscleOverviewExpanded) {
                    updateMuscleMap()
                }
            }
        }
    }
    
    private fun setupMuscleOverview() {
        // Set up expand/collapse listener
        binding.layoutMuscleOverviewHeader.setOnClickListener {
            toggleMuscleOverview()
        }
        
        // Set up WebView
        binding.webviewMuscleOverview.settings.apply {
            javaScriptEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
        }
        binding.webviewMuscleOverview.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        
        binding.webviewMuscleOverview.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    android.util.Log.d("MuscleOverview", 
                        "${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}")
                }
                return true
            }
        }
        
        binding.webviewMuscleOverview.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isWebViewReady = true
                binding.progressMuscleOverview.visibility = View.GONE
                if (isMuscleOverviewExpanded) {
                    updateMuscleMap()
                }
            }
        }
        
        // Load WebView eagerly (as per plan requirement)
        binding.webviewMuscleOverview.loadUrl("file:///android_asset/muscle_map.html")
    }
    
    private fun toggleMuscleOverview() {
        isMuscleOverviewExpanded = !isMuscleOverviewExpanded
        
        if (isMuscleOverviewExpanded) {
            binding.layoutMuscleOverviewContent.visibility = View.VISIBLE
            binding.imageMuscleOverviewExpand.rotation = 180f
            
            // Update muscle map if WebView is ready and we have data
            if (isWebViewReady && muscleActivationState != null) {
                updateMuscleMap()
            }
        } else {
            binding.layoutMuscleOverviewContent.visibility = View.GONE
            binding.imageMuscleOverviewExpand.rotation = 0f
        }
    }
    
    private fun updateMuscleOverviewBadge() {
        val activated = muscleActivationState
        if (activated != null) {
            val count = activated.getTotalActivated()
            val total = activated.getTotalPossible()
            binding.textMuscleOverviewBadge.text = "$count/$total"
        } else {
            binding.textMuscleOverviewBadge.text = "0/24"
        }
    }
    
    private fun updateMuscleMap() {
        if (!isWebViewReady || muscleActivationState == null) {
            return
        }
        
        val activated = muscleActivationState!!
        
        // Show empty state if no muscles activated
        if (activated.isEmpty()) {
            binding.textMuscleOverviewEmpty.visibility = View.VISIBLE
            binding.webviewMuscleOverview.visibility = View.GONE
            return
        }
        
        binding.textMuscleOverviewEmpty.visibility = View.GONE
        binding.webviewMuscleOverview.visibility = View.VISIBLE
        
        // Convert muscle sets to JavaScript arrays
        val primaryArray = activated.primaryMuscles.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ", "
        ) { "'${it.name}'" }
        
        val secondaryArray = activated.secondaryMuscles.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ", "
        ) { "'${it.name}'" }
        
        // Call JavaScript setHighlights function
        val jsCode = """
            (function() {
                try {
                    if (typeof setHighlights === 'function') {
                        setHighlights($primaryArray, $secondaryArray);
                        return 'setHighlights called';
                    } else if (typeof window.setHighlights === 'function') {
                        window.setHighlights($primaryArray, $secondaryArray);
                        return 'window.setHighlights called';
                    } else {
                        console.error('setHighlights function not found!');
                        return 'ERROR: setHighlights not found';
                    }
                } catch (e) {
                    console.error('Error calling setHighlights:', e);
                    return 'ERROR: ' + e.message;
                }
            })();
        """.trimIndent()
        
        binding.webviewMuscleOverview.evaluateJavascript(jsCode) { result ->
            android.util.Log.d("MuscleOverview", "JavaScript result: $result")
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

        // 2. Filter by Missing Muscles (OR logic: if both selected, show exercises matching either)
        val missingState = missingMusclesState
        if (missingState != null && (filterMissingPrimary || filterMissingSecondary)) {
            result = result.filter { exercise ->
                val hasMissingPrimary = filterMissingPrimary && 
                    exercise.primaryTargets.intersect(missingState.primaryMuscles).isNotEmpty()
                val hasMissingSecondary = filterMissingSecondary && 
                    exercise.secondaryTargets.intersect(missingState.secondaryMuscles).isNotEmpty()
                
                // OR logic: exercise matches if it has missing primary OR missing secondary
                hasMissingPrimary || hasMissingSecondary
            }
        }

        // 3. Filter by Search Text (Name or Target Muscle)
        if (searchQuery.isNotEmpty()) {
            val query = searchQuery.lowercase()
            result = result.filter { 
                it.name.lowercase().contains(query) || 
                it.primaryTargets.any { muscle -> muscle.name.lowercase().contains(query) }
            }
        }

        // 4. Filter by Movement Pattern
        selectedMovementPattern?.let { pattern ->
            result = result.filter { it.pattern == pattern }
        }

        // 5. Filter by Body Region
        selectedRegion?.let { region ->
            result = result.filter { it.region == region }
        }

        // 6. Filter by Muscle Groups (show exercises where any selected muscle is in primary or secondary targets)
        if (selectedMuscleGroups.isNotEmpty()) {
            result = result.filter { exercise ->
                val hasPrimaryMatch = exercise.primaryTargets.intersect(selectedMuscleGroups).isNotEmpty()
                val hasSecondaryMatch = exercise.secondaryTargets.intersect(selectedMuscleGroups).isNotEmpty()
                hasPrimaryMatch || hasSecondaryMatch
            }
        }

        // 7. Sort alphabetically by name
        result = result.sortedBy { it.name.lowercase() }

        displayedExercises = result
        
        // 8. Create list items with section headers if muscle group filters are applied
        val listItems = if (selectedMuscleGroups.isNotEmpty()) {
            // Split into primary and secondary
            val primaryExercises = result.filter { exercise ->
                exercise.primaryTargets.intersect(selectedMuscleGroups).isNotEmpty()
            }.sortedBy { it.name.lowercase() }
            
            val secondaryExercises = result.filter { exercise ->
                exercise.primaryTargets.intersect(selectedMuscleGroups).isEmpty() &&
                exercise.secondaryTargets.intersect(selectedMuscleGroups).isNotEmpty()
            }.sortedBy { it.name.lowercase() }
            
            mutableListOf<ListItem>().apply {
                if (primaryExercises.isNotEmpty()) {
                    add(ListItem.SectionHeader("Primary"))
                    primaryExercises.forEach { add(ListItem.ExerciseItem(it)) }
                }
                if (secondaryExercises.isNotEmpty()) {
                    add(ListItem.SectionHeader("Secondary"))
                    secondaryExercises.forEach { add(ListItem.ExerciseItem(it)) }
                }
            }
        } else {
            // No section headers, just exercises
            result.map { ListItem.ExerciseItem(it) }
        }
        
        // Update Adapter
        adapter.updateItems(listItems)
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
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}