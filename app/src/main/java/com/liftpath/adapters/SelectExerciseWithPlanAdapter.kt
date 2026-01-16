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

sealed class ListItem {
    data class ExerciseItem(val exercise: ExerciseLibraryItem, val isVisible: Boolean = true) : ListItem()
    data class SectionHeader(
        val title: String,
        val sectionId: String,
        val isCollapsed: Boolean = false,
        val onHeaderClick: ((String) -> Unit)? = null
    ) : ListItem()
}

class SelectExerciseWithPlanAdapter(
    var items: List<ListItem>,
    private val planExerciseIds: Set<Int>,
    private val onExerciseClicked: (ExerciseLibraryItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var defaultBackground: android.graphics.drawable.Drawable? = null

    companion object {
        private const val VIEW_TYPE_EXERCISE = 0
        private const val VIEW_TYPE_HEADER = 1
    }

    class ExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val itemViewContainer: View = view.findViewById(R.id.card_view_exercise_item)
        val exerciseName: TextView = view.findViewById(R.id.text_exercise_name)
        val planBadge: ImageView = view.findViewById(R.id.image_plan_badge)
        val favoriteStar: ImageView = view.findViewById(R.id.image_favorite_star)
    }

    class SectionHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sectionTitle: TextView = view.findViewById(R.id.text_section_header)
        val expandIcon: ImageView = view.findViewById(R.id.image_expand_icon)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ListItem.ExerciseItem -> VIEW_TYPE_EXERCISE
            is ListItem.SectionHeader -> VIEW_TYPE_HEADER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_EXERCISE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_select_exercise_with_badge, parent, false)
                val holder = ExerciseViewHolder(view)
                // Save default background on first inflation
                if (defaultBackground == null) {
                    defaultBackground = holder.itemViewContainer.background
                }
                holder
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
            is ListItem.ExerciseItem -> {
                val exerciseHolder = holder as ExerciseViewHolder
                val exercise = item.exercise
                exerciseHolder.exerciseName.text = exercise.name

                // Show/hide favorite star
                if (exercise.isFavorite) {
                    exerciseHolder.favoriteStar.visibility = View.VISIBLE
                } else {
                    exerciseHolder.favoriteStar.visibility = View.GONE
                }

                // Check if exercise is in plan
                val isInPlan = planExerciseIds.contains(exercise.id)
                
                if (isInPlan) {
                    // Show badge and highlight background with light green
                    exerciseHolder.planBadge.visibility = View.VISIBLE
                    val lightGreen = 0xFFE8F5E9.toInt()
                    exerciseHolder.itemViewContainer.background = ColorDrawable(lightGreen)
                } else {
                    // Hide badge and restore default background
                    exerciseHolder.planBadge.visibility = View.GONE
                    exerciseHolder.itemViewContainer.background = defaultBackground?.constantState?.newDrawable()
                }

                // Hide item if section is collapsed
                holder.itemView.visibility = if (item.isVisible) View.VISIBLE else View.GONE
                holder.itemView.layoutParams.height = if (item.isVisible) ViewGroup.LayoutParams.WRAP_CONTENT else 0

                exerciseHolder.itemView.setOnClickListener {
                    onExerciseClicked(exercise)
                }
            }
            is ListItem.SectionHeader -> {
                val headerHolder = holder as SectionHeaderViewHolder
                headerHolder.sectionTitle.text = item.title
                
                // Update expand/collapse icon
                headerHolder.expandIcon.rotation = if (item.isCollapsed) 0f else 180f
                
                // Set click listener
                holder.itemView.setOnClickListener {
                    item.onHeaderClick?.invoke(item.sectionId)
                }
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<ListItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }
}

