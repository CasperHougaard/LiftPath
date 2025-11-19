package com.lilfitness.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lilfitness.R
import com.lilfitness.models.ExerciseLibraryItem

class ExerciseLibraryAdapter(
    private var exercises: List<ExerciseLibraryItem>,
    private val onEditClicked: (ExerciseLibraryItem) -> Unit
) : RecyclerView.Adapter<ExerciseLibraryAdapter.ExerciseViewHolder>() {

    class ExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val exerciseName: TextView = view.findViewById(R.id.text_exercise_name)
        val editButton: Button = view.findViewById(R.id.button_edit_exercise)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_exercise_library, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        val exercise = exercises[position]
        holder.exerciseName.text = exercise.name
        holder.editButton.setOnClickListener {
            onEditClicked(exercise)
        }
    }

    override fun getItemCount() = exercises.size

    fun updateExercises(newExercises: List<ExerciseLibraryItem>) {
        this.exercises = newExercises
        notifyDataSetChanged()
    }
}
