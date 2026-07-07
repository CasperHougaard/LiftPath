package com.liftpath.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.R
import com.liftpath.models.ExerciseFamily
import com.liftpath.models.ExerciseLibraryItem

class ExerciseLibraryAdapter(
    private var exercises: List<ExerciseLibraryItem>,
    private val onEditClicked: (ExerciseLibraryItem) -> Unit,
    private var families: List<ExerciseFamily> = emptyList()
) : RecyclerView.Adapter<ExerciseLibraryAdapter.ExerciseViewHolder>() {

    class ExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val illustration: ImageView = view.findViewById(R.id.image_exercise_illustration)
        val exerciseName: TextView = view.findViewById(R.id.text_exercise_name)
        val exerciseMeta: TextView = view.findViewById(R.id.text_exercise_meta)
        val favoriteStar: ImageView = view.findViewById(R.id.image_favorite_star)
        val editButton: CardView = view.findViewById(R.id.button_edit_exercise)
    }

    private val familyNameMap: Map<String, String> get() = families.associate { it.id to it.name }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_exercise_library, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        val exercise = exercises[position]
        holder.illustration.setImageResource(exercise.illustrationRes ?: R.drawable.ic_dumbbell)
        holder.exerciseName.text = exercise.name

        val metaParts = listOfNotNull(
            exercise.equipment?.displayName,
            exercise.familyId?.let { familyNameMap[it] }
        )
        if (metaParts.isNotEmpty()) {
            holder.exerciseMeta.text = metaParts.joinToString(" · ")
            holder.exerciseMeta.visibility = View.VISIBLE
        } else {
            holder.exerciseMeta.visibility = View.GONE
        }

        holder.favoriteStar.visibility = if (exercise.isFavorite) View.VISIBLE else View.GONE

        holder.editButton.setOnClickListener {
            onEditClicked(exercise)
        }
    }

    override fun getItemCount() = exercises.size

    fun updateExercises(newExercises: List<ExerciseLibraryItem>) {
        this.exercises = newExercises
        notifyDataSetChanged()
    }

    fun updateFamilies(newFamilies: List<ExerciseFamily>) {
        this.families = newFamilies
        notifyDataSetChanged()
    }
}
