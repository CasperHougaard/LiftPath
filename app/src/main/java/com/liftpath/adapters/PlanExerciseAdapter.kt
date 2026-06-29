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
import com.liftpath.models.PlanSlotType
import com.liftpath.models.SetIntent

class PlanExerciseAdapter(
    private val configs: MutableList<PlanExerciseSlot>,
    private val onRemoveClicked: (Int) -> Unit,
    private val onMoveUp: (Int) -> Unit,
    private val onMoveDown: (Int) -> Unit,
    private val onIntentClicked: (Int) -> Unit  // position -> show intent picker
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_EXERCISE = 0
        private const val VIEW_TYPE_SPECIAL  = 1
    }

    /** Positions that are currently expanded (exercise slots only). */
    private val expandedPositions = mutableSetOf<Int>()

    /** Map from exerciseId to display name (populated externally). */
    var exerciseNames: Map<Int, String> = emptyMap()

    /** Map from familyId to display name (populated externally). */
    var familyNames: Map<String, String> = emptyMap()

    // ── ViewHolder: regular exercise ────────────────────────────────────────

    inner class ExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
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

        var setsWatcher: TextWatcher? = null
        var rpeWatcher: TextWatcher? = null
        var repsWatcher: TextWatcher? = null
        var restWatcher: TextWatcher? = null
        var notesWatcher: TextWatcher? = null
    }

    // ── ViewHolder: warmup / cooldown ───────────────────────────────────────

    inner class SpecialSlotViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconText: TextView = view.findViewById(R.id.text_slot_icon)
        val label: TextView = view.findViewById(R.id.text_slot_label)
        val subtitle: TextView = view.findViewById(R.id.text_slot_subtitle)
        val btnMoveUp: ImageButton = view.findViewById(R.id.button_move_up)
        val btnMoveDown: ImageButton = view.findViewById(R.id.button_move_down)
        val btnRemove: ImageButton = view.findViewById(R.id.button_remove_slot)
        val editDuration: EditText = view.findViewById(R.id.edit_slot_duration)
        val editNotes: EditText = view.findViewById(R.id.edit_slot_notes)
        var durationWatcher: TextWatcher? = null
        var notesWatcher: TextWatcher? = null
    }

    // ── Adapter overrides ────────────────────────────────────────────────────

    override fun getItemViewType(position: Int): Int =
        if (configs[position].isSpecialElement) VIEW_TYPE_SPECIAL else VIEW_TYPE_EXERCISE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SPECIAL) {
            SpecialSlotViewHolder(
                inflater.inflate(R.layout.list_item_plan_special_slot, parent, false)
            )
        } else {
            ExerciseViewHolder(
                inflater.inflate(R.layout.list_item_plan_exercise, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val config = configs[position]
        when (holder) {
            is SpecialSlotViewHolder -> bindSpecial(holder, position, config)
            is ExerciseViewHolder    -> bindExercise(holder, position, config)
        }
    }

    override fun getItemCount() = configs.size

    // ── Bind helpers ─────────────────────────────────────────────────────────

    private fun bindSpecial(holder: SpecialSlotViewHolder, position: Int, config: PlanExerciseSlot) {
        val isWarmup = config.slotType == PlanSlotType.WARMUP
        holder.iconText.text = if (isWarmup) "🔥" else "🧊"
        holder.label.setText(if (isWarmup) R.string.label_warmup_element else R.string.label_cooldown_element)
        holder.subtitle.setText(if (isWarmup) R.string.warmup_element_subtitle else R.string.cooldown_element_subtitle)

        holder.btnMoveUp.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
        holder.btnMoveDown.visibility = if (position == itemCount - 1) View.INVISIBLE else View.VISIBLE

        // Duration field (in minutes)
        holder.editDuration.removeTextChangedListener(holder.durationWatcher)
        val durationMins = (config.durationSeconds ?: 300) / 60
        holder.editDuration.setText(durationMins.toString())
        holder.durationWatcher = makeWatcher { text ->
            val pos = holder.bindingAdapterPosition
            if (pos >= 0) {
                val mins = text.toIntOrNull()?.coerceAtLeast(1) ?: 5
                configs[pos] = configs[pos].copy(durationSeconds = mins * 60)
            }
        }
        holder.editDuration.addTextChangedListener(holder.durationWatcher)

        holder.editNotes.removeTextChangedListener(holder.notesWatcher)
        holder.editNotes.setText(config.notes ?: "")
        holder.notesWatcher = makeWatcher { text ->
            val pos = holder.bindingAdapterPosition
            if (pos >= 0) configs[pos] = configs[pos].copy(notes = text.ifEmpty { null })
        }
        holder.editNotes.addTextChangedListener(holder.notesWatcher)

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

    private fun bindExercise(holder: ExerciseViewHolder, position: Int, config: PlanExerciseSlot) {
        val isExpanded = expandedPositions.contains(position)

        holder.number.text = (position + 1).toString()
        holder.name.text = when (config.effectiveSelectionType) {
            PlanExerciseSelectionType.SPECIFIC_VARIANT ->
                config.exerciseId?.let { exerciseNames[it] ?: "Exercise $it" }
                    ?: "(No exercise selected)"
            PlanExerciseSelectionType.FAMILY_SLOT ->
                "[Family] ${config.familyId?.let { familyNames[it] } ?: "Unknown Family"}"
        }

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

        holder.dividerConfig.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.layoutConfig.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.btnExpand.setImageResource(
            if (isExpanded) R.drawable.ic_arrow_up else R.drawable.ic_expand_more
        )

        holder.btnMoveUp.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
        holder.btnMoveDown.visibility = if (position == itemCount - 1) View.INVISIBLE else View.VISIBLE

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

        holder.intentValue.setOnClickListener { onIntentClicked(holder.bindingAdapterPosition) }

        val toggleExpand = View.OnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos < 0) return@OnClickListener
            if (expandedPositions.contains(pos)) expandedPositions.remove(pos)
            else expandedPositions.add(pos)
            notifyItemChanged(pos)
        }
        holder.btnExpand.setOnClickListener(toggleExpand)
        holder.itemView.setOnClickListener(toggleExpand)

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

    // ── Public API ────────────────────────────────────────────────────────────

    fun getSlots(): List<PlanExerciseSlot> = configs.toList()

    fun addSlot(slot: PlanExerciseSlot) {
        configs.add(slot)
        notifyItemInserted(configs.size - 1)
    }

    fun insertSlot(index: Int, slot: PlanExerciseSlot) {
        configs.add(index, slot)
        notifyItemInserted(index)
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
