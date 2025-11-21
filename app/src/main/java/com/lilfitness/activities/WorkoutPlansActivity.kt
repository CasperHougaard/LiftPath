package com.lilfitness.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.lilfitness.databinding.ActivityWorkoutPlansBinding
import com.lilfitness.helpers.JsonHelper
import com.lilfitness.models.WorkoutPlan
import com.lilfitness.adapters.WorkoutPlansAdapter

class WorkoutPlansActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorkoutPlansBinding
    private lateinit var jsonHelper: JsonHelper
    private lateinit var adapter: WorkoutPlansAdapter
    private var plans: MutableList<WorkoutPlan> = mutableListOf()

    private val editPlanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            loadPlans()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkoutPlansBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup background animation
        setupBackgroundAnimation()

        jsonHelper = JsonHelper(this)
        setupRecyclerView()
        setupClickListeners()
        loadPlans()
    }
    
    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is android.graphics.drawable.Animatable) {
            drawable.start()
        }
    }

    private fun setupRecyclerView() {
        adapter = WorkoutPlansAdapter(
            plans = plans,
            onUsePlanClicked = { plan ->
                // TODO: Will be implemented later - for now just show message
                AlertDialog.Builder(this)
                    .setTitle("Use Plan")
                    .setMessage("Plan usage will be implemented in the workout flow.")
                    .setPositiveButton("OK", null)
                    .show()
            },
            onEditPlanClicked = { plan ->
                editPlan(plan)
            },
            onDeletePlanClicked = { plan ->
                deletePlan(plan)
            }
        )
        binding.recyclerViewPlans.adapter = adapter
        binding.recyclerViewPlans.layoutManager = LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
        binding.buttonBack.setOnClickListener {
            onBackPressed()
        }
        
        binding.fabCreatePlan.setOnClickListener {
            createNewPlan()
        }
    }

    private fun loadPlans() {
        val trainingData = jsonHelper.readTrainingData()
        plans = trainingData.workoutPlans.toMutableList()
        adapter.updatePlans(plans)
        
        // Show/hide empty state
        if (plans.isEmpty()) {
            binding.textEmptyState.visibility = android.view.View.VISIBLE
            binding.recyclerViewPlans.visibility = android.view.View.GONE
        } else {
            binding.textEmptyState.visibility = android.view.View.GONE
            binding.recyclerViewPlans.visibility = android.view.View.VISIBLE
        }
    }

    private fun createNewPlan() {
        val intent = Intent(this, EditWorkoutPlanActivity::class.java)
        editPlanLauncher.launch(intent)
    }

    private fun editPlan(plan: WorkoutPlan) {
        val intent = Intent(this, EditWorkoutPlanActivity::class.java).apply {
            putExtra(EditWorkoutPlanActivity.EXTRA_PLAN_ID, plan.id)
        }
        editPlanLauncher.launch(intent)
    }

    private fun deletePlan(plan: WorkoutPlan) {
        AlertDialog.Builder(this)
            .setTitle("Delete Plan")
            .setMessage("Are you sure you want to delete \"${plan.name}\"? This will not affect your workout history.")
            .setPositiveButton("Delete") { _, _ ->
                val trainingData = jsonHelper.readTrainingData()
                trainingData.workoutPlans.removeAll { it.id == plan.id }
                jsonHelper.writeTrainingData(trainingData)
                loadPlans()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

