package com.liftpath.components

import android.app.Dialog
import android.content.res.ColorStateList
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
import androidx.core.content.ContextCompat
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
import com.liftpath.helpers.PlanRotationHelper
import com.liftpath.helpers.lpColor
import com.liftpath.models.PlanSet
import com.liftpath.models.TargetMuscle
import com.liftpath.models.TrainingData
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
    private var trainingData: TrainingData = TrainingData()
    private var plans: List<WorkoutPlan> = emptyList()
    private var planSets: List<PlanSet> = emptyList()
    private var isPlanListExpanded = false
    private var isStretchPickerExpanded = false

    /** Which section the sheet opens on. */
    enum class Section { NONE, PLAN, STRETCH }

    companion object {
        fun newInstance(
            onCustomSelected: () -> Unit,
            onPlanSelected: (WorkoutPlan, PlanSet?) -> Unit,
            onStretchSelected: (Set<TargetMuscle>) -> Unit,
            initialSection: Section = Section.NONE
        ): SelectWorkoutModeBottomSheet {
            return SelectWorkoutModeBottomSheet().apply {
                this.onCustomSelected = onCustomSelected
                this.onPlanSelected = onPlanSelected
                this.onStretchSelected = onStretchSelected
                // The Workout tab's mode chips name the section, so opening collapsed would
                // charge the user a tap for a choice they already made.
                this.isPlanListExpanded = initialSection == Section.PLAN
                this.isStretchPickerExpanded = initialSection == Section.STRETCH
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
        // Applies whichever section newInstance asked for; a no-op for Section.NONE.
        updateSectionVisibility(view)
    }

    private fun loadData() {
        trainingData = jsonHelper.readTrainingData()
        plans = trainingData.workoutPlans.toList()
        planSets = trainingData.planSets.toList()
    }

    private fun setupTiles(view: View) {
        val customTile = view.findViewById<View>(R.id.tile_custom)
        val customIcon = customTile.findViewById<ImageView>(R.id.icon_tile)
        val customTitle = customTile.findViewById<TextView>(R.id.text_tile_title)
        customIcon.setImageResource(R.drawable.ic_dumbbell)
        customTitle.text = getString(R.string.workout_mode_manual)
        customTile.setOnClickListener {
            dismiss()
            onCustomSelected?.invoke()
        }

        val planTile = view.findViewById<View>(R.id.tile_plan)
        val planIcon = planTile.findViewById<ImageView>(R.id.icon_tile)
        val planTitle = planTile.findViewById<TextView>(R.id.text_tile_title)
        planIcon.setImageResource(R.drawable.ic_plans)
        planTitle.text = getString(R.string.workout_mode_follow_plan)
        planTile.setOnClickListener { togglePlanList(view) }

        val stretchTile = view.findViewById<View>(R.id.tile_stretch)
        val stretchIcon = stretchTile.findViewById<ImageView>(R.id.icon_tile)
        val stretchTitle = stretchTile.findViewById<TextView>(R.id.text_tile_title)
        stretchIcon.setImageResource(R.drawable.ic_stretch)
        stretchTitle.text = getString(R.string.workout_mode_stretch)
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

        // Same resolution the Workout tab hero uses, so this card never disagrees with it.
        when (val routine = PlanRotationHelper.resolveActiveRoutine(trainingData)) {
            is PlanRotationHelper.ActiveRoutine.Rotation -> {
                continueCard.visibility = View.VISIBLE
                continuePlanSetName.text = routine.planSet.name
                continueNextPlan.visibility = View.VISIBLE
                continueNextPlan.text = getString(R.string.label_continue_next_plan, routine.nextPlan.name)
                continueCard.setOnClickListener {
                    dismiss()
                    onPlanSelected?.invoke(routine.nextPlan, routine.planSet)
                }
            }
            is PlanRotationHelper.ActiveRoutine.SinglePlan -> {
                continueCard.visibility = View.VISIBLE
                continuePlanSetName.text = routine.plan.name
                continueNextPlan.visibility = View.GONE
                continueCard.setOnClickListener {
                    dismiss()
                    onPlanSelected?.invoke(routine.plan, null)
                }
            }
            null -> continueCard.visibility = View.GONE
        }
    }

    /** Next plan in rotation. Shared with the Workout tab via [PlanRotationHelper]. */
    private fun resolveNextPlan(planSet: PlanSet): WorkoutPlan? =
        PlanRotationHelper.nextPlan(trainingData, planSet)

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
            text = getString(R.string.stretch_area_full_body)
            isCheckable = true
            isChecked = true
            styleAsChoiceChip()
        }
        val areaChips = DefaultStretchesHelper.STRETCH_AREAS.keys.map { area ->
            Chip(requireContext()).apply {
                id = View.generateViewId()
                text = area
                isCheckable = true
                styleAsChoiceChip()
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

    /**
     * Applies [R.style.Widget_LP_Chip_Choice] to a programmatically-created chip. XML-inflated
     * chips get this via `style="@style/Widget.LP.Chip.Choice"`, but that attribute set only
     * applies at inflation time, so chips built in code need it mirrored here.
     */
    private fun Chip.styleAsChoiceChip() {
        setTextAppearance(R.style.TextAppearance_LP_Label)
        chipBackgroundColor = ContextCompat.getColorStateList(context, R.color.lp_chip_background)
        setTextColor(ContextCompat.getColorStateList(context, R.color.lp_chip_text))
        chipStrokeColor = ColorStateList.valueOf(context.lpColor(R.attr.lpHairline))
        chipStrokeWidth = resources.getDimension(R.dimen.lp_hairline_width)
        chipCornerRadius = resources.getDimension(R.dimen.lp_radius_sm)
        rippleColor = ColorStateList.valueOf(context.lpColor(R.attr.lpRipple))
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
            holder.sub.text = holder.itemView.context.getString(R.string.label_continue_next_plan, nextPlan.name)
            holder.itemView.setOnClickListener { onClicked(planSet, nextPlan) }
        }

        override fun getItemCount() = items.size
    }
}
