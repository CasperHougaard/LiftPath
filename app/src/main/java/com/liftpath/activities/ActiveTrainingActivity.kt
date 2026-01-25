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
import com.liftpath.components.MuscleMapDialog
import com.liftpath.components.AddSpecialBottomSheet
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem

class ActiveTrainingActivity : AppCompatActivity() {

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
    private val lastWorkoutData = mutableMapOf<Int, MutableMap<SetIntent, List<ExerciseEntry>>>()
    private val lastIntents = mutableMapOf<Int, SetIntent>() // Track last intent used for each exercise

    private lateinit var adapter: ActiveExercisesAdapter
    private val selectedDate = Calendar.getInstance()
    private val sessionDateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    private val TAG = "ActiveTrainingActivity"

    private var workoutType: String = "heavy"
    private var appliedPlanId: String? = null
    private var appliedPlanName: String? = null
    private var hasRestoredDraft = false

    // Timer state
    private var isActivityVisible = false
    private var timerReceiver: BroadcastReceiver? = null
    
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
                        groupedExercises.add(newGroup)
                        adapter.notifyItemInserted(groupedExercises.size - 1)
                    }
                    // Exercise added - user can log sets by clicking on the exercise
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
                applyPlanById(planId)
            }
            
            // Show auto-generate dialog if needed (after plan is applied)
            if (!isCustomWorkout && shouldAutoGenerate) {
                showSmartWorkoutSetupDialog()
            } else {
                // Start workout timer if no draft to restore and no dialogs to show
                startWorkoutTimer()
            }
        }
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
                    groupedExercises.add(GroupedExercise(exerciseId, exerciseName, emptyList()))
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

    private fun applyPlan(plan: WorkoutPlan, showToast: Boolean = true) {
        appliedPlanId = plan.id
        appliedPlanName = plan.name
        // If current workout is custom, keep it as custom (don't change to plan's workout type)
        // Otherwise, use the plan's workout type
        if (workoutType != "custom") {
            workoutType = plan.workoutType
        }
        updateTitle()
        updatePlanButtonState()

        val trainingData = jsonHelper.readTrainingData()
        lastSetsCount.clear()

        // Clear existing exercises if needed (or merge - for now, we'll add to existing)
        // For simplicity, we'll add plan exercises even if some already exist
        plan.exerciseIds.forEach { exerciseId ->
            val exercise = trainingData.exerciseLibrary.find { it.id == exerciseId }
            if (exercise != null) {
                // Check if exercise already exists in workout
                val existingGroup = groupedExercises.find { it.exerciseId == exerciseId }
                if (existingGroup == null) {
                    // Find the last logged exercise entry for this exercise
                    val lastEntry = trainingData.trainings
                        .flatMap { it.exercises }
                        .filter { it.exerciseId == exerciseId }
                        .lastOrNull()
                    
                    if (lastEntry != null) {
                        // Store last logged kg and reps
                        lastLoggedKg[exerciseId] = lastEntry.kg
                        lastLoggedReps[exerciseId] = lastEntry.reps
                        
                        // Find the last number of sets for this exercise
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

                    // Add exercise as empty GroupedExercise
                    groupedExercises.add(GroupedExercise(exerciseId, exercise.name, emptyList()))
                    // If workout is custom, use custom for exercise type, otherwise use plan's workout type
                    exerciseWorkoutTypes[exerciseId] = if (workoutType == "custom") "custom" else plan.workoutType
                    // Initialize intent from plan config if available, otherwise BUILD (handle legacy plans)
                    val planConfig = plan.exerciseConfigs?.find { it.exerciseId == exerciseId }
                    val intent = planConfig?.defaultIntent ?: SetIntent.BUILD
                    exerciseIntents[exerciseId] = intent
                    
                    // Initialize last workout data for this exercise and intent
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
            }
        }

        adapter.notifyDataSetChanged()
        persistDraft()
        if (showToast) {
            Toast.makeText(this, "Plan \"$appliedPlanName\" applied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removePlan() {
        appliedPlanId = null
        appliedPlanName = null
        lastSetsCount.clear()
        lastLoggedKg.clear()
        lastLoggedReps.clear()
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
    private fun applyPlanById(planId: String) {
        val trainingData = jsonHelper.readTrainingData()
        val plan = trainingData.workoutPlans.find { it.id == planId }
        
        if (plan != null) {
            applyPlan(plan, showToast = false)
        } else {
            // Plan not found - log error but don't crash
            android.util.Log.e(TAG, "Plan with ID $planId not found")
        }
    }

    private fun updatePlanButtonState() {
        if (appliedPlanId != null) {
            // Plan is applied - change tint to indicate active state
            binding.buttonPlan.setColorFilter(
                ContextCompat.getColor(this, R.color.fitness_primary),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        } else {
            // No plan applied - use default tint
            binding.buttonPlan.setColorFilter(
                ContextCompat.getColor(this, R.color.fitness_primary),
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
                    !entry.isWarmup && 
                    entry.rpe != 6.0f // Exclude warmups
                }
            }
        
        return lastSession?.let { session ->
            // Get the intent of the exercise in this session
            val exerciseEntries = session.exercises
                .filter { it.exerciseId == exerciseId && !it.isWarmup && it.rpe != 6.0f }
            
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
        // RPE 6.0 = warmup for legacy data
        if (entry.explicitIntent == null && entry.rpe == 6.0f) {
            return SetIntent.WARMUP
        }
        
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
            }
        )
        binding.recyclerViewActiveWorkout.adapter = adapter
        binding.recyclerViewActiveWorkout.layoutManager = LinearLayoutManager(this)
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
        // Extract unique exercise IDs from grouped exercises
        val exerciseIds = groupedExercises.map { it.exerciseId }.distinct()
        
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

    private fun launchLogSetActivity(exerciseId: Int, exerciseName: String) {
        try {
            val previousSet = currentExerciseEntries
                .filter { it.exerciseId == exerciseId }
                .maxByOrNull { it.setNumber }
            val setNumber = (previousSet?.setNumber ?: 0) + 1
            val setWorkoutType = exerciseWorkoutTypes[exerciseId] ?: workoutType
            val exerciseIntent = exerciseIntents[exerciseId] ?: SetIntent.BUILD
            val intent = Intent(this, LogSetActivity::class.java).apply {
                putExtra(LogSetActivity.EXTRA_EXERCISE_ID, exerciseId)
                putExtra(LogSetActivity.EXTRA_EXERCISE_NAME, exerciseName)
                putExtra(LogSetActivity.EXTRA_SET_NUMBER, setNumber)
                putExtra(LogSetActivity.EXTRA_WORKOUT_TYPE, setWorkoutType)
                putExtra(LogSetActivity.EXTRA_INTENT, exerciseIntent.name)
                previousSet?.let {
                    putExtra(LogSetActivity.EXTRA_PREVIOUS_SET_REPS, it.reps)
                }
                // Pass last logged values if available (from plan)
                lastLoggedKg[exerciseId]?.let {
                    putExtra(LogSetActivity.EXTRA_LAST_LOGGED_KG, it)
                }
                lastLoggedReps[exerciseId]?.let {
                    putExtra(LogSetActivity.EXTRA_LAST_LOGGED_REPS, it)
                }
            }
            logSetLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch LogSetActivity", e)
        }
    }

    private fun updateExercises(loggedSet: ExerciseEntry) {
        // If explicitIntent is not set, use the exercise's intent
        val setWithIntent = if (loggedSet.explicitIntent == null) {
            val exerciseIntent = exerciseIntents[loggedSet.exerciseId] ?: SetIntent.BUILD
            loggedSet.copy(explicitIntent = exerciseIntent)
        } else {
            loggedSet
        }
        currentExerciseEntries.add(setWithIntent)
        loggedSet.workoutType?.let { exerciseWorkoutTypes[loggedSet.exerciseId] = it }
        
        // Lock the intent when the first set is logged for this exercise
        if (!lockedIntents.containsKey(loggedSet.exerciseId)) {
            lockedIntents[loggedSet.exerciseId] = setWithIntent.explicitIntent ?: SetIntent.BUILD
        }

        val groupIndex = groupedExercises.indexOfFirst { it.exerciseId == loggedSet.exerciseId }
        if (groupIndex != -1) {
            val oldGroup = groupedExercises[groupIndex]
            val newSets = oldGroup.sets + setWithIntent
            val newGroup = oldGroup.copy(sets = newSets.sortedBy { it.setNumber })
            groupedExercises[groupIndex] = newGroup
            adapter.notifyItemChanged(groupIndex)
        }
        persistDraft()
    }

    private fun duplicateLastSet(exerciseId: Int) {
        val lastSet = currentExerciseEntries.filter { it.exerciseId == exerciseId }.lastOrNull()
        if (lastSet != null) {
            val newSetNumber = lastSet.setNumber + 1
            val newSet = lastSet.copy(setNumber = newSetNumber, rating = null, note = null)
            updateExercises(newSet)
            startTimer()
        }
    }

    private fun deleteExercise(exerciseId: Int) {
        val groupIndex = groupedExercises.indexOfFirst { it.exerciseId == exerciseId }
        if (groupIndex != -1) {
            groupedExercises.removeAt(groupIndex)
            currentExerciseEntries.removeAll { it.exerciseId == exerciseId }
            exerciseWorkoutTypes.remove(exerciseId)
            exerciseIntents.remove(exerciseId)
            lockedIntents.remove(exerciseId)
            exerciseRecommendations.remove(exerciseId)
            lastSetsCount.remove(exerciseId)
            lastLoggedKg.remove(exerciseId)
            lastLoggedReps.remove(exerciseId)
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
            val sortedSets = updatedSets.sortedBy { it.setNumber }
            val updatedGroup = GroupedExercise(exerciseId, updatedSets.first().exerciseName, sortedSets)
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
                startWarmupTimer()
            },
            onCooldownSelected = {
                Toast.makeText(this, "Cooldown feature coming soon", Toast.LENGTH_SHORT).show()
            }
        )
        bottomSheet.show(supportFragmentManager, "AddSpecialBottomSheet")
    }

    private fun startWarmupTimer() {
        val settings = settingsManager.getSettings()
        if (!settings.restTimerEnabled) {
            Toast.makeText(this, getString(R.string.toast_rest_timer_disabled), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check and request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                pendingTimerTime = 300 // 5 minutes
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        
        startWarmupTimerAfterPermissionCheck()
    }

    private fun startWarmupTimerAfterPermissionCheck() {
        RestTimerService.startTimer(this, 300, getString(R.string.warmup_timer_title), showDialog = false)
        setTimerState(TimerState.RUNNING)
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

            val newSession = TrainingSession(
                trainingNumber = nextTrainingNumber,
                date = binding.textDate.text.toString(),
                exercises = currentExerciseEntries.toMutableList(),
                defaultWorkoutType = workoutType,
                planId = appliedPlanId,
                planName = appliedPlanName,
                durationSeconds = durationSeconds
            )

            trainingData.trainings.add(newSession)
            jsonHelper.writeTrainingData(trainingData)
            draftManager.clearDraft()
            // Clear entries so onPause/onStop don't re-persist the draft during activity teardown
            currentExerciseEntries.clear()
            groupedExercises.clear()

            // Launch workout report activity
            val reportIntent = Intent(this, WorkoutReportActivity::class.java).apply {
                putExtra(WorkoutReportActivity.EXTRA_TRAINING_SESSION, newSession)
            }
            startActivity(reportIntent)

            setResult(Activity.RESULT_OK)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finish workout", e)
        }
    }

    private fun maybeRestoreDraft(forceResume: Boolean) {
        if (hasRestoredDraft || currentExerciseEntries.isNotEmpty()) return
        val draft = draftManager.loadDraft() ?: return
        if (draft.entries.isEmpty()) {
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

        currentExerciseEntries.clear()
        currentExerciseEntries.addAll(draft.entries.map { it.copy() })

        exerciseWorkoutTypes.clear()
        exerciseIntents.clear()
        lockedIntents.clear()
        currentExerciseEntries.forEach { entry ->
            entry.workoutType?.let { exerciseWorkoutTypes[entry.exerciseId] = it }
            entry.explicitIntent?.let { exerciseIntents[entry.exerciseId] = it }
        }
        // Initialize intents for exercises that don't have one yet
        groupedExercises.forEach { group ->
            if (!exerciseIntents.containsKey(group.exerciseId)) {
                exerciseIntents[group.exerciseId] = SetIntent.BUILD
            }
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

        // Initialize last workout data for all exercises with intents
        lastWorkoutData.clear()
        lastIntents.clear()
        exerciseIntents.forEach { (exerciseId, intent) ->
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

        // Restore last sets count and last logged kg/reps if plan was applied
        if (appliedPlanId != null) {
            val trainingData = jsonHelper.readTrainingData()
            lastSetsCount.clear()
            lastLoggedKg.clear()
            lastLoggedReps.clear()
            val exerciseIds = currentExerciseEntries.map { it.exerciseId }.distinct()
            exerciseIds.forEach { exerciseId ->
                // Find the last logged exercise entry
                val lastEntry = trainingData.trainings
                    .flatMap { it.exercises }
                    .filter { it.exerciseId == exerciseId }
                    .lastOrNull()
                
                if (lastEntry != null) {
                    lastLoggedKg[exerciseId] = lastEntry.kg
                    lastLoggedReps[exerciseId] = lastEntry.reps
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
        }

        rebuildGroupedExercisesFromEntries()
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
            groupedExercises.add(GroupedExercise(first.exerciseId, first.exerciseName, sortedSets))
        }
        adapter.notifyDataSetChanged()
    }

    private fun persistDraft() {
        if (currentExerciseEntries.isEmpty()) {
            draftManager.clearDraft()
            return
        }
        val entriesCopy = currentExerciseEntries.map { it.copy() }
        val draft = ActiveWorkoutDraft(
            workoutType = workoutType,
            date = binding.textDate.text.toString(),
            appliedPlanId = appliedPlanId,
            appliedPlanName = appliedPlanName,
            entries = entriesCopy,
            startTimeMillis = workoutStartTimeMillis
        )
        draftManager.saveDraft(draft)
    }

    private fun persistDraftIfHasEntries() {
        if (currentExerciseEntries.isNotEmpty()) {
            persistDraft()
        }
    }

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
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
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

    private fun startTimer(useCustomTime: Int? = null) {
        val settings = settingsManager.getSettings()
        if (!settings.restTimerEnabled) {
            Toast.makeText(this, getString(R.string.toast_rest_timer_disabled), Toast.LENGTH_SHORT).show()
            return
        }
        
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

    private fun startTimerAfterPermissionCheck(useCustomTime: Int? = null) {
        val settings = settingsManager.getSettings()
        val actualTime = pendingTimerTime ?: useCustomTime
        pendingTimerTime = null
        
        // Check if this is a warmup timer (300 seconds = 5 minutes)
        if (actualTime == 300) {
            startWarmupTimerAfterPermissionCheck()
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
        RestTimerService.startTimer(this, restSeconds, "Rest", showDialog = false)
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
                    child.setTextColor(ContextCompat.getColor(this, R.color.fitness_text_primary))
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
    }
}