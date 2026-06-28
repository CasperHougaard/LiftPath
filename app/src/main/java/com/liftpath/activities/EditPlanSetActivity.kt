package com.liftpath.activities

import android.app.Activity
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.liftpath.R
import com.liftpath.adapters.PlanOrderAdapter
import com.liftpath.databinding.ActivityEditPlanSetBinding
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.showWithTransparentWindow
import com.liftpath.models.PlanSet
import com.liftpath.models.WorkoutPlan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditPlanSetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditPlanSetBinding
    private lateinit var jsonHelper: JsonHelper
    private lateinit var adapter: PlanOrderAdapter

    private val orderedPlanIds = mutableListOf<String>()
    private var planSetId: String? = null
    private var isEditing = false
    private var allPlans: List<WorkoutPlan> = emptyList()

    companion object {
        const val EXTRA_PLAN_SET_ID = "extra_plan_set_id"
        private const val MAX_PLANS_IN_SET = 7
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditPlanSetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jsonHelper = JsonHelper(this)
        planSetId = intent.getStringExtra(EXTRA_PLAN_SET_ID)
        isEditing = planSetId != null

        setupBackgroundAnimation()
        binding.textHeaderTitle.text = if (isEditing) "Edit Rotation" else "Create Rotation"

        allPlans = jsonHelper.readTrainingData().workoutPlans

        setupRecyclerView()
        setupClickListeners()
        loadIfEditing()
    }

    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is Animatable) drawable.start()
    }

    private fun setupRecyclerView() {
        val planNames = allPlans.associate { it.id to it.name }
        adapter = PlanOrderAdapter(
            planIds = orderedPlanIds,
            planNames = planNames,
            onRemove = { position ->
                adapter.removePlan(position)
                updateCountLabel()
                updateEmptyState()
            },
            onMoveUp = { /* handled inside adapter */ },
            onMoveDown = { /* handled inside adapter */ }
        )
        binding.recyclerViewPlanOrder.adapter = adapter
        binding.recyclerViewPlanOrder.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewPlanOrder.isNestedScrollingEnabled = false
    }

    private fun setupClickListeners() {
        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonSavePlanSet.setOnClickListener { savePlanSet() }
        binding.buttonAddPlan.setOnClickListener { showAddPlanDialog() }
    }

    private fun loadIfEditing() {
        if (!isEditing || planSetId == null) {
            updateCountLabel()
            updateEmptyState()
            return
        }
        val data = jsonHelper.readTrainingData()
        val planSet = data.planSets.find { it.id == planSetId } ?: return
        binding.editTextPlanSetName.setText(planSet.name)
        binding.editTextNotes.setText(planSet.notes ?: "")
        orderedPlanIds.addAll(planSet.planIds)
        adapter.notifyDataSetChanged()
        updateCountLabel()
        updateEmptyState()
    }

    private fun showAddPlanDialog() {
        if (orderedPlanIds.size >= MAX_PLANS_IN_SET) {
            Toast.makeText(this, "Maximum $MAX_PLANS_IN_SET plans per rotation", Toast.LENGTH_SHORT).show()
            return
        }
        // Only show plans not already in the set
        val available = allPlans.filter { it.id !in orderedPlanIds }
        if (available.isEmpty()) {
            Toast.makeText(this, "All plans are already in this rotation", Toast.LENGTH_SHORT).show()
            return
        }
        val names = available.map { it.name }.toTypedArray()
        DialogHelper.createBuilder(this)
            .setTitle("Add Plan to Rotation")
            .setItems(names) { _, which ->
                val selected = available[which]
                adapter.addPlan(selected.id)
                updateCountLabel()
                updateEmptyState()
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    private fun updateCountLabel() {
        val count = orderedPlanIds.size
        binding.textPlanCountLabel.text = "$count / $MAX_PLANS_IN_SET"
    }

    private fun updateEmptyState() {
        binding.textEmptyPlans.visibility = if (orderedPlanIds.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun savePlanSet() {
        val name = binding.editTextPlanSetName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a rotation name", Toast.LENGTH_SHORT).show()
            return
        }
        if (orderedPlanIds.isEmpty()) {
            Toast.makeText(this, "Please add at least one plan", Toast.LENGTH_SHORT).show()
            return
        }

        val notes = binding.editTextNotes.text.toString().trim().takeIf { it.isNotEmpty() }
        val currentIds = adapter.getOrderedIds().toMutableList()
        val data = jsonHelper.readTrainingData()
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

        if (isEditing && planSetId != null) {
            val index = data.planSets.indexOfFirst { it.id == planSetId }
            if (index >= 0) {
                data.planSets[index] = data.planSets[index].copy(
                    name = name,
                    planIds = currentIds,
                    notes = notes
                )
            }
        } else {
            data.planSets.add(
                PlanSet(
                    name = name,
                    planIds = currentIds,
                    notes = notes,
                    createdDate = dateFormat.format(Date())
                )
            )
        }

        jsonHelper.writeTrainingData(data)
        setResult(Activity.RESULT_OK)
        finish()
    }
}
