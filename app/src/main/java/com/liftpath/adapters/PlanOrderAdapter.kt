package com.liftpath.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.R

/** Ordered list of plans within a PlanSet being edited. */
class PlanOrderAdapter(
    private val planIds: MutableList<String>,
    private val planNames: Map<String, String>,
    private val onRemove: (Int) -> Unit,
    private val onMoveUp: (Int) -> Unit,
    private val onMoveDown: (Int) -> Unit
) : RecyclerView.Adapter<PlanOrderAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val position: TextView = view.findViewById(R.id.text_position)
        val planName: TextView = view.findViewById(R.id.text_plan_name)
        val btnUp: ImageButton = view.findViewById(R.id.button_move_up)
        val btnDown: ImageButton = view.findViewById(R.id.button_move_down)
        val btnRemove: ImageButton = view.findViewById(R.id.button_remove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_plan_in_set, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val planId = planIds[position]
        holder.position.text = ('A' + position).toString()
        holder.planName.text = planNames[planId] ?: "Unknown Plan"

        holder.btnUp.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
        holder.btnDown.visibility = if (position == itemCount - 1) View.INVISIBLE else View.VISIBLE

        holder.btnUp.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos > 0) {
                onMoveUp(pos)
                planIds.add(pos - 1, planIds.removeAt(pos))
                notifyItemMoved(pos, pos - 1)
                notifyItemChanged(pos - 1)
                notifyItemChanged(pos)
            }
        }
        holder.btnDown.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos < itemCount - 1) {
                onMoveDown(pos)
                planIds.add(pos + 1, planIds.removeAt(pos))
                notifyItemMoved(pos, pos + 1)
                notifyItemChanged(pos)
                notifyItemChanged(pos + 1)
            }
        }
        holder.btnRemove.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos >= 0) onRemove(pos)
        }
    }

    override fun getItemCount() = planIds.size

    fun addPlan(planId: String) {
        planIds.add(planId)
        notifyItemInserted(planIds.size - 1)
    }

    fun removePlan(position: Int) {
        if (position < 0 || position >= planIds.size) return
        planIds.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, planIds.size)
    }

    fun getOrderedIds(): List<String> = planIds.toList()
}
