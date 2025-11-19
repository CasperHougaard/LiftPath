package com.lilfitness.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.lilfitness.R
import com.lilfitness.models.ExerciseLibraryItem

class SelectExerciseWithPlanAdapter(
    var exercises: List<ExerciseLibraryItem>,
    private val planExerciseIds: Set<Int>,
    private val onExerciseClicked: (ExerciseLibraryItem) -> Unit
) : RecyclerView.Adapter<SelectExerciseWithPlanAdapter.ExerciseViewHolder>() {

    class ExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: CardView = view.findViewById(R.id.card_view_exercise_item)
        val exerciseName: TextView = view.findViewById(R.id.text_exercise_name)
        val planBadge: ImageView = view.findViewById(R.id.image_plan_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_select_exercise_with_badge, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        val exercise = exercises[position]
        holder.exerciseName.text = exercise.name

        // Check if exercise is in plan
        val isInPlan = planExerciseIds.contains(exercise.id)
        
        if (isInPlan) {
            // Show badge and highlight background
            holder.planBadge.visibility = View.VISIBLE
            holder.cardView.setCardBackgroundColor(0xFFE8F5E9.toInt()) // Light green
        } else {
            // Hide badge and use default background
            holder.planBadge.visibility = View.GONE
            holder.cardView.setCardBackgroundColor(0xFFFFFFFF.toInt()) // White
        }

        holder.itemView.setOnClickListener {
            onExerciseClicked(exercise)
        }
    }

    override fun getItemCount() = exercises.size

    fun updateExercises(newExercises: List<ExerciseLibraryItem>) {
        this.exercises = newExercises
        notifyDataSetChanged()
    }
}

