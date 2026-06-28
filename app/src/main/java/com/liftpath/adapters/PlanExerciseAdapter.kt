package com.liftpath.adapters

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.R
import com.liftpath.models.PlanExerciseSelectionType
import com.liftpath.models.PlanExerciseSlot
import com.liftpath.models.SetIntent

class PlanExerciseAdapter(
    private val configs: MutableList<PlanExerciseSlot>,
    private val onRemoveClicked: (Int) -> Unit,
    private val onMoveUp: (Int) -> Unit,
    private val onMoveDown: (Int) -> Unit,
    private val onIntentClicked: (Int) -> Unit  // position -> show intent picker
) : RecyclerView.Adapter<PlanExerciseAdapter.ViewHolder>() {

    /** Positions that are currently expanded. */
    private val expandedPositions = mutableSetOf<Int>()

    /** Map from exerciseId to display name (populated externally). */
    var exerciseNames: Map<Int, String> = emptyMap()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val number: TextView = view.findViewById(R.id.text_exercise_number)
        val name: TextView = view.findViewById(R.id.text_exercise_name)
        val targetsSummary: TextView = view.findViewById(R.id.text_targets_summary)
        val btnMoveUp: ImageButton = view.findViewById(R.id.button_move_up)
        val btnMoveDown: ImageButton = view.findViewById(R.id.button_move_down)
        val btnExpand: ImageButton = view.findViewById(R.id.button_expand)
        val btnRemove: ImageButton = view.findViewById(R.id.button_remove_exercise)
        val dividerConfig: View = view.findViewById(R.id.divider_config)
        val layoutConfig: LinearLayout = view.findViewById(R.id.layout_config)
        val intentValue: TextView = view.findViewById(R.id.text_intent_value)
        val editSets: EditText = view.findViewById(R.id.edit_sets_target)
        val editRpe: EditText = view.findViewById(R.id.edit_rpe_target)
        val editReps: EditText = view.findViewById(R.id.edit_reps_target)
        val editRest: EditText = view.findViewById(R.id.edit_rest_seconds)
        val editNotes: EditText = view.findViewById(R.id.edit_notes)

        // Text watchers — stored so we can remove before rebinding to avoid stale callbacks
        var setsWatcher: TextWatcher? = null
        var rpeWatcher: TextWatcher? = null
        var repsWatcher: TextWatcher? = null
        var restWatcher: TextWatcher? = null
        var notesWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_plan_exercise, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val config = configs[position]
        val isExpanded = expandedPositions.contains(position)

        holder.number.text = (position + 1).toString()
        holder.name.text = config.exerciseId?.let { exerciseNames[it] ?: "Exercise $it" }
            ?: "(No exercise selected)"

        // Targets summary (shown when collapsed)
        val summaryParts = mutableListOf<String>()
        config.defaultIntent?.let { summaryParts.add(it.displayName) }
        config.setsTarget?.let { summaryParts.add("${it}×") }
        config.repsTarget?.let { summaryParts.add(it) }
        config.rpeTarget?.let { summaryParts.add("@RPE${it.toInt()}") }
        config.restTimeSeconds?.let { summaryParts.add("${it}s rest") }
        if (summaryParts.isNotEmpty()) {
            holder.targetsSummary.text = summaryParts.joinToString(" · ")
            holder.targetsSummary.visibility = if (isExpanded) View.GONE else View.VISIBLE
        } else {
            holder.targetsSummary.visibility = View.GONE
        }

        // Expand/collapse
        holder.dividerConfig.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.layoutConfig.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.btnExpand.setImageResource(
            if (isExpanded) R.drawable.ic_arrow_up else R.drawable.ic_expand_more
        )

        // Hide move-up on first item, move-down on last
        holder.btnMoveUp.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
        holder.btnMoveDown.visibility = if (position == itemCount - 1) View.INVISIBLE else View.VISIBLE

        // Populate config fields (remove old watchers first)
        holder.editSets.removeTextChangedListener(holder.setsWatcher)
        holder.editRpe.removeTextChangedListener(holder.rpeWatcher)
        holder.editReps.removeTextChangedListener(holder.repsWatcher)
        holder.editRest.removeTextChangedListener(holder.restWatcher)
        holder.editNotes.removeTextChangedListener(holder.notesWatcher)

        holder.editSets.setText(config.setsTarget?.toString() ?: "")
        holder.editRpe.setText(config.rpeTarget?.let { if (it == it.toLong().toFloat()) it.toLong().toString() else it.toString() } ?: "")
        holder.editReps.setText(config.repsTarget ?: "")
        holder.editRest.setText(config.restTimeSeconds?.toString() ?: "")
        holder.editNotes.setText(config.notes ?: "")
        holder.intentValue.text = config.defaultIntent?.displayName ?: SetIntent.BUILD.displayName

        // Re-attach watchers
        holder.setsWatcher = makeWatcher { text ->
            val pos = holder.bindingAdapterPosition
            if (pos >= 0) configs[pos] = configs[pos].copy(setsTarget = text.toIntOrNull())
        }
        holder.rpeWatcher = makeWatcher { text ->
            val pos = holder.bindingAdapterPosition
            if (pos >= 0) configs[pos] = configs[pos].copy(rpeTarget = text.toFloatOrNull())
        }
        holder.repsWatcher = makeWatcher { text ->
            val pos = holder.bindingAdapterPosition
            if (pos >= 0) configs[pos] = configs[pos].copy(repsTarget = text.ifEmpty { null })
        }
        holder.restWatcher = makeWatcher { text ->
            val pos = holder.bindingAdapterPosition
            if (pos >= 0) configs[pos] = configs[pos].copy(restTimeSeconds = text.toIntOrNull())
        }
        holder.notesWatcher = makeWatcher { text ->
            val pos = holder.bindingAdapterPosition
            if (pos >= 0) configs[pos] = configs[pos].copy(notes = text.ifEmpty { null })
        }

        holder.editSets.addTextChangedListener(holder.setsWatcher)
        holder.editRpe.addTextChangedListener(holder.rpeWatcher)
        holder.editReps.addTextChangedListener(holder.repsWatcher)
        holder.editRest.addTextChangedListener(holder.restWatcher)
        holder.editNotes.addTextChangedListener(holder.notesWatcher)

        // Click listeners
        holder.intentValue.setOnClickListener { onIntentClicked(holder.bindingAdapterPosition) }

        holder.btnExpand.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos < 0) return@setOnClickListener
            if (expandedPositions.contains(pos)) expandedPositions.remove(pos)
            else expandedPositions.add(pos)
            notifyItemChanged(pos)
        }
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos < 0) return@setOnClickListener
            if (expandedPositions.contains(pos)) expandedPositions.remove(pos)
            else expandedPositions.add(pos)
            notifyItemChanged(pos)
        }

        holder.btnRemove.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos >= 0) onRemoveClicked(pos)
        }
        holder.btnMoveUp.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos > 0) onMoveUp(pos)
        }
        holder.btnMoveDown.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos >= 0 && pos < itemCount - 1) onMoveDown(pos)
        }
    }

    override fun getItemCount() = configs.size

    fun getSlots(): List<PlanExerciseSlot> = configs.toList()

    fun addSlot(slot: PlanExerciseSlot) {
        configs.add(slot)
        notifyItemInserted(configs.size - 1)
    }

    fun updateIntentAt(position: Int, intent: SetIntent) {
        if (position < 0 || position >= configs.size) return
        configs[position] = configs[position].copy(defaultIntent = intent)
        notifyItemChanged(position)
    }

    fun moveUp(position: Int) {
        if (position <= 0 || position >= configs.size) return
        val item = configs.removeAt(position)
        configs.add(position - 1, item)
        // Shift expanded positions
        expandedPositions.remove(position)
        expandedPositions.remove(position - 1)
        notifyItemMoved(position, position - 1)
        notifyItemChanged(position - 1)
        notifyItemChanged(position)
    }

    fun moveDown(position: Int) {
        if (position < 0 || position >= configs.size - 1) return
        val item = configs.removeAt(position)
        configs.add(position + 1, item)
        expandedPositions.remove(position)
        expandedPositions.remove(position + 1)
        notifyItemMoved(position, position + 1)
        notifyItemChanged(position)
        notifyItemChanged(position + 1)
    }

    private fun makeWatcher(onChange: (String) -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) { onChange(s?.toString()?.trim() ?: "") }
    }
}

