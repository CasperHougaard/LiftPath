package com.lilfitness.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lilfitness.databinding.ItemEditSetBinding
import com.lilfitness.models.ExerciseEntry
import java.util.Locale

class EditActivityAdapter(
    private val sets: List<ExerciseEntry>,
    private val onKgChanged: (Int, Float) -> Unit,
    private val onRepsChanged: (Int, Int) -> Unit,
    private val onRpeChanged: (Int, Float?) -> Unit,
    private val onNoteClicked: (Int) -> Unit
) : RecyclerView.Adapter<EditActivityAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEditSetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val set = sets[position]
        holder.binding.textSetNumber.text = "Set ${set.setNumber}"

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
        holder.binding.editTextRpe.hint = "RPE (6-10)"
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
                    holder.binding.editTextRpe.error = "RPE must be 6.0-10.0"
                } else {
                    onRpeChanged(position, rpe)
                    holder.binding.editTextRpe.error = null
                }
            }
        }

        // Note button and preview
        val hasNote = set.note != null && set.note.isNotBlank()
        holder.binding.buttonNote.text = if (hasNote) "Note ✓" else "+"
        holder.binding.buttonNote.setOnClickListener {
            onNoteClicked(position)
        }
        
        // Show note preview if it exists
        if (hasNote) {
            holder.binding.textNotePreview.text = "Note: ${set.note}"
            holder.binding.textNotePreview.visibility = View.VISIBLE
        } else {
            holder.binding.textNotePreview.visibility = View.GONE
        }
    }

    override fun getItemCount() = sets.size

    class ViewHolder(val binding: ItemEditSetBinding) : RecyclerView.ViewHolder(binding.root)
}

