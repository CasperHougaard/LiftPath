package com.liftpath.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.liftpath.R
import com.liftpath.helpers.PlanRotationHelper
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.WorkoutPlan

class WorkoutPlansAdapter(
    private var plans: List<WorkoutPlan>,
    private var exerciseLibrary: List<ExerciseLibraryItem>,
    private var activePlanId: String?,
    private val onUsePlanClicked: (WorkoutPlan) -> Unit,
    private val onEditPlanClicked: (WorkoutPlan) -> Unit,
    private val onDeletePlanClicked: (WorkoutPlan) -> Unit
) : RecyclerView.Adapter<WorkoutPlansAdapter.PlanViewHolder>() {

    class PlanViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val planName: TextView = view.findViewById(R.id.text_plan_name)
        val exerciseCount: TextView = view.findViewById(R.id.text_exercise_count)
        val activeBadge: ImageView = view.findViewById(R.id.image_active_plan_badge)
        val usePlanButton: MaterialButton = view.findViewById(R.id.button_use_plan)
        val editPlanButton: MaterialButton = view.findViewById(R.id.button_edit_plan)
        val deletePlanButton: MaterialCardView = view.findViewById(R.id.button_delete_plan)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_workout_plan, parent, false)
        return PlanViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
        val plan = plans[position]
        holder.planName.text = plan.name
        val exerciseCount = PlanRotationHelper.exerciseCount(plan, exerciseLibrary)
        holder.exerciseCount.text = holder.itemView.resources.getQuantityString(
            R.plurals.workout_plan_exercises, exerciseCount, exerciseCount
        )
        holder.activeBadge.visibility = if (plan.id == activePlanId) View.VISIBLE else View.GONE

        holder.usePlanButton.setOnClickListener {
            onUsePlanClicked(plan)
        }

        holder.editPlanButton.setOnClickListener {
            onEditPlanClicked(plan)
        }

        holder.deletePlanButton.setOnClickListener {
            onDeletePlanClicked(plan)
        }
    }

    override fun getItemCount() = plans.size

    fun updatePlans(
        newPlans: List<WorkoutPlan>,
        newExerciseLibrary: List<ExerciseLibraryItem> = exerciseLibrary,
        newActivePlanId: String? = activePlanId
    ) {
        this.plans = newPlans
        this.exerciseLibrary = newExerciseLibrary
        this.activePlanId = newActivePlanId
        notifyDataSetChanged()
    }
}

