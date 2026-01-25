package com.liftpath.activities

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.liftpath.databinding.ActivityTrainingDetailBinding
import com.liftpath.helpers.JsonHelper
import com.liftpath.adapters.TrainingDetailAdapter
import com.liftpath.models.ExerciseEntry
import com.liftpath.models.GroupedExercise
import com.liftpath.models.TrainingSession
import com.liftpath.models.SetIntent
import com.liftpath.utils.WorkoutTypeFormatter
import com.liftpath.helpers.DurationHelper
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.showWithTransparentWindow
import com.liftpath.R
import android.widget.EditText
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.liftpath.models.WorkoutPlan

class TrainingDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrainingDetailBinding
    private lateinit var jsonHelper: JsonHelper
    private lateinit var trainingSession: TrainingSession

    private var currentEditingEntry: ExerciseEntry? = null

    private val editSetLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val updatedEntry = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.data?.getParcelableExtra(EditSetActivity.EXTRA_EXERCISE_ENTRY, ExerciseEntry::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    result.data?.getParcelableExtra(EditSetActivity.EXTRA_EXERCISE_ENTRY)
                }

                if (updatedEntry != null) {
                    updateTrainingSession(updatedEntry)
                }
            }
            EditSetActivity.RESULT_DELETE -> {
                // Delete the set
                currentEditingEntry?.let { entryToDelete ->
                    deleteSet(entryToDelete)
                }
            }
        }
        currentEditingEntry = null
    }

    private val editActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Reload the training session and refresh the view
            val trainingData = jsonHelper.readTrainingData()
            val updatedSession = trainingData.trainings.find { it.id == trainingSession.id }
            if (updatedSession != null) {
                trainingSession = updatedSession
                setupRecyclerView()
            }
        }
    }

    private val selectExerciseLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val exerciseId = result.data?.getIntExtra(SelectExerciseActivity.EXTRA_EXERCISE_ID, -1) ?: -1
            val exerciseName = result.data?.getStringExtra(SelectExerciseActivity.EXTRA_EXERCISE_NAME) ?: ""

            if (exerciseId != -1 && exerciseName.isNotEmpty()) {
                // Launch LogSetActivity to add the first set for this new exercise (default to BUILD intent)
                launchLogSetActivity(exerciseId, exerciseName, SetIntent.BUILD, isNewExercise = true)
            }
        }
    }

    private val logSetLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val loggedSet = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(LogSetActivity.EXTRA_LOGGED_SET, ExerciseEntry::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(LogSetActivity.EXTRA_LOGGED_SET)
            }

            if (loggedSet != null) {
                addSetToSession(loggedSet)
            }
        }
    }

    companion object {
        const val EXTRA_TRAINING_SESSION = "extra_training_session"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable Edge-to-Edge
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityTrainingDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply Window Insets
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            
            // This activity doesn't have a toolbar, so we apply top padding to the main content
            // inside the NestedScrollView to avoid overlap with the status bar.
            binding.scrollContent.setPadding(
                binding.scrollContent.paddingLeft,
                insets.top + (24 * resources.displayMetrics.density).toInt(),
                binding.scrollContent.paddingRight,
                binding.scrollContent.paddingBottom
            )
            
            // Apply bottom padding to the NestedScrollView to avoid overlap with the gesture bar
            binding.scrollView.setPadding(
                binding.scrollView.paddingLeft,
                binding.scrollView.paddingTop,
                binding.scrollView.paddingRight,
                insets.bottom
            )
            
            windowInsets
        }

        jsonHelper = JsonHelper(this)

        val session = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_TRAINING_SESSION, TrainingSession::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_TRAINING_SESSION)
        }

        if (session != null) {
            trainingSession = session
            title = "Training #${trainingSession.trainingNumber} - ${trainingSession.date}"
            setupDominantIntentBadge()
            setupDurationDisplay()
            setupRecyclerView()
            setupClickListeners()
        } else {
            title = "Training Details"
        }
    }

    private fun setupClickListeners() {
        binding.buttonBack.setOnClickListener {
            onBackPressed()
        }

        binding.buttonDelete.setOnClickListener {
            DialogHelper.createBuilder(this)
                .setTitle("Delete Training")
                .setMessage("Are you sure you want to delete this training permanently?")
                .setPositiveButton("Delete") { _, _ ->
                    val trainingData = jsonHelper.readTrainingData()
                    val updatedTrainings = trainingData.trainings.toMutableList()
                    updatedTrainings.remove(trainingSession)
                    jsonHelper.writeTrainingData(trainingData.copy(trainings = updatedTrainings))
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .showWithTransparentWindow()
        }

        binding.buttonEditDuration.setOnClickListener {
            showEditDurationDialog()
        }

        binding.buttonAddExercise.setOnClickListener {
            launchSelectExerciseActivity()
        }

        binding.buttonSaveAsPlan.setOnClickListener {
            showSaveAsPlanDialog()
        }
    }

    private fun setupRecyclerView() {
        val groupedExercises = trainingSession.exercises
            .groupBy { it.exerciseId }
            .map { (exerciseId, sets) ->
                val sortedSets = sets.sortedBy { it.setNumber }
                GroupedExercise(exerciseId, sortedSets.first().exerciseName, sortedSets)
            }
            .sortedBy { it.exerciseName }

        binding.recyclerViewTrainingDetail.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewTrainingDetail.adapter = TrainingDetailAdapter(
            groupedExercises,
            trainingSession.defaultWorkoutType,
            onEditSetClicked = {
                currentEditingEntry = it
                val intent = Intent(this, EditSetActivity::class.java).apply {
                    putExtra(EditSetActivity.EXTRA_EXERCISE_ENTRY, it)
                    putExtra(EditSetActivity.EXTRA_IS_EDIT_MODE, true)
                }
                editSetLauncher.launch(intent)
            },
            onEditActivityClicked = { groupedExercise ->
                val intent = Intent(this, com.liftpath.activities.EditActivityActivity::class.java).apply {
                    putExtra(com.liftpath.activities.EditActivityActivity.EXTRA_TRAINING_SESSION_ID, trainingSession.id)
                    putExtra(com.liftpath.activities.EditActivityActivity.EXTRA_EXERCISE_ID, groupedExercise.exerciseId)
                    putExtra(com.liftpath.activities.EditActivityActivity.EXTRA_EXERCISE_NAME, groupedExercise.exerciseName)
                }
                editActivityLauncher.launch(intent)
            },
            onAddSetClicked = { groupedExercise ->
                // Get intent from last set or default to BUILD
                val lastSet = groupedExercise.sets.lastOrNull()
                val exerciseIntent = lastSet?.explicitIntent ?: SetIntent.BUILD
                launchLogSetActivity(groupedExercise.exerciseId, groupedExercise.exerciseName, exerciseIntent, isNewExercise = false)
            }
        )
    }

    private fun updateTrainingSession(updatedEntry: ExerciseEntry) {
        val exerciseIndex = trainingSession.exercises.indexOfFirst { it.setNumber == updatedEntry.setNumber && it.exerciseId == updatedEntry.exerciseId }
        if (exerciseIndex != -1) {
            trainingSession.exercises[exerciseIndex] = updatedEntry
            persistTrainingSession()
            setupRecyclerView()
        }
    }

    private fun deleteSet(entryToDelete: ExerciseEntry) {
        val exerciseIndex = trainingSession.exercises.indexOfFirst { 
            it.setNumber == entryToDelete.setNumber && it.exerciseId == entryToDelete.exerciseId 
        }
        if (exerciseIndex != -1) {
            trainingSession.exercises.removeAt(exerciseIndex)
            persistTrainingSession()
            setupRecyclerView()
            Toast.makeText(this, "Set deleted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupDominantIntentBadge() {
        val dominantIntent = trainingSession.getDominantIntent()
        val isLegacy = trainingSession.isLegacySession()
        
        binding.textDominantIntent.text = if (isLegacy) {
            "${dominantIntent.displayName} (Legacy)"
        } else {
            dominantIntent.displayName
        }
        binding.textDominantIntent.visibility = View.VISIBLE
    }

    private fun persistTrainingSession() {
        val trainingData = jsonHelper.readTrainingData()
        val sessionIndex = trainingData.trainings.indexOfFirst { it.id == trainingSession.id }
        if (sessionIndex != -1) {
            trainingData.trainings[sessionIndex] = trainingSession
            jsonHelper.writeTrainingData(trainingData)
        }
    }

    private fun setupDurationDisplay() {
        trainingSession.durationSeconds?.let { seconds ->
            binding.textWorkoutDuration.text = DurationHelper.formatDuration(seconds)
        } ?: run {
            binding.textWorkoutDuration.text = "Not recorded"
        }
    }

    private fun showEditDurationDialog() {
        val currentDuration = trainingSession.durationSeconds ?: 0L
        val currentFormatted = DurationHelper.formatDuration(currentDuration)
        
        val input = EditText(this)
        input.setText(currentFormatted)
        input.hint = "HH:mm:ss or mm:ss"
        input.setTextColor(ContextCompat.getColor(this, R.color.fitness_text_primary))
        input.setHintTextColor(ContextCompat.getColor(this, R.color.fitness_text_secondary))
        input.setTextSize(16f)
        input.setPadding(16, 16, 16, 16)
        
        DialogHelper.createBuilder(this)
            .setTitle("Edit Workout Duration")
            .setMessage("Enter duration in format HH:mm:ss (e.g., 01:30:00) or mm:ss (e.g., 90:00)")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val inputText = input.text.toString().trim()
                val parsedSeconds = DurationHelper.parseDurationToSeconds(inputText)
                
                if (parsedSeconds != null && parsedSeconds >= 0) {
                    trainingSession = trainingSession.copy(durationSeconds = parsedSeconds)
                    persistTrainingSession()
                    setupDurationDisplay()
                    Toast.makeText(this, "Duration updated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Invalid duration format. Use HH:mm:ss or mm:ss", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .showWithTransparentWindow()
    }

    private fun launchSelectExerciseActivity() {
        val alreadyAddedExerciseIds = trainingSession.exercises.map { it.exerciseId }.distinct().toIntArray()
        val intent = Intent(this, SelectExerciseActivity::class.java).apply {
            putExtra(SelectExerciseActivity.EXTRA_WORKOUT_TYPE, trainingSession.defaultWorkoutType ?: "heavy")
            putExtra(SelectExerciseActivity.EXTRA_ALREADY_ADDED_EXERCISE_IDS, alreadyAddedExerciseIds)
        }
        selectExerciseLauncher.launch(intent)
    }

    private fun launchLogSetActivity(exerciseId: Int, exerciseName: String, exerciseIntent: SetIntent, isNewExercise: Boolean) {
        val setNumber = if (isNewExercise) {
            1
        } else {
            val maxSetNumber = trainingSession.exercises
                .filter { it.exerciseId == exerciseId }
                .maxByOrNull { it.setNumber }?.setNumber ?: 0
            maxSetNumber + 1
        }

        // Get last logged values for this exercise from training history
        val trainingData = jsonHelper.readTrainingData()
        val lastEntry = trainingData.trainings
            .flatMap { it.exercises }
            .filter { it.exerciseId == exerciseId }
            .lastOrNull()

        val intent = Intent(this, LogSetActivity::class.java).apply {
            putExtra(LogSetActivity.EXTRA_EXERCISE_ID, exerciseId)
            putExtra(LogSetActivity.EXTRA_EXERCISE_NAME, exerciseName)
            putExtra(LogSetActivity.EXTRA_SET_NUMBER, setNumber)
            putExtra(LogSetActivity.EXTRA_WORKOUT_TYPE, trainingSession.defaultWorkoutType ?: "custom")  // Keep for legacy compatibility
            putExtra(LogSetActivity.EXTRA_INTENT, exerciseIntent.name)
            
            // Pass last logged values if available
            lastEntry?.let {
                putExtra(LogSetActivity.EXTRA_LAST_LOGGED_KG, it.kg)
                putExtra(LogSetActivity.EXTRA_LAST_LOGGED_REPS, it.reps)
            }
        }
        logSetLauncher.launch(intent)
    }

    private fun addSetToSession(loggedSet: ExerciseEntry) {
        trainingSession.exercises.add(loggedSet)
        persistTrainingSession()
        setupRecyclerView()
        Toast.makeText(this, "Set added to workout", Toast.LENGTH_SHORT).show()
    }

    private fun showSaveAsPlanDialog() {
        val input = EditText(this)
        input.hint = "Enter plan name"
        input.setTextColor(ContextCompat.getColor(this, R.color.fitness_text_primary))
        input.setHintTextColor(ContextCompat.getColor(this, R.color.fitness_text_secondary))
        input.setTextSize(16f)
        input.setPadding(16, 16, 16, 16)

        DialogHelper.createBuilder(this)
            .setTitle("Save as Plan")
            .setMessage("Enter a name for this workout plan")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val planName = input.text.toString().trim()
                if (planName.isEmpty()) {
                    Toast.makeText(this, "Please enter a plan name", Toast.LENGTH_SHORT).show()
                } else {
                    saveWorkoutAsPlan(planName)
                }
            }
            .setNegativeButton("Cancel", null)
            .showWithTransparentWindow()
    }

    private fun saveWorkoutAsPlan(planName: String) {
        // Check if workout has exercises
        if (trainingSession.exercises.isEmpty()) {
            Toast.makeText(this, "Cannot save plan: workout has no exercises", Toast.LENGTH_SHORT).show()
            return
        }

        // Extract unique exercise IDs in order (preserve order of first appearance)
        val exerciseIds = trainingSession.exercises
            .distinctBy { it.exerciseId }
            .map { it.exerciseId }
            .toMutableList()

        // Get workout type from session
        val workoutType = trainingSession.defaultWorkoutType ?: "heavy"

        // Create WorkoutPlan
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val newPlan = WorkoutPlan(
            id = UUID.randomUUID().toString(),
            name = planName,
            exerciseIds = exerciseIds,
            workoutType = workoutType,
            notes = null,
            createdDate = dateFormat.format(Date())
        )

        // Read current training data
        val trainingData = jsonHelper.readTrainingData()

        // Add new plan
        trainingData.workoutPlans.add(newPlan)

        // Save to disk
        jsonHelper.writeTrainingData(trainingData)

        // Show success message
        Toast.makeText(this, "Plan saved successfully", Toast.LENGTH_SHORT).show()
    }
}