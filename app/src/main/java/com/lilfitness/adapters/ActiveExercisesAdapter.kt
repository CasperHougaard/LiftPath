package com.lilfitness.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lilfitness.R
import com.lilfitness.models.ExerciseEntry
import com.lilfitness.models.GroupedExercise

class ActiveExercisesAdapter(
    private val groupedExercises: List<GroupedExercise>,
    private val onAddSetClicked: (exerciseId: Int, exerciseName: String) -> Unit,
    private val onDuplicateSetClicked: (exerciseId: Int) -> Unit,
    private val onDeleteExerciseClicked: (exerciseId: Int) -> Unit
) : RecyclerView.Adapter<ActiveExercisesAdapter.GroupedExerciseViewHolder>() {

    class GroupedExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val exerciseName: TextView = view.findViewById(R.id.text_exercise_name)
        val setsSummary: TextView = view.findViewById(R.id.text_sets_summary)
        val addSetButton: Button = view.findViewById(R.id.button_add_set)
        val duplicateSetButton: Button = view.findViewById(R.id.button_duplicate_set)
        val deleteExerciseButton: Button = view.findViewById(R.id.button_delete_exercise)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupedExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_active_exercise, parent, false)
        return GroupedExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupedExerciseViewHolder, position: Int) {
        val groupedExercise = groupedExercises[position]
        holder.exerciseName.text = groupedExercise.exerciseName

        val setsText = groupedExercise.sets.joinToString("\n") { set ->
            "Set ${set.setNumber}: ${set.kg} kg x ${set.reps} reps"
        }
        holder.setsSummary.text = setsText.ifEmpty { "No sets logged yet." }

        holder.addSetButton.setOnClickListener {
            onAddSetClicked(groupedExercise.exerciseId, groupedExercise.exerciseName)
        }

        holder.duplicateSetButton.setOnClickListener {
            onDuplicateSetClicked(groupedExercise.exerciseId)
        }

        holder.deleteExerciseButton.setOnClickListener {
            onDeleteExerciseClicked(groupedExercise.exerciseId)
        }
    }

    override fun getItemCount() = groupedExercises.size
}
