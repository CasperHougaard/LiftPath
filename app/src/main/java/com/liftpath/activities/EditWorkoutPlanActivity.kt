package com.liftpath.activities

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.liftpath.R
import com.liftpath.databinding.ActivityEditWorkoutPlanBinding
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.showWithTransparentWindow
import com.liftpath.models.MovementPattern
import com.liftpath.models.PlanExerciseSelectionType
import com.liftpath.models.PlanExerciseSlot
import com.liftpath.models.PlanSlotType
import com.liftpath.models.SetIntent
import com.liftpath.models.WorkoutPlan
import com.liftpath.adapters.PlanExerciseAdapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditWorkoutPlanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditWorkoutPlanBinding
    private lateinit var jsonHelper: JsonHelper
    private lateinit var adapter: PlanExerciseAdapter
    private val planConfigs: MutableList<PlanExerciseSlot> = mutableListOf()
    private var planId: String? = null
    private var isEditing = false

    companion object {
        const val EXTRA_PLAN_ID = "extra_plan_id"
    }

    private val selectExercisesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedIds = result.data?.getIntegerArrayListExtra(SelectExercisesForPlanActivity.EXTRA_SELECTED_EXERCISE_IDS)
            if (selectedIds != null) {
                addNewExercises(selectedIds)
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
        if (drawable is Animatable) drawable.start()
    }

    private fun updateHeaderTitle() {
        binding.textHeaderTitle.text = if (isEditing) "Edit Workout Plan" else "Create Workout Plan"
    }

    private fun setupRecyclerView() {
        adapter = PlanExerciseAdapter(
            configs = planConfigs,
            onRemoveClicked = { position ->
                planConfigs.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.notifyItemRangeChanged(position, planConfigs.size)
                refreshExerciseNames()
            },
            onMoveUp = { position -> adapter.moveUp(position) },
            onMoveDown = { position -> adapter.moveDown(position) },
            onIntentClicked = { position -> showIntentDialog(position) }
        )
        binding.recyclerViewPlanExercises.adapter = adapter
        binding.recyclerViewPlanExercises.layoutManager = LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
        binding.buttonBack.setOnClickListener { finish() }

        binding.buttonAddExercises.setOnClickListener {
            // Pass existing IDs; the picker allows re-selecting for duplicates
            val preselectedIds = planConfigs.mapNotNull { it.exerciseId }.toSet().toIntArray()
            val intent = Intent(this, SelectExercisesForPlanActivity::class.java).apply {
                putExtra(SelectExercisesForPlanActivity.EXTRA_PRESELECTED_IDS, preselectedIds)
            }
            selectExercisesLauncher.launch(intent)
        }

        binding.buttonAddFamilySlot.setOnClickListener {
            showFamilyPickerDialog()
        }

        binding.buttonAddWarmup.setOnClickListener {
            val slot = PlanExerciseSlot(slotType = PlanSlotType.WARMUP)
            adapter.insertSlot(0, slot)
            binding.recyclerViewPlanExercises.scrollToPosition(0)
        }

        binding.buttonAddCooldown.setOnClickListener {
            val slot = PlanExerciseSlot(slotType = PlanSlotType.COOLDOWN)
            adapter.addSlot(slot)
            binding.recyclerViewPlanExercises.scrollToPosition(adapter.itemCount - 1)
        }

        binding.buttonSavePlan.setOnClickListener { savePlan() }
    }

    private fun loadPlanIfEditing() {
        if (!isEditing || planId == null) return
        val trainingData = jsonHelper.readTrainingData()
        val plan = trainingData.workoutPlans.find { it.id == planId } ?: return

        binding.editTextPlanName.setText(plan.name)
        plan.notes?.let { binding.editTextNotes.setText(it) }

        // Load configs â€” V2 plans use exerciseConfigs; legacy plans fall back to exerciseIds
        planConfigs.clear()
        val configs = plan.exerciseConfigs?.takeIf { it.isNotEmpty() }
            ?: plan.exerciseIds.map { id ->
                PlanExerciseSlot(exerciseId = id, selectionType = PlanExerciseSelectionType.SPECIFIC_VARIANT, defaultIntent = SetIntent.BUILD)
            }
        planConfigs.addAll(configs)

        refreshExerciseNames()
        adapter.notifyDataSetChanged()
    }

    /** Called after selection returns: adds exercises that aren't already in the list. */
    private fun addNewExercises(selectedIds: List<Int>) {
        val trainingData = jsonHelper.readTrainingData()
        val exerciseMap = trainingData.exerciseLibrary.associateBy { it.id }
        val existingIds = planConfigs.map { it.exerciseId }.toSet()

        selectedIds.forEach { id ->
            if (id !in existingIds && exerciseMap.containsKey(id)) {
                planConfigs.add(PlanExerciseSlot(exerciseId = id, selectionType = PlanExerciseSelectionType.SPECIFIC_VARIANT, defaultIntent = SetIntent.BUILD))
            }
        }
        refreshExerciseNames()
        adapter.notifyDataSetChanged()
    }

    /** Keeps the adapter's exercise name map in sync with the library. */
    private fun refreshExerciseNames() {
        val trainingData = jsonHelper.readTrainingData()
        adapter.exerciseNames = trainingData.exerciseLibrary.associate { it.id to it.name }
        adapter.familyNames = trainingData.exerciseFamilies?.associate { it.id to it.name } ?: emptyMap()
    }

    private fun showFamilyPickerDialog() {
        val trainingData = jsonHelper.readTrainingData()
        val families = trainingData.exerciseFamilies
        if (families.isNullOrEmpty()) {
            android.widget.Toast.makeText(this, "No exercise families available", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val names = families.map { it.name }.toTypedArray()
        var selectedIndex = -1
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Select Movement Family")
            .setSingleChoiceItems(names, -1) { _, which -> selectedIndex = which }
            .setPositiveButton("Add") { _, _ ->
                if (selectedIndex >= 0) {
                    val family = families[selectedIndex]
                    planConfigs.add(
                        PlanExerciseSlot(
                            selectionType = PlanExerciseSelectionType.FAMILY_SLOT,
                            familyId = family.id,
                            movementPattern = family.movementPattern,
                            defaultIntent = SetIntent.BUILD
                        )
                    )
                    refreshExerciseNames()
                    adapter.notifyItemInserted(planConfigs.size - 1)
                    binding.recyclerViewPlanExercises.scrollToPosition(planConfigs.size - 1)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun savePlan() {
        val planName = binding.editTextPlanName.text.toString().trim()
        if (planName.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_please_enter_plan_name), Toast.LENGTH_SHORT).show()
            return
        }
        if (planConfigs.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_please_add_exercise), Toast.LENGTH_SHORT).show()
            return
        }

        val notes = binding.editTextNotes.text.toString().trim().takeIf { it.isNotEmpty() }
        val currentConfigs = adapter.getSlots()
        val exerciseIds = currentConfigs.mapNotNull { it.exerciseId }.toMutableList()
        val trainingData = jsonHelper.readTrainingData()

        if (isEditing && planId != null) {
            val planIndex = trainingData.workoutPlans.indexOfFirst { it.id == planId }
            if (planIndex != -1) {
                val existingPlan = trainingData.workoutPlans[planIndex]
                trainingData.workoutPlans[planIndex] = existingPlan.copy(
                    name = planName,
                    exerciseIds = exerciseIds,
                    exerciseConfigs = currentConfigs,
                    notes = notes
                )
            }
        } else {
            val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
            trainingData.workoutPlans.add(
                WorkoutPlan(
                    name = planName,
                    exerciseIds = exerciseIds,
                    workoutType = "custom",
                    exerciseConfigs = currentConfigs,
                    notes = notes,
                    createdDate = dateFormat.format(Date())
                )
            )
        }

        jsonHelper.writeTrainingData(trainingData)
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun showIntentDialog(position: Int) {
        if (position < 0 || position >= planConfigs.size) return
        val currentIntent = planConfigs[position].defaultIntent ?: SetIntent.BUILD
        val options = arrayOf("Strength", "Build", "Flush")
        val currentIndex = when (currentIntent) {
            SetIntent.STRENGTH -> 0
            SetIntent.FLUSH -> 2
            else -> 1
        }
        DialogHelper.createBuilder(this)
            .setTitle("Set Intent")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val selected = when (which) {
                    0 -> SetIntent.STRENGTH
                    2 -> SetIntent.FLUSH
                    else -> SetIntent.BUILD
                }
                adapter.updateIntentAt(position, selected)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }
}
