package com.liftpath.activities

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.liftpath.R
import com.liftpath.databinding.ActivityEditWorkoutPlanBinding
import com.liftpath.helpers.JsonHelper
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.WorkoutPlan
import com.liftpath.models.PlanExerciseConfig
import com.liftpath.models.SetIntent
import com.liftpath.adapters.PlanExerciseAdapter
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.showWithTransparentWindow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditWorkoutPlanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditWorkoutPlanBinding
    private lateinit var jsonHelper: JsonHelper
    private lateinit var adapter: PlanExerciseAdapter
    private var selectedExercises: MutableList<ExerciseLibraryItem> = mutableListOf()
    private var planId: String? = null
    private var isEditing = false
    private var exerciseIntents: MutableMap<Int, SetIntent> = mutableMapOf()  // exerciseId -> intent

    companion object {
        const val EXTRA_PLAN_ID = "extra_plan_id"
    }

    private val selectExercisesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedIds = result.data?.getIntegerArrayListExtra(SelectExercisesForPlanActivity.EXTRA_SELECTED_EXERCISE_IDS)
            if (selectedIds != null) {
                updateSelectedExercises(selectedIds)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditWorkoutPlanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jsonHelper = JsonHelper(this)
        planId = intent.getStringExtra(EXTRA_PLAN_ID)
        isEditing = planId != null

        setupBackgroundAnimation()
        updateHeaderTitle()
        setupRecyclerView()
        setupClickListeners()
        loadPlanIfEditing()
    }

    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is Animatable) {
            drawable.start()
        }
    }

    private fun updateHeaderTitle() {
        if (isEditing) {
            binding.textHeaderTitle.text = "Edit Workout Plan"
        } else {
            binding.textHeaderTitle.text = "Create Workout Plan"
        }
    }

    private fun setupRecyclerView() {
        adapter = PlanExerciseAdapter(
            exercises = selectedExercises,
            exerciseIntents = exerciseIntents,
            onRemoveClicked = { position ->
                val exerciseId = selectedExercises[position].id
                selectedExercises.removeAt(position)
                exerciseIntents.remove(exerciseId)
                adapter.notifyItemRemoved(position)
                adapter.notifyItemRangeChanged(position, selectedExercises.size)
            },
            onExerciseClicked = { position ->
                showIntentDialog(selectedExercises[position])
            }
        )
        binding.recyclerViewPlanExercises.adapter = adapter
        binding.recyclerViewPlanExercises.layoutManager = LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
        binding.buttonBack.setOnClickListener {
            finish()
        }

        binding.buttonAddExercises.setOnClickListener {
            val preselectedIds = selectedExercises.map { it.id }.toIntArray()
            val intent = Intent(this, SelectExercisesForPlanActivity::class.java).apply {
                putExtra(SelectExercisesForPlanActivity.EXTRA_PRESELECTED_IDS, preselectedIds)
            }
            selectExercisesLauncher.launch(intent)
        }

        binding.buttonSavePlan.setOnClickListener {
            savePlan()
        }
    }

    private fun loadPlanIfEditing() {
        if (isEditing && planId != null) {
            val trainingData = jsonHelper.readTrainingData()
            val plan = trainingData.workoutPlans.find { it.id == planId }
            if (plan != null) {
                binding.editTextPlanName.setText(plan.name)
                
                // Load exercises
                val exerciseMap = trainingData.exerciseLibrary.associateBy { it.id }
                selectedExercises.clear()
                exerciseIntents.clear()
                plan.exerciseIds.forEach { id ->
                    exerciseMap[id]?.let { exercise ->
                        selectedExercises.add(exercise)
                        // Load intent from plan config if available (handle legacy plans without exerciseConfigs)
                        val config = plan.exerciseConfigs?.find { it.exerciseId == id }
                        exerciseIntents[id] = config?.defaultIntent ?: SetIntent.BUILD
                    }
                }
                adapter.notifyDataSetChanged()
                
                // Load notes
                plan.notes?.let { binding.editTextNotes.setText(it) }
            }
        }
    }

    private fun updateSelectedExercises(selectedIds: List<Int>) {
        val trainingData = jsonHelper.readTrainingData()
        val exerciseMap = trainingData.exerciseLibrary.associateBy { it.id }
        
        // Keep existing order, then add new ones
        val existingIds = selectedExercises.map { it.id }.toSet()
        val newIds = selectedIds.filter { it !in existingIds }
        
        // Add new exercises in the order they appear in selectedIds
        selectedIds.forEach { id ->
            if (id !in existingIds) {
                exerciseMap[id]?.let { 
                    selectedExercises.add(it)
                    // Initialize intent to BUILD for new exercises
                    exerciseIntents[id] = SetIntent.BUILD
                }
            }
        }
        
        // Remove exercises that are no longer selected
        selectedExercises.removeAll { it.id !in selectedIds }
        
        adapter.notifyDataSetChanged()
    }

    private fun savePlan() {
        val planName = binding.editTextPlanName.text.toString().trim()
        
        if (planName.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_please_enter_plan_name), Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selectedExercises.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_please_add_exercise), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Keep workoutType for legacy compatibility (default to "custom")
        val workoutType = "custom"
        
        val notes = binding.editTextNotes.text.toString().trim().takeIf { it.isNotEmpty() }
        val exerciseIds = selectedExercises.map { it.id }.toMutableList()
        
        // Build exercise configs from intents
        val exerciseConfigs = exerciseIds.mapNotNull { exerciseId ->
            exerciseIntents[exerciseId]?.let { intent ->
                PlanExerciseConfig(exerciseId = exerciseId, defaultIntent = intent)
            }
        }
        
        val trainingData = jsonHelper.readTrainingData()
        
        if (isEditing && planId != null) {
            // Update existing plan
            val planIndex = trainingData.workoutPlans.indexOfFirst { it.id == planId }
            if (planIndex != -1) {
                val existingPlan = trainingData.workoutPlans[planIndex]
                val updatedPlan = existingPlan.copy(
                    name = planName,
                    exerciseIds = exerciseIds,
                    workoutType = existingPlan.workoutType,  // Keep existing for legacy
                    exerciseConfigs = exerciseConfigs,
                    notes = notes
                )
                trainingData.workoutPlans[planIndex] = updatedPlan
            }
        } else {
            // Create new plan
            val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
            val newPlan = WorkoutPlan(
                name = planName,
                exerciseIds = exerciseIds,
                workoutType = workoutType,
                exerciseConfigs = exerciseConfigs,
                notes = notes,
                createdDate = dateFormat.format(Date())
            )
            trainingData.workoutPlans.add(newPlan)
        }
        
        jsonHelper.writeTrainingData(trainingData)
        setResult(Activity.RESULT_OK)
        finish()
    }
    
    private fun showIntentDialog(exercise: ExerciseLibraryItem) {
        val currentIntent = exerciseIntents[exercise.id] ?: SetIntent.BUILD
        val intentOptions = arrayOf("Strength", "Build", "Flush", "None")
        val currentIndex = when (currentIntent) {
            SetIntent.STRENGTH -> 0
            SetIntent.BUILD -> 1
            SetIntent.FLUSH -> 2
            else -> 3
        }
        
        DialogHelper.createBuilder(this)
            .setTitle("Set Intent for ${exercise.name}")
            .setSingleChoiceItems(intentOptions, currentIndex) { dialog, which ->
                val selectedIntent = when (which) {
                    0 -> SetIntent.STRENGTH
                    1 -> SetIntent.BUILD
                    2 -> SetIntent.FLUSH
                    else -> null
                }
                if (selectedIntent != null) {
                    exerciseIntents[exercise.id] = selectedIntent
                } else {
                    exerciseIntents.remove(exercise.id)
                }
                adapter.notifyDataSetChanged()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .showWithTransparentWindow()
    }
}

