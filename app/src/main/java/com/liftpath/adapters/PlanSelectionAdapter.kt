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

class PlanSelectionAdapter(
    private var plans: List<WorkoutPlan>,
    private val onPlanClicked: (WorkoutPlan) -> Unit
) : RecyclerView.Adapter<PlanSelectionAdapter.PlanViewHolder>() {

    class PlanViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: CardView = view.findViewById(R.id.card_view_plan_item)
        val planName: TextView = view.findViewById(R.id.text_plan_name)
        val workoutTypeBadge: TextView = view.findViewById(R.id.text_workout_type_badge)
        val exerciseCount: TextView = view.findViewById(R.id.text_exercise_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_plan_selection, parent, false)
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

        // Set click listener on the entire card
        holder.cardView.setOnClickListener {
            onPlanClicked(plan)
        }
    }

    override fun getItemCount() = plans.size

    fun updatePlans(newPlans: List<WorkoutPlan>) {
        this.plans = newPlans
        notifyDataSetChanged()
    }
}
