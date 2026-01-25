package com.liftpath.adapters

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.databinding.ItemSetDetailBinding
import com.liftpath.databinding.ItemGroupedExerciseBinding
import com.liftpath.models.ExerciseEntry
import com.liftpath.models.GroupedExercise
import com.liftpath.models.SetIntent
import com.liftpath.utils.WorkoutTypeFormatter

class TrainingDetailAdapter(
    private val groupedExercises: List<GroupedExercise>,
    private val sessionDefaultType: String?,
    private val onEditSetClicked: (ExerciseEntry) -> Unit,
    private val onEditActivityClicked: (GroupedExercise) -> Unit,
    private val onAddSetClicked: (GroupedExercise) -> Unit
) : RecyclerView.Adapter<TrainingDetailAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGroupedExerciseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val groupedExercise = groupedExercises[position]
        holder.binding.textExerciseName.text = groupedExercise.exerciseName
        // Show dominant intent per exercise instead of workout type
        val dominantIntent = groupedExercise.sets
            .filterNot { it.isWarmup }
            .groupingBy { it.getEffectiveIntent(sessionDefaultType) }
            .eachCount()
            .maxByOrNull { it.value }?.key ?: com.liftpath.models.SetIntent.BUILD
        holder.binding.textExerciseType.text = "Intent: ${dominantIntent.displayName}"
        holder.binding.buttonEditActivity.setOnClickListener {
            onEditActivityClicked(groupedExercise)
        }
        // Remove buttonChangeType - intent is now shown per-set or per-exercise
        holder.binding.buttonChangeType?.visibility = android.view.View.GONE
        holder.binding.buttonAddSet?.setOnClickListener {
            onAddSetClicked(groupedExercise)
        }

        holder.binding.setsContainer.removeAllViews()

        for (set in groupedExercise.sets) {
            val setBinding = ItemSetDetailBinding.inflate(LayoutInflater.from(holder.itemView.context))
            setBinding.textSetDetails.text = formatSetDetails(set)
            
            // Set note separately if it exists
            set.note?.let { note ->
                setBinding.textSetNote.text = note
                setBinding.textSetNote.visibility = android.view.View.VISIBLE
                // Enable marquee scrolling for note after view is added to parent
                setBinding.root.post {
                    setBinding.textSetNote.isSelected = true
                }
            } ?: run {
                setBinding.textSetNote.visibility = android.view.View.GONE
            }
            
            holder.binding.setsContainer.addView(setBinding.root)
        }
    }

    private fun formatSetDetails(set: ExerciseEntry): SpannableString {
        val suffix = when {
            set.isWarmup -> " (W)"
            set.rpe != null -> " (${"%.1f".format(set.rpe)})"
            else -> ""
        }
        val base = "Set ${set.setNumber}: ${set.kg}kg × ${set.reps} reps"
        val text = base + suffix

        val spannable = SpannableString(text)

        // Color-code (W) / (RPE) suffix: warmup stays default, RPE by difficulty
        if (suffix.isNotEmpty() && set.rpe != null) {
            val rpe = set.rpe
            val color = when {
                rpe < 7.0f -> Color.parseColor("#4CAF50")  // Green - easy
                rpe < 8.5f -> Color.parseColor("#FF9800")  // Orange - moderate
                rpe < 9.5f -> Color.parseColor("#F44336")  // Red - hard
                else -> Color.parseColor("#9C27B0")        // Purple - max effort
            }
            spannable.setSpan(
                ForegroundColorSpan(color),
                base.length,
                text.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                base.length,
                text.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return spannable
    }

    override fun getItemCount() = groupedExercises.size

    class ViewHolder(val binding: ItemGroupedExerciseBinding) : RecyclerView.ViewHolder(binding.root)
}

