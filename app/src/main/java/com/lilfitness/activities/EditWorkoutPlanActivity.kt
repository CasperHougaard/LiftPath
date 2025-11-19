package com.lilfitness.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.lilfitness.R
import com.lilfitness.databinding.ActivityEditWorkoutPlanBinding
import com.lilfitness.helpers.JsonHelper
import com.lilfitness.models.ExerciseLibraryItem
import com.lilfitness.models.WorkoutPlan
import com.lilfitness.adapters.PlanExerciseAdapter
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

        setupRecyclerView()
        setupClickListeners()
        loadPlanIfEditing()
    }

    private fun setupRecyclerView() {
        adapter = PlanExerciseAdapter(
            exercises = selectedExercises,
            onRemoveClicked = { position ->
                selectedExercises.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.notifyItemRangeChanged(position, selectedExercises.size)
            }
        )
        binding.recyclerViewPlanExercises.adapter = adapter
        binding.recyclerViewPlanExercises.layoutManager = LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
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
                
                // Set workout type radio button
                when (plan.workoutType) {
                    "heavy" -> binding.radioHeavy.isChecked = true
                    "light" -> binding.radioLight.isChecked = true
                    "custom" -> binding.radioCustom.isChecked = true
                }
                
                // Load exercises
                val exerciseMap = trainingData.exerciseLibrary.associateBy { it.id }
                selectedExercises.clear()
                plan.exerciseIds.forEach { id ->
                    exerciseMap[id]?.let { selectedExercises.add(it) }
                }
                adapter.notifyDataSetChanged()
                
                // Load notes
                plan.notes?.let { binding.editTextNotes.setText(it) }
            }
        } else {
            // Default to heavy
            binding.radioHeavy.isChecked = true
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
                exerciseMap[id]?.let { selectedExercises.add(it) }
            }
        }
        
        // Remove exercises that are no longer selected
        selectedExercises.removeAll { it.id !in selectedIds }
        
        adapter.notifyDataSetChanged()
    }

    private fun savePlan() {
        val planName = binding.editTextPlanName.text.toString().trim()
        
        if (planName.isEmpty()) {
            Toast.makeText(this, "Please enter a plan name", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selectedExercises.isEmpty()) {
            Toast.makeText(this, "Please add at least one exercise", Toast.LENGTH_SHORT).show()
            return
        }
        
        val selectedRadioId = binding.radioGroupWorkoutType.checkedRadioButtonId
        val workoutType = when (selectedRadioId) {
            R.id.radio_heavy -> "heavy"
            R.id.radio_light -> "light"
            R.id.radio_custom -> "custom"
            else -> "heavy"
        }
        
        val notes = binding.editTextNotes.text.toString().trim().takeIf { it.isNotEmpty() }
        val exerciseIds = selectedExercises.map { it.id }.toMutableList()
        
        val trainingData = jsonHelper.readTrainingData()
        
        if (isEditing && planId != null) {
            // Update existing plan
            val planIndex = trainingData.workoutPlans.indexOfFirst { it.id == planId }
            if (planIndex != -1) {
                val existingPlan = trainingData.workoutPlans[planIndex]
                val updatedPlan = existingPlan.copy(
                    name = planName,
                    exerciseIds = exerciseIds,
                    workoutType = workoutType,
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
                notes = notes,
                createdDate = dateFormat.format(Date())
            )
            trainingData.workoutPlans.add(newPlan)
        }
        
        jsonHelper.writeTrainingData(trainingData)
        setResult(Activity.RESULT_OK)
        finish()
    }
}

