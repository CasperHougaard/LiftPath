package com.liftpath.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.databinding.ItemHistoryBinding
import com.liftpath.models.TrainingSession
import com.liftpath.utils.WorkoutTypeFormatter
import com.liftpath.activities.TrainingDetailActivity

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
        holder.binding.textTrainingVolume.text = String.format("%,dkg", totalVolume.toInt())
        
        // Format type as uppercase badge
        holder.binding.textTrainingType.text = (training.defaultWorkoutType ?: "heavy").uppercase()

        // Exercise summary
        val uniqueExercises = training.exercises.map { it.exerciseId }.distinct().size
        val totalSets = training.exercises.size
        holder.binding.textExercisesSummary.text = "$uniqueExercises exercise${if (uniqueExercises > 1) "s" else ""} • $totalSets set${if (totalSets > 1) "s" else ""}"

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