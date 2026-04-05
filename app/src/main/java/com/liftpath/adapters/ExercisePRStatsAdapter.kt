package com.liftpath.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.liftpath.R
import com.liftpath.helpers.ProgressAnalysisHelper
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for the PR page "Player Stats Card" list.
 * Binds [ProgressAnalysisHelper.ExerciseStatsSummary]; formats [lastPrDate] (Long) as "2 days ago" or "Oct 24, 2025".
 */
class ExercisePRStatsAdapter(
    private var summaries: List<ProgressAnalysisHelper.ExerciseStatsSummary>
) : RecyclerView.Adapter<ExercisePRStatsAdapter.ViewHolder>() {

    private val displayDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exercise_pr_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(summaries[position])
    }

    override fun getItemCount(): Int = summaries.size

    fun updateSummaries(newSummaries: List<ProgressAnalysisHelper.ExerciseStatsSummary>) {
        summaries = newSummaries
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardPrTile: MaterialCardView = itemView.findViewById(R.id.card_pr_tile)
        private val textExerciseName: TextView = itemView.findViewById(R.id.text_exercise_name)
        private val textLastPrDate: TextView = itemView.findViewById(R.id.text_last_pr_date)
        private val textStat1rm: TextView = itemView.findViewById(R.id.text_stat_1rm)
        private val textStatWeight: TextView = itemView.findViewById(R.id.text_stat_weight)
        private val textStatVolume: TextView = itemView.findViewById(R.id.text_stat_volume)
        private val textStatReps: TextView = itemView.findViewById(R.id.text_stat_reps)

        fun bind(summary: ProgressAnalysisHelper.ExerciseStatsSummary) {
            textExerciseName.text = summary.exerciseName

            val ctx = itemView.context
            val mutedColor = ContextCompat.getColor(ctx, R.color.fitness_text_secondary)

            fun daysSince(timestamp: Long) =
                if (timestamp <= 0) Int.MAX_VALUE
                else ((System.currentTimeMillis() - timestamp) / (24 * 60 * 60 * 1000)).toInt()

            fun recencyColor(daysSince: Int) = when {
                daysSince <= 7  -> ContextCompat.getColor(ctx, R.color.pr_fresh)
                daysSince <= 30 -> ContextCompat.getColor(ctx, R.color.pr_improved)
                else            -> ContextCompat.getColor(ctx, R.color.pr_older)
            }

            // Card border uses the most recent PR date across all types
            val overallDays = daysSince(summary.lastPrDate)
            val borderColor = recencyColor(overallDays)
            val strokeWidthPx = (2 * ctx.resources.displayMetrics.density).toInt()
            cardPrTile.strokeWidth = strokeWidthPx
            cardPrTile.strokeColor = borderColor

            // "Last PR" header text
            textLastPrDate.text = if (summary.lastPrDate <= 0) {
                ctx.getString(R.string.progress_last_pr) + ": —"
            } else {
                val formatted = when (overallDays) {
                    in Int.MIN_VALUE..-1 -> displayDateFormat.format(Date(summary.lastPrDate))
                    0    -> ctx.getString(R.string.progress_today)
                    1    -> ctx.getString(R.string.progress_yesterday)
                    in 2..6 -> ctx.getString(R.string.progress_days_ago, overallDays)
                    else -> displayDateFormat.format(Date(summary.lastPrDate))
                }
                ctx.getString(R.string.progress_last_pr) + ": " + formatted
            }
            textLastPrDate.setTextColor(if (summary.lastPrDate > 0) borderColor else mutedColor)

            // Each stat uses its own per-type PR date for color so a fresh Volume PR
            // doesn't make an old Weight PR glow green.
            textStat1rm.text   = summary.best1RM?.let { String.format(Locale.US, "%.1f kg", it) } ?: "—"
            textStatWeight.text = summary.bestWeight?.let { String.format(Locale.US, "%.1f kg", it) } ?: "—"
            textStatVolume.text = summary.bestVolume?.let { String.format(Locale.US, "%,.0f kg", it) } ?: "—"
            textStatReps.text  = "—"

            textStat1rm.setTextColor(
                if (summary.best1RM != null) recencyColor(daysSince(summary.last1RMPrDate)) else mutedColor
            )
            textStatWeight.setTextColor(
                if (summary.bestWeight != null) recencyColor(daysSince(summary.lastWeightPrDate)) else mutedColor
            )
            textStatVolume.setTextColor(
                if (summary.bestVolume != null) recencyColor(daysSince(summary.lastVolumePrDate)) else mutedColor
            )
            textStatReps.setTextColor(mutedColor)
        }
    }
}
