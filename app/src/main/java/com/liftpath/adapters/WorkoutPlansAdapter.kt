package com.liftpath.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.R
import com.liftpath.models.WorkoutPlan
import java.util.Locale

class WorkoutPlansAdapter(
    private var plans: List<WorkoutPlan>,
    private val onUsePlanClicked: (WorkoutPlan) -> Unit,
    private val onEditPlanClicked: (WorkoutPlan) -> Unit,
    private val onDeletePlanClicked: (WorkoutPlan) -> Unit
) : RecyclerView.Adapter<WorkoutPlansAdapter.PlanViewHolder>() {

    class PlanViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val planName: TextView = view.findViewById(R.id.text_plan_name)
        val workoutTypeBadge: TextView = view.findViewById(R.id.text_workout_type_badge)
        val exerciseCount: TextView = view.findViewById(R.id.text_exercise_count)
        val usePlanButton: CardView = view.findViewById(R.id.button_use_plan)
        val editPlanButton: CardView = view.findViewById(R.id.button_edit_plan)
        val deletePlanButton: CardView = view.findViewById(R.id.button_delete_plan)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_workout_plan, parent, false)
        return PlanViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
        val plan = plans[position]
        holder.planName.text = plan.name
        holder.exerciseCount.text = "${plan.exerciseIds.size} exercise${if (plan.exerciseIds.size != 1) "s" else ""}"

        // Set workout type badge
        val typeLabel = plan.workoutType.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
        holder.workoutTypeBadge.text = typeLabel
        
        // Set badge color based on workout type
        val badgeColor = when (plan.workoutType) {
            "heavy" -> android.graphics.Color.parseColor("#2196F3") // Blue
            "light" -> android.graphics.Color.parseColor("#FF9800") // Orange
            else -> android.graphics.Color.parseColor("#757575") // Gray
        }
        holder.workoutTypeBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(badgeColor)

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

    fun updatePlans(newPlans: List<WorkoutPlan>) {
        this.plans = newPlans
        notifyDataSetChanged()
    }
}

