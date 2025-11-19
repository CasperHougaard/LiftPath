package com.lilfitness.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lilfitness.databinding.ItemHistoryBinding
import com.lilfitness.models.TrainingSession
import com.lilfitness.utils.WorkoutTypeFormatter
import com.lilfitness.activities.TrainingDetailActivity

class HistoryAdapter(private val trainings: List<TrainingSession>) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val training = trainings[position]
        holder.binding.textTrainingTitle.text = "Training #${training.trainingNumber}"
        holder.binding.textTrainingDate.text = training.date

        val totalVolume = training.exercises.sumOf { (it.reps ?: 0) * (it.kg ?: 0f).toDouble() }
        holder.binding.textTrainingVolume.text = "Volume: ${totalVolume.toInt()} kg"
        holder.binding.textTrainingType.text = "Type: ${WorkoutTypeFormatter.label(training.defaultWorkoutType)}"

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