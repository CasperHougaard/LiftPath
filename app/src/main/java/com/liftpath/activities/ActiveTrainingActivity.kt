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
import android.view.MenuItem
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.os.Handler
import android.os.Looper

class ActiveTrainingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActiveTrainingBinding
    private lateinit var jsonHelper: JsonHelper
    private lateinit var draftManager: ActiveWorkoutDraftManager
    private lateinit var settingsManager: ProgressionSettingsManager

    // Data State
    private val currentExerciseEntries = mutableListOf<ExerciseEntry>()
    private val groupedExercises = mutableListOf<GroupedExercise>()
    private val exerciseWorkoutTypes = mutableMapOf<Int, String>()
    private val exerciseRecommendations = mutableMapOf<Int, WorkoutGenerator.RecommendedExercise>()

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
                val exerciseType = data?.getStringExtra(SelectExerciseActivity.EXTRA_SELECTED_WORKOUT_TYPE) ?: workoutType

                if (exerciseId != -1 && exerciseName.isNotEmpty()) {
                    exerciseWorkoutTypes[exerciseId] = exerciseType
                    val existingGroup = groupedExercises.find { it.exerciseId == exerciseId }
                    if (existingGroup == null) {
                        val newGroup = GroupedExercise(exerciseId, exerciseName, emptyList())
                        groupedExercises.add(newGroup)
                        adapter.notifyItemInserted(groupedExercises.size - 1)
                    }
                    launchLogSetActivity(exerciseId, exerciseName)
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
        binding = ActivityActiveTrainingBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        if (resumeRequested) {
            maybeRestoreDraft(forceResume = true)
        } else if (draftManager.hasDraft()) {
            maybeRestoreDraft(forceResume = false)
        } else if (!isCustomWorkout && shouldAutoGenerate) {
            showSmartWorkoutSetupDialog()
        } else {
            // Start workout timer if no draft to restore and no dialogs to show
            startWorkoutTimer()
        }
    }

    // --- NAVIGATION ---

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
            .setPositiveButton("Next") { _, _ ->
                showIntensityDialog(selectedFocus)
            }
            .setNegativeButton(getString(R.string.button_cancel)) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .showWithTransparentWindow()
    }

    private fun showIntensityDialog(focusIndex: Int) {
        val intensityOptions = arrayOf("Heavy (Strength)", "Light (Volume/Hypertrophy)")
        var selectedIntensityIndex = 0

        DialogHelper.createBuilder(this)
            .setTitle("Select Intensity")
            .setSingleChoiceItems(intensityOptions, 0) { _, which ->
                selectedIntensityIndex = which
            }
            .setPositiveButton("Create Workout") { _, _ ->
                generateSmartWorkout(focusIndex, selectedIntensityIndex)
            }
            .setNeutralButton("Back") { _, _ ->
                showSmartWorkoutSetupDialog()
            }
            .setCancelable(false)
            .showWithTransparentWindow()
    }

    private fun generateSmartWorkout(focusIndex: Int, intensityIndex: Int) {
        try {
            val focus = when(focusIndex) {
                0 -> SessionFocus.UPPER
                1 -> SessionFocus.LOWER
                else -> SessionFocus.FULL
            }
            val intensity = when(intensityIndex) {
                0 -> SessionIntensity.HEAVY
                else -> SessionIntensity.LIGHT
            }

            this.workoutType = if (intensity == SessionIntensity.HEAVY) "heavy" else "light"
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
            exerciseRecommendations.clear()

            if (recommendedExercises.isNotEmpty()) {
                // Add exercises without sets - user will add sets manually
                recommendedExercises.forEach { recommendation ->
                    val exerciseId = recommendation.exerciseId
                    val exerciseName = recommendation.exerciseName
                    
                    // Store recommendations for tooltip display
                    exerciseRecommendations[exerciseId] = recommendation
                    
                    // Set workout type
                    exerciseWorkoutTypes[exerciseId] = recommendation.workoutType
                    
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

    private fun updateTitle() {
        val displayType = workoutType.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        supportActionBar?.title = "Active Workout ($displayType)"
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
            }
        )
        binding.recyclerViewActiveWorkout.adapter = adapter
        binding.recyclerViewActiveWorkout.layoutManager = LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
        binding.buttonAddExercise.setOnClickListener {
            val alreadyAddedExerciseIds = groupedExercises.map { it.exerciseId }.toIntArray()
            val intent = Intent(this, SelectExerciseActivity::class.java).apply {
                putExtra(SelectExerciseActivity.EXTRA_WORKOUT_TYPE, workoutType)
                putExtra(SelectExerciseActivity.EXTRA_PLAN_ID, appliedPlanId)
                putExtra(SelectExerciseActivity.EXTRA_ALREADY_ADDED_EXERCISE_IDS, alreadyAddedExerciseIds)
            }
            selectExerciseLauncher.launch(intent)
        }

        binding.buttonFinishWorkout.setOnClickListener {
            finishWorkout()
        }

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
            val intent = Intent(this, LogSetActivity::class.java).apply {
                putExtra(LogSetActivity.EXTRA_EXERCISE_ID, exerciseId)
                putExtra(LogSetActivity.EXTRA_EXERCISE_NAME, exerciseName)
                putExtra(LogSetActivity.EXTRA_SET_NUMBER, setNumber)
                putExtra(LogSetActivity.EXTRA_WORKOUT_TYPE, setWorkoutType)
                previousSet?.let {
                    putExtra(LogSetActivity.EXTRA_PREVIOUS_SET_REPS, it.reps)
                }
            }
            logSetLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch LogSetActivity", e)
        }
    }

    private fun updateExercises(loggedSet: ExerciseEntry) {
        currentExerciseEntries.add(loggedSet)
        loggedSet.workoutType?.let { exerciseWorkoutTypes[loggedSet.exerciseId] = it }

        val groupIndex = groupedExercises.indexOfFirst { it.exerciseId == loggedSet.exerciseId }
        if (groupIndex != -1) {
            val oldGroup = groupedExercises[groupIndex]
            val newSets = oldGroup.sets + loggedSet
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
            exerciseRecommendations.remove(exerciseId)
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
        currentExerciseEntries.forEach { entry ->
            entry.workoutType?.let { exerciseWorkoutTypes[entry.exerciseId] = it }
        }

        rebuildGroupedExercisesFromEntries()
        
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
        
        var restSeconds = actualTime ?: let {
            val currentTime = RestTimerService.getRemainingSeconds(this)
            if (currentTime > 0) {
                currentTime
            } else {
                when (workoutType) {
                    "heavy" -> settings.heavyRestSeconds
                    "light" -> settings.lightRestSeconds
                    "custom" -> settings.customRestSeconds
                    else -> settings.customRestSeconds
                }
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
    }
}