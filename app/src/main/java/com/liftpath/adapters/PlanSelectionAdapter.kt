package com.liftpath.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.liftpath.R
import com.liftpath.models.WorkoutPlan

class PlanSelectionAdapter(
    private var plans: List<WorkoutPlan>,
    private val onPlanClicked: (WorkoutPlan) -> Unit
) : RecyclerView.Adapter<PlanSelectionAdapter.PlanViewHolder>() {

    class PlanViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: MaterialCardView = view.findViewById(R.id.card_view_plan_item)
        val planName: TextView = view.findViewById(R.id.text_plan_name)
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
