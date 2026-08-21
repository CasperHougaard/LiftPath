package com.liftpath.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.R
import com.liftpath.adapters.ListItem
import com.liftpath.adapters.SelectExerciseWithPlanAdapter
import com.liftpath.databinding.ActivitySelectExerciseBinding
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import com.liftpath.helpers.CircuitStore
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.MuscleActivationHelper
import com.liftpath.helpers.MuscleMapColorResolver
import com.liftpath.helpers.MuscleMapRenderer
import com.liftpath.models.BodyRegion
import com.liftpath.models.CircuitTemplate
import com.liftpath.models.ExerciseFamily
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.MovementPattern
import com.liftpath.models.TargetMuscle
import kotlinx.coroutines.*

class SelectExerciseActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySelectExerciseBinding
    private lateinit var jsonHelper: JsonHelper
    
    // Data sources
    private var allExercises: List<ExerciseLibraryItem> = emptyList()
    private var displayedExercises: List<ExerciseLibraryItem> = emptyList()
    private var allFamilies: List<ExerciseFamily> = emptyList()
    private val familyNameMap: Map<String, String> get() = allFamilies.associate { it.id to it.name }
    
    private lateinit var adapter: SelectExerciseWithPlanAdapter
    
    // Intent / Context Data
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
    private var isGroupedByFamily: Boolean = false
    
    // Collapsible sections state
    private val collapsedSections = mutableSetOf<String>()
    
    // Muscle Activation State
    private var workoutExercises: List<ExerciseLibraryItem> = emptyList()
    private var muscleActivationState: MuscleActivationHelper.MuscleActivationState? = null
    private var missingMusclesState: MuscleActivationHelper.MuscleActivationState? = null
    private var isMuscleOverviewExpanded: Boolean = false
    
    // Coroutine scope for background loading
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Group-mode picking (Superset / Circuit) — opt-in, see EXTRA_ENABLE_GROUP_MODES.
    private enum class PickMode { EXERCISE, SUPERSET, CIRCUIT }
    private var enableGroupModes = false
    private var pickMode = PickMode.EXERCISE
    private val selectedForSuperset = mutableSetOf<Int>()
    private var allCircuits: List<CircuitTemplate> = emptyList()
    private lateinit var circuitAdapter: CircuitPickAdapter

    companion object {
        const val EXTRA_EXERCISE_ID = "extra_exercise_id"
        const val EXTRA_EXERCISE_NAME = "extra_exercise_name"
        const val EXTRA_WORKOUT_TYPE = "extra_workout_type"  // Keep for legacy compatibility
        const val EXTRA_PLAN_ID = "extra_plan_id"
        const val EXTRA_ALREADY_ADDED_EXERCISE_IDS = "extra_already_added_exercise_ids"
        /** Opt-in: shows the Exercise/Superset/Circuit mode chips. Only the active workout's
         *  regular add-exercise entry point sets this — every other caller of this screen wants
         *  a plain single-exercise picker and must see no behavior change. */
        const val EXTRA_ENABLE_GROUP_MODES = "extra_enable_group_modes"
        /** "EXERCISE" | "SUPERSET" | "CIRCUIT". Absent means "EXERCISE" for legacy callers. */
        const val EXTRA_RESULT_TYPE = "extra_result_type"
        const val EXTRA_SUPERSET_EXERCISE_IDS = "extra_superset_exercise_ids"
        const val EXTRA_SUPERSET_EXERCISE_NAMES = "extra_superset_exercise_names"
        const val EXTRA_SELECTED_CIRCUIT_ID = "extra_selected_circuit_id"
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
                onExerciseSelected(newExercise)
            }
        }
    }

    // Authoring a brand new circuit from within the picker: on success, reload the list in
    // place so the circuit just saved is right there to pick — stays on screen, no auto-select.
    private val editCircuitLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            loadCircuits()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectExerciseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBackgroundAnimation()

        jsonHelper = JsonHelper(this)
        
        // Unpack Intent
        planId = intent.getStringExtra(EXTRA_PLAN_ID)
        alreadyAddedExerciseIds = intent.getIntArrayExtra(EXTRA_ALREADY_ADDED_EXERCISE_IDS)?.toSet() ?: emptySet()
        enableGroupModes = intent.getBooleanExtra(EXTRA_ENABLE_GROUP_MODES, false)

        // Initial Setup
        loadPlanExercises()
        setupRecyclerView()
        setupFilterChips()
        setupAdvancedFilters()
        setupMuscleOverview()
        if (enableGroupModes) {
            setupPickModeChips()
        }

        // Load workout exercises and calculate muscle activation (on background thread)
        loadWorkoutExercises()

        // Initial Load
        loadExercises()

        binding.buttonCreateNewExercise.setOnClickListener {
            when (pickMode) {
                PickMode.EXERCISE -> {
                    val intent = Intent(this, EditExerciseActivity::class.java)
                    createExerciseLauncher.launch(intent)
                }
                PickMode.SUPERSET -> returnSupersetSelection()
                PickMode.CIRCUIT -> editCircuitLauncher.launch(Intent(this, EditCircuitActivity::class.java))
            }
        }

        binding.buttonBack.setOnClickListener {
            finish()
        }
        
        // Search field TextWatcher
        binding.editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                try {
                    searchQuery = s?.toString() ?: ""
                    applyFilters()
                } catch (e: Exception) {
                    android.util.Log.e("SelectExerciseActivity", "Error in search filter", e)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        
        // Handle IME action to prevent activity from closing
        binding.editTextSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                // Hide keyboard and keep focus on search field
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(binding.editTextSearch.windowToken, 0)
                binding.editTextSearch.clearFocus()
                true
            } else {
                false
            }
        }
    }

    // --- GROUP MODES: Exercise / Superset / Circuit ---

    private fun setupPickModeChips() {
        binding.chipGroupPickMode.visibility = View.VISIBLE
        circuitAdapter = CircuitPickAdapter(emptyList(), emptyList()) { template -> onCircuitSelected(template) }
        loadCircuits()

        binding.chipGroupPickMode.setOnCheckedStateChangeListener { _, checkedIds ->
            val newMode = when (checkedIds.firstOrNull()) {
                binding.chipModeSuperset.id -> PickMode.SUPERSET
                binding.chipModeCircuit.id -> PickMode.CIRCUIT
                else -> PickMode.EXERCISE
            }
            setPickMode(newMode)
        }
    }

    private fun setPickMode(mode: PickMode) {
        if (pickMode == mode) return
        pickMode = mode
        selectedForSuperset.clear()

        when (mode) {
            PickMode.EXERCISE, PickMode.SUPERSET -> {
                binding.cardSearch.visibility = View.VISIBLE
                binding.cardFilter.visibility = View.VISIBLE
                binding.textCircuitListEmpty.visibility = View.GONE
                binding.recyclerViewSelectExercise.visibility = View.VISIBLE
                binding.recyclerViewSelectExercise.adapter = adapter
                adapter.setSelectionMode(mode == PickMode.SUPERSET)
                applyFilters()
            }
            PickMode.CIRCUIT -> {
                binding.cardSearch.visibility = View.GONE
                binding.cardFilter.visibility = View.GONE
                binding.recyclerViewSelectExercise.adapter = circuitAdapter
                refreshCircuitEmptyState()
            }
        }
        updateBottomButton()
    }

    private fun updateBottomButton() {
        when (pickMode) {
            PickMode.EXERCISE -> {
                binding.buttonCreateNewExercise.text = getString(R.string.button_create_new_exercise)
                binding.buttonCreateNewExercise.isEnabled = true
            }
            PickMode.SUPERSET -> {
                val count = selectedForSuperset.size
                binding.buttonCreateNewExercise.text = if (count >= 2) {
                    getString(R.string.button_add_superset_selection, count)
                } else {
                    getString(R.string.button_add_superset_selection_empty)
                }
                binding.buttonCreateNewExercise.isEnabled = count >= 2
            }
            PickMode.CIRCUIT -> {
                binding.buttonCreateNewExercise.text = getString(R.string.button_create_new_circuit)
                binding.buttonCreateNewExercise.isEnabled = true
            }
        }
    }

    /** Row tap: Superset mode toggles selection, everything else returns immediately as before. */
    private fun onExerciseRowTapped(exercise: ExerciseLibraryItem) {
        if (pickMode == PickMode.SUPERSET) {
            if (!selectedForSuperset.remove(exercise.id)) {
                selectedForSuperset.add(exercise.id)
            }
            applyFilters()
            updateBottomButton()
        } else {
            onExerciseSelected(exercise)
        }
    }

    private fun returnSupersetSelection() {
        if (selectedForSuperset.size < 2) return
        val ids = selectedForSuperset.toList()
        val names = ids.mapNotNull { id -> allExercises.find { it.id == id }?.name }
        val intent = Intent().apply {
            putExtra(EXTRA_RESULT_TYPE, "SUPERSET")
            putExtra(EXTRA_SUPERSET_EXERCISE_IDS, ids.toIntArray())
            putExtra(EXTRA_SUPERSET_EXERCISE_NAMES, names.toTypedArray())
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun onCircuitSelected(template: CircuitTemplate) {
        val intent = Intent().apply {
            putExtra(EXTRA_RESULT_TYPE, "CIRCUIT")
            putExtra(EXTRA_SELECTED_CIRCUIT_ID, template.id)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun loadCircuits() {
        val data = jsonHelper.readTrainingData()
        allCircuits = CircuitStore.circuits(data)
        circuitAdapter.update(allCircuits, data.exerciseLibrary)
        if (pickMode == PickMode.CIRCUIT) refreshCircuitEmptyState()
    }

    private fun refreshCircuitEmptyState() {
        val empty = allCircuits.isEmpty()
        binding.textCircuitListEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.recyclerViewSelectExercise.visibility = if (empty) View.GONE else View.VISIBLE
    }

    /** Inline "which circuit?" list — the active-workout picker used to be a popup for this
     *  ([com.liftpath.components.CircuitPickerBottomSheet]); this mirrors its row rendering
     *  ([R.layout.list_item_circuit_pick], [CircuitStore.formatSummary]) as a plain adapter the
     *  RecyclerView swaps to instead of showing a dialog. */
    private inner class CircuitPickAdapter(
        private var circuits: List<CircuitTemplate>,
        private var library: List<ExerciseLibraryItem>,
        private val onCircuitClicked: (CircuitTemplate) -> Unit
    ) : RecyclerView.Adapter<CircuitPickAdapter.ViewHolder>() {

        fun update(newCircuits: List<CircuitTemplate>, newLibrary: List<ExerciseLibraryItem>) {
            circuits = newCircuits
            library = newLibrary
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.list_item_circuit_pick, parent, false)
        )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val circuit = circuits[position]
            val nameById = library.associate { it.id to it.name }
            holder.name.text = circuit.name
            holder.summary.text = CircuitStore.formatSummary(circuit.suggestedRounds, circuit.restBetweenRoundsSeconds)
            val names = circuit.items.mapNotNull { nameById[it.exerciseId] }
            holder.stations.text = if (names.isEmpty()) {
                getString(R.string.circuit_no_stations)
            } else {
                names.joinToString(" · ")
            }
            holder.card.setOnClickListener { onCircuitClicked(circuit) }
        }

        override fun getItemCount() = circuits.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view.findViewById(R.id.card_circuit_pick)
            val name: TextView = view.findViewById(R.id.text_pick_name)
            val summary: TextView = view.findViewById(R.id.text_pick_summary)
            val stations: TextView = view.findViewById(R.id.text_pick_stations)
        }
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
                onExerciseRowTapped(exercise)
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

        binding.chipGroupByFamily.setOnCheckedChangeListener { _, isChecked ->
            isGroupedByFamily = isChecked
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
        binding.progressMuscleOverview.visibility = View.GONE
    }

    private fun toggleMuscleOverview() {
        isMuscleOverviewExpanded = !isMuscleOverviewExpanded

        if (isMuscleOverviewExpanded) {
            binding.layoutMuscleOverviewContent.visibility = View.VISIBLE
            binding.imageMuscleOverviewExpand.rotation = 180f

            if (muscleActivationState != null) {
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
        val activated = muscleActivationState ?: return

        // Show empty state if no muscles activated
        if (activated.isEmpty()) {
            binding.textMuscleOverviewEmpty.visibility = View.VISIBLE
            binding.imageMuscleOverview.visibility = View.GONE
            return
        }

        binding.textMuscleOverviewEmpty.visibility = View.GONE
        binding.imageMuscleOverview.visibility = View.VISIBLE

        scope.launch {
            val muscleRoles = MuscleMapColorResolver.resolveHighlightColors(
                activated.primaryMuscles, activated.secondaryMuscles
            )
            val maskRoles = MuscleMapColorResolver.flattenToMaskCategories(
                muscleRoles, rank = MuscleMapColorResolver::highlightRank
            )
            val maskColors = maskRoles.map { (maskResId, role) ->
                maskResId to MuscleMapColorResolver.colorFor(this@SelectExerciseActivity, role)
            }
            val bitmap = withContext(Dispatchers.Default) {
                MuscleMapRenderer.render(this@SelectExerciseActivity, maskColors)
            }
            binding.imageMuscleOverview.setImageBitmap(bitmap)
        }
    }

    private fun loadExercises() {
        val trainingData = jsonHelper.readTrainingData()
        allExercises = trainingData.exerciseLibrary
        allFamilies = trainingData.exerciseFamilies ?: emptyList()
        adapter.updateFamilies(allFamilies)
        applyFilters()
    }

    private fun applyFilters() {
        try {
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

        // 3. Filter by Search Text (Name only)
        if (searchQuery.isNotEmpty()) {
            val query = searchQuery.trim().lowercase()
            if (query.isNotEmpty()) {
                result = result.filter { exercise ->
                    try {
                        exercise.name.lowercase().contains(query)
                            || exercise.aliases?.any { it.lowercase().contains(query) } == true
                            || (exercise.familyId?.let { familyNameMap[it]?.lowercase()?.contains(query) } == true)
                    } catch (e: Exception) {
                        android.util.Log.e("SelectExerciseActivity", "Error filtering exercise: ${exercise.name}", e)
                        false
                    }
                }
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

        // 7. Sort: Favorites first, then alphabetically within each group
        val favorites = result.filter { it.isFavorite }.sortedBy { it.name.lowercase() }
        val nonFavorites = result.filter { !it.isFavorite }.sortedBy { it.name.lowercase() }

        displayedExercises = result
        
        // 8. Create list items with favorites first, then collapsible non-favorites section
        val listItems = mutableListOf<ListItem>()
        
        // Add favorites section (always visible)
        if (favorites.isNotEmpty()) {
            listItems.add(ListItem.SectionHeader(
                title = "Favorites",
                sectionId = "favorites",
                isCollapsed = false,
                onHeaderClick = null
            ))
            favorites.forEach { exercise ->
                listItems.add(ListItem.ExerciseItem(exercise, isVisible = true))
            }
        }
        
        // Add non-favorites section (collapsible, starts collapsed if favorites exist)
        if (nonFavorites.isNotEmpty()) {
            // Initialize collapsed state: if favorites exist and section hasn't been toggled yet, default to collapsed
            // We check if the section is NOT in the set at all - if not, and favorites exist, add it to mark as collapsed
            if (favorites.isNotEmpty() && "non_favorites" !in collapsedSections && "non_favorites_expanded" !in collapsedSections) {
                collapsedSections.add("non_favorites")
            }
            
            val isNonFavoritesCollapsed = "non_favorites" in collapsedSections
            
            listItems.add(ListItem.SectionHeader(
                title = "All Exercises",
                sectionId = "non_favorites",
                isCollapsed = isNonFavoritesCollapsed,
                onHeaderClick = { sectionId ->
                    if (sectionId in collapsedSections) {
                        // Currently collapsed, so expand it
                        collapsedSections.remove(sectionId)
                        // Mark as explicitly expanded so we don't re-collapse it
                        collapsedSections.add("non_favorites_expanded")
                    } else {
                        // Currently expanded, so collapse it
                        collapsedSections.remove("non_favorites_expanded")
                        collapsedSections.add(sectionId)
                    }
                    applyFilters() // Rebuild list with new collapsed state
                }
            ))
            nonFavorites.forEach { exercise ->
                listItems.add(ListItem.ExerciseItem(exercise, isVisible = !isNonFavoritesCollapsed))
            }
        } else if (favorites.isEmpty() && result.isNotEmpty()) {
            // If no favorites but have results, show all exercises without collapsible section
            result.forEach { exercise ->
                listItems.add(ListItem.ExerciseItem(exercise, isVisible = true))
            }
        }
        
        // 9. If muscle group filters are applied, apply primary/secondary sections but still maintain favorites
        val finalListItems = if (selectedMuscleGroups.isNotEmpty()) {
            // Group by muscle filter match, but still split by favorites
            val primaryFavorites = favorites.filter { exercise ->
                exercise.primaryTargets.intersect(selectedMuscleGroups).isNotEmpty()
            }.sortedBy { it.name.lowercase() }
            
            val primaryNonFavorites = nonFavorites.filter { exercise ->
                exercise.primaryTargets.intersect(selectedMuscleGroups).isNotEmpty()
            }.sortedBy { it.name.lowercase() }
            
            val secondaryFavorites = favorites.filter { exercise ->
                exercise.primaryTargets.intersect(selectedMuscleGroups).isEmpty() &&
                exercise.secondaryTargets.intersect(selectedMuscleGroups).isNotEmpty()
            }.sortedBy { it.name.lowercase() }
            
            val secondaryNonFavorites = nonFavorites.filter { exercise ->
                exercise.primaryTargets.intersect(selectedMuscleGroups).isEmpty() &&
                exercise.secondaryTargets.intersect(selectedMuscleGroups).isNotEmpty()
            }.sortedBy { it.name.lowercase() }
            
            mutableListOf<ListItem>().apply {
                // Primary favorites first
                if (primaryFavorites.isNotEmpty()) {
                    add(ListItem.SectionHeader(
                        title = "Favorites - Primary",
                        sectionId = "primary_favorites",
                        isCollapsed = false,
                        onHeaderClick = null
                    ))
                    primaryFavorites.forEach { add(ListItem.ExerciseItem(it, isVisible = true)) }
                }
                
                // Primary non-favorites (collapsible)
                if (primaryNonFavorites.isNotEmpty()) {
                    // Initialize collapsed state: if primary favorites exist, default to collapsed
                    val hasBeenToggled = "primary_non_favorites" in collapsedSections || 
                        "primary_non_favorites_expanded" in collapsedSections
                    if (primaryFavorites.isNotEmpty() && !hasBeenToggled) {
                        collapsedSections.add("primary_non_favorites")
                    }
                    val isCollapsed = "primary_non_favorites" in collapsedSections
                    
                    add(ListItem.SectionHeader(
                        title = "Primary",
                        sectionId = "primary_non_favorites",
                        isCollapsed = isCollapsed,
                        onHeaderClick = { sectionId ->
                            if (sectionId in collapsedSections) {
                                collapsedSections.remove(sectionId)
                                if (primaryFavorites.isNotEmpty()) {
                                    collapsedSections.add("primary_non_favorites_expanded")
                                }
                            } else {
                                collapsedSections.remove("primary_non_favorites_expanded")
                                collapsedSections.add(sectionId)
                            }
                            applyFilters()
                        }
                    ))
                    primaryNonFavorites.forEach { add(ListItem.ExerciseItem(it, isVisible = !isCollapsed)) }
                }
                
                // Secondary favorites
                if (secondaryFavorites.isNotEmpty()) {
                    add(ListItem.SectionHeader(
                        title = "Favorites - Secondary",
                        sectionId = "secondary_favorites",
                        isCollapsed = false,
                        onHeaderClick = null
                    ))
                    secondaryFavorites.forEach { add(ListItem.ExerciseItem(it, isVisible = true)) }
                }
                
                // Secondary non-favorites (collapsible)
                if (secondaryNonFavorites.isNotEmpty()) {
                    // Initialize collapsed state: if secondary favorites exist, default to collapsed
                    val hasBeenToggled = "secondary_non_favorites" in collapsedSections || 
                        "secondary_non_favorites_expanded" in collapsedSections
                    if (secondaryFavorites.isNotEmpty() && !hasBeenToggled) {
                        collapsedSections.add("secondary_non_favorites")
                    }
                    val isCollapsed = "secondary_non_favorites" in collapsedSections
                    
                    add(ListItem.SectionHeader(
                        title = "Secondary",
                        sectionId = "secondary_non_favorites",
                        isCollapsed = isCollapsed,
                        onHeaderClick = { sectionId ->
                            if (sectionId in collapsedSections) {
                                collapsedSections.remove(sectionId)
                                if (secondaryFavorites.isNotEmpty()) {
                                    collapsedSections.add("secondary_non_favorites_expanded")
                                }
                            } else {
                                collapsedSections.remove("secondary_non_favorites_expanded")
                                collapsedSections.add(sectionId)
                            }
                            applyFilters()
                        }
                    ))
                    secondaryNonFavorites.forEach { add(ListItem.ExerciseItem(it, isVisible = !isCollapsed)) }
                }
            }
        } else {
            listItems
        }
        
        // When grouped-by-family is active and no search/muscle-filter, use family sections instead
        val outputItems = if (isGroupedByFamily && searchQuery.isEmpty() && selectedMuscleGroups.isEmpty()) {
            buildGroupedFamilyItems(result)
        } else {
            finalListItems
        }

        // Update Adapter
        try {
            val itemsWithSelection = if (pickMode == PickMode.SUPERSET) {
                outputItems.map { item ->
                    if (item is ListItem.ExerciseItem) {
                        item.copy(isSelected = item.exercise.id in selectedForSuperset)
                    } else item
                }
            } else outputItems
            adapter.updateItems(itemsWithSelection)
        } catch (e: Exception) {
            android.util.Log.e("SelectExerciseActivity", "Error updating adapter", e)
            adapter.updateItems(emptyList())
        }
        } catch (e: Exception) {
            android.util.Log.e("SelectExerciseActivity", "Error in applyFilters", e)
            // On error, show all exercises to prevent activity from closing
            try {
                val favorites = allExercises.filter { it.isFavorite }.sortedBy { it.name.lowercase() }
                val nonFavorites = allExercises.filter { !it.isFavorite }.sortedBy { it.name.lowercase() }
                val fallbackItems = mutableListOf<ListItem>()
                
                if (favorites.isNotEmpty()) {
                    fallbackItems.add(ListItem.SectionHeader("Favorites", "favorites", false, null))
                    favorites.forEach { fallbackItems.add(ListItem.ExerciseItem(it, true)) }
                }
                
                if (nonFavorites.isNotEmpty()) {
                    fallbackItems.add(ListItem.SectionHeader("All Exercises", "non_favorites", true, null))
                    nonFavorites.forEach { fallbackItems.add(ListItem.ExerciseItem(it, false)) }
                }
                
                adapter.updateItems(fallbackItems)
            } catch (e2: Exception) {
                android.util.Log.e("SelectExerciseActivity", "Error in fallback update", e2)
            }
        }
    }

    private fun buildGroupedFamilyItems(exercises: List<ExerciseLibraryItem>): List<ListItem> {
        val result = mutableListOf<ListItem>()
        val withFamily = exercises.filter { it.familyId != null }
        val withoutFamily = exercises.filter { it.familyId == null }

        val grouped = withFamily.groupBy { it.familyId!! }
        val sortedFamilyIds = grouped.keys.sortedBy { familyNameMap[it] ?: it }

        for (familyId in sortedFamilyIds) {
            val familyExercises = grouped[familyId] ?: continue
            val familyName = familyNameMap[familyId] ?: familyId
            result.add(ListItem.SectionHeader(title = familyName, sectionId = familyId, isCollapsed = false, onHeaderClick = null))
            familyExercises.sortedBy { it.name.lowercase() }.forEach { result.add(ListItem.ExerciseItem(it, isVisible = true)) }
        }

        if (withoutFamily.isNotEmpty()) {
            result.add(ListItem.SectionHeader(title = "Other", sectionId = "other", isCollapsed = false, onHeaderClick = null))
            withoutFamily.sortedBy { it.name.lowercase() }.forEach { result.add(ListItem.ExerciseItem(it, isVisible = true)) }
        }

        return result
    }

    private fun onExerciseSelected(exercise: ExerciseLibraryItem) {
        // Add exercise directly to workout without any dialogs
        returnExercise(exercise)
    }

    private fun returnExercise(exercise: ExerciseLibraryItem) {
        val intent = Intent().apply {
            putExtra(EXTRA_EXERCISE_ID, exercise.id)
            putExtra(EXTRA_EXERCISE_NAME, exercise.name)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}