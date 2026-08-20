package com.liftpath.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.R
import com.liftpath.helpers.DurationHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.RestTimerHelper
import com.liftpath.helpers.SetMetrics
import com.liftpath.helpers.WorkoutComparisonHelper
import com.liftpath.models.SetIntent
import com.liftpath.models.TrainingSession
import java.text.SimpleDateFormat
import java.util.*
import com.liftpath.helpers.lpColor

class SessionComparisonAdapter(
    private var sessions: List<TrainingSession>,
    private val onSessionClick: (TrainingSession) -> Unit,
    private val allSessions: List<TrainingSession> = emptyList(),
    private val onViewReportClick: (TrainingSession) -> Unit = {}
) : RecyclerView.Adapter<SessionComparisonAdapter.SessionViewHolder>() {

    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("dd", Locale.getDefault())
    
    // Store allSessions as a property for PR calculation
    private val allSessionsForPR: List<TrainingSession> = allSessions

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session_card, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(sessions[position])
    }

    override fun getItemCount(): Int = sessions.size

    fun updateSessions(newSessions: List<TrainingSession>, newAllSessions: List<TrainingSession>? = null) {
        sessions = newSessions
        if (newAllSessions != null) {
            // Note: allSessions is immutable, so we'd need to recreate adapter
            // For now, this method is kept for compatibility but may not work correctly
            // The fragment should recreate the adapter instead
        }
        notifyDataSetChanged()
    }

    inner class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textDay: TextView = itemView.findViewById(R.id.text_day)
        private val textMonth: TextView = itemView.findViewById(R.id.text_month)
        private val textIntentBadge: TextView = itemView.findViewById(R.id.text_intent_badge)
        private val textVolume: TextView = itemView.findViewById(R.id.text_volume)
        private val textExercises: TextView = itemView.findViewById(R.id.text_exercises)
        private val textPrs: TextView = itemView.findViewById(R.id.text_prs)
        private val textSessionSummary: TextView = itemView.findViewById(R.id.text_session_summary)
        private val buttonViewReport: ImageButton = itemView.findViewById(R.id.button_view_report)

        fun bind(session: TrainingSession) {
            // Parse date
            try {
                val date = dateFormat.parse(session.date)
                if (date != null) {
                    textDay.text = dayFormat.format(date)
                    textMonth.text = monthFormat.format(date)
                }
            } catch (e: Exception) {
                textDay.text = "--"
                textMonth.text = ""
            }

            // Dominant intent
            val dominantIntent = session.getDominantIntent()
            textIntentBadge.text = dominantIntent.displayName.uppercase()
            textIntentBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(
                when (dominantIntent) {
                    SetIntent.STRENGTH -> itemView.context.lpColor(R.attr.lpIntentStrength)
                    SetIntent.BUILD -> itemView.context.lpColor(R.attr.lpIntentBuild)
                    SetIntent.FLUSH -> itemView.context.lpColor(R.attr.lpIntentFlush)
                    else -> itemView.context.lpColor(R.attr.lpAccent)
                }
            )

            // Volume (warmups excluded, matching every other volume figure in the app)
            val workingSets = SetMetrics.workingSets(session)
            val totalVolume = SetMetrics.totalVolumeKg(workingSets)
            textVolume.text = String.format(Locale.US, "%,.0fkg", totalVolume)

            // Exercises count
            val uniqueExercises = session.exercises
                .filterNot { it.isWarmup }
                .distinctBy { it.exerciseId }
                .size
            textExercises.text = uniqueExercises.toString()

            // PRs - calculate using WorkoutComparisonHelper
            val prCount = if (allSessionsForPR.isNotEmpty()) {
                val summary = WorkoutComparisonHelper.calculateSessionSummary(session, allSessionsForPR)
                summary.prCount
            } else {
                0
            }
            textPrs.text = prCount.toString()

            // Sets / Avg RPE / Duration / hold-time summary line
            val totalSets = session.exercises.size
            val rpeValues = session.exercises.mapNotNull { it.rpe }
            val avgRpeText = if (rpeValues.isNotEmpty()) {
                String.format(Locale.US, "%.1f", rpeValues.average())
            } else {
                "N/A"
            }
            val durationText = session.durationSeconds?.let { DurationHelper.formatDuration(it) } ?: "N/A"
            val holdSeconds = SetMetrics.totalHoldSeconds(workingSets)
            val summaryParts = mutableListOf(
                "$totalSets set${if (totalSets != 1) "s" else ""}",
                "Avg RPE $avgRpeText",
                durationText
            )
            if (holdSeconds > 0) {
                summaryParts.add(RestTimerHelper.formatHoldTotal(holdSeconds))
            }
            textSessionSummary.text = summaryParts.joinToString(" • ")

            itemView.setOnClickListener {
                onSessionClick(session)
            }
            buttonViewReport.setOnClickListener {
                onViewReportClick(session)
            }
        }
    }
}
