package com.liftpath.components

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.liftpath.R
import com.liftpath.adapters.PlanSelectionAdapter
import com.liftpath.helpers.DefaultStretchesHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.models.PlanSet
import com.liftpath.models.PlanSetProgress
import com.liftpath.models.TargetMuscle
import com.liftpath.models.WorkoutPlan

/**
 * Top sheet dialog for selecting workout mode: Manual, Individual Plan, Plan Set rotation,
 * or a standalone Stretch session.
 * Slides down from the top.
 */
class SelectWorkoutModeBottomSheet : DialogFragment() {

    private var onCustomSelected: (() -> Unit)? = null
    /** Called with the selected plan and optionally the PlanSet it came from (null for individual plan). */
    private var onPlanSelected: ((WorkoutPlan, PlanSet?) -> Unit)? = null
    /** Called with the target muscles for a standalone stretch session. */
    private var onStretchSelected: ((Set<TargetMuscle>) -> Unit)? = null

    private lateinit var jsonHelper: JsonHelper
    private var plans: List<WorkoutPlan> = emptyList()
    private var planSets: List<PlanSet> = emptyList()
    private var planSetProgress: List<PlanSetProgress> = emptyList()
    private var isPlanListExpanded = false
    private var isStretchPickerExpanded = false

    companion object {
        fun newInstance(
            onCustomSelected: () -> Unit,
            onPlanSelected: (WorkoutPlan, PlanSet?) -> Unit,
            onStretchSelected: (Set<TargetMuscle>) -> Unit
        ): SelectWorkoutModeBottomSheet {
            return SelectWorkoutModeBottomSheet().apply {
                this.onCustomSelected = onCustomSelected
                this.onPlanSelected = onPlanSelected
                this.onStretchSelected = onStretchSelected
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), R.style.ThemeOverlay_Fitness_BottomSheetDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.let { window ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            val params = window.attributes
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.gravity = Gravity.TOP
            params.dimAmount = 0.5f
            window.attributes = params
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND, WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setWindowAnimations(R.style.TopSheetDialogAnimation)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.bottom_sheet_select_workout_mode, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        jsonHelper = JsonHelper(requireContext())
        loadData()
        setupTiles(view)
        setupPlanList(view)
        setupStretchPicker(view)
    }

    private fun loadData() {
        val trainingData = jsonHelper.readTrainingData()
        plans = trainingData.workoutPlans.toList()
        planSets = trainingData.planSets.toList()
        planSetProgress = trainingData.planSetProgress.toList()
    }

    private fun setupTiles(view: View) {
        val customTile = view.findViewById<View>(R.id.tile_custom)
        val customIcon = customTile.findViewById<ImageView>(R.id.icon_tile)
        val customTitle = customTile.findViewById<TextView>(R.id.text_tile_title)
        customIcon.setImageResource(R.drawable.ic_dumbbell)
        customTitle.text = "Manual"
        customTile.setOnClickListener {
            dismiss()
            onCustomSelected?.invoke()
        }

        val planTile = view.findViewById<View>(R.id.tile_plan)
        val planIcon = planTile.findViewById<ImageView>(R.id.icon_tile)
        val planTitle = planTile.findViewById<TextView>(R.id.text_tile_title)
        planIcon.setImageResource(R.drawable.ic_plans)
        planTitle.text = "Follow Plan"
        planTile.setOnClickListener { togglePlanList(view) }

        val stretchTile = view.findViewById<View>(R.id.tile_stretch)
        val stretchIcon = stretchTile.findViewById<ImageView>(R.id.icon_tile)
        val stretchTitle = stretchTile.findViewById<TextView>(R.id.text_tile_title)
        stretchIcon.setImageResource(R.drawable.ic_stretch)
        stretchTitle.text = "Stretch"
        stretchTile.setOnClickListener { toggleStretchPicker(view) }
    }

    private fun setupPlanList(view: View) {
        val planRecycler = view.findViewById<RecyclerView>(R.id.recycler_view_plans)
        val emptyState = view.findViewById<TextView>(R.id.text_empty_plans)
        val planAdapter = PlanSelectionAdapter(plans) { plan ->
            dismiss()
            onPlanSelected?.invoke(plan, null)
        }
        planRecycler.layoutManager = LinearLayoutManager(requireContext())
        planRecycler.adapter = planAdapter

        if (plans.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            planRecycler.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            planRecycler.visibility = View.VISIBLE
        }

        // Plan sets (rotations)
        val rotationsTitle = view.findViewById<TextView>(R.id.text_rotations_title)
        val rotationsRecycler = view.findViewById<RecyclerView>(R.id.recycler_view_plan_sets)

        if (planSets.isNotEmpty()) {
            rotationsTitle.visibility = View.VISIBLE
            rotationsRecycler.visibility = View.VISIBLE

            val planNames = plans.associate { it.id to it.name }
            val rotationItems = planSets.mapNotNull { planSet ->
                val nextPlan = resolveNextPlan(planSet) ?: return@mapNotNull null
                Pair(planSet, nextPlan)
            }

            rotationsRecycler.layoutManager = LinearLayoutManager(requireContext())
            rotationsRecycler.adapter = PlanSetSelectionAdapter(rotationItems, planNames) { planSet, nextPlan ->
                dismiss()
                onPlanSelected?.invoke(nextPlan, planSet)
            }
        } else {
            rotationsTitle.visibility = View.GONE
            rotationsRecycler.visibility = View.GONE
        }

        // Continue card: the most recently used PlanSet with a valid next plan
        setupContinueCard(view)
    }

    private fun setupContinueCard(view: View) {
        val continueCard = view.findViewById<View>(R.id.card_continue_rotation)
        val continuePlanSetName = view.findViewById<TextView>(R.id.text_continue_plan_set_name)
        val continueNextPlan = view.findViewById<TextView>(R.id.text_continue_next_plan)

        // Find the most recently completed plan set
        val latestProgress = planSetProgress.maxByOrNull { it.lastCompletedAt ?: 0L }
        val activePlanSet = latestProgress?.planSetId?.let { id -> planSets.find { it.id == id } }
        val nextPlan = activePlanSet?.let { resolveNextPlan(it) }

        if (activePlanSet != null && nextPlan != null) {
            continueCard.visibility = View.VISIBLE
            continuePlanSetName.text = activePlanSet.name
            continueNextPlan.text = "→ Next: ${nextPlan.name}"
            continueCard.setOnClickListener {
                dismiss()
                onPlanSelected?.invoke(nextPlan, activePlanSet)
            }
        } else {
            continueCard.visibility = View.GONE
        }
    }

    /** Returns the next plan in rotation for a given PlanSet, or null if unavailable. */
    private fun resolveNextPlan(planSet: PlanSet): WorkoutPlan? {
        if (planSet.planIds.isEmpty()) return null
        val progress = planSetProgress.find { it.planSetId == planSet.id }
        val lastIndex = planSet.planIds.indexOf(progress?.lastCompletedPlanId)
        val nextIndex = if (lastIndex == -1) 0 else (lastIndex + 1) % planSet.planIds.size
        val nextPlanId = planSet.planIds.getOrNull(nextIndex) ?: return null
        return plans.find { it.id == nextPlanId }
    }

    private fun togglePlanList(view: View) {
        isPlanListExpanded = !isPlanListExpanded
        if (isPlanListExpanded) isStretchPickerExpanded = false
        updateSectionVisibility(view)
    }

    private fun toggleStretchPicker(view: View) {
        isStretchPickerExpanded = !isStretchPickerExpanded
        if (isStretchPickerExpanded) isPlanListExpanded = false
        updateSectionVisibility(view)
    }

    /** The expanded section keeps only its own tile visible (acting as the collapse toggle). */
    private fun updateSectionVisibility(view: View) {
        view.findViewById<View>(R.id.layout_plan_list).visibility =
            if (isPlanListExpanded) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.layout_stretch_picker).visibility =
            if (isStretchPickerExpanded) View.VISIBLE else View.GONE
        val anyExpanded = isPlanListExpanded || isStretchPickerExpanded
        view.findViewById<View>(R.id.tile_custom).visibility =
            if (anyExpanded) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.tile_plan).visibility =
            if (isStretchPickerExpanded) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.tile_stretch).visibility =
            if (isPlanListExpanded) View.GONE else View.VISIBLE
    }

    private fun setupStretchPicker(view: View) {
        val chipGroup = view.findViewById<ChipGroup>(R.id.chip_group_stretch_areas)

        val fullBodyChip = Chip(requireContext()).apply {
            id = View.generateViewId()
            text = "Full body"
            isCheckable = true
            isChecked = true
        }
        val areaChips = DefaultStretchesHelper.STRETCH_AREAS.keys.map { area ->
            Chip(requireContext()).apply {
                id = View.generateViewId()
                text = area
                isCheckable = true
            }
        }

        // "Full body" and specific areas are mutually exclusive; with nothing selected,
        // fall back to "Full body" so the start button always has a valid selection.
        fullBodyChip.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                areaChips.forEach { it.isChecked = false }
            } else if (areaChips.none { it.isChecked }) {
                fullBodyChip.isChecked = true
            }
        }
        areaChips.forEach { chip ->
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    fullBodyChip.isChecked = false
                } else if (areaChips.none { it.isChecked }) {
                    fullBodyChip.isChecked = true
                }
            }
        }

        chipGroup.addView(fullBodyChip)
        areaChips.forEach { chipGroup.addView(it) }

        view.findViewById<View>(R.id.button_start_stretching).setOnClickListener {
            val muscles: Set<TargetMuscle> = if (fullBodyChip.isChecked) {
                TargetMuscle.values().toSet()
            } else {
                areaChips.filter { it.isChecked }
                    .flatMap { DefaultStretchesHelper.STRETCH_AREAS[it.text.toString()] ?: emptyList() }
                    .toSet()
            }
            dismiss()
            onStretchSelected?.invoke(muscles)
        }
    }

    /** Inline adapter for showing plan sets (rotations) in the plan list. */
    private class PlanSetSelectionAdapter(
        private val items: List<Pair<PlanSet, WorkoutPlan>>,  // (planSet, nextPlan)
        private val planNames: Map<String, String>,
        private val onClicked: (PlanSet, WorkoutPlan) -> Unit
    ) : RecyclerView.Adapter<PlanSetSelectionAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.text_plan_name_rotation)
            val sub: TextView = view.findViewById(R.id.text_plan_next_rotation)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_plan_set_selection, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (planSet, nextPlan) = items[position]
            holder.name.text = planSet.name
            holder.sub.text = "Next: ${nextPlan.name}"
            holder.itemView.setOnClickListener { onClicked(planSet, nextPlan) }
        }

        override fun getItemCount() = items.size
    }
}
