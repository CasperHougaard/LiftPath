package com.liftpath.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.R
import com.liftpath.models.ExerciseLibraryItem

class PlanExerciseAdapter(
    private var exercises: MutableList<ExerciseLibraryItem>,
    private val onRemoveClicked: (Int) -> Unit
) : RecyclerView.Adapter<PlanExerciseAdapter.ExerciseViewHolder>() {

    class ExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val exerciseNumber: TextView = view.findViewById(R.id.text_exercise_number)
        val exerciseName: TextView = view.findViewById(R.id.text_exercise_name)
        val removeButton: ImageButton = view.findViewById(R.id.button_remove_exercise)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_plan_exercise, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        val exercise = exercises[position]
        holder.exerciseNumber.text = (position + 1).toString()
        holder.exerciseName.text = exercise.name

        holder.removeButton.setOnClickListener {
            onRemoveClicked(position)
        }
    }

    override fun getItemCount() = exercises.size

    fun updateExercises(newExercises: List<ExerciseLibraryItem>) {
        exercises.clear()
        exercises.addAll(newExercises)
        notifyDataSetChanged()
    }
}

