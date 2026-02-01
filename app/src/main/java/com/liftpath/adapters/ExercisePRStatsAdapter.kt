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

            // Recency tier from lastPrDate: Fresh ≤7d, Improved 8–30d, Older >30d or no PR
            val ctx = itemView.context
            val daysSinceLastPr = if (summary.lastPrDate <= 0) Int.MAX_VALUE
            else ((System.currentTimeMillis() - summary.lastPrDate) / (24 * 60 * 60 * 1000)).toInt()
            val recencyColor = when {
                daysSinceLastPr <= 7 -> ContextCompat.getColor(ctx, R.color.pr_fresh)
                daysSinceLastPr <= 30 -> ContextCompat.getColor(ctx, R.color.pr_improved)
                else -> ContextCompat.getColor(ctx, R.color.pr_older)
            }
            val mutedColor = ContextCompat.getColor(ctx, R.color.fitness_text_secondary)

            // Card stroke: 2dp border in recency color
            val strokeWidthPx = (2 * ctx.resources.displayMetrics.density).toInt()
            cardPrTile.strokeWidth = strokeWidthPx
            cardPrTile.strokeColor = recencyColor

            // Format lastPrDate (Long): recent = "2 days ago", older = "Oct 24, 2025"
            textLastPrDate.text = if (summary.lastPrDate <= 0) {
                ctx.getString(R.string.progress_last_pr) + ": —"
            } else {
                val lastPr = summary.lastPrDate
                val formatted = when {
                    daysSinceLastPr < 0 -> ctx.getString(R.string.progress_last_pr) + ": " + displayDateFormat.format(Date(lastPr))
                    daysSinceLastPr == 0 -> ctx.getString(R.string.progress_last_pr) + ": " + ctx.getString(R.string.progress_today)
                    daysSinceLastPr == 1 -> ctx.getString(R.string.progress_last_pr) + ": " + ctx.getString(R.string.progress_yesterday)
                    daysSinceLastPr in 2..6 -> ctx.getString(R.string.progress_last_pr) + ": " + ctx.getString(R.string.progress_days_ago, daysSinceLastPr)
                    else -> ctx.getString(R.string.progress_last_pr) + ": " + displayDateFormat.format(Date(lastPr))
                }
                formatted
            }
            textLastPrDate.setTextColor(if (summary.lastPrDate > 0) recencyColor else mutedColor)

            // Stats: show value or "—"; color by recency when value present
            val has1rm = summary.best1RM != null
            val hasWeight = summary.bestWeight != null
            val hasVolume = summary.bestVolume != null
            val hasReps = summary.bestRepsRecord != null
            textStat1rm.text = summary.best1RM?.let { String.format(Locale.US, "%.1f kg", it) } ?: "—"
            textStatWeight.text = summary.bestWeight?.let { String.format(Locale.US, "%.1f kg", it) } ?: "—"
            textStatVolume.text = summary.bestVolume?.let { String.format(Locale.US, "%,.0f kg", it) } ?: "—"
            textStatReps.text = summary.bestRepsRecord ?: "—"
            textStat1rm.setTextColor(if (has1rm) recencyColor else mutedColor)
            textStatWeight.setTextColor(if (hasWeight) recencyColor else mutedColor)
            textStatVolume.setTextColor(if (hasVolume) recencyColor else mutedColor)
            textStatReps.setTextColor(if (hasReps) recencyColor else mutedColor)
        }
    }
}
