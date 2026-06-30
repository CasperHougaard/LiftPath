package com.liftpath.adapters

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.databinding.ItemSetDetailBinding
import com.liftpath.databinding.ItemGroupedExerciseBinding
import com.liftpath.helpers.RestTimerHelper
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

    private fun formatSetDetails(set: ExerciseEntry): CharSequence {
        val suffix = when {
            set.isWarmup -> " (W)"
            set.rpe != null -> " (${"%.1f".format(set.rpe)})"
            else -> ""
        }

        val b = SpannableStringBuilder()
        b.append("Set ${set.setNumber}: ")

        if (set.isTimedEntry()) {
            // Timed hold: show duration (m:ss), with weight appended only when present.
            b.append(RestTimerHelper.formatDuration(set.durationSeconds ?: 0))
            if (set.kg > 0f) {
                val kgStr = if (set.kg % 1 == 0f) set.kg.toInt().toString() else "%.1f".format(set.kg)
                b.append(" + ${kgStr}kg")
            }
        } else if (set.isBodyweightEntry()) {
            // Body weight muted (1 decimal); signed added/assisted weight in green +/red − so the
            // progression (the added part) is readable even when body weight differs between workouts.
            val bw = set.bodyweightKg ?: 0f
            val added = set.addedKg ?: 0f
            val bwStart = b.length
            b.append("%.1f".format(bw))
            b.setSpan(ForegroundColorSpan(Color.parseColor("#6B7280")), bwStart, b.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (added != 0f) {
                val sign = if (added > 0f) "+" else "−"
                val mag = if (added < 0f) -added else added
                val magStr = if (mag % 1 == 0f) mag.toInt().toString() else "%.1f".format(mag)
                val aStart = b.length
                b.append(" $sign$magStr")
                val aColor = if (added > 0f) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
                b.setSpan(ForegroundColorSpan(aColor), aStart, b.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                b.setSpan(StyleSpan(Typeface.BOLD), aStart, b.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            b.append("kg × ${set.reps} reps")
        } else {
            b.append("${set.kg}kg × ${set.reps} reps")
        }

        val baseEnd = b.length
        b.append(suffix)

        // Color-code (W) / (RPE) suffix: warmup stays default, RPE by difficulty
        if (suffix.isNotEmpty() && set.rpe != null) {
            val rpe = set.rpe
            val color = when {
                rpe < 7.0f -> Color.parseColor("#4CAF50")  // Green - easy
                rpe < 8.5f -> Color.parseColor("#FF9800")  // Orange - moderate
                rpe < 9.5f -> Color.parseColor("#F44336")  // Red - hard
                else -> Color.parseColor("#9C27B0")        // Purple - max effort
            }
            b.setSpan(ForegroundColorSpan(color), baseEnd, b.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            b.setSpan(StyleSpan(Typeface.BOLD), baseEnd, b.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return b
    }

    override fun getItemCount() = groupedExercises.size

    class ViewHolder(val binding: ItemGroupedExerciseBinding) : RecyclerView.ViewHolder(binding.root)
}

