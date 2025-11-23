package com.liftpath.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.R
import com.liftpath.models.ExerciseLibraryItem

class SelectExercisesAdapter(
    private var exercises: List<ExerciseLibraryItem>,
    private val preselectedIds: Set<Int>,
    private val onSelectionChanged: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<SelectExercisesAdapter.ExerciseViewHolder>() {

    private val selectedIds = preselectedIds.toMutableSet()

    class ExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.checkbox_exercise)
        val exerciseName: TextView = view.findViewById(R.id.text_exercise_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_select_exercise_checkbox, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        val exercise = exercises[position]
        holder.exerciseName.text = exercise.name
        holder.checkbox.isChecked = selectedIds.contains(exercise.id)

        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedIds.add(exercise.id)
            } else {
                selectedIds.remove(exercise.id)
            }
            onSelectionChanged(exercise.id, isChecked)
        }

        // Handle item click
        holder.itemView.setOnClickListener {
            holder.checkbox.isChecked = !holder.checkbox.isChecked
        }
    }

    override fun getItemCount() = exercises.size

    fun getSelectedIds(): List<Int> {
        return selectedIds.toList()
    }

    fun updateExercises(newExercises: List<ExerciseLibraryItem>) {
        this.exercises = newExercises
        notifyDataSetChanged()
    }
}

