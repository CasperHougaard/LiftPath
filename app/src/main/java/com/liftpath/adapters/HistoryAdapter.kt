package com.liftpath.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.databinding.ItemHistoryBinding
import com.liftpath.models.TrainingSession
import com.liftpath.utils.WorkoutTypeFormatter
import com.liftpath.activities.TrainingDetailActivity
import com.liftpath.helpers.DurationHelper
import androidx.core.content.ContextCompat
import com.liftpath.R

class HistoryAdapter(private val trainings: List<TrainingSession>) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val training = trainings[position]
        holder.binding.textTrainingTitle.text = "Training #${training.trainingNumber}"
        holder.binding.textTrainingDate.text = training.date

        // Format volume with comma separator
        val totalVolume = training.exercises.sumOf { (it.reps ?: 0) * (it.kg ?: 0f).toDouble() }
        val volumeText = String.format("%,dkg", totalVolume.toInt())
        
        // Format duration if available
        val durationText = training.durationSeconds?.let { DurationHelper.formatDuration(it) } ?: "N/A"
        holder.binding.textTrainingVolume.text = "$volumeText • $durationText"
        
        // Format type as uppercase badge with color based on workout type
        val workoutType = training.defaultWorkoutType ?: "heavy"
        holder.binding.textTrainingType.text = workoutType.uppercase()
        
        // Set badge color: light blue for light, dark blue for heavy
        val badgeColor = when (workoutType.lowercase()) {
            "light" -> ContextCompat.getColor(holder.itemView.context, R.color.fitness_light_blue)
            "heavy" -> ContextCompat.getColor(holder.itemView.context, R.color.fitness_dark_blue)
            else -> ContextCompat.getColor(holder.itemView.context, R.color.fitness_dark_blue) // Default to dark blue
        }
        holder.binding.textTrainingType.setBackgroundTintList(android.content.res.ColorStateList.valueOf(badgeColor))

        // Calculate average RPE
        val rpeValues = training.exercises.mapNotNull { it.rpe }
        val avgRpe = if (rpeValues.isNotEmpty()) {
            String.format("%.1f", rpeValues.average())
        } else {
            "N/A"
        }

        // Exercise summary with average RPE
        val uniqueExercises = training.exercises.map { it.exerciseId }.distinct().size
        val totalSets = training.exercises.size
        holder.binding.textExercisesSummary.text = "$uniqueExercises exercise${if (uniqueExercises > 1) "s" else ""} • $totalSets set${if (totalSets > 1) "s" else ""} • Avg RPE: $avgRpe"

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, TrainingDetailActivity::class.java).apply {
                putExtra(TrainingDetailActivity.EXTRA_TRAINING_SESSION, training)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = trainings.size

    class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)
}