package com.liftpath.adapters

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.R
import com.liftpath.models.CircuitItem
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.Laterality

/**
 * The station list while authoring a circuit.
 *
 * Which numeric field a station shows is decided by the exercise: a time-based one (wall sit,
 * plank) gets the hold-time field and no reps, everything else gets reps. That keeps a plank from
 * being given "12 reps" and a squat from being given "45 seconds", which the log screen would then
 * have no way to interpret.
 */
class EditCircuitItemAdapter(
    private val items: MutableList<CircuitItem>,
    private val onRemoveClicked: (Int) -> Unit
) : RecyclerView.Adapter<EditCircuitItemAdapter.StationViewHolder>() {

    var library: List<ExerciseLibraryItem> = emptyList()

    class StationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val number: TextView = view.findViewById(R.id.text_station_number)
        val name: TextView = view.findViewById(R.id.text_station_name)
        val btnMoveUp: ImageButton = view.findViewById(R.id.button_move_up)
        val btnMoveDown: ImageButton = view.findViewById(R.id.button_move_down)
        val btnRemove: ImageButton = view.findViewById(R.id.button_remove_station)
        val layoutReps: View = view.findViewById(R.id.layout_reps)
        val layoutTime: View = view.findViewById(R.id.layout_time)
        val labelReps: TextView = view.findViewById(R.id.label_reps)
        val editReps: EditText = view.findViewById(R.id.edit_station_reps)
        val editTime: EditText = view.findViewById(R.id.edit_station_time)
        val editKg: EditText = view.findViewById(R.id.edit_station_kg)

        var repsWatcher: TextWatcher? = null
        var timeWatcher: TextWatcher? = null
        var kgWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder =
        StationViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_edit_circuit_item, parent, false)
        )

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        val item = items[position]
        val exercise = library.find { it.id == item.exerciseId }
        val isTimed = exercise?.isTimeBased == true

        holder.number.text = (position + 1).toString()
        holder.name.text = exercise?.name ?: holder.itemView.context.getString(
            R.string.circuit_unknown_exercise, item.exerciseId
        )

        holder.layoutReps.visibility = if (isTimed) View.GONE else View.VISIBLE
        holder.layoutTime.visibility = if (isTimed) View.VISIBLE else View.GONE
        holder.labelReps.setText(
            if (exercise?.laterality == Laterality.UNILATERAL) {
                R.string.circuit_station_hint_reps_per_side
            } else {
                R.string.circuit_station_hint_reps
            }
        )

        holder.btnMoveUp.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
        holder.btnMoveDown.visibility =
            if (position == itemCount - 1) View.INVISIBLE else View.VISIBLE

        holder.editReps.removeTextChangedListener(holder.repsWatcher)
        holder.editTime.removeTextChangedListener(holder.timeWatcher)
        holder.editKg.removeTextChangedListener(holder.kgWatcher)

        holder.editReps.setText(item.targetReps ?: "")
        holder.editTime.setText(item.targetDurationSeconds?.toString() ?: "")
        holder.editKg.setText(item.targetKg?.let { trim(it) } ?: "")

        holder.repsWatcher = watcher { text ->
            holder.bindingAdapterPosition.takeIf { it >= 0 }?.let { pos ->
                items[pos] = items[pos].copy(targetReps = text.ifEmpty { null })
            }
        }
        holder.timeWatcher = watcher { text ->
            holder.bindingAdapterPosition.takeIf { it >= 0 }?.let { pos ->
                items[pos] = items[pos].copy(
                    targetDurationSeconds = text.toIntOrNull()?.takeIf { it > 0 }
                )
            }
        }
        holder.kgWatcher = watcher { text ->
            holder.bindingAdapterPosition.takeIf { it >= 0 }?.let { pos ->
                items[pos] = items[pos].copy(targetKg = text.toFloatOrNull())
            }
        }

        holder.editReps.addTextChangedListener(holder.repsWatcher)
        holder.editTime.addTextChangedListener(holder.timeWatcher)
        holder.editKg.addTextChangedListener(holder.kgWatcher)

        holder.btnRemove.setOnClickListener {
            holder.bindingAdapterPosition.takeIf { it >= 0 }?.let(onRemoveClicked)
        }
        holder.btnMoveUp.setOnClickListener {
            holder.bindingAdapterPosition.takeIf { it > 0 }?.let { move(it, it - 1) }
        }
        holder.btnMoveDown.setOnClickListener {
            holder.bindingAdapterPosition.takeIf { it in 0 until itemCount - 1 }
                ?.let { move(it, it + 1) }
        }
    }

    override fun getItemCount() = items.size

    fun addAll(newItems: List<CircuitItem>) {
        if (newItems.isEmpty()) return
        val start = items.size
        items.addAll(newItems)
        notifyItemRangeInserted(start, newItems.size)
        // The neighbours' move arrows depend on being first/last, so they need a rebind.
        if (start > 0) notifyItemChanged(start - 1)
    }

    fun removeAt(position: Int) {
        if (position !in items.indices) return
        items.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, items.size - position)
        if (position > 0) notifyItemChanged(position - 1)
    }

    /** Current list in display order — the screen's source of truth when saving. */
    fun snapshot(): List<CircuitItem> = items.toList()

    private fun move(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices) return
        items.add(to, items.removeAt(from))
        notifyItemMoved(from, to)
        notifyItemChanged(from)
        notifyItemChanged(to)
    }

    private fun trim(v: Float): String = if (v % 1 == 0f) v.toInt().toString() else v.toString()

    private fun watcher(onChange: (String) -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = onChange(s?.toString()?.trim() ?: "")
    }
}
