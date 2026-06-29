package com.liftpath.activities

import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.liftpath.adapters.PlanSetListAdapter
import com.liftpath.databinding.ActivityPlanSetBinding
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.showWithTransparentWindow
import com.liftpath.models.PlanSet
import com.liftpath.R

class PlanSetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlanSetBinding
    private lateinit var jsonHelper: JsonHelper
    private lateinit var adapter: PlanSetListAdapter

    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) loadData()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlanSetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jsonHelper = JsonHelper(this)
        setupBackgroundAnimation()
        setupRecyclerView()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is Animatable) drawable.start()
    }

    private fun setupRecyclerView() {
        val data = jsonHelper.readTrainingData()
        val planNames = data.workoutPlans.associate { it.id to it.name }
        adapter = PlanSetListAdapter(
            planSets = mutableListOf(),
            planSetProgress = emptyList(),
            planNames = planNames,
            onEditClicked = { planSet ->
                val intent = Intent(this, EditPlanSetActivity::class.java).apply {
                    putExtra(EditPlanSetActivity.EXTRA_PLAN_SET_ID, planSet.id)
                }
                editLauncher.launch(intent)
            },
            onDeleteClicked = { planSet -> confirmDelete(planSet) }
        )
        binding.recyclerViewPlanSets.adapter = adapter
        binding.recyclerViewPlanSets.layoutManager = LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
        binding.buttonBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.fabCreatePlanSet.setOnClickListener {
            editLauncher.launch(Intent(this, EditPlanSetActivity::class.java))
        }
    }

    private fun loadData() {
        val trainingData = jsonHelper.readTrainingData()
        val planNames = trainingData.workoutPlans.associate { it.id to it.name }
        // Rebuild adapter with fresh plan names and progress
        binding.recyclerViewPlanSets.adapter = PlanSetListAdapter(
            planSets = trainingData.planSets.toMutableList(),
            planSetProgress = trainingData.planSetProgress,
            planNames = planNames,
            onEditClicked = { planSet ->
                val intent = Intent(this, EditPlanSetActivity::class.java).apply {
                    putExtra(EditPlanSetActivity.EXTRA_PLAN_SET_ID, planSet.id)
                }
                editLauncher.launch(intent)
            },
            onDeleteClicked = { planSet -> confirmDelete(planSet) }
        )

        val isEmpty = trainingData.planSets.isEmpty()
        binding.textEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerViewPlanSets.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun confirmDelete(planSet: PlanSet) {
        DialogHelper.createBuilder(this)
            .setTitle("Delete Rotation")
            .setMessage("Delete \"${planSet.name}\"? This will not delete the individual plans.")
            .setPositiveButton(getString(R.string.button_delete)) { _, _ ->
                val data = jsonHelper.readTrainingData()
                data.planSets.removeAll { it.id == planSet.id }
                data.planSetProgress.removeAll { it.planSetId == planSet.id }
                jsonHelper.writeTrainingData(data)
                loadData()
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }
}
