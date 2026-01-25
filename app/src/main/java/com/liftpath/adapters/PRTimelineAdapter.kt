package com.liftpath.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.R
import com.liftpath.helpers.ProgressAnalysisHelper
import com.liftpath.helpers.ProgressAnalysisHelper.PRType
import com.liftpath.models.SetIntent
import java.text.SimpleDateFormat
import java.util.*

class PRTimelineAdapter(
    private var prs: List<ProgressAnalysisHelper.PRRecord>
) : RecyclerView.Adapter<PRTimelineAdapter.PRViewHolder>() {

    private val inputDateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PRViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pr_timeline, parent, false)
        return PRViewHolder(view)
    }

    override fun onBindViewHolder(holder: PRViewHolder, position: Int) {
        holder.bind(prs[position])
    }

    override fun getItemCount(): Int = prs.size

    fun updatePRs(newPRs: List<ProgressAnalysisHelper.PRRecord>) {
        prs = newPRs
        notifyDataSetChanged()
    }

    inner class PRViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imagePrIcon: ImageView = itemView.findViewById(R.id.image_pr_icon)
        private val textExerciseName: TextView = itemView.findViewById(R.id.text_exercise_name)
        private val textIntentBadge: TextView = itemView.findViewById(R.id.text_intent_badge)
        private val textPrType: TextView = itemView.findViewById(R.id.text_pr_type)
        private val textPrValue: TextView = itemView.findViewById(R.id.text_pr_value)
        private val textImprovement: TextView = itemView.findViewById(R.id.text_improvement)
        private val textDate: TextView = itemView.findViewById(R.id.text_date)

        fun bind(pr: ProgressAnalysisHelper.PRRecord) {
            textExerciseName.text = pr.exerciseName

            // Intent badge
            textIntentBadge.text = pr.intent.displayName.uppercase()
            textIntentBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(
                when (pr.intent) {
                    SetIntent.STRENGTH -> itemView.context.getColor(R.color.intent_strength)
                    SetIntent.BUILD -> itemView.context.getColor(R.color.intent_build)
                    SetIntent.FLUSH -> itemView.context.getColor(R.color.intent_flush)
                    else -> itemView.context.getColor(R.color.fitness_primary)
                }
            )

            // PR type and value
            when (pr.prType) {
                PRType.WEIGHT -> {
                    textPrType.text = "Weight PR"
                    textPrValue.text = String.format(Locale.US, "%.1fkg", pr.value)
                }
                PRType.VOLUME -> {
                    textPrType.text = "Volume PR"
                    textPrValue.text = String.format(Locale.US, "%.0fkg", pr.value)
                }
                PRType.ONE_RM -> {
                    textPrType.text = "1RM PR"
                    textPrValue.text = String.format(Locale.US, "%.1fkg", pr.value)
                }
                PRType.REPS -> {
                    textPrType.text = "Reps PR"
                    textPrValue.text = String.format(Locale.US, "%.0f reps", pr.value)
                }
            }

            // Improvement (if available)
            if (pr.previousValue != null && pr.previousValue > 0) {
                val improvement = pr.value - pr.previousValue
                textImprovement.visibility = View.VISIBLE
                textImprovement.text = String.format(Locale.US, "+%.1f", improvement)
            } else {
                textImprovement.visibility = View.GONE
            }

            // Date
            try {
                val date = inputDateFormat.parse(pr.date)
                if (date != null) {
                    textDate.text = displayDateFormat.format(date)
                }
            } catch (e: Exception) {
                textDate.text = pr.date
            }

            // Icon tint based on PR type
            imagePrIcon.backgroundTintList = android.content.res.ColorStateList.valueOf(
                when (pr.prType) {
                    PRType.WEIGHT -> itemView.context.getColor(R.color.intent_strength)
                    PRType.VOLUME -> itemView.context.getColor(R.color.intent_build)
                    PRType.ONE_RM -> itemView.context.getColor(R.color.fitness_accent)
                    PRType.REPS -> itemView.context.getColor(R.color.intent_flush)
                }
            )
        }
    }
}
