package com.liftpath.adapters

import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.R
import com.liftpath.models.ExerciseLibraryItem

class SelectExerciseWithPlanAdapter(
    var exercises: List<ExerciseLibraryItem>,
    private val planExerciseIds: Set<Int>,
    private val onExerciseClicked: (ExerciseLibraryItem) -> Unit
) : RecyclerView.Adapter<SelectExerciseWithPlanAdapter.ExerciseViewHolder>() {

    private var defaultBackground: android.graphics.drawable.Drawable? = null

    class ExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val itemViewContainer: View = view.findViewById(R.id.card_view_exercise_item)
        val exerciseName: TextView = view.findViewById(R.id.text_exercise_name)
        val planBadge: ImageView = view.findViewById(R.id.image_plan_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_select_exercise_with_badge, parent, false)
        val holder = ExerciseViewHolder(view)
        // Save default background on first inflation
        if (defaultBackground == null) {
            defaultBackground = holder.itemViewContainer.background
        }
        return holder
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        val exercise = exercises[position]
        holder.exerciseName.text = exercise.name

        // Check if exercise is in plan
        val isInPlan = planExerciseIds.contains(exercise.id)
        
        if (isInPlan) {
            // Show badge and highlight background with light green
            holder.planBadge.visibility = View.VISIBLE
            val lightGreen = 0xFFE8F5E9.toInt()
            holder.itemViewContainer.background = ColorDrawable(lightGreen)
        } else {
            // Hide badge and restore default background
            holder.planBadge.visibility = View.GONE
            holder.itemViewContainer.background = defaultBackground?.constantState?.newDrawable()
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

