package com.liftpath.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.R
import com.liftpath.models.PlanSet
import com.liftpath.models.PlanSetProgress

class PlanSetListAdapter(
    private val planSets: MutableList<PlanSet>,
    private val planSetProgress: List<PlanSetProgress>,
    private val planNames: Map<String, String>,  // planId -> planName
    private val onEditClicked: (PlanSet) -> Unit,
    private val onDeleteClicked: (PlanSet) -> Unit
) : RecyclerView.Adapter<PlanSetListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.text_plan_set_name)
        val planCount: TextView = view.findViewById(R.id.text_plan_count)
        val nextPlan: TextView = view.findViewById(R.id.text_next_plan)
        val btnEdit: TextView = view.findViewById(R.id.button_edit_plan_set)
        val btnDelete: TextView = view.findViewById(R.id.button_delete_plan_set)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_plan_set, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val planSet = planSets[position]
        holder.name.text = planSet.name
        holder.planCount.text = "${planSet.planIds.size} plans"

        // Calculate next plan in rotation
        val progress = planSetProgress.find { it.planSetId == planSet.id }
        if (planSet.planIds.isNotEmpty()) {
            val lastIndex = planSet.planIds.indexOf(progress?.lastCompletedPlanId)
            val nextIndex = if (lastIndex == -1) 0 else (lastIndex + 1) % planSet.planIds.size
            val nextPlanId = planSet.planIds.getOrNull(nextIndex)
            val nextPlanName = nextPlanId?.let { planNames[it] }
            if (nextPlanName != null) {
                holder.nextPlan.text = "Next: $nextPlanName"
                holder.nextPlan.visibility = View.VISIBLE
            } else {
                holder.nextPlan.visibility = View.GONE
            }
        } else {
            holder.nextPlan.visibility = View.GONE
        }

        holder.btnEdit.setOnClickListener { onEditClicked(planSet) }
        holder.btnDelete.setOnClickListener { onDeleteClicked(planSet) }
    }

    override fun getItemCount() = planSets.size

    fun updateData(newPlanSets: List<PlanSet>, newProgress: List<PlanSetProgress>) {
        planSets.clear()
        planSets.addAll(newPlanSets)
        notifyDataSetChanged()
    }
}
