package com.lilfitness.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.lilfitness.R
import com.lilfitness.models.ExerciseEntry
import com.lilfitness.models.GroupedExercise

class ActiveExercisesAdapter(
    private val groupedExercises: List<GroupedExercise>,
    private val onAddSetClicked: (exerciseId: Int, exerciseName: String) -> Unit,
    private val onEditActivityClicked: (GroupedExercise) -> Unit,
    private val onDuplicateSetClicked: (exerciseId: Int) -> Unit,
    private val onDeleteExerciseClicked: (exerciseId: Int) -> Unit
) : RecyclerView.Adapter<ActiveExercisesAdapter.GroupedExerciseViewHolder>() {

    class GroupedExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val exerciseName: TextView = view.findViewById(R.id.text_exercise_name)
        val setsSummary: TextView = view.findViewById(R.id.text_sets_summary)
        val addSetButton: CardView = view.findViewById(R.id.button_add_set)
        val duplicateSetButton: CardView = view.findViewById(R.id.button_duplicate_set)
        val editActivityButton: CardView = view.findViewById(R.id.button_edit_activity)
        val deleteExerciseButton: CardView = view.findViewById(R.id.button_delete_exercise)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupedExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_active_exercise, parent, false)
        return GroupedExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupedExerciseViewHolder, position: Int) {
        val groupedExercise = groupedExercises[position]
        holder.exerciseName.text = groupedExercise.exerciseName

        // Format sets vertically, one per line
        val hasSets = groupedExercise.sets.isNotEmpty()
        if (hasSets) {
            val setsText = groupedExercise.sets.joinToString("\n") { set ->
                "Set ${set.setNumber}: ${set.kg}kg × ${set.reps}"
            }
            holder.setsSummary.text = setsText
        } else {
            holder.setsSummary.text = "No sets logged yet"
        }

        // Show/hide buttons based on whether there are sets
        holder.duplicateSetButton.visibility = if (hasSets) View.VISIBLE else View.GONE
        holder.editActivityButton.visibility = if (hasSets) View.VISIBLE else View.GONE
        holder.deleteExerciseButton.visibility = if (hasSets) View.VISIBLE else View.GONE

        holder.addSetButton.setOnClickListener {
            onAddSetClicked(groupedExercise.exerciseId, groupedExercise.exerciseName)
        }

        holder.duplicateSetButton.setOnClickListener {
            onDuplicateSetClicked(groupedExercise.exerciseId)
        }

        holder.editActivityButton.setOnClickListener {
            onEditActivityClicked(groupedExercise)
        }

        holder.deleteExerciseButton.setOnClickListener {
            onDeleteExerciseClicked(groupedExercise.exerciseId)
        }
    }

    override fun getItemCount() = groupedExercises.size
}
