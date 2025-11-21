package com.lilfitness.activities

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lilfitness.R
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.lilfitness.databinding.ActivityActiveTrainingBinding
import com.lilfitness.helpers.ActiveWorkoutDraftManager
import com.lilfitness.helpers.DefaultExercisesHelper
import com.lilfitness.helpers.DialogHelper
import com.lilfitness.helpers.JsonHelper
import com.lilfitness.helpers.ProgressionSettingsManager
import com.lilfitness.helpers.WorkoutGenerator
import com.lilfitness.helpers.showWithTransparentWindow
import com.lilfitness.adapters.ActiveExercisesAdapter
import com.lilfitness.models.ActiveWorkoutDraft
import com.lilfitness.models.ExerciseEntry
import com.lilfitness.models.GroupedExercise
import com.lilfitness.models.TrainingSession
import com.lilfitness.services.RestTimerService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ActiveTrainingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActiveTrainingBinding
    private lateinit var jsonHelper: JsonHelper
    private lateinit var draftManager: ActiveWorkoutDraftManager
    private val currentExerciseEntries = mutableListOf<ExerciseEntry>()
    private val groupedExercises = mutableListOf<GroupedExercise>()
    private val exerciseWorkoutTypes = mutableMapOf<Int, String>()
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

    private val logSetLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val data = result.data
                val loggedSet = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    data?.getParcelableExtra(com.lilfitness.activities.LogSetActivity.EXTRA_LOGGED_SET, ExerciseEntry::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    data?.getParcelableExtra(com.lilfitness.activities.LogSetActivity.EXTRA_LOGGED_SET)
                }

                if (loggedSet != null) {
                    updateExercises(loggedSet)
                } else {
                    Log.e(TAG, "Received null set from LogSetActivity")
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
                val exerciseId = data?.getIntExtra(com.lilfitness.activities.SelectExerciseActivity.EXTRA_EXERCISE_ID, -1) ?: -1
                val exerciseName = data?.getStringExtra(com.lilfitness.activities.SelectExerciseActivity.EXTRA_EXERCISE_NAME) ?: ""
                val exerciseType = data?.getStringExtra(com.lilfitness.activities.SelectExerciseActivity.EXTRA_SELECTED_WORKOUT_TYPE) ?: workoutType

                if (exerciseId != -1 && exerciseName.isNotEmpty()) {
                    exerciseWorkoutTypes[exerciseId] = exerciseType
                    val existingGroup = groupedExercises.find { it.exerciseId == exerciseId }
                    if (existingGroup == null) {
                        val newGroup = GroupedExercise(exerciseId, exerciseName, emptyList())
                        groupedExercises.add(newGroup)
                        adapter.notifyItemInserted(groupedExercises.size - 1)
                    }
                    launchLogSetActivity(exerciseId, exerciseName)
                } else {
                    Log.e(TAG, "Invalid exercise data received from SelectExerciseActivity")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing result from SelectExerciseActivity", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActiveTrainingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jsonHelper = JsonHelper(this)
        draftManager = ActiveWorkoutDraftManager(this)
        workoutType = intent.getStringExtra(EXTRA_WORKOUT_TYPE) ?: "heavy"
        val resumeRequested = intent.getBooleanExtra(EXTRA_RESUME_DRAFT, false)
        val shouldAutoGenerate = intent.getBooleanExtra(EXTRA_AUTO_GENERATE, false)
        val isCustomWorkout = workoutType == "custom"
        binding.textActiveTrainingTitle.text = "Active Workout (${workoutType.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }})"

        setupBackgroundAnimation()
        setupRecyclerView()
        setupClickListeners()
        setupBackButtonInterceptor()
        setupTimerUI()
        setupTimerReceiver()
        updateDateDisplay()
        
        // Auto-generate workout if "Continue Plan" was selected (not custom)
        if (!isCustomWorkout && shouldAutoGenerate && !resumeRequested) {
            autoGenerateWorkout()
        }
        
        maybeRestoreDraft(forceResume = resumeRequested || savedInstanceState != null)
    }

    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is Animatable) {
            drawable.start()
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

    private fun setupRecyclerView() {
        adapter = ActiveExercisesAdapter(
            groupedExercises,
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
        binding.recyclerViewActiveExercises.adapter = adapter
        binding.recyclerViewActiveExercises.layoutManager = LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
        binding.buttonAddExerciseToSession.setOnClickListener {
            val alreadyAddedExerciseIds = groupedExercises.map { it.exerciseId }.toIntArray()
            val intent = Intent(this, com.lilfitness.activities.SelectExerciseActivity::class.java).apply {
                putExtra(com.lilfitness.activities.SelectExerciseActivity.EXTRA_WORKOUT_TYPE, workoutType)
                putExtra(com.lilfitness.activities.SelectExerciseActivity.EXTRA_PLAN_ID, appliedPlanId)
                putExtra(com.lilfitness.activities.SelectExerciseActivity.EXTRA_ALREADY_ADDED_EXERCISE_IDS, alreadyAddedExerciseIds)
            }
            selectExerciseLauncher.launch(intent)
        }

        binding.buttonFinishWorkout.setOnClickListener {
            finishWorkout()
        }

        binding.buttonChangeDate.setOnClickListener {
            showDatePickerDialog()
        }

        binding.buttonBack.setOnClickListener {
            handleBackButton()
        }

        binding.buttonApplyPlan.setOnClickListener {
            showPlanSelectionDialog()
        }
        
        // Timer buttons
        binding.buttonTimerStartPause.setOnClickListener {
            handleTimerStartPause()
        }
        
        binding.buttonTimerResetStop.setOnClickListener {
            handleTimerResetStop()
        }
        
        // Timer display click to set custom time
        binding.textTimerDisplay.setOnClickListener {
            showSetTimerDialog()
        }
        
        // Timer +/- 15 buttons
        binding.buttonTimerMinus15.setOnClickListener {
            RestTimerService.removeTime(this, 15)
            // Update display immediately from current time
            val currentTime = RestTimerService.getRemainingSeconds(this)
            updateTimerDisplay(currentTime)
        }
        
        binding.buttonTimerPlus15.setOnClickListener {
            val currentTime = RestTimerService.getRemainingSeconds(this)
            val wasZero = currentTime == 0
            val isRunning = RestTimerService.isTimerRunning(this)
            
            if (wasZero && !isRunning) {
                // If time is 0 and timer is not running, set time to 15 and start
                RestTimerService.setTimerTime(this, 15)
                updateTimerDisplay(15)
                startTimer(useCustomTime = 15)
            } else {
                // Otherwise, just add time (works if timer is running or has remaining time)
                RestTimerService.addTime(this, 15)
                // Update display immediately from current time
                val newTime = RestTimerService.getRemainingSeconds(this)
                updateTimerDisplay(newTime)
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
                    // Stop timer when cancelling workout
                    stopTimerIfRunning()
                    finish()
                }
                .setNegativeButton(getString(R.string.button_continue), null)
                .showWithTransparentWindow()
        } else {
            // Stop timer when cancelling workout
            stopTimerIfRunning()
            finish()
        }
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
            val intent = Intent(this, com.lilfitness.activities.LogSetActivity::class.java).apply {
                putExtra(com.lilfitness.activities.LogSetActivity.EXTRA_EXERCISE_ID, exerciseId)
                putExtra(com.lilfitness.activities.LogSetActivity.EXTRA_EXERCISE_NAME, exerciseName)
                putExtra(com.lilfitness.activities.LogSetActivity.EXTRA_SET_NUMBER, setNumber)
                putExtra(com.lilfitness.activities.LogSetActivity.EXTRA_WORKOUT_TYPE, setWorkoutType)
                previousSet?.let {
                    putExtra(com.lilfitness.activities.LogSetActivity.EXTRA_PREVIOUS_SET_REPS, it.reps)
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
            
            // Start timer with default time from settings
            startTimer()
        }
    }

    private fun deleteExercise(exerciseId: Int) {
        val groupIndex = groupedExercises.indexOfFirst { it.exerciseId == exerciseId }
        if (groupIndex != -1) {
            groupedExercises.removeAt(groupIndex)
            currentExerciseEntries.removeAll { it.exerciseId == exerciseId }
            exerciseWorkoutTypes.remove(exerciseId)
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
        
        // Remove old sets for this exercise
        currentExerciseEntries.removeAll { it.exerciseId == exerciseId }
        
        // Add updated sets
        currentExerciseEntries.addAll(updatedSets)
        
        // Update the grouped exercise
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
        binding.textActiveTrainingDate.text = sessionDateFormat.format(selectedDate.time)
        persistDraftIfHasEntries()
    }

    private fun showPlanSelectionDialog() {
        val trainingData = jsonHelper.readTrainingData()
        val plans = trainingData.workoutPlans

        if (plans.isEmpty()) {
            DialogHelper.createBuilder(this)
                .setTitle(getString(R.string.dialog_title_no_plans_available))
                .setMessage(getString(R.string.dialog_message_no_plans))
                .setPositiveButton(getString(R.string.button_ok), null)
                .show()
            return
        }

        val planNames = plans.map { it.name }.toTypedArray()
        val currentPlanIndex = plans.indexOfFirst { it.id == appliedPlanId }.takeIf { it >= 0 } ?: -1

        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.dialog_title_apply_workout_plan))
            .setSingleChoiceItems(planNames, currentPlanIndex) { dialog, which ->
                val selectedPlan = plans[which]
                appliedPlanId = selectedPlan.id
                appliedPlanName = selectedPlan.name
                dialog.dismiss()
                updatePlanIndicator()
                persistDraftIfHasEntries()
            }
            .setNeutralButton(getString(R.string.button_clear_plan)) { _, _ ->
                appliedPlanId = null
                appliedPlanName = null
                updatePlanIndicator()
                persistDraftIfHasEntries()
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    private fun updatePlanIndicator() {
        // Visual feedback could be added here if needed
        // For now, the button remains visible to show plan can be changed
    }

    private fun finishWorkout() {
        try {
            val trainingData = jsonHelper.readTrainingData()
            val nextTrainingNumber = (trainingData.trainings.maxOfOrNull { it.trainingNumber } ?: 0) + 1

            val newSession = TrainingSession(
                trainingNumber = nextTrainingNumber,
                date = binding.textActiveTrainingDate.text.toString(),
                exercises = currentExerciseEntries.toMutableList(),
                defaultWorkoutType = workoutType,
                planId = appliedPlanId,
                planName = appliedPlanName
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
        binding.textActiveTrainingTitle.text = "Active Workout (${workoutType.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }})"

        try {
            val parsedDate = sessionDateFormat.parse(draft.date)
            if (parsedDate != null) {
                selectedDate.time = parsedDate
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse draft date", e)
        }
        binding.textActiveTrainingDate.text = draft.date

        appliedPlanId = draft.appliedPlanId
        appliedPlanName = draft.appliedPlanName

        currentExerciseEntries.clear()
        currentExerciseEntries.addAll(draft.entries.map { it.copy() })

        exerciseWorkoutTypes.clear()
        currentExerciseEntries.forEach { entry ->
            entry.workoutType?.let { exerciseWorkoutTypes[entry.exerciseId] = it }
        }

        rebuildGroupedExercisesFromEntries()
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

    private fun autoGenerateWorkout() {
        try {
            val trainingData = jsonHelper.readTrainingData()
            val settingsManager = ProgressionSettingsManager(this)
            val settings = settingsManager.getSettings()
            
            // Get default exercises from DefaultExercisesHelper
            val defaultExercises = DefaultExercisesHelper.getPopularDefaults()
            
            // Generate the workout - returns Pair(selected exercises, default exercises that were selected)
            val (generatedExercises, defaultExercisesUsed) = WorkoutGenerator.generateFullWorkout(
                userLevel = settings.userLevel,
                sessionType = workoutType,
                userExercises = trainingData.exerciseLibrary,
                defaultExercises = defaultExercises,
                settings = settings
            )
            
            if (generatedExercises.isNotEmpty()) {
                // Add default exercises to user's library if they were selected
                if (defaultExercisesUsed.isNotEmpty()) {
                    val updatedTrainingData = jsonHelper.readTrainingData()
                    val existingIds = updatedTrainingData.exerciseLibrary.map { it.id }.toSet()
                    val existingNames = updatedTrainingData.exerciseLibrary.map { it.name.lowercase() }.toSet()
                    
                    // Only add defaults that aren't already in library (by ID or name)
                    defaultExercisesUsed.forEach { defaultExercise ->
                        if (!existingIds.contains(defaultExercise.id) && 
                            !existingNames.contains(defaultExercise.name.lowercase())) {
                            updatedTrainingData.exerciseLibrary.add(defaultExercise)
                        }
                    }
                    
                    // Save updated library
                    jsonHelper.writeTrainingData(updatedTrainingData)
                }
                
                // Add exercises as GroupedExercise objects with empty sets
                generatedExercises.forEach { exercise ->
                    // Set workout type for this exercise
                    exerciseWorkoutTypes[exercise.id] = workoutType
                    
                    // Create a grouped exercise with no sets
                    val groupedExercise = GroupedExercise(
                        exerciseId = exercise.id,
                        exerciseName = exercise.name,
                        sets = emptyList()
                    )
                    
                    // Only add if not already present
                    if (groupedExercises.none { it.exerciseId == exercise.id }) {
                        groupedExercises.add(groupedExercise)
                    }
                }
                
                // Notify adapter of changes
                adapter.notifyDataSetChanged()
                
                // Persist the draft
                persistDraft()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error auto-generating workout", e)
        }
    }

    private fun persistDraft() {
        if (currentExerciseEntries.isEmpty()) {
            draftManager.clearDraft()
            return
        }

        val entriesCopy = currentExerciseEntries.map { it.copy() }

        val draft = ActiveWorkoutDraft(
            workoutType = workoutType,
            date = binding.textActiveTrainingDate.text.toString(),
            appliedPlanId = appliedPlanId,
            appliedPlanName = appliedPlanName,
            entries = entriesCopy
        )

        draftManager.saveDraft(draft)
    }

    private fun persistDraftIfHasEntries() {
        if (currentExerciseEntries.isNotEmpty()) {
            persistDraft()
        }
    }
    
    // Timer setup and management
    private fun setupTimerUI() {
        updateTimerDisplay(0)
        setTimerState(TimerState.IDLE)
    }
    
    private fun setupTimerReceiver() {
        timerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "com.lilfitness.REST_TIMER_TICK" -> {
                        val remaining = intent.getIntExtra("remaining", 0)
                        updateTimerDisplay(remaining)
                        // Ensure state is RUNNING when receiving ticks
                        if (remaining > 0) {
                            setTimerState(TimerState.RUNNING)
                        }
                    }
                    "com.lilfitness.REST_TIMER_COMPLETE" -> {
                        // Timer completed
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
        
        // Register timer receiver
        val filter = IntentFilter().apply {
            addAction("com.lilfitness.REST_TIMER_TICK")
            addAction("com.lilfitness.REST_TIMER_COMPLETE")
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(timerReceiver, filter)
        }
        
        // Sync timer state
        syncTimerState()
    }
    
    override fun onPause() {
        super.onPause()
        isActivityVisible = false
        
        // Unregister receiver
        timerReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering timer receiver", e)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Stop timer when activity is destroyed (workout cancelled or finished)
        stopTimerIfRunning()
        
        // Unregister receiver if still registered
        timerReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering timer receiver in onDestroy", e)
            }
        }
    }
    
    private fun syncTimerState() {
        val isRunning = RestTimerService.isTimerRunning(this)
        val remaining = RestTimerService.getRemainingSeconds(this)
        
        if (isRunning) {
            updateTimerDisplay(remaining)
            setTimerState(TimerState.RUNNING)
        } else if (remaining > 0) {
            // Timer was stopped but has remaining time
            updateTimerDisplay(remaining)
            setTimerState(TimerState.IDLE)
        } else {
            updateTimerDisplay(0)
            setTimerState(TimerState.IDLE)
        }
    }
    
    private fun handleTimerStartPause() {
        val isRunning = RestTimerService.isTimerRunning(this)
        
        if (isRunning) {
            // Stop the timer
            RestTimerService.stopTimer(this)
            val remaining = RestTimerService.getRemainingSeconds(this)
            updateTimerDisplay(remaining)
            setTimerState(TimerState.IDLE)
        } else {
            // Start the timer
            startTimer()
        }
    }
    
    private fun handleTimerResetStop() {
        val isRunning = RestTimerService.isTimerRunning(this)
        
        if (isRunning) {
            // Stop the timer
            RestTimerService.stopTimer(this)
        }
        
        // Reset to idle
        updateTimerDisplay(0)
        setTimerState(TimerState.IDLE)
    }
    
    private fun stopTimerIfRunning() {
        if (RestTimerService.isTimerRunning(this)) {
            RestTimerService.stopTimer(this)
        }
    }
    
    private fun startTimer(useCustomTime: Int? = null) {
        val settings = ProgressionSettingsManager(this).getSettings()
        
        if (!settings.restTimerEnabled) {
            Toast.makeText(this, getString(R.string.toast_rest_timer_disabled), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Request permission
                Toast.makeText(this, getString(R.string.toast_notification_permission_required), Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        // Use custom time if provided, otherwise check if there's already a time set, otherwise use default
        var restSeconds = useCustomTime ?: let {
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
        
        // Start the timer service with "Rest" as exercise name, don't show dialog (we have permanent UI)
        RestTimerService.startTimer(this, restSeconds, "Rest", showDialog = false)
        
        setTimerState(TimerState.RUNNING)
    }
    
    private fun updateTimerDisplay(seconds: Int) {
        val minutes = seconds / 60
        val secs = seconds % 60
        val timeText = String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
        binding.textTimerDisplay.text = timeText
    }
    
    private fun setTimerState(state: TimerState) {
        when (state) {
            TimerState.IDLE -> {
                // Show play icon
                binding.buttonTimerStartPause.setImageResource(com.lilfitness.R.drawable.ic_play)
                binding.buttonTimerStartPause.contentDescription = "Start timer"
                binding.buttonTimerStartPause.isEnabled = true
                binding.buttonTimerResetStop.isEnabled = true
            }
            TimerState.RUNNING -> {
                // Show pause icon
                binding.buttonTimerStartPause.setImageResource(com.lilfitness.R.drawable.ic_pause)
                binding.buttonTimerStartPause.contentDescription = "Pause timer"
                binding.buttonTimerStartPause.isEnabled = true
                binding.buttonTimerResetStop.isEnabled = true
            }
            TimerState.COMPLETED -> {
                // Show play icon
                binding.buttonTimerStartPause.setImageResource(com.lilfitness.R.drawable.ic_play)
                binding.buttonTimerStartPause.contentDescription = "Start timer"
                binding.buttonTimerStartPause.isEnabled = true
                binding.buttonTimerResetStop.isEnabled = true
            }
        }
    }
    
    private fun showSetTimerDialog() {
        // Pause timer if running
        val wasRunning = RestTimerService.isTimerRunning(this)
        if (wasRunning) {
            RestTimerService.stopTimer(this)
            setTimerState(TimerState.IDLE)
        }
        
        // Get current remaining time
        val currentSeconds = RestTimerService.getRemainingSeconds(this)
        val currentMinutes = currentSeconds / 60
        val currentSecs = currentSeconds % 60
        
        // Inflate dialog layout
        val dialogBinding = com.lilfitness.databinding.DialogSetTimerBinding.inflate(layoutInflater)
        
        // Set up NumberPickers
        dialogBinding.numberPickerMinutes.minValue = 0
        dialogBinding.numberPickerMinutes.maxValue = 59
        dialogBinding.numberPickerMinutes.value = currentMinutes
        dialogBinding.numberPickerMinutes.wrapSelectorWheel = false
        
        dialogBinding.numberPickerSeconds.minValue = 0
        dialogBinding.numberPickerSeconds.maxValue = 59
        dialogBinding.numberPickerSeconds.value = currentSecs
        dialogBinding.numberPickerSeconds.wrapSelectorWheel = false
        
        // Style NumberPicker text colors
        styleNumberPicker(dialogBinding.numberPickerMinutes)
        styleNumberPicker(dialogBinding.numberPickerSeconds)
        
        // Create dialog
        val dialog = DialogHelper.createBuilder(this)
            .setView(dialogBinding.root)
            .create()
        
        // Style the dialog to match design guidelines
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Set button listeners
        dialogBinding.buttonCancel.setOnClickListener {
            // Resume timer if it was running
            if (wasRunning && currentSeconds > 0) {
                startTimer()
            }
            dialog.dismiss()
        }
        
        dialogBinding.buttonSet.setOnClickListener {
            val minutes = dialogBinding.numberPickerMinutes.value
            val seconds = dialogBinding.numberPickerSeconds.value
            val totalSeconds = (minutes * 60) + seconds
            
            // Set the new time and start timer automatically with the custom time
            if (totalSeconds > 0) {
                RestTimerService.setTimerTime(this, totalSeconds)
                updateTimerDisplay(totalSeconds)
                // Start timer with the custom time directly
                startTimer(useCustomTime = totalSeconds)
            } else {
                RestTimerService.setTimerTime(this, 0)
                updateTimerDisplay(0)
                setTimerState(TimerState.IDLE)
            }
            
            dialog.dismiss()
        }
        
        // Window background already set to transparent above, but use extension for consistency
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }
    
    private fun styleNumberPicker(numberPicker: android.widget.NumberPicker) {
        try {
            val count = numberPicker.childCount
            for (i in 0 until count) {
                val child = numberPicker.getChildAt(i)
                if (child is android.widget.TextView) {
                    child.setTextColor(ContextCompat.getColor(this, com.lilfitness.R.color.fitness_text_primary))
                    child.textSize = 18f
                }
            }
        } catch (e: Exception) {
            // If styling fails, just continue with default styling
            Log.e(TAG, "Error styling NumberPicker", e)
        }
    }
    
    
    private enum class TimerState {
        IDLE,
        RUNNING,
        COMPLETED
    }

    companion object {
        const val EXTRA_WORKOUT_TYPE = "WORKOUT_TYPE"
        const val EXTRA_RESUME_DRAFT = "RESUME_DRAFT"
        const val EXTRA_AUTO_GENERATE = "AUTO_GENERATE"
    }
}