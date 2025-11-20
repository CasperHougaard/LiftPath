package com.lilfitness.activities

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.lilfitness.databinding.ActivityActiveTrainingBinding
import com.lilfitness.helpers.ActiveWorkoutDraftManager
import com.lilfitness.helpers.JsonHelper
import com.lilfitness.adapters.ActiveExercisesAdapter
import com.lilfitness.models.ActiveWorkoutDraft
import com.lilfitness.models.ExerciseEntry
import com.lilfitness.models.GroupedExercise
import com.lilfitness.models.TrainingSession
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
        binding.textActiveTrainingTitle.text = "Active Workout (${workoutType.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }})"

        setupRecyclerView()
        setupClickListeners()
        setupBackButtonInterceptor()
        updateDateDisplay()
        maybeRestoreDraft(forceResume = resumeRequested || savedInstanceState != null)
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
            AlertDialog.Builder(this)
                .setTitle("Cancel Workout")
                .setMessage("Are you sure you want to cancel this workout? Your progress will remain saved as a draft and can be resumed later.")
                .setPositiveButton("Cancel Workout") { _, _ ->
                    finish()
                }
                .setNegativeButton("Continue", null)
                .show()
        } else {
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
            AlertDialog.Builder(this)
                .setTitle("No Plans Available")
                .setMessage("You don't have any workout plans yet. Create one from the Plans screen.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val planNames = plans.map { it.name }.toTypedArray()
        val currentPlanIndex = plans.indexOfFirst { it.id == appliedPlanId }.takeIf { it >= 0 } ?: -1

        AlertDialog.Builder(this)
            .setTitle("Apply Workout Plan")
            .setSingleChoiceItems(planNames, currentPlanIndex) { dialog, which ->
                val selectedPlan = plans[which]
                appliedPlanId = selectedPlan.id
                appliedPlanName = selectedPlan.name
                dialog.dismiss()
                updatePlanIndicator()
                persistDraftIfHasEntries()
            }
            .setNeutralButton("Clear Plan") { _, _ ->
                appliedPlanId = null
                appliedPlanName = null
                updatePlanIndicator()
                persistDraftIfHasEntries()
            }
            .setNegativeButton("Cancel", null)
            .show()
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

        AlertDialog.Builder(this)
            .setTitle("Resume unfinished workout?")
            .setMessage("You have an unfinished ${draft.workoutType} workout from ${draft.date}. Resume or discard it?")
            .setPositiveButton("Resume") { _, _ ->
                applyDraft(draft)
            }
            .setNegativeButton("Discard") { _, _ ->
                draftManager.clearDraft()
            }
            .setNeutralButton("Cancel") { _, _ ->
                finish()
            }
            .show()
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

    companion object {
        const val EXTRA_WORKOUT_TYPE = "WORKOUT_TYPE"
        const val EXTRA_RESUME_DRAFT = "RESUME_DRAFT"
    }
}