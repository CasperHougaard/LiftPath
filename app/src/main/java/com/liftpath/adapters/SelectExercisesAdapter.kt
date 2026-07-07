package com.liftpath.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.R
import com.liftpath.models.ExerciseFamily
import com.liftpath.models.ExerciseLibraryItem

sealed class SelectListItem {
    data class ExerciseItem(val exercise: ExerciseLibraryItem) : SelectListItem()
    data class SectionHeader(val title: String) : SelectListItem()
}

class SelectExercisesAdapter(
    exercises: List<ExerciseLibraryItem> = emptyList(),
    private val preselectedIds: Set<Int>,
    private val onSelectionChanged: (Int, Boolean) -> Unit,
    private var families: List<ExerciseFamily> = emptyList()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<SelectListItem> = exercises.map { SelectListItem.ExerciseItem(it) }
    private val selectedIds = preselectedIds.toMutableSet()
    private val familyNameMap: Map<String, String> get() = families.associate { it.id to it.name }

    companion object {
        private const val VIEW_TYPE_EXERCISE = 0
        private const val VIEW_TYPE_HEADER = 1
    }

    class ExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val illustration: ImageView = view.findViewById(R.id.image_exercise_illustration)
        val checkbox: CheckBox = view.findViewById(R.id.checkbox_exercise)
        val exerciseName: TextView = view.findViewById(R.id.text_exercise_name)
        val exerciseMeta: TextView = view.findViewById(R.id.text_exercise_meta)
    }

    class SectionHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sectionTitle: TextView = view.findViewById(R.id.text_section_header)
        val expandIcon: ImageView = view.findViewById(R.id.image_expand_icon)
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is SelectListItem.ExerciseItem -> VIEW_TYPE_EXERCISE
        is SelectListItem.SectionHeader -> VIEW_TYPE_HEADER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_EXERCISE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_select_exercise_checkbox, parent, false)
                ExerciseViewHolder(view)
            }
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_section_header, parent, false)
                SectionHeaderViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is SelectListItem.ExerciseItem -> {
                val exerciseHolder = holder as ExerciseViewHolder
                val exercise = item.exercise
                exerciseHolder.illustration.setImageResource(exercise.illustrationRes ?: R.drawable.ic_dumbbell)
                exerciseHolder.exerciseName.text = exercise.name

                val metaParts = listOfNotNull(
                    exercise.equipment?.displayName,
                    exercise.familyId?.let { familyNameMap[it] }
                )
                if (metaParts.isNotEmpty()) {
                    exerciseHolder.exerciseMeta.text = metaParts.joinToString(" · ")
                    exerciseHolder.exerciseMeta.visibility = View.VISIBLE
                } else {
                    exerciseHolder.exerciseMeta.visibility = View.GONE
                }

                exerciseHolder.checkbox.setOnCheckedChangeListener(null)
                exerciseHolder.checkbox.isChecked = selectedIds.contains(exercise.id)

                exerciseHolder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedIds.add(exercise.id)
                    else selectedIds.remove(exercise.id)
                    onSelectionChanged(exercise.id, isChecked)
                }

                exerciseHolder.itemView.setOnClickListener {
                    exerciseHolder.checkbox.isChecked = !exerciseHolder.checkbox.isChecked
                }
            }
            is SelectListItem.SectionHeader -> {
                val headerHolder = holder as SectionHeaderViewHolder
                headerHolder.sectionTitle.text = item.title
                headerHolder.expandIcon.visibility = View.GONE
            }
        }
    }

    override fun getItemCount() = items.size

    fun getSelectedIds(): List<Int> = selectedIds.toList()

    fun updateItems(newItems: List<SelectListItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    fun updateExercises(exercises: List<ExerciseLibraryItem>) {
        updateItems(exercises.map { SelectListItem.ExerciseItem(it) })
    }

    fun updateFamilies(newFamilies: List<ExerciseFamily>) {
        this.families = newFamilies
        notifyDataSetChanged()
    }
}
