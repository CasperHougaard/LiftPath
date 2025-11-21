package com.lilfitness.adapters

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lilfitness.databinding.ItemSetDetailBinding
import com.lilfitness.databinding.ItemGroupedExerciseBinding
import com.lilfitness.models.ExerciseEntry
import com.lilfitness.models.GroupedExercise
import com.lilfitness.utils.WorkoutTypeFormatter

class TrainingDetailAdapter(
    private val groupedExercises: List<GroupedExercise>,
    private val sessionDefaultType: String?,
    private val onEditSetClicked: (ExerciseEntry) -> Unit,
    private val onChangeTypeClicked: (GroupedExercise) -> Unit,
    private val onEditActivityClicked: (GroupedExercise) -> Unit
) : RecyclerView.Adapter<TrainingDetailAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGroupedExerciseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val groupedExercise = groupedExercises[position]
        holder.binding.textExerciseName.text = groupedExercise.exerciseName
        val exerciseType = groupedExercise.sets.firstOrNull()?.workoutType ?: sessionDefaultType
        holder.binding.textExerciseType.text = "Type: ${WorkoutTypeFormatter.label(exerciseType)}"
        holder.binding.buttonEditActivity.setOnClickListener {
            onEditActivityClicked(groupedExercise)
        }
        holder.binding.buttonChangeType.setOnClickListener {
            onChangeTypeClicked(groupedExercise)
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
        val text = buildString {
            append("Set ${set.setNumber}: ${set.kg}kg × ${set.reps} reps")

            // Show RPE if available (new data)
            set.rpe?.let {
                append(" • RPE ${String.format("%.1f", it)}")
            }

            // Show legacy rating if no RPE (old data)
            if (set.rpe == null && set.rating != null) {
                append(" • Rating ${set.rating}/5")
            }
        }

        val spannable = SpannableString(text)

        // Color-code by RPE
        set.rpe?.let { rpe ->
            val color = when {
                rpe < 7.0f -> Color.parseColor("#4CAF50")  // Green - easy
                rpe < 8.5f -> Color.parseColor("#FF9800")  // Orange - moderate
                rpe < 9.5f -> Color.parseColor("#F44336")  // Red - hard
                else -> Color.parseColor("#9C27B0")        // Purple - max effort
            }

            val rpeStart = text.indexOf("RPE")
            if (rpeStart >= 0) {
                val rpeEnd = rpeStart + 8  // "RPE X.X"
                spannable.setSpan(
                    ForegroundColorSpan(color),
                    rpeStart,
                    minOf(rpeEnd, text.length),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    rpeStart,
                    minOf(rpeEnd, text.length),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        return spannable
    }

    override fun getItemCount() = groupedExercises.size

    class ViewHolder(val binding: ItemGroupedExerciseBinding) : RecyclerView.ViewHolder(binding.root)
}

