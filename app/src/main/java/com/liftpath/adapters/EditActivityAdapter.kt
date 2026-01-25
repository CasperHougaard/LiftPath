package com.liftpath.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.R
import com.liftpath.databinding.ItemEditSetBinding
import com.liftpath.models.ExerciseEntry
import java.util.Locale

class EditActivityAdapter(
    private val sets: List<ExerciseEntry>,
    private val onKgChanged: (Int, Float) -> Unit,
    private val onRepsChanged: (Int, Int) -> Unit,
    private val onRpeChanged: (Int, Float?) -> Unit,
    private val onNoteClicked: (Int) -> Unit,
    private val onWarmupChanged: (Int, Boolean) -> Unit,
    private val onDeleteClicked: (Int) -> Unit
) : RecyclerView.Adapter<EditActivityAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEditSetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val set = sets[position]
        val context = holder.itemView.context
        
        holder.binding.textSetNumber.text = context.getString(R.string.set_number_format, set.setNumber)

        // Set kg value
        holder.binding.editTextKg.setText(String.format(Locale.US, "%.1f", set.kg))
        holder.binding.editTextKg.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val kg = holder.binding.editTextKg.text.toString().toFloatOrNull()
                if (kg != null && kg > 0) {
                    onKgChanged(position, kg)
                } else {
                    holder.binding.editTextKg.setText(String.format(Locale.US, "%.1f", set.kg))
                }
            }
        }

        // Set reps value
        holder.binding.editTextReps.setText(set.reps.toString())
        holder.binding.editTextReps.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val reps = holder.binding.editTextReps.text.toString().toIntOrNull()
                if (reps != null && reps > 0) {
                    onRepsChanged(position, reps)
                } else {
                    holder.binding.editTextReps.setText(set.reps.toString())
                }
            }
        }

        // Set RPE value
        holder.binding.editTextRpe.setText(set.rpe?.let { String.format(Locale.US, "%.1f", it) } ?: "")
        holder.binding.editTextRpe.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val rpeText = holder.binding.editTextRpe.text.toString()
                val rpe = if (rpeText.isBlank()) {
                    null
                } else {
                    rpeText.toFloatOrNull()?.takeIf { it in 6.0f..10.0f }
                }
                if (rpe == null && rpeText.isNotBlank()) {
                    holder.binding.editTextRpe.setText("")
                    holder.binding.textInputLayoutRpe.error = context.getString(R.string.validation_rpe_range)
                } else {
                    onRpeChanged(position, rpe)
                    holder.binding.textInputLayoutRpe.error = null
                }
            }
        }

        // Note button and preview
        val hasNote = !set.note.isNullOrBlank()
        holder.binding.buttonNote.text = if (hasNote) {
            context.getString(R.string.button_edit_note)
        } else {
            context.getString(R.string.button_add_note)
        }
        holder.binding.buttonNote.setOnClickListener {
            onNoteClicked(position)
        }
        
        // Show note preview if it exists
        if (hasNote) {
            holder.binding.textNotePreview.text = set.note
            holder.binding.textNotePreview.visibility = View.VISIBLE
        } else {
            holder.binding.textNotePreview.visibility = View.GONE
        }

        // Warmup checkbox
        holder.binding.cbWarmup.setOnCheckedChangeListener(null)
        holder.binding.cbWarmup.isChecked = set.isWarmup
        holder.binding.cbWarmup.setOnCheckedChangeListener { _, isChecked ->
            onWarmupChanged(position, isChecked)
        }
        
        // Dim inputs when warmup
        val alpha = if (set.isWarmup) 0.6f else 1.0f
        holder.binding.textInputLayoutKg.alpha = alpha
        holder.binding.textInputLayoutReps.alpha = alpha
        holder.binding.textInputLayoutRpe.alpha = alpha

        // Delete button
        holder.binding.buttonDelete.setOnClickListener {
            onDeleteClicked(position)
        }
    }

    override fun getItemCount() = sets.size

    class ViewHolder(val binding: ItemEditSetBinding) : RecyclerView.ViewHolder(binding.root)
}
