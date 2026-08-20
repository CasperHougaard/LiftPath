package com.liftpath.adapters

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.R
import com.liftpath.databinding.ItemSetDetailBinding
import com.liftpath.databinding.ItemGroupedExerciseBinding
import com.liftpath.helpers.SetFormatter
import com.liftpath.helpers.lpColor
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
        holder.binding.textExerciseType.text = holder.itemView.context.getString(
            R.string.format_exercise_intent_label, dominantIntent.displayName
        )
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
            setBinding.textSetDetails.text = formatSetDetails(holder.itemView.context, set)
            
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

    private fun formatSetDetails(context: Context, set: ExerciseEntry): CharSequence {
        val suffix = when {
            set.isWarmup -> " (W)"
            set.rpe != null -> " (${"%.1f".format(set.rpe)})"
            else -> ""
        }

        // Load/metric rendering (all four weighted|bodyweight × reps|time combinations) lives in
        // SetFormatter; this adapter only owns the RPE/warmup suffix coloring below.
        val b = SpannableStringBuilder(
            SetFormatter.setLine(context, set, prefix = "Set ${set.setNumber}: ", repsUnit = true)
        )

        val baseEnd = b.length
        b.append(suffix)

        // Color-code (W) / (RPE) suffix: warmup stays default, RPE by difficulty. Only three
        // semantic tiers exist in the token set (lpPositive/lpNeutral/lpNegative), so the old
        // four stock-Material hues (green/orange/red/purple) collapse into three here — hard
        // and max-effort both read as lpNegative, which is the correct signal either way.
        if (suffix.isNotEmpty() && set.rpe != null) {
            val rpe = set.rpe
            val colorAttr = when {
                rpe < 7.0f -> R.attr.lpPositive   // Easy
                rpe < 8.5f -> R.attr.lpNeutral    // Moderate
                else -> R.attr.lpNegative         // Hard / max effort
            }
            b.setSpan(ForegroundColorSpan(context.lpColor(colorAttr)), baseEnd, b.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            b.setSpan(StyleSpan(Typeface.BOLD), baseEnd, b.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return b
    }

    override fun getItemCount() = groupedExercises.size

    class ViewHolder(val binding: ItemGroupedExerciseBinding) : RecyclerView.ViewHolder(binding.root)
}

