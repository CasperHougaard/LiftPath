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
import com.liftpath.models.WorkoutPlan

/**
 * Top sheet dialog for selecting workout mode: Custom, Plan, or AUTO.
 * Slides down from the top. When Plan is selected, expands to show a list of available workout plans.
 */
class SelectWorkoutModeBottomSheet : DialogFragment() {

    private var onCustomSelected: (() -> Unit)? = null
    private var onPlanSelected: ((WorkoutPlan) -> Unit)? = null
    private var onAutoSelected: (() -> Unit)? = null

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
            onPlanSelected: (WorkoutPlan) -> Unit,
            onAutoSelected: () -> Unit
        ): SelectWorkoutModeBottomSheet {
            return SelectWorkoutModeBottomSheet().apply {
                this.onCustomSelected = onCustomSelected
                this.onPlanSelected = onPlanSelected
                this.onAutoSelected = onAutoSelected
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
        // Custom Tile
        val customTile = view.findViewById<View>(R.id.tile_custom)
        val customIcon = customTile.findViewById<ImageView>(R.id.icon_tile)
        val customTitle = customTile.findViewById<TextView>(R.id.text_tile_title)
        customIcon.setImageResource(R.drawable.ic_dumbbell)
        customTitle.text = "Custom"
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

        // AUTO Tile
        val autoTile = view.findViewById<View>(R.id.tile_auto)
        val autoIcon = autoTile.findViewById<ImageView>(R.id.icon_tile)
        val autoTitle = autoTile.findViewById<TextView>(R.id.text_tile_title)
        val autoSubtitle = autoTile.findViewById<TextView>(R.id.text_tile_subtitle)
        autoIcon.setImageResource(R.drawable.ic_refresh)
        autoTitle.text = "AUTO"
        autoSubtitle.text = "Auto-detect next workout"
        autoSubtitle.visibility = View.VISIBLE
        autoTile.setOnClickListener {
            dismiss()
            onAutoSelected?.invoke()
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
            // Collapse main tiles slightly by hiding Custom and AUTO
            view.findViewById<View>(R.id.tile_custom).visibility = View.GONE
            view.findViewById<View>(R.id.tile_auto).visibility = View.GONE
        } else {
            planListLayout.visibility = View.GONE
            view.findViewById<View>(R.id.tile_custom).visibility = View.VISIBLE
            view.findViewById<View>(R.id.tile_auto).visibility = View.VISIBLE
        }
    }
}
