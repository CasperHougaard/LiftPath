package com.liftpath.activities

import android.content.Intent
import android.os.Bundle
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.liftpath.R
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.liftpath.databinding.ActivityWorkoutPlansBinding
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.showWithTransparentWindow
import com.liftpath.models.WorkoutPlan
import com.liftpath.adapters.WorkoutPlansAdapter

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

    private val exportSpecLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        uri ?: return@registerForActivityResult
        jsonHelper.exportWorkoutPlanSpec(uri)
            .onSuccess {
                Toast.makeText(this, getString(R.string.toast_plan_spec_exported), Toast.LENGTH_SHORT).show()
            }
            .onFailure { e ->
                Toast.makeText(this, getString(R.string.toast_plan_spec_export_failed, e.message), Toast.LENGTH_LONG).show()
            }
    }

    private val importPlanLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        jsonHelper.importWorkoutPlans(uri)
            .onSuccess { imported ->
                if (imported.isEmpty()) {
                    Toast.makeText(this, getString(R.string.toast_plan_import_none), Toast.LENGTH_SHORT).show()
                    return@onSuccess
                }
                val data = jsonHelper.readTrainingData()
                data.workoutPlans.addAll(imported)
                jsonHelper.writeTrainingData(data)
                loadPlans()

                val names = imported.joinToString("\n") { "• ${it.name}" }
                DialogHelper.createBuilder(this)
                    .setTitle(getString(R.string.dialog_title_import_plans))
                    .setMessage(getString(R.string.dialog_message_import_plans, imported.size, names))
                    .setPositiveButton(getString(R.string.button_ok), null)
                    .showWithTransparentWindow()
            }
            .onFailure { e ->
                Toast.makeText(this, getString(R.string.toast_plan_import_error, e.message), Toast.LENGTH_LONG).show()
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkoutPlansBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            onUsePlanClicked = { _ ->
                DialogHelper.createBuilder(this)
                    .setTitle(getString(R.string.dialog_title_use_plan))
                    .setMessage(getString(R.string.dialog_message_use_plan))
                    .setPositiveButton(getString(R.string.button_ok), null)
                    .showWithTransparentWindow()
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
            onBackPressedDispatcher.onBackPressed()
        }

        binding.fabCreatePlan.setOnClickListener {
            createNewPlan()
        }

        binding.buttonPlanRotations.setOnClickListener {
            startActivity(Intent(this, PlanSetActivity::class.java))
        }

        binding.buttonMoreOptions.setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menuInflater.inflate(R.menu.menu_workout_plans, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_export_plan_spec -> {
                        exportSpecLauncher.launch("liftpath_plan_spec.md")
                        true
                    }
                    R.id.action_import_plan_from_ai -> {
                        importPlanLauncher.launch(arrayOf("text/markdown", "text/plain", "*/*"))
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun loadPlans() {
        val trainingData = jsonHelper.readTrainingData()
        plans = trainingData.workoutPlans.toMutableList()
        adapter.updatePlans(plans)

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
        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.dialog_title_delete_plan))
            .setMessage(getString(R.string.dialog_message_delete_plan, plan.name))
            .setPositiveButton(getString(R.string.button_delete)) { _, _ ->
                val trainingData = jsonHelper.readTrainingData()
                trainingData.workoutPlans.removeAll { it.id == plan.id }
                jsonHelper.writeTrainingData(trainingData)
                loadPlans()
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }
}
