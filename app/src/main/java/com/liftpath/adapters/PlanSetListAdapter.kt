package com.liftpath.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.liftpath.R
import com.liftpath.helpers.PlanRotationHelper
import com.liftpath.models.PlanSet
import com.liftpath.models.PlanSetProgress

class PlanSetListAdapter(
    private val planSets: MutableList<PlanSet>,
    private val planSetProgress: List<PlanSetProgress>,
    private val planNames: Map<String, String>,  // planId -> planName
    private val activePlanSetId: String?,
    private val onUseClicked: (PlanSet) -> Unit,
    private val onEditClicked: (PlanSet) -> Unit,
    private val onDeleteClicked: (PlanSet) -> Unit
) : RecyclerView.Adapter<PlanSetListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.text_plan_set_name)
        val planCount: TextView = view.findViewById(R.id.text_plan_count)
        val nextPlan: TextView = view.findViewById(R.id.text_next_plan)
        val activeBadge: ImageView = view.findViewById(R.id.image_active_rotation_badge)
        val btnUse: MaterialButton = view.findViewById(R.id.button_use_plan_set)
        val btnEdit: MaterialButton = view.findViewById(R.id.button_edit_plan_set)
        val btnDelete: MaterialButton = view.findViewById(R.id.button_delete_plan_set)
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
        holder.activeBadge.visibility = if (planSet.id == activePlanSetId) View.VISIBLE else View.GONE

        val progress = planSetProgress.find { it.planSetId == planSet.id }
        val nextPlanName = PlanRotationHelper.nextPlanId(planSet.planIds, progress?.lastCompletedPlanId)
            ?.let { planNames[it] }
        if (nextPlanName != null) {
            holder.nextPlan.text = holder.itemView.context.getString(R.string.label_next_plan, nextPlanName)
            holder.nextPlan.visibility = View.VISIBLE
        } else {
            holder.nextPlan.visibility = View.GONE
        }

        holder.btnUse.setOnClickListener { onUseClicked(planSet) }
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
