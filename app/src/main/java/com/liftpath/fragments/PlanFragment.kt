package com.liftpath.fragments

import android.content.Intent
import android.os.Bundle
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.liftpath.R
import androidx.recyclerview.widget.LinearLayoutManager
import com.liftpath.databinding.FragmentPlanBinding
import com.liftpath.components.ExerciseRemapBottomSheet
import com.liftpath.helpers.CircuitStore
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.PlanRotationHelper
import com.liftpath.helpers.WorkoutPlanMarkdownHelper
import com.liftpath.helpers.showWithTransparentWindow
import com.liftpath.models.ActiveRoutineType
import com.liftpath.models.TrainingData
import com.liftpath.models.WorkoutPlan
import com.liftpath.adapters.WorkoutPlansAdapter
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import com.liftpath.activities.EditWorkoutPlanActivity
import com.liftpath.activities.PlanSetActivity
import com.liftpath.activities.SettingsActivity
import com.liftpath.helpers.TrainingDataTransfer

class PlanFragment : Fragment() {

    private var _binding: FragmentPlanBinding? = null
    private val binding get() = _binding!!
    private lateinit var jsonHelper: JsonHelper

    /** Lazy so it resolves after [jsonHelper] is assigned in onCreate; the SAF launchers below
     *  are field initializers but their lambdas only run once the user picks a document. */
    private val transfer by lazy { TrainingDataTransfer(requireContext(), jsonHelper) }
    private lateinit var adapter: WorkoutPlansAdapter
    private var plans: MutableList<WorkoutPlan> = mutableListOf()

    private val editPlanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            loadPlans()
        }
    }

    private val exportSpecLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        uri ?: return@registerForActivityResult
        transfer.exportWorkoutPlanSpec(uri)
            .onSuccess {
                Toast.makeText(requireContext(), getString(R.string.toast_plan_spec_exported), Toast.LENGTH_SHORT).show()
            }
            .onFailure { e ->
                Toast.makeText(requireContext(), getString(R.string.toast_plan_spec_export_failed, e.message), Toast.LENGTH_LONG).show()
            }
    }

    private val importPlanLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        transfer.importWorkoutPlans(uri)
            .onSuccess { result ->
                if (result.unresolved.isEmpty()) {
                    finalizeImport(result.plans, result.circuits, emptyList(), emptyList(), result.unresolvedCircuitNames)
                } else {
                    // Some exercises aren't in the library — let the user remap or skip them first.
                    val library = jsonHelper.readTrainingData().exerciseLibrary
                    ExerciseRemapBottomSheet.newInstance(result.unresolved, library) { mapping ->
                        val outcome = WorkoutPlanMarkdownHelper.applyRemap(result.plans, mapping, result.circuits)
                        val skippedNames = result.unresolved
                            .filter { mapping[it.rawId] == null }
                            .map { it.displayName }
                        finalizeImport(outcome.plans, outcome.circuits, skippedNames, outcome.droppedPlanNames, result.unresolvedCircuitNames)
                    }.show(parentFragmentManager, "exercise_remap")
                }
            }
            .onFailure { e ->
                Toast.makeText(requireContext(), getString(R.string.toast_plan_import_error, e.message), Toast.LENGTH_LONG).show()
            }
    }

    /** Persists the (already-remapped) plans and shows a summary that reports any skips/drops. */
    private fun finalizeImport(
        plansToImport: List<WorkoutPlan>,
        circuitsToImport: List<com.liftpath.models.CircuitTemplate>,
        skippedExerciseNames: List<String>,
        droppedPlanNames: List<String>,
        unresolvedCircuitNames: List<String>
    ) {
        if (plansToImport.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.toast_plan_import_none), Toast.LENGTH_SHORT).show()
            return
        }

        val data = jsonHelper.readTrainingData()
        data.workoutPlans.addAll(plansToImport)
        circuitsToImport.forEach { CircuitStore.upsert(data, it) }
        jsonHelper.writeTrainingData(data)
        loadPlans()

        val message = buildString {
            val names = plansToImport.joinToString("\n") { "• ${it.name}" }
            append(getString(R.string.dialog_message_import_plans, plansToImport.size, names))
            if (skippedExerciseNames.isNotEmpty()) {
                append("\n\n")
                append(getString(R.string.import_summary_skipped, skippedExerciseNames.joinToString(", ")))
            }
            if (droppedPlanNames.isNotEmpty()) {
                append("\n\n")
                append(getString(R.string.import_summary_dropped_plans, droppedPlanNames.joinToString(", ")))
            }
            if (unresolvedCircuitNames.isNotEmpty()) {
                append("\n\n")
                append(getString(R.string.import_summary_unresolved_circuits, unresolvedCircuitNames.joinToString(", ")))
            }
        }
        DialogHelper.createBuilder(requireContext())
            .setTitle(getString(R.string.dialog_title_import_plans))
            .setMessage(message)
            .setPositiveButton(getString(R.string.button_ok), null)
            .showWithTransparentWindow()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentPlanBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        jsonHelper = JsonHelper(requireContext())
        setupRecyclerView()
        setupClickListeners()
        loadPlans()
    }

    override fun onResume() {
        super.onResume()
        // Catches changes made on PlanSetActivity (e.g. "Use" on a rotation) since
        // buttonPlanRotations navigates there with a plain startActivity.
        loadPlans()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    private fun setupRecyclerView() {
        adapter = WorkoutPlansAdapter(
            plans = plans,
            exerciseLibrary = emptyList(),
            activePlanId = null,
            onUsePlanClicked = { plan -> usePlan(plan) },
            onEditPlanClicked = { plan ->
                editPlan(plan)
            },
            onDeletePlanClicked = { plan ->
                deletePlan(plan)
            }
        )
        binding.recyclerViewPlans.adapter = adapter
        binding.recyclerViewPlans.layoutManager = LinearLayoutManager(requireContext())
    }

    /** Declares [plan] the Plan tab's active routine (single-plan mode) — see
     *  PlanRotationHelper.resolveActiveRoutine, which the Workout tab trusts over its old
     *  completion-based heuristic. */
    private fun usePlan(plan: WorkoutPlan) {
        val data = jsonHelper.readTrainingData()
        data.activeRoutineType = ActiveRoutineType.SINGLE_PLAN
        data.activePlanId = plan.id
        jsonHelper.writeTrainingData(data)
        Toast.makeText(requireContext(), getString(R.string.toast_plan_set_active, plan.name), Toast.LENGTH_SHORT).show()
        loadPlans()
    }

    private fun setupClickListeners() {
        binding.fabCreatePlan.setOnClickListener {
            createNewPlan()
        }

        binding.buttonPlanRotations.setOnClickListener {
            startActivity(Intent(requireContext(), PlanSetActivity::class.java))
        }

        binding.cardSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        binding.buttonMoreOptions.setOnClickListener { anchor ->
            val popup = PopupMenu(requireContext(), anchor)
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
        val activePlanId = trainingData.activePlanId
            .takeIf { trainingData.activeRoutineType == ActiveRoutineType.SINGLE_PLAN }
        adapter.updatePlans(plans, trainingData.exerciseLibrary, activePlanId)
        updateActiveRoutineCard(trainingData)

        if (plans.isEmpty()) {
            binding.textEmptyState.visibility = android.view.View.VISIBLE
            binding.recyclerViewPlans.visibility = android.view.View.GONE
        } else {
            binding.textEmptyState.visibility = android.view.View.GONE
            binding.recyclerViewPlans.visibility = android.view.View.VISIBLE
        }
    }

    /** Populates the "active routine" card from whatever [PlanRotationHelper.resolveActiveRoutine]
     *  says is current — a single plan, a rotation's next plan, or nothing chosen yet. */
    private fun updateActiveRoutineCard(trainingData: TrainingData) {
        val filledViews = listOf(
            binding.textActiveRoutineEyebrow,
            binding.textActiveRoutineTitle,
            binding.textActiveRoutineSub
        )

        when (val routine = PlanRotationHelper.resolveActiveRoutine(trainingData)) {
            is PlanRotationHelper.ActiveRoutine.SinglePlan -> {
                binding.textActiveRoutineEyebrow.text = getString(R.string.label_active_routine_plan)
                binding.textActiveRoutineTitle.text = routine.plan.name
                val exerciseCount = PlanRotationHelper.exerciseCount(routine.plan, trainingData.exerciseLibrary)
                binding.textActiveRoutineSub.text = resources.getQuantityString(
                    R.plurals.workout_plan_exercises, exerciseCount, exerciseCount
                )
                binding.textActiveRoutineEmpty.visibility = View.GONE
                filledViews.forEach { it.visibility = View.VISIBLE }
            }
            is PlanRotationHelper.ActiveRoutine.Rotation -> {
                binding.textActiveRoutineEyebrow.text = getString(R.string.label_active_routine_rotation)
                binding.textActiveRoutineTitle.text = routine.planSet.name
                binding.textActiveRoutineSub.text = getString(R.string.label_next_plan, routine.nextPlan.name)
                binding.textActiveRoutineEmpty.visibility = View.GONE
                filledViews.forEach { it.visibility = View.VISIBLE }
            }
            null -> {
                filledViews.forEach { it.visibility = View.GONE }
                binding.textActiveRoutineEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun createNewPlan() {
        val intent = Intent(requireContext(), EditWorkoutPlanActivity::class.java)
        editPlanLauncher.launch(intent)
    }

    private fun editPlan(plan: WorkoutPlan) {
        val intent = Intent(requireContext(), EditWorkoutPlanActivity::class.java).apply {
            putExtra(EditWorkoutPlanActivity.EXTRA_PLAN_ID, plan.id)
        }
        editPlanLauncher.launch(intent)
    }

    private fun deletePlan(plan: WorkoutPlan) {
        DialogHelper.createBuilder(requireContext())
            .setTitle(getString(R.string.dialog_title_delete_plan))
            .setMessage(getString(R.string.dialog_message_delete_plan, plan.name))
            .setPositiveButton(getString(R.string.button_delete)) { _, _ ->
                val trainingData = jsonHelper.readTrainingData()
                trainingData.workoutPlans.removeAll { it.id == plan.id }
                if (trainingData.activeRoutineType == ActiveRoutineType.SINGLE_PLAN &&
                    trainingData.activePlanId == plan.id
                ) {
                    trainingData.activeRoutineType = null
                    trainingData.activePlanId = null
                }
                jsonHelper.writeTrainingData(trainingData)
                loadPlans()
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }
}
