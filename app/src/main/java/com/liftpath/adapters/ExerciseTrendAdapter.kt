package com.liftpath.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.liftpath.R
import com.liftpath.databinding.ItemExerciseTrendBinding
import com.liftpath.models.ExerciseTrendData
import com.liftpath.models.SetIntent
import java.util.Locale

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
            SetIntent.STRENGTH -> ContextCompat.getColor(context, R.color.intent_strength)
            SetIntent.BUILD -> ContextCompat.getColor(context, R.color.intent_build)
            SetIntent.FLUSH -> ContextCompat.getColor(context, R.color.intent_flush)
            else -> ContextCompat.getColor(context, R.color.fitness_primary)
        }
        holder.binding.textIntentBadge.backgroundTintList = 
            android.content.res.ColorStateList.valueOf(badgeColor)
        
        // PR badge
        holder.binding.imagePrBadge.visibility = if (trend.isPR) View.VISIBLE else View.GONE
        
        // Calculate recency for border color
        val mostRecentPrDate = maxOf(
            trend.prWeightDate,
            trend.prVolumeDate,
            trend.pr1RMDate,
            trend.prRepsDate
        )
        val daysSinceLastPr = if (mostRecentPrDate <= 0) Int.MAX_VALUE
        else ((System.currentTimeMillis() - mostRecentPrDate) / (24 * 60 * 60 * 1000)).toInt()
        
        val recencyColor = when {
            daysSinceLastPr <= 7 -> ContextCompat.getColor(context, R.color.pr_fresh)
            daysSinceLastPr <= 30 -> ContextCompat.getColor(context, R.color.pr_improved)
            else -> ContextCompat.getColor(context, R.color.pr_older)
        }
        
        // Set card stroke (2dp border)
        val cardView = holder.binding.root as MaterialCardView
        val strokeWidthPx = (2 * context.resources.displayMetrics.density).toInt()
        cardView.strokeWidth = strokeWidthPx
        cardView.strokeColor = recencyColor
        
        // Volume comparison
        val volumeFormatted = String.format(Locale.US, "%,dkg", trend.currentVolume.toInt())
        holder.binding.textVolumeCurrent.text = volumeFormatted
        
        if (trend.previousVolume != null) {
            val prevVolumeFormatted = String.format(Locale.US, "%,dkg", trend.previousVolume.toInt())
            holder.binding.textVolumeComparison.text = "vs $prevVolumeFormatted"
            holder.binding.textVolumeComparison.visibility = View.VISIBLE
            
            val volumeChange = ((trend.currentVolume - trend.previousVolume) / trend.previousVolume) * 100f
            holder.binding.textVolumeChange.text = String.format(Locale.US, "%+.1f%%", volumeChange)
            holder.binding.textVolumeChange.visibility = View.VISIBLE
            holder.binding.textVolumeChange.setTextColor(getChangeColor(context, volumeChange))
            
            holder.binding.textFirstTimeNote.visibility = View.GONE
        } else {
            holder.binding.textVolumeComparison.visibility = View.GONE
            holder.binding.textVolumeChange.visibility = View.GONE
            holder.binding.textFirstTimeNote.visibility = View.GONE
        }
        
        // 1RM comparison
        if (trend.currentEstimated1RM != null) {
            holder.binding.layout1rm.visibility = View.VISIBLE
            holder.binding.text1rmCurrent.text = String.format(Locale.US, "%.1fkg", trend.currentEstimated1RM)
            
            if (trend.previousEstimated1RM != null) {
                holder.binding.text1rmComparison.text = String.format(Locale.US, "vs %.1fkg", trend.previousEstimated1RM)
                holder.binding.text1rmComparison.visibility = View.VISIBLE
                
                val rmChange = ((trend.currentEstimated1RM - trend.previousEstimated1RM) / trend.previousEstimated1RM) * 100f
                holder.binding.text1rmChange.text = String.format(Locale.US, "%+.1f%%", rmChange)
                holder.binding.text1rmChange.visibility = View.VISIBLE
                holder.binding.text1rmChange.setTextColor(getChangeColor(context, rmChange))
            } else {
                holder.binding.text1rmComparison.visibility = View.GONE
                holder.binding.text1rmChange.visibility = View.GONE
            }
        } else {
            holder.binding.layout1rm.visibility = View.GONE
        }
        
        // Top set comparison
        if (trend.currentTopSet != null) {
            holder.binding.layoutTopSet.visibility = View.VISIBLE
            val (kg, reps) = trend.currentTopSet
            holder.binding.textTopSetCurrent.text = String.format(Locale.US, "%.1fkg × %d", kg, reps)
            
            if (trend.previousTopSet != null) {
                val (prevKg, prevReps) = trend.previousTopSet
                holder.binding.textTopSetComparison.text = String.format(Locale.US, "vs %.1fkg × %d", prevKg, prevReps)
                holder.binding.textTopSetComparison.visibility = View.VISIBLE
            } else {
                holder.binding.textTopSetComparison.visibility = View.GONE
            }
        } else {
            holder.binding.layoutTopSet.visibility = View.GONE
        }
        
        // Personal Records Section
        val hasPRs = trend.prWeight != null || trend.prVolume != null || 
                     trend.pr1RM != null || trend.prReps != null
        
        if (hasPRs) {
            holder.binding.dividerPrs.visibility = View.VISIBLE
            holder.binding.textPrsTitle.visibility = View.VISIBLE
            holder.binding.layoutPrGrid.visibility = View.VISIBLE
            
            // Weight PR
            if (trend.prWeight != null) {
                holder.binding.layoutPrWeight.visibility = View.VISIBLE
                holder.binding.textPrWeight.text = String.format(Locale.US, "%.1fkg", trend.prWeight)
                val daysSince = getDaysSince(trend.prWeightDate)
                val prColor = getPrRecencyColor(context, daysSince)
                holder.binding.textPrWeight.setTextColor(prColor)
            } else {
                holder.binding.layoutPrWeight.visibility = View.GONE
            }
            
            // Volume PR
            if (trend.prVolume != null) {
                holder.binding.layoutPrVolume.visibility = View.VISIBLE
                holder.binding.textPrVolume.text = String.format(Locale.US, "%,dkg", trend.prVolume.toInt())
                val daysSince = getDaysSince(trend.prVolumeDate)
                val prColor = getPrRecencyColor(context, daysSince)
                holder.binding.textPrVolume.setTextColor(prColor)
            } else {
                holder.binding.layoutPrVolume.visibility = View.GONE
            }
            
            // 1RM PR
            if (trend.pr1RM != null) {
                holder.binding.layoutPr1rm.visibility = View.VISIBLE
                holder.binding.textPr1rm.text = String.format(Locale.US, "%.1fkg", trend.pr1RM)
                val daysSince = getDaysSince(trend.pr1RMDate)
                val prColor = getPrRecencyColor(context, daysSince)
                holder.binding.textPr1rm.setTextColor(prColor)
            } else {
                holder.binding.layoutPr1rm.visibility = View.GONE
            }
            
            // Reps PR
            if (trend.prReps != null) {
                holder.binding.layoutPrReps.visibility = View.VISIBLE
                holder.binding.textPrReps.text = trend.prReps
                val daysSince = getDaysSince(trend.prRepsDate)
                val prColor = getPrRecencyColor(context, daysSince)
                holder.binding.textPrReps.setTextColor(prColor)
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

    /**
     * Get color for percentage change
     */
    private fun getChangeColor(context: android.content.Context, changePercent: Float): Int {
        return when {
            changePercent > 1f -> ContextCompat.getColor(context, R.color.fitness_highlight_border) // Green
            changePercent < -1f -> ContextCompat.getColor(context, R.color.fitness_error_border) // Red
            else -> ContextCompat.getColor(context, R.color.fitness_text_secondary) // Gray
        }
    }
    
    /**
     * Get color based on PR recency
     */
    private fun getPrRecencyColor(context: android.content.Context, daysSince: Int): Int {
        return when {
            daysSince <= 7 -> ContextCompat.getColor(context, R.color.pr_fresh)
            daysSince <= 30 -> ContextCompat.getColor(context, R.color.pr_improved)
            else -> ContextCompat.getColor(context, R.color.pr_older)
        }
    }
    
    /**
     * Calculate days since timestamp
     */
    private fun getDaysSince(timestamp: Long): Int {
        return if (timestamp <= 0) Int.MAX_VALUE
        else ((System.currentTimeMillis() - timestamp) / (24 * 60 * 60 * 1000)).toInt()
    }

    class ViewHolder(val binding: ItemExerciseTrendBinding) : RecyclerView.ViewHolder(binding.root)
}
