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
import com.liftpath.R
import com.liftpath.adapters.PlanSelectionAdapter
import com.liftpath.helpers.JsonHelper
import com.liftpath.models.PlanSet
import com.liftpath.models.PlanSetProgress
import com.liftpath.models.WorkoutPlan

/**
 * Top sheet dialog for selecting workout mode: Manual, Individual Plan, or Plan Set rotation.
 * Slides down from the top.
 */
class SelectWorkoutModeBottomSheet : DialogFragment() {

    private var onCustomSelected: (() -> Unit)? = null
    /** Called with the selected plan and optionally the PlanSet it came from (null for individual plan). */
    private var onPlanSelected: ((WorkoutPlan, PlanSet?) -> Unit)? = null

    private lateinit var jsonHelper: JsonHelper
    private var plans: List<WorkoutPlan> = emptyList()
    private var planSets: List<PlanSet> = emptyList()
    private var planSetProgress: List<PlanSetProgress> = emptyList()
    private var isPlanListExpanded = false

    companion object {
        fun newInstance(
            onCustomSelected: () -> Unit,
            onPlanSelected: (WorkoutPlan, PlanSet?) -> Unit
        ): SelectWorkoutModeBottomSheet {
            return SelectWorkoutModeBottomSheet().apply {
                this.onCustomSelected = onCustomSelected
                this.onPlanSelected = onPlanSelected
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
        val planListLayout = view.findViewById<View>(R.id.layout_plan_list)
        isPlanListExpanded = !isPlanListExpanded
        if (isPlanListExpanded) {
            planListLayout.visibility = View.VISIBLE
            view.findViewById<View>(R.id.tile_custom).visibility = View.GONE
        } else {
            planListLayout.visibility = View.GONE
            view.findViewById<View>(R.id.tile_custom).visibility = View.VISIBLE
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


/**
 * Top sheet dialog for selecting workout mode: Manual or Plan.
 * Slides down from the top. When Plan is selected, expands to show a list of available workout plans.
 */
class SelectWorkoutModeBottomSheet : DialogFragment() {

    private var onCustomSelected: (() -> Unit)? = null
    private var onPlanSelected: ((WorkoutPlan) -> Unit)? = null

    private lateinit var jsonHelper: JsonHelper
    private lateinit var planAdapter: PlanSelectionAdapter
    private var plans: List<WorkoutPlan> = emptyList()
    private var isPlanListExpanded = false

    companion object {
        /**
         * Creates a new instance of SelectWorkoutModeBottomSheet.
         */
        fun newInstance(
            onCustomSelected: () -> Unit,
            onPlanSelected: (WorkoutPlan) -> Unit
        ): SelectWorkoutModeBottomSheet {
            return SelectWorkoutModeBottomSheet().apply {
                this.onCustomSelected = onCustomSelected
                this.onPlanSelected = onPlanSelected
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), R.style.ThemeOverlay_Fitness_BottomSheetDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(true)
        
        // Enable edge-to-edge to handle system insets properly
        dialog.window?.let { window ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        
        return dialog
    }
    
    override fun onStart() {
        super.onStart()
        
        // Configure window to slide from top and be full width
        dialog?.window?.let { window ->
            val params = window.attributes
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.gravity = Gravity.TOP
            params.dimAmount = 0.5f
            window.attributes = params
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )
            
            // Set animations for sliding from top
            window.setWindowAnimations(R.style.TopSheetDialogAnimation)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_select_workout_mode, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Apply window insets to respect system UI (status bar, etc.)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Add top padding to account for status bar, matching MainActivity
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        jsonHelper = JsonHelper(requireContext())
        loadPlans()
        setupTiles(view)
        setupPlanList(view)
    }

    private fun loadPlans() {
        val trainingData = jsonHelper.readTrainingData()
        plans = trainingData.workoutPlans.toList()
    }

    private fun setupTiles(view: View) {
        // Manual Tile
        val customTile = view.findViewById<View>(R.id.tile_custom)
        val customIcon = customTile.findViewById<ImageView>(R.id.icon_tile)
        val customTitle = customTile.findViewById<TextView>(R.id.text_tile_title)
        customIcon.setImageResource(R.drawable.ic_dumbbell)
        customTitle.text = "Manual"
        customTile.setOnClickListener {
            dismiss()
            onCustomSelected?.invoke()
        }

        // Plan Tile
        val planTile = view.findViewById<View>(R.id.tile_plan)
        val planIcon = planTile.findViewById<ImageView>(R.id.icon_tile)
        val planTitle = planTile.findViewById<TextView>(R.id.text_tile_title)
        planIcon.setImageResource(R.drawable.ic_plans)
        planTitle.text = "Plan"
        planTile.setOnClickListener {
            togglePlanList(view)
        }
    }

    private fun setupPlanList(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_plans)
        val emptyState = view.findViewById<TextView>(R.id.text_empty_plans)

        planAdapter = PlanSelectionAdapter(plans) { plan ->
            dismiss()
            onPlanSelected?.invoke(plan)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = planAdapter

        // Show/hide empty state
        if (plans.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun togglePlanList(view: View) {
        val planListLayout = view.findViewById<View>(R.id.layout_plan_list)
        val mainTilesLayout = view.findViewById<View>(R.id.layout_main_tiles)

        isPlanListExpanded = !isPlanListExpanded

        if (isPlanListExpanded) {
            planListLayout.visibility = View.VISIBLE
            // Collapse main tiles slightly by hiding Manual
            view.findViewById<View>(R.id.tile_custom).visibility = View.GONE
        } else {
            planListLayout.visibility = View.GONE
            view.findViewById<View>(R.id.tile_custom).visibility = View.VISIBLE
        }
    }
}
