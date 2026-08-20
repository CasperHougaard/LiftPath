package com.liftpath.activities

import android.app.Activity
import android.app.DatePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Animatable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.liftpath.R // <--- ENSURE THIS IMPORT EXISTS
import com.liftpath.adapters.ActiveExercisesAdapter
import com.liftpath.databinding.ActivityActiveTrainingBinding
import com.liftpath.helpers.*
import com.liftpath.helpers.showWithTransparentWindow
import com.liftpath.helpers.DurationHelper
import com.liftpath.models.*
import com.liftpath.services.RestTimerService
import com.liftpath.watch.WatchCommand
import com.liftpath.watch.WatchExercise
import com.liftpath.watch.WatchLink
import com.liftpath.watch.WatchState
import com.liftpath.components.MuscleMapDialog
import com.liftpath.components.AddSpecialBottomSheet
import com.liftpath.components.ChangeExerciseBottomSheet
import com.liftpath.components.CircuitPickerBottomSheet
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.text.InputType
import com.liftpath.helpers.lpColor

class ActiveTrainingActivity : AppCompatActivity(), WatchLink.Host {

    private lateinit var binding: ActivityActiveTrainingBinding
    private lateinit var jsonHelper: JsonHelper
    private lateinit var draftManager: ActiveWorkoutDraftManager
    private lateinit var settingsManager: ProgressionSettingsManager

    // Data State
    private val currentExerciseEntries = mutableListOf<ExerciseEntry>()
    private val groupedExercises = mutableListOf<GroupedExercise>()
    private val exerciseWorkoutTypes = mutableMapOf<Int, String>()
    private val exerciseIntents = mutableMapOf<Int, SetIntent>()
    private val lockedIntents = mutableMapOf<Int, SetIntent>() // Track locked intent per exercise (locked when first set is logged)
    private val exerciseRecommendations = mutableMapOf<Int, WorkoutGenerator.RecommendedExercise>()
    private val lastSetsCount = mutableMapOf<Int, Int>()
    private val lastLoggedKg = mutableMapOf<Int, Float>()
    private val lastLoggedReps = mutableMapOf<Int, Int>()
    private val lastLoggedRpe = mutableMapOf<Int, Float>()
    private val lastWorkoutData = mutableMapOf<Int, MutableMap<SetIntent, List<ExerciseEntry>>>()
    private val lastIntents = mutableMapOf<Int, SetIntent>() // Track last intent used for each exercise

    private lateinit var adapter: ActiveExercisesAdapter
    private val selectedDate = Calendar.getInstance()
    private val sessionDateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    private val TAG = "ActiveTrainingActivity"

    private var workoutType: String = "heavy"
    private var appliedPlanId: String? = null
    private var appliedPlanName: String? = null
    private var workoutSource: WorkoutSource? = null
    // Per-exercise plan snapshot data, keyed by exerciseId. Populated when a plan is applied
    // and persisted in the draft so targets survive app restarts.
    private val planExerciseSnapshots = mutableMapOf<Int, DraftExerciseRow>()
    private var hasRestoredDraft = false
    private var addAsSupersetPartner = false
    private val selectedForSupersetPositions = mutableSetOf<Int>()
    private val supersetTargetSetsByGroupId = mutableMapOf<String, Int>()
    private val completedSupersetGroupIds = mutableSetOf<String>()
    private val pendingSupersetCompleteRunnables = mutableMapOf<String, Runnable>()

    // Timer state
    private var isActivityVisible = false
    private var timerReceiver: BroadcastReceiver? = null
    // Tracks end times for warmup/cooldown card timers: exerciseId → System.currentTimeMillis() + durationMs
    private val specialTimerEndTimes = mutableMapOf<Int, Long>()

    // Workout timer state
    private var workoutStartTimeMillis: Long? = null
    private val workoutTimerHandler = Handler(Looper.getMainLooper())
    private var workoutTimerRunnable: Runnable? = null

    // --- LAUNCHERS ---

    private val logSetLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val data = result.data
                val loggedSet = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    data?.getParcelableExtra(LogSetActivity.EXTRA_LOGGED_SET, ExerciseEntry::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    data?.getParcelableExtra(LogSetActivity.EXTRA_LOGGED_SET)
                }

                if (loggedSet != null) {
                    updateExercises(loggedSet)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing result from LogSetActivity", e)
            }
        }
    }

    private val selectExerciseLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val data = result.data
                val exerciseId = data?.getIntExtra(SelectExerciseActivity.EXTRA_EXERCISE_ID, -1) ?: -1
                val exerciseName = data?.getStringExtra(SelectExerciseActivity.EXTRA_EXERCISE_NAME) ?: ""

                if (exerciseId != -1 && exerciseName.isNotEmpty()) {
                    // Don't set default intent when exercise is added
                    // The adapter will show the last intent used with "(Last + emoji)" label
                    val existingGroup = groupedExercises.find { it.exerciseId == exerciseId }
                    if (existingGroup == null) {
                        // Initialize last workout data for this exercise (for all intents)
                        if (!lastWorkoutData.containsKey(exerciseId)) {
                            lastWorkoutData[exerciseId] = mutableMapOf()
                        }
                        // Pre-fetch last workout data for all intents to show in adapter
                        for (intent in listOf(SetIntent.STRENGTH, SetIntent.BUILD, SetIntent.FLUSH)) {
                            val lastSets = fetchLastWorkoutSets(exerciseId, intent)
                            lastWorkoutData[exerciseId]!![intent] = lastSets
                        }
                        
                        // Get and store the last intent used for this exercise
                        val lastIntent = getLastIntentForExercise(exerciseId)
                        if (lastIntent != null) {
                            lastIntents[exerciseId] = lastIntent
                        }
                        
                        val newGroup = GroupedExercise(exerciseId, exerciseName, emptyList())
                        val insertIndex = insertRegularExerciseGroup(newGroup)

                        // First time a bodyweight exercise is added with no known body weight: ask for it.
                        if (isBodyweightExercise(exerciseId) && BodyWeightHelper.needsInitialBodyweight(this)) {
                            BodyWeightDialogs.showInitialBodyweightPrompt(this)
                        }

                        // If added as superset partner, link to previous exercise (or existing superset group)
                        if (addAsSupersetPartner && insertIndex >= 1) {
                            addAsSupersetPartner = false
                            val lastIndex = insertIndex
                            val prevIndex = lastIndex - 1
                            val prevGroup = groupedExercises[prevIndex]
                            val lastGroup = groupedExercises[lastIndex]
                            val supersetGroupId = prevGroup.supersetGroupId ?: UUID.randomUUID().toString()
                            if (prevGroup.supersetGroupId == null) {
                                groupedExercises[prevIndex] = prevGroup.copy(supersetGroupId = supersetGroupId, groupType = GroupType.SUPERSET)
                                adapter.notifyItemChanged(prevIndex)
                            }
                            groupedExercises[lastIndex] = lastGroup.copy(supersetGroupId = supersetGroupId, groupType = GroupType.SUPERSET)
                            adapter.notifyItemChanged(lastIndex)
                            val groupIndices = groupedExercises.mapIndexed { i, g -> if (g.supersetGroupId == supersetGroupId) i else -1 }.filter { it >= 0 }
                            if (supersetTargetSetsByGroupId[supersetGroupId] == null) {
                                showSupersetTargetSetsDialog(supersetGroupId, groupIndices)
                            } else {
                                persistDraft()
                            }
                        } else {
                            persistDraft()
                        }
                    } else {
                        addAsSupersetPartner = false
                    }
                    // Exercise added - user can log sets by clicking on the exercise
                } else {
                    addAsSupersetPartner = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing result from SelectExerciseActivity", e)
            }
        }
    }

    private val editActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val updatedSets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableArrayListExtra(EditActivityActivity.EXTRA_UPDATED_SETS, ExerciseEntry::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableArrayListExtra(EditActivityActivity.EXTRA_UPDATED_SETS)
            }

            if (updatedSets != null) {
                updateSetsFromEditActivity(updatedSets)
            }
        }
    }

    // Authoring a brand new circuit from the active workout: on success, reopen the picker so
    // the circuit just saved is right there to pick.
    private val editCircuitLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            showCircuitPicker()
        }
    }

    private val circuitRunnerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            handleCircuitRunnerResult(result.data)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, start the timer
            startTimerAfterPermissionCheck()
        } else {
            Toast.makeText(this, getString(R.string.toast_notification_permission_required), Toast.LENGTH_LONG).show()
        }
    }

    // --- LIFECYCLE ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable Edge-to-Edge
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityActiveTrainingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply Window Insets
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            
            // Apply top padding to toolbar
            val typedValue = android.util.TypedValue()
            var actionBarHeight = 0
            if (theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
                actionBarHeight = android.util.TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
            }
            binding.toolbar.setPadding(0, insets.top, 0, 0)
            binding.toolbar.layoutParams.height = actionBarHeight + insets.top
            
            // Apply bottom margin to the timer card so it doesn't get hidden by the gesture bar
            val timerLayoutParams = binding.cardTimerContainer.layoutParams as android.view.ViewGroup.MarginLayoutParams
            val baseMargin = (16 * resources.displayMetrics.density).toInt()
            timerLayoutParams.bottomMargin = insets.bottom + baseMargin
            binding.cardTimerContainer.layoutParams = timerLayoutParams
            
            windowInsets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Init Helpers
        jsonHelper = JsonHelper(this)
        draftManager = ActiveWorkoutDraftManager(this)
        settingsManager = ProgressionSettingsManager(this)

        // Get Intent Data
        workoutType = intent.getStringExtra(EXTRA_WORKOUT_TYPE) ?: "heavy"
        val resumeRequested = intent.getBooleanExtra(EXTRA_RESUME_DRAFT, false)
        val shouldAutoGenerate = intent.getBooleanExtra(EXTRA_AUTO_GENERATE, false)
        val planId = intent.getStringExtra(EXTRA_PLAN_ID)
        val planSetId = intent.getStringExtra(EXTRA_PLAN_SET_ID)
        val isCustomWorkout = workoutType == "custom"

        updateTitle()
        setupBackgroundAnimation()
        setupRecyclerView()
        setupClickListeners()
        setupBackButtonInterceptor()
        setupTimerUI()
        setupTimerReceiver()
        setupWorkoutTimer()
        updateDateDisplay()
        updatePlanButtonState()

        if (resumeRequested) {
            maybeRestoreDraft(forceResume = true)
        } else if (draftManager.hasDraft()) {
            maybeRestoreDraft(forceResume = false)
        } else {
            // Apply plan if provided (before auto-generate dialog)
            if (planId != null) {
                applyPlanById(planId, planSetId)
            }
            
            // Show auto-generate dialog if needed (after plan is applied)
            if (!isCustomWorkout && shouldAutoGenerate) {
                showSmartWorkoutSetupDialog()
            } else {
                // Start workout timer if no draft to restore and no dialogs to show
                startWorkoutTimer()
            }
        }

        // Last, so buildWatchState() sees a fully constructed screen. Whatever the draft
        // restore above settles on gets republished by its own persistDraft() call.
        WatchLink.attachHost(this)
    }

    // --- NAVIGATION ---

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_active_training, menu)
        val finishItem = menu?.findItem(R.id.action_complete_workout)
        val finishButton = finishItem?.actionView?.findViewById<android.view.View>(R.id.action_finish_workout_button)
        finishButton?.setOnClickListener { showCompleteWorkoutDialog() }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_complete_workout -> {
                showCompleteWorkoutDialog()
                true
            }
            android.R.id.home -> {
                handleBackButton()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Replaces onCreateOptionsMenu to handle the back arrow correctly without needing menu XML
    override fun onSupportNavigateUp(): Boolean {
        handleBackButton()
        return true
    }

    // --- NEW SMART SETUP DIALOGS ---

    private fun showSmartWorkoutSetupDialog() {
        val focusOptions = arrayOf("Upper Body", "Lower Body", "Full Body")
        var selectedFocus = 0

        DialogHelper.createBuilder(this)
            .setTitle("Select Session Focus")
            .setSingleChoiceItems(focusOptions, 0) { _, which ->
                selectedFocus = which
            }
            .setPositiveButton("Create Workout") { _, _ ->
                generateSmartWorkout(selectedFocus)
            }
            .setNegativeButton(getString(R.string.button_cancel)) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .showWithTransparentWindow()
    }

    private fun generateSmartWorkout(focusIndex: Int) {
        try {
            val focus = when(focusIndex) {
                0 -> SessionFocus.UPPER
                1 -> SessionFocus.LOWER
                else -> SessionFocus.FULL
            }
            // Default to BUILD intent for all exercises (user can adjust per-exercise)
            val intensity = SessionIntensity.LIGHT  // Use LIGHT as default for WorkoutGenerator compatibility

            this.workoutType = "custom"  // Use custom since we're not using heavy/light anymore
            updateTitle()

            val trainingData = jsonHelper.readTrainingData()
            val settings = settingsManager.getSettings()

            val recommendedExercises = WorkoutGenerator.generate(
                library = trainingData.exerciseLibrary,
                userLevel = settings.userLevel,
                focus = focus,
                intensity = intensity
            )

            currentExerciseEntries.clear()
            groupedExercises.clear()
            exerciseWorkoutTypes.clear()
            exerciseIntents.clear()
            exerciseRecommendations.clear()
            lastWorkoutData.clear()
            lastIntents.clear()

            if (recommendedExercises.isNotEmpty()) {
                // Add exercises without sets - user will add sets manually
                recommendedExercises.forEach { recommendation ->
                    val exerciseId = recommendation.exerciseId
                    val exerciseName = recommendation.exerciseName
                    
                    // Store recommendations for tooltip display
                    exerciseRecommendations[exerciseId] = recommendation
                    
                    // Set workout type
                    exerciseWorkoutTypes[exerciseId] = recommendation.workoutType
                    
                    // Don't set default intent when exercise is added
                    // The adapter will show the last intent used with "(Last + emoji)" label
                    
                    // Initialize last workout data for this exercise (for all intents)
                    if (!lastWorkoutData.containsKey(exerciseId)) {
                        lastWorkoutData[exerciseId] = mutableMapOf()
                    }
                    // Pre-fetch last workout data for all intents to show in adapter
                    for (intent in listOf(SetIntent.STRENGTH, SetIntent.BUILD, SetIntent.FLUSH)) {
                        val lastSets = fetchLastWorkoutSets(exerciseId, intent)
                        lastWorkoutData[exerciseId]!![intent] = lastSets
                    }
                    
                    // Get and store the last intent used for this exercise
                    val lastIntent = getLastIntentForExercise(exerciseId)
                    if (lastIntent != null) {
                        lastIntents[exerciseId] = lastIntent
                    }
                    
                    // Add exercise as empty GroupedExercise (no sets yet)
                    insertRegularExerciseGroup(GroupedExercise(exerciseId, exerciseName, emptyList()), notify = false)
                }

                adapter.notifyDataSetChanged()
                persistDraft()
                // Start workout timer after workout is generated
                startWorkoutTimer()
            } else {
                Toast.makeText(this, "No exercises found for this selection.", Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error generating smart workout", e)
            Toast.makeText(this, "Error creating workout", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPlanSelectionDialog() {
        val trainingData = jsonHelper.readTrainingData()
        val plans = trainingData.workoutPlans

        if (plans.isEmpty()) {
            DialogHelper.createBuilder(this)
                .setTitle("No Workout Plans")
                .setMessage("You don't have any workout plans yet. Create one from the Plans screen.")
                .setPositiveButton(getString(R.string.button_ok), null)
                .showWithTransparentWindow()
            return
        }

        val planNames = plans.map { it.name }.toTypedArray()
        var selectedIndex = -1

        DialogHelper.createBuilder(this)
            .setTitle("Select Workout Plan")
            .setSingleChoiceItems(planNames, -1) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton("Apply") { _, _ ->
                if (selectedIndex >= 0 && selectedIndex < plans.size) {
                    applyPlan(plans[selectedIndex])
                }
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    private fun showPlanManagementDialog() {
        val options = arrayOf("Change Plan", "Remove Plan")
        var selectedIndex = -1

        DialogHelper.createBuilder(this)
            .setTitle("Plan: $appliedPlanName")
            .setSingleChoiceItems(options, -1) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton("OK") { _, _ ->
                when (selectedIndex) {
                    0 -> showPlanSelectionDialog()
                    1 -> removePlan()
                }
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    private fun applyPlan(
        plan: WorkoutPlan,
        showToast: Boolean = true,
        sourcePlanSet: com.liftpath.models.PlanSet? = null
    ) {
        appliedPlanId = plan.id
        appliedPlanName = plan.name
        workoutSource = if (sourcePlanSet != null) {
            WorkoutSource(
                type = WorkoutSourceType.PLAN_SET,
                planId = plan.id,
                planName = plan.name,
                planSetId = sourcePlanSet.id,
                planSetName = sourcePlanSet.name
            )
        } else {
            WorkoutSource(type = WorkoutSourceType.PLAN, planId = plan.id, planName = plan.name)
        }

        // If current workout is custom, keep it as custom (don't change to plan's workout type)
        // Otherwise, use the plan's workout type
        if (workoutType != "custom") {
            workoutType = plan.workoutType
        }
        updateTitle()
        updatePlanButtonState()

        val trainingData = jsonHelper.readTrainingData()
        lastSetsCount.clear()

        // Iterate over exerciseConfigs (V2) — each config has a stable ID so the same exercise
        // can appear multiple times in a plan. Fall back to exerciseIds for legacy plans.
        val configs = plan.exerciseConfigs?.takeIf { it.isNotEmpty() }
            ?: plan.exerciseIds.map { id ->
                PlanExerciseSlot(exerciseId = id, selectionType = PlanExerciseSelectionType.SPECIFIC_VARIANT)
            }

        configs.forEach { config ->
            // Handle warmup/cooldown special slots from the plan
            if (config.isSpecialElement) {
                when (config.slotType) {
                    PlanSlotType.WARMUP -> addWarmupElement(durationSeconds = config.durationSeconds ?: 300)
                    PlanSlotType.COOLDOWN -> addCooldownElement(durationSeconds = config.durationSeconds ?: 300)
                    else -> Unit
                }
                return@forEach
            }
            // Plan-authored circuit: the slot only carries the round/rest overrides, the
            // stations live on the referenced CircuitTemplate.
            if (config.isCircuit) {
                val template = CircuitStore.find(trainingData, config.circuitId)
                if (template != null && groupedExercises.none { it.circuit?.templateId == template.id }) {
                    val nextId = CIRCUIT_ROW_ID_BASE - groupedExercises.count { it.isCircuit }
                    insertRegularExerciseGroup(
                        GroupedExercise(
                            exerciseId = nextId,
                            exerciseName = template.name,
                            sets = emptyList(),
                            slotType = PlanSlotType.CIRCUIT,
                            circuit = CircuitStore.templateToInstance(template, config)
                        ),
                        notify = false
                    )
                }
                return@forEach
            }
            // V3: resolve concrete exercise from slot (SPECIFIC_VARIANT or FAMILY_SLOT)
            val resolvedSelectionType = config.effectiveSelectionType
            val exercise: ExerciseLibraryItem?
            val resolvedFamilyId: String?
            when (resolvedSelectionType) {
                PlanExerciseSelectionType.SPECIFIC_VARIANT -> {
                    val id = config.exerciseId ?: return@forEach
                    exercise = trainingData.exerciseLibrary.find { it.id == id }
                    resolvedFamilyId = exercise?.familyId
                }
                PlanExerciseSelectionType.FAMILY_SLOT -> {
                    exercise = FamilySlotResolver.resolve(
                        config.familyId, config.movementPattern, trainingData.exerciseLibrary
                    )
                    resolvedFamilyId = config.familyId
                }
            }
            val exerciseId = exercise?.id ?: return@forEach
            if (exercise != null) {
                // Check if exercise already exists in workout (by exerciseId — duplicates deferred)
                val existingGroup = groupedExercises.find { it.exerciseId == exerciseId }
                if (existingGroup == null) {
                    // Find the last working set for this exercise (exclude warmup)
                    val lastWorkingEntry = trainingData.trainings
                        .flatMap { it.exercises }
                        .filter { it.exerciseId == exerciseId && !it.isEffectivelyWarmup() }
                        .lastOrNull()

                    if (lastWorkingEntry != null) {
                        lastLoggedKg[exerciseId] = lastWorkingEntry.kg
                        lastLoggedReps[exerciseId] = lastWorkingEntry.reps
                        lastWorkingEntry.rpe?.let { lastLoggedRpe[exerciseId] = it }

                        val lastSession = trainingData.trainings
                            .sortedByDescending { it.trainingNumber }
                            .firstOrNull { session ->
                                session.exercises.any { it.exerciseId == exerciseId }
                            }

                        val setsCount = lastSession?.exercises
                            ?.filter { it.exerciseId == exerciseId }
                            ?.size ?: 0

                        if (setsCount > 0) {
                            lastSetsCount[exerciseId] = setsCount
                        }
                    }

                    insertRegularExerciseGroup(
                        GroupedExercise(
                            exerciseId = exerciseId,
                            exerciseName = exercise.name,
                            sets = emptyList(),
                            slotSelectionType = resolvedSelectionType,
                            slotFamilyId = resolvedFamilyId
                        ),
                        notify = false
                    )
                    exerciseWorkoutTypes[exerciseId] = if (workoutType == "custom") "custom" else plan.workoutType

                    val intent = config.defaultIntent ?: SetIntent.BUILD
                    exerciseIntents[exerciseId] = intent

                    // Store plan snapshot so rest overrides and targets survive draft restore
                    planExerciseSnapshots[exerciseId] = DraftExerciseRow(
                        exerciseId = exerciseId,
                        exerciseName = exercise.name,
                        fromPlan = true,
                        sourcePlanConfigId = config.id,
                        plannedIntent = config.defaultIntent,
                        plannedRestTimeSeconds = config.restTimeSeconds,
                        plannedRpeTarget = config.rpeTarget,
                        plannedSetsTarget = config.setsTarget,
                        plannedRepsTarget = config.repsTarget,
                        plannedDurationSeconds = config.durationSeconds,
                        plannedNotes = config.notes,
                        slotSelectionType = resolvedSelectionType,
                        slotFamilyId = resolvedFamilyId
                    )

                    if (!lastWorkoutData.containsKey(exerciseId)) {
                        lastWorkoutData[exerciseId] = mutableMapOf()
                    }
                    val lastSets = fetchLastWorkoutSets(exerciseId, intent)
                    lastWorkoutData[exerciseId]!![intent] = lastSets

                    val lastIntent = getLastIntentForExercise(exerciseId)
                    if (lastIntent != null) {
                        lastIntents[exerciseId] = lastIntent
                    }
                }
            }
        }

        normalizeSpecialSlotOrder()
        adapter.notifyDataSetChanged()
        persistDraft()
        if (showToast) {
            Toast.makeText(this, "Plan \"$appliedPlanName\" applied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removePlan() {
        appliedPlanId = null
        appliedPlanName = null
        workoutSource = null
        planExerciseSnapshots.clear()
        lastSetsCount.clear()
        lastLoggedKg.clear()
        lastLoggedReps.clear()
        lastLoggedRpe.clear()
        updatePlanButtonState()
        updateTitle()
        adapter.notifyDataSetChanged()
        persistDraft()
        Toast.makeText(this, "Plan removed", Toast.LENGTH_SHORT).show()
    }

    /**
     * Loads and applies a plan by its ID. Used when plan is passed via intent.
     * Suppresses toast notification since it's auto-applied on startup.
     */
    private fun applyPlanById(planId: String, planSetId: String? = null) {
        val trainingData = jsonHelper.readTrainingData()
        val plan = trainingData.workoutPlans.find { it.id == planId }
        val planSet = planSetId?.let { id -> trainingData.planSets.find { it.id == id } }

        if (plan != null) {
            applyPlan(plan, showToast = false, sourcePlanSet = planSet)
        } else {
            android.util.Log.e(TAG, "Plan with ID $planId not found")
        }
    }

    private fun updatePlanButtonState() {
        if (appliedPlanId != null) {
            // Plan is applied - change tint to indicate active state
            binding.buttonPlan.setColorFilter(
                this.lpColor(R.attr.lpAccent),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        } else {
            // No plan applied - use default tint
            binding.buttonPlan.setColorFilter(
                this.lpColor(R.attr.lpAccent),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        }
    }

    /**
     * Fetches the last workout sets for an exercise filtered by intent.
     * Returns empty list if no matching session found.
     * For legacy sessions, uses smart exercise-level intent evaluation.
     */
    private fun fetchLastWorkoutSets(exerciseId: Int, intent: SetIntent): List<ExerciseEntry> {
        val trainingData = jsonHelper.readTrainingData()
        
        // Find last session containing this exercise with matching intent
        val lastSession = trainingData.trainings
            .sortedByDescending { it.trainingNumber }
            .firstOrNull { session ->
                session.exercises.any { entry ->
                    entry.exerciseId == exerciseId && 
                    getExerciseIntentInSession(entry, session, exerciseId) == intent
                }
            }
        
        return lastSession?.exercises
            ?.filter { it.exerciseId == exerciseId && 
                       getExerciseIntentInSession(it, lastSession, exerciseId) == intent }
            ?.sortedBy { it.setNumber }
            ?: emptyList()
    }
    
    /**
     * Gets the last intent used for an exercise from training history.
     * Returns null if the exercise has never been done before.
     */
    private fun getLastIntentForExercise(exerciseId: Int): SetIntent? {
        val trainingData = jsonHelper.readTrainingData()
        
        // Find last session containing this exercise
        val lastSession = trainingData.trainings
            .sortedByDescending { it.trainingNumber }
            .firstOrNull { session ->
                session.exercises.any { entry ->
                    entry.exerciseId == exerciseId && 
                    !entry.isEffectivelyWarmup()
                }
            }
        
        return lastSession?.let { session ->
            // Get the intent of the exercise in this session
            val exerciseEntries = session.exercises
                .filter { it.exerciseId == exerciseId && !it.isEffectivelyWarmup() }
            
            if (exerciseEntries.isNotEmpty()) {
                // Use the first non-warmup entry to determine intent
                getExerciseIntentInSession(exerciseEntries.first(), session, exerciseId)
            } else {
                null
            }
        }
    }
    
    /**
     * Gets the effective intent of an exercise entry, using smart evaluation for legacy sessions.
     */
    private fun getExerciseIntentInSession(
        entry: ExerciseEntry, 
        session: TrainingSession, 
        exerciseId: Int
    ): SetIntent {
        // Legacy data: RPE 6 = warmup (new data uses isWarmup flag)
        if (entry.isLegacyWarmup()) return SetIntent.WARMUP
        
        // isWarmup flag
        if (entry.isWarmup) return SetIntent.WARMUP
        
        // Explicit intent (modern data)
        if (entry.explicitIntent != null) return entry.explicitIntent
        
        // Legacy session: use smart exercise-level evaluation
        if (session.isLegacySession()) {
            return session.getLegacyExerciseIntent(exerciseId)
        }
        
        // Fallback to per-set evaluation
        return entry.getEffectiveIntent(session.defaultWorkoutType)
    }

    private fun updateTitle() {
        val title = if (appliedPlanName != null) {
            "Active Workout - $appliedPlanName"
        } else {
            "Active Workout"
        }
        supportActionBar?.title = title
    }

    // --- EXISTING FUNCTIONALITY ---

    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is Animatable) {
            drawable.start()
        }
    }

    private fun setupRecyclerView() {
        adapter = ActiveExercisesAdapter(
            groupedExercises,
            exerciseRecommendations,
            jsonHelper,
            workoutType,
            lastSetsCount,
            lastLoggedKg,
            lastLoggedReps,
            exerciseIntents,
            lockedIntents,
            lastWorkoutData,
            lastIntents,
            planSnapshots = planExerciseSnapshots,
            onAddSetClicked = { exerciseId, exerciseName ->
                launchLogSetActivity(exerciseId, exerciseName)
            },
            onEditActivityClicked = { groupedExercise ->
                launchEditActivityForActiveWorkout(groupedExercise)
            },
            onDuplicateSetClicked = { exerciseId ->
                duplicateLastSet(exerciseId)
            },
            onDeleteExerciseClicked = { exerciseId ->
                deleteExercise(exerciseId)
            },
            onIntentChanged = { exerciseId, intent ->
                exerciseIntents[exerciseId] = intent
                
                // Refresh last workout data for new intent
                val lastSets = fetchLastWorkoutSets(exerciseId, intent)
                if (!lastWorkoutData.containsKey(exerciseId)) {
                    lastWorkoutData[exerciseId] = mutableMapOf()
                }
                lastWorkoutData[exerciseId]!![intent] = lastSets
                
                // Notify adapter to refresh this exercise
                val position = groupedExercises.indexOfFirst { it.exerciseId == exerciseId }
                if (position >= 0) {
                    adapter.notifyItemChanged(position)
                }
                
                persistDraft()
            },
            onAddExerciseClicked = {
                handleAddExercise()
            },
            onAddSpecialClicked = {
                showAddSpecialBottomSheet()
            },
            isRestTimerRunning = { RestTimerService.isTimerRunning(this) },
            onUnlinkSuperset = { supersetGroupId -> unlinkSupersetGroup(supersetGroupId) },
            selectedForSupersetPositions = { selectedForSupersetPositions },
            onExerciseLongPress = { position -> handleExerciseLongPress(position) },
            getSupersetTargetSets = { groupId -> supersetTargetSetsByGroupId[groupId] },
            getCompletedSupersetGroupIds = { completedSupersetGroupIds.toSet() },
            onSpecialCompleted = { exerciseId, isCompleted ->
                handleSpecialCompleted(exerciseId, isCompleted)
            },
            onDeleteSpecialClicked = { exerciseId ->
                deleteSpecialElement(exerciseId)
            },
            onStartTimerClicked = { exerciseId ->
                startSpecialElementTimer(exerciseId)
            },
            onEditDurationClicked = { exerciseId ->
                showEditDurationDialog(exerciseId)
            },
            onChangeExerciseClicked = { position ->
                showChangeExerciseBottomSheet(position)
            },
            getTimerEndTimeMillis = { exerciseId -> specialTimerEndTimes[exerciseId] },
            onSpecialTimerReset = { exerciseId ->
                resetSpecialElementTimer(exerciseId)
            },
            onStartCircuitClicked = { position -> launchCircuitRunner(position) },
            onDeleteCircuitClicked = { exerciseId -> deleteCircuit(exerciseId) },
            onCircuitSettingsClicked = { position -> showCircuitSettingsDialog(position) }
        )
        binding.recyclerViewActiveWorkout.adapter = adapter
        binding.recyclerViewActiveWorkout.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewActiveWorkout.setItemViewCacheSize(12)
        binding.recyclerViewActiveWorkout.itemAnimator = null
    }

    private fun setupClickListeners() {
        // Removed button_add_exercise and button_finish_workout click listeners
        // They are now handled via adapter callbacks and toolbar menu

        binding.layoutDate.setOnClickListener {
            showDatePickerDialog()
        }

        // TIMER BUTTONS
        binding.buttonTimerStartPause.setOnClickListener { handleTimerStartPause() }
        binding.buttonTimerResetStop.setOnClickListener { handleTimerResetStop() }
        binding.textTimerDisplay.setOnClickListener { showSetTimerDialog() }

        binding.buttonTimerMinus15.setOnClickListener {
            RestTimerService.removeTime(this, 15)
            val currentTime = RestTimerService.getRemainingSeconds(this)
            updateTimerDisplay(currentTime)
        }

        binding.buttonTimerPlus15.setOnClickListener {
            val currentTime = RestTimerService.getRemainingSeconds(this)
            val isRunning = RestTimerService.isTimerRunning(this)

            if (currentTime == 0 && !isRunning) {
                RestTimerService.setTimerTime(this, 15)
                updateTimerDisplay(15)
                startTimer(useCustomTime = 15)
            } else {
                RestTimerService.addTime(this, 15)
                val newTime = RestTimerService.getRemainingSeconds(this)
                updateTimerDisplay(newTime)
            }
        }

        // MUSCLE OVERVIEW BUTTON
        binding.buttonMuscleOverview.setOnClickListener {
            showMuscleOverview()
        }

        // PLAN BUTTON
        binding.buttonPlan.setOnClickListener {
            if (appliedPlanId != null) {
                showPlanManagementDialog()
            } else {
                showPlanSelectionDialog()
            }
        }
    }

    private fun setupBackButtonInterceptor() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackButton()
            }
        })
    }

    private fun handleBackButton() {
        if (currentExerciseEntries.isNotEmpty()) {
            DialogHelper.createBuilder(this)
                .setTitle(getString(R.string.dialog_title_cancel_workout))
                .setMessage(getString(R.string.dialog_message_cancel_workout))
                .setPositiveButton(getString(R.string.button_cancel_workout)) { _, _ ->
                    stopTimerIfRunning()
                    finish()
                }
                .setNegativeButton(getString(R.string.button_continue), null)
                .showWithTransparentWindow()
        } else {
            stopTimerIfRunning()
            finish()
        }
    }

    private fun showMuscleOverview() {
        // Extract unique exercise IDs from grouped exercises (exclude sentinel IDs for warmup/cooldown/circuit)
        val exerciseIds = groupedExercises.filter { !it.isSpecialElement && !it.isCircuit }.map { it.exerciseId }.distinct()
        
        // Load ExerciseLibraryItem objects for these IDs
        val trainingData = jsonHelper.readTrainingData()
        val exercises = exerciseIds.mapNotNull { id ->
            trainingData.exerciseLibrary.find { it.id == id }
        }
        
        // Calculate activated muscles
        val activationState = MuscleActivationHelper.getActivatedMuscles(exercises)
        
        // Show muscle map dialog
        val dialog = MuscleMapDialog.newInstance(
            primaryMuscles = activationState.primaryMuscles,
            secondaryMuscles = activationState.secondaryMuscles
        )
        dialog.show(supportFragmentManager, "MuscleMapDialog")
    }

    private fun showDatePickerDialog() {
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            selectedDate.set(Calendar.YEAR, year)
            selectedDate.set(Calendar.MONTH, month)
            selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            updateDateDisplay()
        }

        DatePickerDialog(
            this,
            dateSetListener,
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /**
     * Matches [LogSetActivity.EXTRA_REST_SECONDS_OVERRIDE] when opening the log screen for the next set.
     */
    private fun restSecondsOverrideForNextSet(exerciseId: Int): Int? {
        val index = groupedExercises.indexOfFirst { it.exerciseId == exerciseId }
        if (index < 0) return null
        val settings = settingsManager.getSettings()
        if (!settings.restTimerEnabled) return null
        val exerciseIntent = exerciseIntents[exerciseId] ?: SetIntent.BUILD
        val group = groupedExercises[index]
        val prevGroup = if (index > 0) groupedExercises[index - 1] else null
        val nextGroup = if (index < groupedExercises.size - 1) groupedExercises[index + 1] else null
        return when {
            // Superset transition timing takes priority (mechanical)
            group.supersetGroupId != null && nextGroup?.supersetGroupId == group.supersetGroupId -> {
                settings.supersetTransitionSeconds
            }
            group.supersetGroupId != null && prevGroup?.supersetGroupId == group.supersetGroupId -> {
                val standardRest = when (exerciseIntent) {
                    SetIntent.STRENGTH -> settings.strengthRestSeconds
                    SetIntent.BUILD -> settings.buildRestSeconds
                    SetIntent.FLUSH -> settings.flushRestSeconds
                    else -> settings.buildRestSeconds
                }
                standardRest + settings.supersetRestBonusSeconds
            }
            // Plan-defined rest override for non-superset exercises
            else -> planExerciseSnapshots[exerciseId]?.plannedRestTimeSeconds?.takeIf { it > 0 }
        }
    }

    private fun isBodyweightExercise(exerciseId: Int): Boolean =
        ExerciseModeResolver.isBodyweight(jsonHelper.readTrainingData().exerciseLibrary, exerciseId)

    private fun isTimeBasedExercise(exerciseId: Int): Boolean =
        ExerciseModeResolver.isTimeBased(jsonHelper.readTrainingData().exerciseLibrary, exerciseId)

    private fun launchLogSetActivity(exerciseId: Int, exerciseName: String) {
        try {
            val lastWorkingSet = currentExerciseEntries
                .filter { it.exerciseId == exerciseId && !it.isWarmup }
                .maxByOrNull { it.setNumber }
            val setNumber = (lastWorkingSet?.setNumber ?: currentExerciseEntries
                .filter { it.exerciseId == exerciseId }
                .maxByOrNull { it.setNumber }?.setNumber ?: 0) + 1
            val setWorkoutType = exerciseWorkoutTypes[exerciseId] ?: workoutType
            val exerciseIntent = exerciseIntents[exerciseId] ?: SetIntent.BUILD
            val restOverride = restSecondsOverrideForNextSet(exerciseId)

            // Find the same-position set from the previous workout (intent-aware).
            // E.g. when logging Set 2, pre-fill from Set 2 of the last session, not Set 1.
            val workingSetsLogged = currentExerciseEntries
                .filter { it.exerciseId == exerciseId && !it.isWarmup }
                .size
            val prevWorkingSets = lastWorkoutData[exerciseId]?.get(exerciseIntent)
                ?.filter { !it.isEffectivelyWarmup() }
                ?: emptyList()
            val samePositionPrevSet = prevWorkingSets.getOrNull(workingSetsLogged)

            val kgToPass = samePositionPrevSet?.kg ?: lastLoggedKg[exerciseId]
            val repsToPass = samePositionPrevSet?.reps ?: lastLoggedReps[exerciseId]
            val rpeToPass = samePositionPrevSet?.rpe ?: lastLoggedRpe[exerciseId]

            val intent = Intent(this, LogSetActivity::class.java).apply {
                putExtra(LogSetActivity.EXTRA_EXERCISE_ID, exerciseId)
                putExtra(LogSetActivity.EXTRA_EXERCISE_NAME, exerciseName)
                putExtra(LogSetActivity.EXTRA_SET_NUMBER, setNumber)
                putExtra(LogSetActivity.EXTRA_WORKOUT_TYPE, setWorkoutType)
                putExtra(LogSetActivity.EXTRA_INTENT, exerciseIntent.name)
                putExtra(LogSetActivity.EXTRA_IS_BODYWEIGHT, isBodyweightExercise(exerciseId))
                putExtra(LogSetActivity.EXTRA_IS_TIME_BASED, isTimeBasedExercise(exerciseId))
                planExerciseSnapshots[exerciseId]?.plannedDurationSeconds?.takeIf { it > 0 }?.let {
                    putExtra(LogSetActivity.EXTRA_DURATION_TARGET, it)
                }
                restOverride?.let { putExtra(LogSetActivity.EXTRA_REST_SECONDS_OVERRIDE, it) }
                lastWorkingSet?.let {
                    putExtra(LogSetActivity.EXTRA_PREVIOUS_SET_REPS, it.reps)
                }
                kgToPass?.let { putExtra(LogSetActivity.EXTRA_LAST_LOGGED_KG, it) }
                repsToPass?.let { putExtra(LogSetActivity.EXTRA_LAST_LOGGED_REPS, it) }
                rpeToPass?.let { putExtra(LogSetActivity.EXTRA_LAST_LOGGED_RPE, it) }
            }
            logSetLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch LogSetActivity", e)
        }
    }

    private fun updateExercises(loggedSet: ExerciseEntry) {
        val groupIndex = groupedExercises.indexOfFirst { it.exerciseId == loggedSet.exerciseId }
        val group = if (groupIndex != -1) groupedExercises[groupIndex] else null
        // If explicitIntent is not set, use the exercise's intent
        var setWithIntent = if (loggedSet.explicitIntent == null) {
            val exerciseIntent = exerciseIntents[loggedSet.exerciseId] ?: SetIntent.BUILD
            loggedSet.copy(explicitIntent = exerciseIntent)
        } else {
            loggedSet
        }
        setWithIntent = setWithIntent.copy(
            groupId = group?.supersetGroupId,
            groupType = group?.groupType ?: group?.supersetGroupId?.let { GroupType.SUPERSET }
        )
        currentExerciseEntries.add(setWithIntent)
        loggedSet.workoutType?.let { exerciseWorkoutTypes[loggedSet.exerciseId] = it }

        // Update last logged values for working sets (exclude warmup)
        if (!loggedSet.isWarmup) {
            lastLoggedKg[loggedSet.exerciseId] = loggedSet.kg
            lastLoggedReps[loggedSet.exerciseId] = loggedSet.reps
            loggedSet.rpe?.let { lastLoggedRpe[loggedSet.exerciseId] = it }
        }
        
        // Lock the intent when the first set is logged for this exercise
        if (!lockedIntents.containsKey(loggedSet.exerciseId)) {
            lockedIntents[loggedSet.exerciseId] = setWithIntent.explicitIntent ?: SetIntent.BUILD
        }

        if (groupIndex != -1) {
            val oldGroup = groupedExercises[groupIndex]
            val newSets = oldGroup.sets + setWithIntent
            val newGroup = oldGroup.copy(sets = newSets.sortedBy { it.setNumber })
            groupedExercises[groupIndex] = newGroup
            adapter.notifyItemChanged(groupIndex)
            oldGroup.supersetGroupId?.let { gid ->
                groupedExercises.forEachIndexed { i, g ->
                    if (g.supersetGroupId == gid && i != groupIndex) adapter.notifyItemChanged(i)
                }
                checkSupersetCompletionAndHighlight(gid)
            }
        }
        persistDraft()
    }

    private fun checkSupersetCompletionAndHighlight(supersetGroupId: String) {
        val target = supersetTargetSetsByGroupId[supersetGroupId] ?: return
        val indices = groupedExercises.mapIndexed { i, g -> if (g.supersetGroupId == supersetGroupId) i else -1 }.filter { it >= 0 }
        if (indices.isEmpty()) return
        val allReached = indices.all { i ->
            val g = groupedExercises[i]
            g.sets.count { !it.isWarmup } >= target
        }
        if (!allReached || supersetGroupId in completedSupersetGroupIds) return
        completedSupersetGroupIds.add(supersetGroupId)
        indices.forEach { adapter.notifyItemChanged(it) }
        val runnable = Runnable {
            completedSupersetGroupIds.remove(supersetGroupId)
            pendingSupersetCompleteRunnables.remove(supersetGroupId)
            indices.forEach { adapter.notifyItemChanged(it) }
        }
        pendingSupersetCompleteRunnables[supersetGroupId] = runnable
        workoutTimerHandler.postDelayed(runnable, 3000)
    }

    private fun duplicateLastSet(exerciseId: Int) {
        val lastSet = currentExerciseEntries.filter { it.exerciseId == exerciseId }.lastOrNull()
        if (lastSet != null) {
            val newSetNumber = lastSet.setNumber + 1
            val newSet = lastSet.copy(setNumber = newSetNumber, rating = null, note = null)
            updateExercises(newSet)
            startRestTimerAfterLoggedSet(exerciseId, newSet.rpe)
        }
    }

    private fun deleteExercise(exerciseId: Int) {
        val groupIndex = groupedExercises.indexOfFirst { it.exerciseId == exerciseId }
        if (groupIndex != -1) {
            val removedGroup = groupedExercises[groupIndex]
            val supersetGroupId = removedGroup.supersetGroupId
            if (supersetGroupId != null) {
                pendingSupersetCompleteRunnables.remove(supersetGroupId)?.let { runnable ->
                    workoutTimerHandler.removeCallbacks(runnable)
                }
                supersetTargetSetsByGroupId.remove(supersetGroupId)
                completedSupersetGroupIds.remove(supersetGroupId)
                val remainingInGroup = groupedExercises.count { it.supersetGroupId == supersetGroupId && it.exerciseId != exerciseId }
                // If only one exercise left in the superset, unlink it
                if (remainingInGroup == 1) {
                    val partnerIndex = groupedExercises.indexOfFirst { it.supersetGroupId == supersetGroupId && it.exerciseId != exerciseId }
                    if (partnerIndex != -1) {
                        val partner = groupedExercises[partnerIndex]
                        groupedExercises[partnerIndex] = partner.copy(supersetGroupId = null, groupType = null)
                        adapter.notifyItemChanged(partnerIndex)
                    }
                }
            }
            groupedExercises.removeAt(groupIndex)
            currentExerciseEntries.removeAll { it.exerciseId == exerciseId }
            exerciseWorkoutTypes.remove(exerciseId)
            exerciseIntents.remove(exerciseId)
            lockedIntents.remove(exerciseId)
            exerciseRecommendations.remove(exerciseId)
            lastSetsCount.remove(exerciseId)
            lastLoggedKg.remove(exerciseId)
            lastLoggedReps.remove(exerciseId)
            lastLoggedRpe.remove(exerciseId)
            adapter.notifyItemRemoved(groupIndex)
            persistDraft()
        }
    }

    private fun launchEditActivityForActiveWorkout(groupedExercise: GroupedExercise) {
        val intent = Intent(this, EditActivityActivity::class.java).apply {
            putExtra(EditActivityActivity.EXTRA_IS_ACTIVE_WORKOUT, true)
            putExtra(EditActivityActivity.EXTRA_EXERCISE_ID, groupedExercise.exerciseId)
            putExtra(EditActivityActivity.EXTRA_EXERCISE_NAME, groupedExercise.exerciseName)
            putParcelableArrayListExtra(EditActivityActivity.EXTRA_SETS, ArrayList(groupedExercise.sets))
        }
        editActivityLauncher.launch(intent)
    }

    private fun updateSetsFromEditActivity(updatedSets: ArrayList<ExerciseEntry>) {
        if (updatedSets.isEmpty()) return
        val exerciseId = updatedSets.first().exerciseId
        currentExerciseEntries.removeAll { it.exerciseId == exerciseId }
        currentExerciseEntries.addAll(updatedSets)

        val groupIndex = groupedExercises.indexOfFirst { it.exerciseId == exerciseId }
        if (groupIndex != -1) {
            val existingGroup = groupedExercises[groupIndex]
            val sortedSets = updatedSets.sortedBy { it.setNumber }
            val updatedGroup = GroupedExercise(
                exerciseId,
                updatedSets.first().exerciseName,
                sortedSets,
                supersetGroupId = existingGroup.supersetGroupId,
                groupType = existingGroup.groupType
            )
            groupedExercises[groupIndex] = updatedGroup
            adapter.notifyItemChanged(groupIndex)
        }
        persistDraft()
    }

    private fun updateDateDisplay() {
        binding.textDate.text = sessionDateFormat.format(selectedDate.time)
        persistDraftIfHasEntries()
    }

    private fun handleAddExercise() {
        val alreadyAddedExerciseIds = groupedExercises.map { it.exerciseId }.toIntArray()
        val intent = Intent(this, SelectExerciseActivity::class.java).apply {
            putExtra(SelectExerciseActivity.EXTRA_WORKOUT_TYPE, workoutType)
            putExtra(SelectExerciseActivity.EXTRA_PLAN_ID, appliedPlanId)
            putExtra(SelectExerciseActivity.EXTRA_ALREADY_ADDED_EXERCISE_IDS, alreadyAddedExerciseIds)
        }
        selectExerciseLauncher.launch(intent)
    }

    private fun showAddSpecialBottomSheet() {
        val bottomSheet = AddSpecialBottomSheet.newInstance(
            onWarmupSelected = {
                addWarmupElement()
            },
            onCooldownSelected = {
                addCooldownElement()
            },
            onSuperSetSelected = {
                handleAddExerciseAsSupersetPartner()
            },
            onCircuitSelected = {
                showCircuitPicker()
            }
        )
        bottomSheet.show(supportFragmentManager, "AddSpecialBottomSheet")
    }

    private fun showChangeExerciseBottomSheet(position: Int) {
        if (position < 0 || position >= groupedExercises.size) return
        val group = groupedExercises[position]
        if (!group.isFamilySlot) return

        val data = jsonHelper.readTrainingData()
        val familyMovementPattern = group.slotFamilyId?.let { fid ->
            data.exerciseFamilies?.find { it.id == fid }?.movementPattern
        }

        ChangeExerciseBottomSheet.newInstance(
            familyId = group.slotFamilyId,
            movementPattern = familyMovementPattern,
            library = data.exerciseLibrary,
            onSelected = { newExercise ->
                replaceExerciseInSlot(position, newExercise)
            }
        ).show(supportFragmentManager, "ChangeExercise")
    }

    private fun replaceExerciseInSlot(position: Int, newExercise: ExerciseLibraryItem) {
        if (position < 0 || position >= groupedExercises.size) return
        val oldGroup = groupedExercises[position]
        val oldId = oldGroup.exerciseId
        val newId = newExercise.id
        if (oldId == newId) return

        // Swap the GroupedExercise in place; keep slot semantics and any already-logged sets
        groupedExercises[position] = oldGroup.copy(
            exerciseId = newId,
            exerciseName = newExercise.name
        )

        // Migrate per-exercise state from old ID to new ID
        exerciseWorkoutTypes[newId] = exerciseWorkoutTypes[oldId] ?: workoutType
        exerciseIntents[newId] = exerciseIntents[oldId] ?: SetIntent.BUILD
        lockedIntents.remove(oldId)
        exerciseWorkoutTypes.remove(oldId)
        exerciseIntents.remove(oldId)

        // Migrate plan snapshot (preserves targets and slot metadata)
        planExerciseSnapshots[oldId]?.let { snap ->
            planExerciseSnapshots[newId] = snap.copy(
                exerciseId = newId,
                exerciseName = newExercise.name
            )
        }
        planExerciseSnapshots.remove(oldId)

        // Pre-load last workout data for the new exercise
        val intent = exerciseIntents[newId] ?: SetIntent.BUILD
        if (!lastWorkoutData.containsKey(newId)) lastWorkoutData[newId] = mutableMapOf()
        lastWorkoutData[newId]!![intent] = fetchLastWorkoutSets(newId, intent)

        adapter.notifyItemChanged(position)
        persistDraft()
    }

    private fun handleAddExerciseAsSupersetPartner() {
        if (groupedExercises.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_add_exercise_first), Toast.LENGTH_SHORT).show()
            return
        }
        addAsSupersetPartner = true
        val alreadyAddedExerciseIds = groupedExercises.map { it.exerciseId }.toIntArray()
        val intent = Intent(this, SelectExerciseActivity::class.java).apply {
            putExtra(SelectExerciseActivity.EXTRA_WORKOUT_TYPE, workoutType)
            putExtra(SelectExerciseActivity.EXTRA_PLAN_ID, appliedPlanId)
            putExtra(SelectExerciseActivity.EXTRA_ALREADY_ADDED_EXERCISE_IDS, alreadyAddedExerciseIds)
        }
        selectExerciseLauncher.launch(intent)
    }

    private fun unlinkSupersetGroup(supersetGroupId: String) {
        pendingSupersetCompleteRunnables.remove(supersetGroupId)?.let { runnable ->
            workoutTimerHandler.removeCallbacks(runnable)
        }
        supersetTargetSetsByGroupId.remove(supersetGroupId)
        completedSupersetGroupIds.remove(supersetGroupId)
        val indices = groupedExercises.mapIndexed { i, g -> if (g.supersetGroupId == supersetGroupId) i else -1 }.filter { it >= 0 }
        for (i in indices) {
            val g = groupedExercises[i]
            groupedExercises[i] = g.copy(supersetGroupId = null, groupType = null)
            adapter.notifyItemChanged(i)
        }
        persistDraft()
    }

    private fun handleExerciseLongPress(position: Int) {
        if (position < 0 || position >= groupedExercises.size) return
        when {
            selectedForSupersetPositions.isEmpty() -> {
                selectedForSupersetPositions.add(position)
                adapter.notifyItemChanged(position)
                Toast.makeText(this, getString(R.string.toast_longpress_another_superset), Toast.LENGTH_SHORT).show()
            }
            position in selectedForSupersetPositions -> {
                selectedForSupersetPositions.remove(position)
                adapter.notifyItemChanged(position)
            }
            else -> {
                selectedForSupersetPositions.add(position)
                val sorted = selectedForSupersetPositions.sorted()
                val min = sorted.first()
                val max = sorted.last()
                val consecutive = sorted == (min..max).toList()
                if (!consecutive) {
                    Toast.makeText(this, getString(R.string.toast_select_consecutive_superset), Toast.LENGTH_SHORT).show()
                    selectedForSupersetPositions.remove(position)
                    adapter.notifyItemChanged(position)
                    return
                }
                for (i in selectedForSupersetPositions) adapter.notifyItemChanged(i)
                val names = (min..max).map { groupedExercises[it].exerciseName }
                val message = getString(R.string.dialog_message_connect_superset, names.joinToString(", "))
                com.liftpath.helpers.DialogHelper.createBuilder(this)
                    .setTitle(getString(R.string.dialog_title_connect_superset))
                    .setMessage(message)
                    .setPositiveButton(getString(R.string.button_ok)) { _, _ ->
                        linkSelectedAsSuperset(min, max)
                        selectedForSupersetPositions.clear()
                        for (i in min..max) adapter.notifyItemChanged(i)
                    }
                    .setNegativeButton(getString(R.string.button_cancel)) { _, _ ->
                        selectedForSupersetPositions.clear()
                        for (i in min..max) adapter.notifyItemChanged(i)
                    }
                    .showWithTransparentWindow()
            }
        }
    }

    private fun linkSelectedAsSuperset(min: Int, max: Int) {
        val supersetGroupId = UUID.randomUUID().toString()
        for (i in min..max) {
            val g = groupedExercises[i]
            groupedExercises[i] = g.copy(supersetGroupId = supersetGroupId, groupType = GroupType.SUPERSET)
        }
        adapter.notifyItemRangeChanged(min, max - min + 1)
        persistDraft()
        showSupersetTargetSetsDialog(supersetGroupId, (min..max).toList())
    }

    private fun showSupersetTargetSetsDialog(supersetGroupId: String, indices: List<Int>) {
        val input = EditText(this).apply {
            setHint("3")
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("3")
        }
        val padding = (24 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding, padding, padding)
        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.dialog_title_superset_sets))
            .setMessage(getString(R.string.dialog_message_superset_sets))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val raw = input.text?.toString()?.toIntOrNull() ?: 3
                val value = raw.coerceIn(1, 20)
                supersetTargetSetsByGroupId[supersetGroupId] = value
                indices.forEach { adapter.notifyItemChanged(it) }
                persistDraft()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                supersetTargetSetsByGroupId[supersetGroupId] = 3
                persistDraft()
            }
            .showWithTransparentWindow()
    }

    private var pendingSpecialElementId: Int? = null

    private fun startSpecialElementTimer(exerciseId: Int) {
        val settings = settingsManager.getSettings()
        if (!settings.restTimerEnabled) {
            Toast.makeText(this, getString(R.string.toast_rest_timer_disabled), Toast.LENGTH_SHORT).show()
            return
        }
        val element = groupedExercises.firstOrNull { it.exerciseId == exerciseId } ?: return
        val duration = element.durationSeconds

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                pendingSpecialElementId = exerciseId
                pendingTimerTime = duration
                pendingTimerExerciseName = null
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        launchSpecialElementTimer(exerciseId, duration)
    }

    private fun launchSpecialElementTimer(exerciseId: Int, durationSeconds: Int) {
        val element = groupedExercises.firstOrNull { it.exerciseId == exerciseId }
        val isWarmup = element?.slotType == PlanSlotType.WARMUP
        val title = if (isWarmup) getString(R.string.warmup_timer_title) else getString(R.string.cooldown_timer_title)
        specialTimerEndTimes[exerciseId] = System.currentTimeMillis() + durationSeconds * 1000L
        RestTimerService.startTimer(this, durationSeconds, title, showDialog = false)
        setTimerState(TimerState.RUNNING)
        val index = groupedExercises.indexOfFirst { it.exerciseId == exerciseId }
        if (index >= 0) adapter.notifyItemChanged(index)
    }

    private fun resetSpecialElementTimer(exerciseId: Int) {
        specialTimerEndTimes.remove(exerciseId)
        RestTimerService.stopTimer(this)
        setTimerState(TimerState.IDLE)
        val index = groupedExercises.indexOfFirst { it.exerciseId == exerciseId }
        if (index >= 0) adapter.notifyItemChanged(index)
    }

    private fun addWarmupElement(durationSeconds: Int? = null) {
        if (groupedExercises.any { it.slotType == PlanSlotType.WARMUP }) return
        if (durationSeconds != null) {
            addSpecialElement(WARMUP_EXERCISE_ID, getString(R.string.label_warmup_element), PlanSlotType.WARMUP, insertAtFront = true, durationSeconds = durationSeconds)
        } else {
            showDurationPickerDialog(isWarmup = true) { selected ->
                addSpecialElement(WARMUP_EXERCISE_ID, getString(R.string.label_warmup_element), PlanSlotType.WARMUP, insertAtFront = true, durationSeconds = selected)
            }
        }
    }

    private fun addCooldownElement(durationSeconds: Int? = null) {
        if (groupedExercises.any { it.slotType == PlanSlotType.COOLDOWN }) return
        if (durationSeconds != null) {
            addSpecialElement(COOLDOWN_EXERCISE_ID, getString(R.string.label_cooldown_element), PlanSlotType.COOLDOWN, insertAtFront = false, durationSeconds = durationSeconds)
        } else {
            showDurationPickerDialog(isWarmup = false) { selected ->
                addSpecialElement(COOLDOWN_EXERCISE_ID, getString(R.string.label_cooldown_element), PlanSlotType.COOLDOWN, insertAtFront = false, durationSeconds = selected)
            }
        }
    }

    private fun showCircuitPicker() {
        val data = jsonHelper.readTrainingData()
        CircuitPickerBottomSheet.newInstance(
            circuits = CircuitStore.circuits(data),
            library = data.exerciseLibrary,
            onCircuitSelected = { template -> addCircuitElement(template) },
            onNewCircuit = { editCircuitLauncher.launch(Intent(this, EditCircuitActivity::class.java)) }
        ).show(supportFragmentManager, "CircuitPickerBottomSheet")
    }

    private fun addCircuitElement(template: CircuitTemplate) {
        val nextId = CIRCUIT_ROW_ID_BASE - groupedExercises.count { it.isCircuit }
        val group = GroupedExercise(
            exerciseId = nextId,
            exerciseName = template.name,
            sets = emptyList(),
            slotType = PlanSlotType.CIRCUIT,
            circuit = CircuitStore.templateToInstance(template)
        )
        insertRegularExerciseGroup(group)
        persistDraft()
    }

    private fun deleteCircuit(exerciseId: Int) {
        val index = groupedExercises.indexOfFirst { it.exerciseId == exerciseId }
        if (index < 0) return
        val instanceId = groupedExercises[index].circuit?.instanceId
        groupedExercises.removeAt(index)
        adapter.notifyItemRemoved(index)
        if (instanceId != null) currentExerciseEntries.removeAll { it.groupId == instanceId }
        persistDraft()
    }

    private fun launchCircuitRunner(position: Int) {
        if (position < 0 || position >= groupedExercises.size) return
        val group = groupedExercises[position]
        val instance = group.circuit ?: return
        val entries = currentExerciseEntries.filter { it.groupId == instance.instanceId }
        val intent = Intent(this, CircuitRunnerActivity::class.java).apply {
            putExtra(CircuitRunnerActivity.EXTRA_CIRCUIT_INSTANCE, instance)
            putParcelableArrayListExtra(CircuitRunnerActivity.EXTRA_CIRCUIT_ENTRIES, ArrayList(entries))
            putExtra(CircuitRunnerActivity.EXTRA_WORKOUT_TYPE, workoutType)
        }
        circuitRunnerLauncher.launch(intent)
    }

    private fun handleCircuitRunnerResult(data: Intent?) {
        data ?: return
        val instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.getParcelableExtra(CircuitRunnerActivity.RESULT_CIRCUIT_INSTANCE, CircuitInstance::class.java)
        } else {
            @Suppress("DEPRECATION")
            data.getParcelableExtra(CircuitRunnerActivity.RESULT_CIRCUIT_INSTANCE)
        } ?: return
        val entries = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.getParcelableArrayListExtra(CircuitRunnerActivity.RESULT_CIRCUIT_ENTRIES, ExerciseEntry::class.java)
        } else {
            @Suppress("DEPRECATION")
            data.getParcelableArrayListExtra(CircuitRunnerActivity.RESULT_CIRCUIT_ENTRIES)
        } ?: emptyList()

        val index = groupedExercises.indexOfFirst { it.circuit?.instanceId == instance.instanceId }
        if (index < 0) return
        groupedExercises[index] = groupedExercises[index].copy(circuit = instance)
        currentExerciseEntries.removeAll { it.groupId == instance.instanceId }
        currentExerciseEntries.addAll(entries)
        adapter.notifyItemChanged(index)
        persistDraft()
    }

    private fun showCircuitSettingsDialog(position: Int) {
        if (position < 0 || position >= groupedExercises.size) return
        val group = groupedExercises[position]
        val circuit = group.circuit ?: return

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val roundsInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.edit_circuit_hint_rounds)
            circuit.suggestedRounds?.let { setText(it.toString()) }
        }
        val restInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.edit_circuit_hint_rest)
            setText(circuit.restBetweenRoundsSeconds.toString())
        }
        container.addView(roundsInput)
        container.addView(restInput)

        DialogHelper.createBuilder(this)
            .setTitle(group.exerciseName)
            .setView(container)
            .setPositiveButton(getString(R.string.button_ok)) { _, _ ->
                val rounds = roundsInput.text.toString().toIntOrNull()
                val rest = restInput.text.toString().toIntOrNull() ?: circuit.restBetweenRoundsSeconds
                groupedExercises[position] = group.copy(
                    circuit = circuit.copy(suggestedRounds = rounds, restBetweenRoundsSeconds = rest)
                )
                adapter.notifyItemChanged(position)
                persistDraft()
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    private fun showDurationPickerDialog(isWarmup: Boolean, onSelected: (Int) -> Unit) {
        val title = if (isWarmup) getString(R.string.dialog_title_warmup_duration) else getString(R.string.dialog_title_cooldown_duration)
        val options = arrayOf("5 min", "10 min", "15 min", "20 min", getString(R.string.option_custom))
        val durations = intArrayOf(300, 600, 900, 1200, -1)
        DialogHelper.createBuilder(this)
            .setTitle(title)
            .setItems(options) { _, which ->
                if (durations[which] == -1) showCustomDurationDialog(onSelected)
                else onSelected(durations[which])
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    private fun showCustomDurationDialog(onSelected: (Int) -> Unit) {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.hint = "Minutes"
        input.setPadding(48, 24, 48, 24)
        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.dialog_title_custom_duration))
            .setView(input)
            .setPositiveButton(getString(R.string.button_ok)) { _, _ ->
                val mins = input.text.toString().toIntOrNull()
                if (mins != null && mins > 0) onSelected(mins * 60)
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    private fun showEditDurationDialog(exerciseId: Int) {
        val index = groupedExercises.indexOfFirst { it.exerciseId == exerciseId }
        if (index < 0) return
        val isWarmup = groupedExercises[index].slotType == PlanSlotType.WARMUP
        showDurationPickerDialog(isWarmup) { newDuration ->
            groupedExercises[index] = groupedExercises[index].copy(durationSeconds = newDuration)
            adapter.notifyItemChanged(index)
            persistDraft()
        }
    }

    private fun addSpecialElement(exerciseId: Int, name: String, slotType: PlanSlotType, insertAtFront: Boolean, durationSeconds: Int = 300) {
        val group = GroupedExercise(exerciseId, name, emptyList(), slotType = slotType, durationSeconds = durationSeconds)
        if (insertAtFront) {
            groupedExercises.add(0, group)
            adapter.notifyItemInserted(0)
        } else {
            groupedExercises.add(group)
            adapter.notifyItemInserted(groupedExercises.size - 1)
        }
        persistDraft()
    }

    // Warmup is pinned at index 0 by addSpecialElement's insertAtFront and nothing ever
    // inserts before it, so only cooldown needs an active guard: insert regular exercises
    // before it instead of appending past it, so it stays last no matter what's added after.
    private fun insertRegularExerciseGroup(group: GroupedExercise, notify: Boolean = true): Int {
        val cooldownIndex = groupedExercises.indexOfFirst { it.slotType == PlanSlotType.COOLDOWN }
        val insertIndex = if (cooldownIndex >= 0) cooldownIndex else groupedExercises.size
        groupedExercises.add(insertIndex, group)
        if (notify) adapter.notifyItemInserted(insertIndex)
        return insertIndex
    }

    private fun normalizeSpecialSlotOrder() {
        val warmupIndex = groupedExercises.indexOfFirst { it.slotType == PlanSlotType.WARMUP }
        if (warmupIndex > 0) {
            groupedExercises.add(0, groupedExercises.removeAt(warmupIndex))
        }
        val cooldownIndex = groupedExercises.indexOfFirst { it.slotType == PlanSlotType.COOLDOWN }
        if (cooldownIndex in 0 until groupedExercises.size - 1) {
            groupedExercises.add(groupedExercises.removeAt(cooldownIndex))
        }
    }

    private fun handleSpecialCompleted(exerciseId: Int, isCompleted: Boolean) {
        val index = groupedExercises.indexOfFirst { it.exerciseId == exerciseId }
        if (index < 0) return
        // Clean up card timer state when marking done/undone
        specialTimerEndTimes.remove(exerciseId)
        groupedExercises[index] = groupedExercises[index].copy(isSpecialCompleted = isCompleted)
        // Save/remove a completion entry in the session log
        currentExerciseEntries.removeAll { it.exerciseId == exerciseId }
        if (isCompleted) {
            val isWarmup = groupedExercises[index].slotType == PlanSlotType.WARMUP
            currentExerciseEntries.add(
                ExerciseEntry(
                    exerciseId = exerciseId,
                    exerciseName = groupedExercises[index].exerciseName,
                    setNumber = 1,
                    kg = 0f,
                    reps = 0,
                    completed = true,
                    isWarmup = isWarmup
                )
            )
        }
        adapter.notifyItemChanged(index)
        persistDraft()
    }

    private fun deleteSpecialElement(exerciseId: Int) {
        val index = groupedExercises.indexOfFirst { it.exerciseId == exerciseId }
        if (index < 0) return
        groupedExercises.removeAt(index)
        currentExerciseEntries.removeAll { it.exerciseId == exerciseId }
        adapter.notifyItemRemoved(index)
        persistDraft()
    }

    private fun showCompleteWorkoutDialog() {
        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.dialog_title_complete_workout))
            .setMessage(getString(R.string.dialog_message_complete_workout))
            .setPositiveButton(getString(R.string.button_complete)) { _, _ ->
                finishWorkout()
            }
            .setNegativeButton(getString(R.string.button_return), null)
            .showWithTransparentWindow()
    }

    private fun finishWorkout() {
        try {
            // Calculate duration before stopping timer
            val durationSeconds = workoutStartTimeMillis?.let { startTime ->
                calculateElapsedSeconds(startTime)
            }
            
            // Stop workout timer
            stopWorkoutTimer()
            
            val trainingData = jsonHelper.readTrainingData()
            val nextTrainingNumber = (trainingData.trainings.maxOfOrNull { it.trainingNumber } ?: 0) + 1

            // Round data (rounds run, work time per round) has nowhere else to live — the
            // set list alone can't tell "3 rounds of 5 stations" from 15 unrelated sets.
            val circuitLogs = groupedExercises
                .mapNotNull { it.circuit }
                .filter { it.completedRounds > 0 }
                .map { CircuitStore.instanceToLog(it) }
                .ifEmpty { null }

            val newSession = TrainingSession(
                trainingNumber = nextTrainingNumber,
                date = binding.textDate.text.toString(),
                exercises = currentExerciseEntries.toMutableList(),
                defaultWorkoutType = workoutType,
                planId = appliedPlanId,
                planName = appliedPlanName,
                durationSeconds = durationSeconds,
                circuitLogs = circuitLogs
            )

            trainingData.trainings.add(newSession)

            // Update PlanSet progress if this workout was part of a plan set rotation.
            // Only update on actual completion, not on plan application.
            val source = workoutSource
            if (source?.type == WorkoutSourceType.PLAN_SET && source.planSetId != null && source.planId != null) {
                val progressIndex = trainingData.planSetProgress.indexOfFirst { it.planSetId == source.planSetId }
                val updatedProgress = PlanSetProgress(
                    planSetId = source.planSetId,
                    lastCompletedPlanId = source.planId,
                    lastCompletedAt = System.currentTimeMillis()
                )
                if (progressIndex >= 0) {
                    trainingData.planSetProgress[progressIndex] = updatedProgress
                } else {
                    trainingData.planSetProgress.add(updatedProgress)
                }
            }

            jsonHelper.writeTrainingData(trainingData)
            draftManager.clearDraft()

            // Collect primary muscles before clearing entries
            val workedMuscles: Set<TargetMuscle> = currentExerciseEntries
                .map { it.exerciseId }
                .distinct()
                .filter { id -> id != WARMUP_EXERCISE_ID && id != COOLDOWN_EXERCISE_ID }
                .flatMap { id ->
                    trainingData.exerciseLibrary.find { it.id == id }?.primaryTargets
                        ?: DefaultExercisesHelper.getPrimaryTargets(id)
                        ?: emptyList()
                }
                .toSet()

            // Clear entries so onPause/onStop don't re-persist the draft during activity teardown
            currentExerciseEntries.clear()
            groupedExercises.clear()

            val stretches = DefaultStretchesHelper.getStretchesFor(workedMuscles)
            if (stretches.isEmpty()) {
                startActivity(Intent(this, WorkoutReportActivity::class.java).apply {
                    putExtra(WorkoutReportActivity.EXTRA_TRAINING_SESSION, newSession)
                })
            } else {
                startActivity(Intent(this, StretchCooldownActivity::class.java).apply {
                    putExtra(StretchCooldownActivity.EXTRA_TRAINING_SESSION, newSession)
                    putStringArrayListExtra(
                        StretchCooldownActivity.EXTRA_WORKED_MUSCLES,
                        ArrayList(workedMuscles.map { it.name })
                    )
                })
            }

            setResult(Activity.RESULT_OK)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finish workout", e)
        }
    }

    private fun maybeRestoreDraft(forceResume: Boolean) {
        if (hasRestoredDraft || currentExerciseEntries.isNotEmpty()) return
        val draft = draftManager.loadDraft() ?: return
        if (draft.entries.isEmpty() && draft.exerciseOrder.isNullOrEmpty()) {
            draftManager.clearDraft()
            return
        }

        if (forceResume) {
            applyDraft(draft)
            return
        }

        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.dialog_title_resume_workout))
            .setMessage(getString(R.string.dialog_message_resume_workout, draft.workoutType, draft.date))
            .setPositiveButton(getString(R.string.button_resume)) { _, _ ->
                applyDraft(draft)
            }
            .setNegativeButton(getString(R.string.button_discard)) { _, _ ->
                draftManager.clearDraft()
            }
            .setNeutralButton(getString(R.string.button_cancel)) { _, _ ->
                finish()
            }
            .showWithTransparentWindow()
    }

    private fun applyDraft(draft: ActiveWorkoutDraft) {
        hasRestoredDraft = true
        workoutType = draft.workoutType
        updateTitle()

        try {
            val parsedDate = sessionDateFormat.parse(draft.date)
            if (parsedDate != null) {
                selectedDate.time = parsedDate
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse draft date", e)
        }
        binding.textDate.text = draft.date

        appliedPlanId = draft.appliedPlanId
        appliedPlanName = draft.appliedPlanName
        workoutSource = draft.workoutSource

        currentExerciseEntries.clear()
        currentExerciseEntries.addAll(draft.entries.map { it.copy() })

        exerciseWorkoutTypes.clear()
        exerciseIntents.clear()
        lockedIntents.clear()
        currentExerciseEntries.forEach { entry ->
            entry.workoutType?.let { exerciseWorkoutTypes[entry.exerciseId] = it }
            entry.explicitIntent?.let { exerciseIntents[entry.exerciseId] = it }
        }
        draft.exerciseOrder?.forEach { row ->
            row.workoutType?.let { exerciseWorkoutTypes[row.exerciseId] = it }
            row.explicitIntent?.let { exerciseIntents[row.exerciseId] = it }
        }
        // Restore locked intents (first set's intent for each exercise)
        currentExerciseEntries
            .groupBy { it.exerciseId }
            .forEach { (exerciseId, entries) ->
                val firstSet = entries.minByOrNull { it.setNumber }
                firstSet?.explicitIntent?.let { intent ->
                    lockedIntents[exerciseId] = intent
                }
            }

        val order = draft.exerciseOrder
        if (!order.isNullOrEmpty()) {
            groupedExercises.clear()
            planExerciseSnapshots.clear()
            val setsByExercise = currentExerciseEntries.groupBy { it.exerciseId }
            for (row in order) {
                val sets = setsByExercise[row.exerciseId]?.sortedBy { it.setNumber } ?: emptyList()
                groupedExercises.add(
                    GroupedExercise(
                        exerciseId = row.exerciseId,
                        exerciseName = row.exerciseName,
                        sets = sets,
                        supersetGroupId = row.supersetGroupId,
                        groupType = row.groupType,
                        slotType = row.slotType,
                        isSpecialCompleted = row.isSpecialCompleted,
                        durationSeconds = row.durationSeconds,
                        slotSelectionType = row.slotSelectionType,
                        slotFamilyId = row.slotFamilyId,
                        circuit = row.circuit
                    )
                )
                // Restore plan snapshots from draft rows
                if (row.fromPlan && !row.isSpecialElement) {
                    planExerciseSnapshots[row.exerciseId] = row
                }
            }
            adapter.notifyDataSetChanged()
        } else {
            rebuildGroupedExercisesFromEntries()
        }

        // Self-heal drafts persisted before warmup/cooldown pinning was enforced.
        normalizeSpecialSlotOrder()
        adapter.notifyDataSetChanged()

        groupedExercises.forEach { group ->
            if (!group.isSpecialElement && !group.isCircuit && !exerciseIntents.containsKey(group.exerciseId)) {
                exerciseIntents[group.exerciseId] = SetIntent.BUILD
            }
        }

        // Initialize last workout data for all exercises with intents (skip sentinel IDs)
        lastWorkoutData.clear()
        lastIntents.clear()
        exerciseIntents.forEach { (exerciseId, intent) ->
            if (exerciseId <= 0) return@forEach  // skip warmup/cooldown sentinel IDs
            if (!lastWorkoutData.containsKey(exerciseId)) {
                lastWorkoutData[exerciseId] = mutableMapOf()
            }
            val lastSets = fetchLastWorkoutSets(exerciseId, intent)
            lastWorkoutData[exerciseId]!![intent] = lastSets
            
            // Get and store the last intent used for this exercise
            val lastIntent = getLastIntentForExercise(exerciseId)
            if (lastIntent != null) {
                lastIntents[exerciseId] = lastIntent
            }
        }

        // Restore last sets count and last logged kg/reps/rpe if plan was applied
        if (appliedPlanId != null) {
            val trainingData = jsonHelper.readTrainingData()
            lastSetsCount.clear()
            lastLoggedKg.clear()
            lastLoggedReps.clear()
            lastLoggedRpe.clear()
            val exerciseIds = buildSet<Int> {
                currentExerciseEntries.forEach { add(it.exerciseId) }
                draft.exerciseOrder?.forEach { add(it.exerciseId) }
            }
            exerciseIds.filter { it > 0 }.forEach { exerciseId ->
                // Find the last working set for this exercise (exclude warmup)
                val lastWorkingEntry = trainingData.trainings
                    .flatMap { it.exercises }
                    .filter { it.exerciseId == exerciseId && !it.isEffectivelyWarmup() }
                    .lastOrNull()
                
                if (lastWorkingEntry != null) {
                    lastLoggedKg[exerciseId] = lastWorkingEntry.kg
                    lastLoggedReps[exerciseId] = lastWorkingEntry.reps
                    lastWorkingEntry.rpe?.let { lastLoggedRpe[exerciseId] = it }
                }
                
                // Find the last number of sets
                val lastSession = trainingData.trainings
                    .sortedByDescending { it.trainingNumber }
                    .firstOrNull { session ->
                        session.exercises.any { it.exerciseId == exerciseId }
                    }
                
                val setsCount = lastSession?.exercises
                    ?.filter { it.exerciseId == exerciseId }
                    ?.size ?: 0
                
                if (setsCount > 0) {
                    lastSetsCount[exerciseId] = setsCount
                }
            }
        } else {
            lastSetsCount.clear()
            lastLoggedKg.clear()
            lastLoggedReps.clear()
            lastLoggedRpe.clear()
        }

        draft.supersetPairs?.let { pairs ->
            if (pairs.isEmpty()) return@let
            // Build ordered chains: (A,B), (B,C) -> one chain [A,B,C] so 3+ exercises in one superset
            val chains = mutableListOf<MutableList<Int>>()
            val chainTargets = mutableListOf<Int>()
            var targetIndex = 0
            for (index in pairs.indices) {
                val pair = pairs[index]
                val a = pair.exerciseId1
                val b = pair.exerciseId2
                var merged = false
                for (chain in chains) {
                    if (chain.last() == a) {
                        chain.add(b)
                        merged = true
                        break
                    }
                    if (chain.first() == b) {
                        chain.add(0, a)
                        merged = true
                        break
                    }
                }
                if (!merged) {
                    chains.add(mutableListOf(a, b))
                    chainTargets.add(draft.supersetTargetSets?.getOrNull(targetIndex) ?: 3)
                    targetIndex++
                }
            }
            // Assign one groupId per chain to all exercises in that chain
            chains.forEachIndexed { chainIndex, exerciseIds ->
                val groupId = UUID.randomUUID().toString()
                val target = chainTargets.getOrNull(chainIndex) ?: 3
                supersetTargetSetsByGroupId[groupId] = target
                for (eid in exerciseIds) {
                    val idx = groupedExercises.indexOfFirst { it.exerciseId == eid }
                    if (idx >= 0) {
                        val g = groupedExercises[idx]
                        groupedExercises[idx] = g.copy(supersetGroupId = groupId, groupType = GroupType.SUPERSET)
                    }
                }
            }
            adapter.notifyDataSetChanged()
        }
        updatePlanButtonState()
        
        // Restore workout timer if it was started
        draft.startTimeMillis?.let { startTime ->
            workoutStartTimeMillis = startTime
            updateWorkoutTimerDisplay()
            startWorkoutTimerUpdates()
        } ?: run {
            // If no start time in draft, start timer now
            startWorkoutTimer()
        }
    }

    private fun rebuildGroupedExercisesFromEntries() {
        groupedExercises.clear()
        if (currentExerciseEntries.isEmpty()) {
            adapter.notifyDataSetChanged()
            return
        }
        val groupedByExercise = currentExerciseEntries.groupBy { it.exerciseId }
        groupedByExercise.values.forEach { sets ->
            val sortedSets = sets.sortedBy { it.setNumber }
            val first = sortedSets.first()
            groupedExercises.add(GroupedExercise(
                first.exerciseId,
                first.exerciseName,
                sortedSets,
                supersetGroupId = first.groupId,
                groupType = first.groupType
            ))
        }
        adapter.notifyDataSetChanged()
    }

    private fun persistDraft() {
        if (groupedExercises.isEmpty() && currentExerciseEntries.isEmpty()) {
            draftManager.clearDraft()
            WatchLink.publish(buildWatchState())
            return
        }
        val entriesCopy = currentExerciseEntries.map { it.copy() }
        val exerciseOrder = groupedExercises.map { g ->
            val snapshot = planExerciseSnapshots[g.exerciseId]
            DraftExerciseRow(
                exerciseId = g.exerciseId,
                exerciseName = g.exerciseName,
                supersetGroupId = g.supersetGroupId,
                groupType = g.groupType,
                workoutType = exerciseWorkoutTypes[g.exerciseId],
                explicitIntent = exerciseIntents[g.exerciseId],
                fromPlan = snapshot != null,
                sourcePlanConfigId = snapshot?.sourcePlanConfigId,
                plannedIntent = snapshot?.plannedIntent,
                plannedRestTimeSeconds = snapshot?.plannedRestTimeSeconds,
                plannedRpeTarget = snapshot?.plannedRpeTarget,
                plannedSetsTarget = snapshot?.plannedSetsTarget,
                plannedRepsTarget = snapshot?.plannedRepsTarget,
                plannedDurationSeconds = snapshot?.plannedDurationSeconds,
                plannedNotes = snapshot?.plannedNotes,
                slotType = g.slotType,
                isSpecialCompleted = g.isSpecialCompleted,
                durationSeconds = g.durationSeconds,
                slotSelectionType = g.slotSelectionType,
                slotFamilyId = g.slotFamilyId,
                circuit = g.circuit
            )
        }
        val supersetPairs = mutableListOf<SupersetPair>()
        val supersetTargetSets = mutableListOf<Int>()
        var lastGroupId: String? = null
        for (i in 0 until groupedExercises.size - 1) {
            val a = groupedExercises[i]
            val b = groupedExercises[i + 1]
            if (a.supersetGroupId != null && a.supersetGroupId == b.supersetGroupId) {
                supersetPairs.add(SupersetPair(a.exerciseId, b.exerciseId))
                if (a.supersetGroupId != lastGroupId) {
                    lastGroupId = a.supersetGroupId
                    supersetTargetSets.add(supersetTargetSetsByGroupId[a.supersetGroupId!!] ?: 3)
                }
            } else {
                lastGroupId = null
            }
        }
        val draft = ActiveWorkoutDraft(
            workoutType = workoutType,
            date = binding.textDate.text.toString(),
            appliedPlanId = appliedPlanId,
            appliedPlanName = appliedPlanName,
            entries = entriesCopy,
            startTimeMillis = workoutStartTimeMillis,
            supersetPairs = supersetPairs.ifEmpty { null },
            supersetTargetSets = supersetTargetSets.ifEmpty { null },
            exerciseOrder = exerciseOrder.ifEmpty { null },
            workoutSource = workoutSource
        )
        draftManager.saveDraft(draft)
        // Single publish point for the watch. persistDraft() is already called from every
        // mutation path in this screen, so the watch cannot drift out of date unless the draft
        // does too — which would be a bug worth having anyway.
        WatchLink.publish(buildWatchState())
    }

    private fun persistDraftIfHasEntries() {
        if (groupedExercises.isNotEmpty() || currentExerciseEntries.isNotEmpty()) {
            persistDraft()
        }
    }

    // --- WATCH LINK (Garmin Fenix) ---

    /**
     * Projects the session down to what a watch screen can act on: what to do next, how many
     * sets remain, and what to load. Everything else stays on the phone.
     *
     * Warmup/cooldown special elements are excluded — they carry no sets, so there is nothing
     * to log against them from the wrist.
     */
    override fun buildWatchState(): WatchState {
        val library = jsonHelper.readTrainingData().exerciseLibrary
        val exercises = groupedExercises
            .filter { it.slotType == PlanSlotType.EXERCISE }
            .map { group ->
                val id = group.exerciseId
                val snapshot = planExerciseSnapshots[id]
                WatchExercise(
                    exerciseId = id,
                    name = group.exerciseName,
                    setsDone = currentExerciseEntries.count {
                        it.exerciseId == id && !it.isEffectivelyWarmup()
                    },
                    setsTarget = snapshot?.plannedSetsTarget ?: 0,
                    repsTarget = parseWatchRepsTarget(snapshot?.plannedRepsTarget),
                    suggestedKg = lastLoggedKg[id] ?: 0f,
                    isBodyweight = ExerciseModeResolver.isBodyweight(library, id)
                )
            }
        return WatchState(
            sessionActive = exercises.isNotEmpty(),
            restRemainingSeconds = RestTimerService.getRemainingSeconds(this),
            exercises = exercises
        )
    }

    /** Plans store reps as free text ("8-12", "10", "AMRAP"). The watch needs one number. */
    private fun parseWatchRepsTarget(raw: String?): Int =
        raw?.trimStart()?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0

    override fun onWatchCommand(command: WatchCommand) {
        when (command) {
            is WatchCommand.LogSet -> logSetFromWatch(command)

            is WatchCommand.StartRest -> if (canPostNotifications()) {
                val name = groupedExercises.firstOrNull()?.exerciseName ?: "Rest"
                RestTimerService.startTimer(this, command.seconds, name, showDialog = false)
                setTimerState(TimerState.RUNNING)
                WatchLink.publish(buildWatchState())
            }

            WatchCommand.StopRest -> {
                RestTimerService.stopTimer(this)
                setTimerState(TimerState.IDLE)
                WatchLink.publish(buildWatchState())
            }

            WatchCommand.Sync -> WatchLink.publish(buildWatchState())
        }
    }

    /**
     * The watch's equivalent of [launchLogSetActivity] plus its result callback, collapsed into
     * one step — there is no form to fill in, the numbers arrive already decided.
     *
     * Set numbering, workout type and intent are derived exactly as the phone path derives
     * them, so a set logged from the wrist is indistinguishable from one logged here. It also
     * routes through [updateExercises], which means superset highlighting, intent locking and
     * the draft write all happen for free.
     */
    private fun logSetFromWatch(command: WatchCommand.LogSet) {
        val exerciseId = command.exerciseId
        val group = groupedExercises.firstOrNull { it.exerciseId == exerciseId }
        if (group == null) {
            // The watch was showing a stale projection — the exercise has since been removed.
            Log.w(TAG, "watch logged a set for exercise $exerciseId, no longer in the session")
            return
        }

        val lastWorkingSet = currentExerciseEntries
            .filter { it.exerciseId == exerciseId && !it.isWarmup }
            .maxByOrNull { it.setNumber }
        val setNumber = (lastWorkingSet?.setNumber ?: currentExerciseEntries
            .filter { it.exerciseId == exerciseId }
            .maxByOrNull { it.setNumber }?.setNumber ?: 0) + 1

        // The watch only ever sends added load. `kg` must stay (bodyweight + added) so every
        // existing reader of ExerciseEntry keeps working — see the note on ExerciseEntry.kg.
        val bodyweightKg = if (ExerciseModeResolver.isBodyweight(
                jsonHelper.readTrainingData().exerciseLibrary, exerciseId
            )
        ) {
            BodyWeightHelper.getCurrentBodyweightKg(this)
        } else {
            null
        }

        // familyIdSnapshot is deliberately left null: JsonHelper backfills it from the live
        // library when the session is written, the same as for a phone-logged set.
        val entry = ExerciseEntry(
            exerciseId = exerciseId,
            exerciseName = group.exerciseName,
            setNumber = setNumber,
            kg = if (bodyweightKg != null) bodyweightKg + command.kg else command.kg,
            reps = command.reps,
            workoutType = exerciseWorkoutTypes[exerciseId] ?: workoutType,
            rpe = command.rpe,
            explicitIntent = exerciseIntents[exerciseId] ?: SetIntent.BUILD,
            bodyweightKg = bodyweightKg,
            addedKg = if (bodyweightKg != null) command.kg else null
        )

        updateExercises(entry)

        // Not startRestTimerAfterLoggedSet: that one can launch a runtime permission request,
        // and a button press on a wrist must never try to raise a dialog on a phone that may
        // be asleep in a pocket. If the permission is missing the watch simply gets no timer.
        if (canPostNotifications()) {
            startRestTimerAfterLoggedSet(exerciseId, entry.rpe)
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    // --- TIMER LOGIC ---

    private fun setupTimerUI() {
        updateTimerDisplay(0)
        setTimerState(TimerState.IDLE)
    }

    private fun setupTimerReceiver() {
        timerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "com.liftpath.REST_TIMER_TICK" -> {
                        val remaining = intent.getIntExtra("remaining", 0)
                        updateTimerDisplay(remaining)
                        if (remaining > 0) setTimerState(TimerState.RUNNING)
                    }
                    "com.liftpath.REST_TIMER_COMPLETE" -> {
                        updateTimerDisplay(0)
                        setTimerState(TimerState.COMPLETED)
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Other activities use their own JsonHelper and write training_data.json; drop cache so reads stay correct.
        jsonHelper.invalidateTrainingDataCache()
        adapter.invalidateProgressionSettingsCache()
        isActivityVisible = true
        val filter = IntentFilter().apply {
            addAction("com.liftpath.REST_TIMER_TICK")
            addAction("com.liftpath.REST_TIMER_COMPLETE")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(timerReceiver, filter)
        }
        syncTimerState()
        // Resume workout timer if it was started
        if (workoutStartTimeMillis != null) {
            startWorkoutTimerUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        isActivityVisible = false
        timerReceiver?.let {
            try { unregisterReceiver(it) } catch (e: Exception) { Log.e(TAG, "Error", e) }
        }
        // Pause workout timer updates (but keep tracking time)
        stopWorkoutTimer()
        // Save workout state when app goes to background
        persistDraftIfHasEntries()
    }

    override fun onStop() {
        super.onStop()
        // Additional safety net: save workout state when activity is no longer visible
        // This ensures state is persisted even if activity is destroyed while backgrounded
        persistDraftIfHasEntries()
    }

    override fun onDestroy() {
        super.onDestroy()
        WatchLink.detachHost(this)
        stopTimerIfRunning()
        stopWorkoutTimer()
        timerReceiver?.let {
            try { unregisterReceiver(it) } catch (e: Exception) { Log.e(TAG, "Error", e) }
        }
    }

    private fun syncTimerState() {
        val isRunning = RestTimerService.isTimerRunning(this)
        val remaining = RestTimerService.getRemainingSeconds(this)
        if (isRunning) {
            updateTimerDisplay(remaining)
            setTimerState(TimerState.RUNNING)
        } else if (remaining > 0) {
            updateTimerDisplay(remaining)
            setTimerState(TimerState.IDLE)
        } else {
            updateTimerDisplay(0)
            setTimerState(TimerState.IDLE)
        }
        // Auto-complete any special element whose card timer expired while backgrounded
        val now = System.currentTimeMillis()
        specialTimerEndTimes.keys.toList().forEach { exerciseId ->
            val endTime = specialTimerEndTimes[exerciseId] ?: return@forEach
            if (now >= endTime) {
                handleSpecialCompleted(exerciseId, true)
            }
        }
    }

    private fun handleTimerStartPause() {
        if (RestTimerService.isTimerRunning(this)) {
            RestTimerService.stopTimer(this)
            val remaining = RestTimerService.getRemainingSeconds(this)
            updateTimerDisplay(remaining)
            setTimerState(TimerState.IDLE)
        } else {
            startTimer()
        }
    }

    private fun handleTimerResetStop() {
        if (RestTimerService.isTimerRunning(this)) {
            RestTimerService.stopTimer(this)
        }
        updateTimerDisplay(0)
        setTimerState(TimerState.IDLE)
    }

    private fun stopTimerIfRunning() {
        if (RestTimerService.isTimerRunning(this)) {
            RestTimerService.stopTimer(this)
        }
    }

    private var pendingTimerTime: Int? = null
    /** When set with [pendingTimerTime], rest timer uses this label (e.g. after duplicate / logged set). */
    private var pendingTimerExerciseName: String? = null

    private fun startTimer(useCustomTime: Int? = null) {
        val settings = settingsManager.getSettings()
        if (!settings.restTimerEnabled) {
            Toast.makeText(this, getString(R.string.toast_rest_timer_disabled), Toast.LENGTH_SHORT).show()
            return
        }
        pendingTimerExerciseName = null

        // Check and request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Store the timer time to use after permission is granted
                pendingTimerTime = useCustomTime
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        
        startTimerAfterPermissionCheck(useCustomTime)
    }

    /**
     * Same rest duration and notification label as after saving from [LogSetActivity] (intent, RPE adjustments, superset overrides).
     */
    private fun startRestTimerAfterLoggedSet(exerciseId: Int, rpe: Float?) {
        val settings = settingsManager.getSettings()
        if (!settings.restTimerEnabled) return

        val setIntent = exerciseIntents[exerciseId] ?: SetIntent.BUILD
        val override = restSecondsOverrideForNextSet(exerciseId)
        val seconds = RestTimerHelper.restSecondsAfterLoggedSet(settings, setIntent, rpe, override)
        val exerciseName = groupedExercises.find { it.exerciseId == exerciseId }?.exerciseName ?: "Rest"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                pendingTimerTime = seconds
                pendingTimerExerciseName = exerciseName
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        RestTimerService.startTimer(this, seconds, exerciseName, showDialog = false)
        setTimerState(TimerState.RUNNING)
    }

    private fun startTimerAfterPermissionCheck(useCustomTime: Int? = null) {
        val settings = settingsManager.getSettings()
        val actualTime = pendingTimerTime ?: useCustomTime
        pendingTimerTime = null
        val exerciseLabel = pendingTimerExerciseName
        pendingTimerExerciseName = null
        val specialId = pendingSpecialElementId
        pendingSpecialElementId = null

        // Check if this is a special element timer (warmup or cooldown)
        if (specialId != null) {
            launchSpecialElementTimer(specialId, actualTime ?: 300)
            return
        }
        
        var restSeconds = actualTime ?: let {
            val currentTime = RestTimerService.getRemainingSeconds(this)
            if (currentTime > 0) {
                currentTime
            } else {
                // Default to BUILD rest time for manual timer (no exercise context)
                settings.buildRestSeconds
            }
        }
        RestTimerService.startTimer(this, restSeconds, exerciseLabel ?: "Rest", showDialog = false)
        setTimerState(TimerState.RUNNING)
    }

    private fun updateTimerDisplay(seconds: Int) {
        val minutes = seconds / 60
        val secs = seconds % 60
        binding.textTimerDisplay.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
    }

    private fun setTimerState(state: TimerState) {
        when (state) {
            TimerState.IDLE -> {
                binding.buttonTimerStartPause.setImageResource(R.drawable.ic_play)
                binding.buttonTimerStartPause.isEnabled = true
                binding.buttonTimerResetStop.isEnabled = true
            }
            TimerState.RUNNING -> {
                binding.buttonTimerStartPause.setImageResource(R.drawable.ic_pause)
                binding.buttonTimerStartPause.isEnabled = true
                binding.buttonTimerResetStop.isEnabled = true
            }
            TimerState.COMPLETED -> {
                binding.buttonTimerStartPause.setImageResource(R.drawable.ic_play)
                binding.buttonTimerStartPause.isEnabled = true
                binding.buttonTimerResetStop.isEnabled = true
            }
        }
    }

    private fun showSetTimerDialog() {
        val wasRunning = RestTimerService.isTimerRunning(this)
        if (wasRunning) {
            RestTimerService.stopTimer(this)
            setTimerState(TimerState.IDLE)
        }
        val currentSeconds = RestTimerService.getRemainingSeconds(this)
        val dialogBinding = com.liftpath.databinding.DialogSetTimerBinding.inflate(layoutInflater)

        dialogBinding.numberPickerMinutes.minValue = 0
        dialogBinding.numberPickerMinutes.maxValue = 59
        dialogBinding.numberPickerMinutes.value = currentSeconds / 60

        dialogBinding.numberPickerSeconds.minValue = 0
        dialogBinding.numberPickerSeconds.maxValue = 59
        dialogBinding.numberPickerSeconds.value = currentSeconds % 60

        styleNumberPicker(dialogBinding.numberPickerMinutes)
        styleNumberPicker(dialogBinding.numberPickerSeconds)

        val dialog = DialogHelper.createBuilder(this).setView(dialogBinding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.buttonCancel.setOnClickListener {
            if (wasRunning && currentSeconds > 0) startTimer()
            dialog.dismiss()
        }

        dialogBinding.buttonSet.setOnClickListener {
            val totalSeconds = (dialogBinding.numberPickerMinutes.value * 60) + dialogBinding.numberPickerSeconds.value
            if (totalSeconds > 0) {
                RestTimerService.setTimerTime(this, totalSeconds)
                updateTimerDisplay(totalSeconds)
                startTimer(useCustomTime = totalSeconds)
            } else {
                RestTimerService.setTimerTime(this, 0)
                updateTimerDisplay(0)
                setTimerState(TimerState.IDLE)
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun styleNumberPicker(numberPicker: android.widget.NumberPicker) {
        try {
            val count = numberPicker.childCount
            for (i in 0 until count) {
                val child = numberPicker.getChildAt(i)
                if (child is android.widget.TextView) {
                    child.setTextColor(this.lpColor(R.attr.lpInk))
                    child.textSize = 18f
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Error styling", e) }
    }

    private enum class TimerState { IDLE, RUNNING, COMPLETED }

    // --- WORKOUT TIMER LOGIC ---

    private fun setupWorkoutTimer() {
        binding.textWorkoutTimer.text = DurationHelper.formatDuration(0)
    }

    private fun startWorkoutTimer() {
        if (workoutStartTimeMillis == null) {
            workoutStartTimeMillis = System.currentTimeMillis()
            updateWorkoutTimerDisplay()
            startWorkoutTimerUpdates()
        }
    }

    private fun stopWorkoutTimer() {
        workoutTimerRunnable?.let {
            workoutTimerHandler.removeCallbacks(it)
        }
        workoutTimerRunnable = null
    }

    private fun startWorkoutTimerUpdates() {
        workoutTimerRunnable = object : Runnable {
            override fun run() {
                updateWorkoutTimerDisplay()
                workoutTimerHandler.postDelayed(this, 1000) // Update every second
            }
        }
        workoutTimerHandler.post(workoutTimerRunnable!!)
    }

    private fun updateWorkoutTimerDisplay() {
        workoutStartTimeMillis?.let { startTime ->
            val elapsedSeconds = calculateElapsedSeconds(startTime)
            binding.textWorkoutTimer.text = DurationHelper.formatDuration(elapsedSeconds)
        } ?: run {
            binding.textWorkoutTimer.text = DurationHelper.formatDuration(0)
        }
    }

    private fun calculateElapsedSeconds(startTimeMillis: Long): Long {
        val currentTimeMillis = System.currentTimeMillis()
        return (currentTimeMillis - startTimeMillis) / 1000
    }

    companion object {
        const val EXTRA_WORKOUT_TYPE = "WORKOUT_TYPE"
        const val EXTRA_RESUME_DRAFT = "RESUME_DRAFT"
        const val EXTRA_AUTO_GENERATE = "AUTO_GENERATE"
        const val EXTRA_PLAN_ID = "PLAN_ID"
        const val EXTRA_PLAN_SET_ID = "PLAN_SET_ID"
    }
}