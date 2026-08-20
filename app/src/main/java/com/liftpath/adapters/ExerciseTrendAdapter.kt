package com.liftpath.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.liftpath.R
import com.liftpath.databinding.ItemExerciseTrendBinding
import com.liftpath.helpers.RestTimerHelper
import com.liftpath.models.ExerciseTrendData
import com.liftpath.models.SetIntent
import java.util.Locale
import com.liftpath.helpers.lpColor

class ExerciseTrendAdapter(
    private val trends: List<ExerciseTrendData>
) : RecyclerView.Adapter<ExerciseTrendAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExerciseTrendBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val trend = trends[position]
        val context = holder.itemView.context

        // Exercise name
        holder.binding.textExerciseName.text = trend.exerciseName

        // Intent badge
        holder.binding.textIntentBadge.text = trend.intent.displayName.uppercase()
        val badgeColor = when (trend.intent) {
            SetIntent.STRENGTH -> context.lpColor(R.attr.lpIntentStrength)
            SetIntent.BUILD -> context.lpColor(R.attr.lpIntentBuild)
            SetIntent.FLUSH -> context.lpColor(R.attr.lpIntentFlush)
            else -> context.lpColor(R.attr.lpAccent)
        }
        holder.binding.textIntentBadge.backgroundTintList =
            android.content.res.ColorStateList.valueOf(badgeColor)

        // All-time PR star badge (canonical only)
        holder.binding.imagePrBadge.visibility =
            if (trend.hasNewAllTimePR) View.VISIBLE else View.GONE

        // Card border recency from most recent all-time PR (weight, volume, 1RM, hold)
        val mostRecentPrDate =
            maxOf(trend.prWeightDate, trend.prVolumeDate, trend.pr1RMDate, trend.prHoldDate)
        val daysSinceLastPr = if (mostRecentPrDate <= 0) Int.MAX_VALUE
        else ((System.currentTimeMillis() - mostRecentPrDate) / (24 * 60 * 60 * 1000)).toInt()
        val recencyColor = when {
            daysSinceLastPr <= 7 -> context.lpColor(R.attr.lpPositive)
            daysSinceLastPr <= 30 -> context.lpColor(R.attr.lpAccent)
            else -> context.lpColor(R.attr.lpInkTertiary)
        }
        val cardView = holder.binding.root as MaterialCardView
        val strokeWidthPx = (2 * context.resources.displayMetrics.density).toInt()
        cardView.strokeWidth = strokeWidthPx
        cardView.strokeColor = recencyColor

        // --- Headline metric: hold time for timed exercises, volume for everything else ---
        // A timed hold has no rep-based volume, so reporting "0kg" here would read as no work done.
        holder.binding.textVolumeLabel.setText(
            if (trend.isTimedExercise) R.string.total_hold_time_label else R.string.volume_label
        )
        if (trend.isTimedExercise) {
            val best = trend.currentBestHoldSeconds ?: 0
            holder.binding.textVolumeCurrent.text = context.getString(
                R.string.hold_best_and_total,
                RestTimerHelper.formatDuration(best),
                RestTimerHelper.formatHoldTotal(trend.currentTotalHoldSeconds)
            )

            val prevBest = trend.previousBestHoldSeconds
            if (prevBest != null && prevBest > 0) {
                holder.binding.textVolumeComparison.text =
                    "vs ${RestTimerHelper.formatDuration(prevBest)}"
                holder.binding.textVolumeComparison.visibility = View.VISIBLE

                val holdChange = ((best - prevBest).toFloat() / prevBest) * 100f
                holder.binding.textVolumeChange.text =
                    String.format(Locale.US, "%+.1f%%", holdChange)
                holder.binding.textVolumeChange.visibility = View.VISIBLE
                holder.binding.textVolumeChange.setTextColor(getChangeColor(context, holdChange))

                holder.binding.textFirstTimeNote.visibility = View.GONE
            } else {
                holder.binding.textVolumeComparison.visibility = View.GONE
                holder.binding.textVolumeChange.visibility = View.GONE
                holder.binding.textFirstTimeNote.visibility =
                    if (trend.intentSessionCount == 0) View.VISIBLE else View.GONE
            }
        } else {
            val volumeFormatted = String.format(Locale.US, "%,dkg", trend.currentVolume.toInt())
            holder.binding.textVolumeCurrent.text = volumeFormatted

            if (trend.previousVolume != null) {
                val prevVolumeFormatted =
                    String.format(Locale.US, "%,dkg", trend.previousVolume.toInt())
                holder.binding.textVolumeComparison.text = "vs $prevVolumeFormatted"
                holder.binding.textVolumeComparison.visibility = View.VISIBLE

                val volumeChange =
                    ((trend.currentVolume - trend.previousVolume) / trend.previousVolume) * 100f
                holder.binding.textVolumeChange.text =
                    String.format(Locale.US, "%+.1f%%", volumeChange)
                holder.binding.textVolumeChange.visibility = View.VISIBLE
                holder.binding.textVolumeChange.setTextColor(getChangeColor(context, volumeChange))

                holder.binding.textFirstTimeNote.visibility = View.GONE
            } else {
                holder.binding.textVolumeComparison.visibility = View.GONE
                holder.binding.textVolumeChange.visibility = View.GONE
                // Show baseline note when no prior same-intent session exists
                holder.binding.textFirstTimeNote.visibility =
                    if (trend.intentSessionCount == 0) View.VISIBLE else View.GONE
            }
        }

        // --- 1RM comparison (rep-based only) ---
        if (trend.currentEstimated1RM != null) {
            holder.binding.layout1rm.visibility = View.VISIBLE
            holder.binding.text1rmCurrent.text =
                String.format(Locale.US, "%.1fkg", trend.currentEstimated1RM)

            if (trend.previousEstimated1RM != null) {
                holder.binding.text1rmComparison.text =
                    String.format(Locale.US, "vs %.1fkg", trend.previousEstimated1RM)
                holder.binding.text1rmComparison.visibility = View.VISIBLE

                val rmChange =
                    ((trend.currentEstimated1RM - trend.previousEstimated1RM) / trend.previousEstimated1RM) * 100f
                holder.binding.text1rmChange.text =
                    String.format(Locale.US, "%+.1f%%", rmChange)
                holder.binding.text1rmChange.visibility = View.VISIBLE
                holder.binding.text1rmChange.setTextColor(getChangeColor(context, rmChange))
            } else {
                holder.binding.text1rmComparison.visibility = View.GONE
                holder.binding.text1rmChange.visibility = View.GONE
            }
        } else {
            holder.binding.layout1rm.visibility = View.GONE
        }

        // --- Top set comparison, or load-seconds for a weighted hold ---
        holder.binding.textTopSetLabel.setText(
            if (trend.isTimedExercise) R.string.load_time_label else R.string.top_set_label
        )
        if (trend.isTimedExercise) {
            // A weighted plank progresses by load as well as duration; kg·s captures both.
            val loadSeconds = trend.currentLoadSeconds
            if (loadSeconds != null && loadSeconds > 0f) {
                holder.binding.layoutTopSet.visibility = View.VISIBLE
                holder.binding.textTopSetCurrent.text = context.getString(
                    R.string.hold_load_seconds,
                    String.format(Locale.US, "%,d", loadSeconds.toInt())
                )
                holder.binding.textTopSetComparison.visibility = View.GONE
            } else {
                holder.binding.layoutTopSet.visibility = View.GONE
            }
        } else if (trend.currentTopSet != null) {
            holder.binding.layoutTopSet.visibility = View.VISIBLE
            val (kg, reps) = trend.currentTopSet
            holder.binding.textTopSetCurrent.text =
                String.format(Locale.US, "%.1fkg × %d", kg, reps)

            if (trend.previousTopSet != null) {
                val (prevKg, prevReps) = trend.previousTopSet
                holder.binding.textTopSetComparison.text =
                    String.format(Locale.US, "vs %.1fkg × %d", prevKg, prevReps)
                holder.binding.textTopSetComparison.visibility = View.VISIBLE
            } else {
                holder.binding.textTopSetComparison.visibility = View.GONE
            }
        } else {
            holder.binding.layoutTopSet.visibility = View.GONE
        }

        // --- All-time Records section (canonical PRs only, no reps) ---
        val hasPRs = trend.prWeight != null || trend.prVolume != null || trend.pr1RM != null ||
            trend.prHoldSeconds != null

        if (hasPRs) {
            holder.binding.dividerPrs.visibility = View.VISIBLE
            holder.binding.textPrsTitle.visibility = View.VISIBLE
            holder.binding.layoutPrGrid.visibility = View.VISIBLE

            if (trend.prWeight != null) {
                holder.binding.layoutPrWeight.visibility = View.VISIBLE
                holder.binding.textPrWeight.text =
                    String.format(Locale.US, "%.1fkg", trend.prWeight)
                holder.binding.textPrWeight.setTextColor(
                    getPrRecencyColor(context, getDaysSince(trend.prWeightDate))
                )
            } else {
                holder.binding.layoutPrWeight.visibility = View.GONE
            }

            if (trend.prVolume != null) {
                holder.binding.layoutPrVolume.visibility = View.VISIBLE
                holder.binding.textPrVolume.text =
                    String.format(Locale.US, "%,dkg", trend.prVolume.toInt())
                holder.binding.textPrVolume.setTextColor(
                    getPrRecencyColor(context, getDaysSince(trend.prVolumeDate))
                )
            } else {
                holder.binding.layoutPrVolume.visibility = View.GONE
            }

            if (trend.pr1RM != null) {
                holder.binding.layoutPr1rm.visibility = View.VISIBLE
                holder.binding.textPr1rm.text =
                    String.format(Locale.US, "%.1fkg", trend.pr1RM)
                holder.binding.textPr1rm.setTextColor(
                    getPrRecencyColor(context, getDaysSince(trend.pr1RMDate))
                )
            } else {
                holder.binding.layoutPr1rm.visibility = View.GONE
            }

            // Reps PRs are excluded from the canonical PR system; this slot now carries the
            // longest-hold record for timed exercises (see item_exercise_trend.xml).
            if (trend.prHoldSeconds != null) {
                holder.binding.layoutPrReps.visibility = View.VISIBLE
                holder.binding.textPrReps.text =
                    RestTimerHelper.formatDuration(trend.prHoldSeconds)
                holder.binding.textPrReps.setTextColor(
                    getPrRecencyColor(context, getDaysSince(trend.prHoldDate))
                )
            } else {
                holder.binding.layoutPrReps.visibility = View.GONE
            }
        } else {
            holder.binding.dividerPrs.visibility = View.GONE
            holder.binding.textPrsTitle.visibility = View.GONE
            holder.binding.layoutPrGrid.visibility = View.GONE
        }
    }

    override fun getItemCount() = trends.size

    private fun getChangeColor(context: android.content.Context, changePercent: Float): Int {
        return when {
            changePercent > 1f -> context.lpColor(R.attr.lpPositive)
            changePercent < -1f -> context.lpColor(R.attr.lpNegative)
            else -> context.lpColor(R.attr.lpInkSecondary)
        }
    }

    private fun getPrRecencyColor(context: android.content.Context, daysSince: Int): Int {
        return when {
            daysSince <= 7 -> context.lpColor(R.attr.lpPositive)
            daysSince <= 30 -> context.lpColor(R.attr.lpAccent)
            else -> context.lpColor(R.attr.lpInkTertiary)
        }
    }

    private fun getDaysSince(timestamp: Long): Int {
        return if (timestamp <= 0) Int.MAX_VALUE
        else ((System.currentTimeMillis() - timestamp) / (24 * 60 * 60 * 1000)).toInt()
    }

    class ViewHolder(val binding: ItemExerciseTrendBinding) :
        RecyclerView.ViewHolder(binding.root)
}
