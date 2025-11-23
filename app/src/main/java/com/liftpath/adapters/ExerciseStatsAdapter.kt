package com.liftpath.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.databinding.ItemExerciseStatBinding
import com.liftpath.models.ExerciseSet

class ExerciseStatsAdapter(private val sets: List<ExerciseSet>) : RecyclerView.Adapter<ExerciseStatsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExerciseStatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val set = sets[position]
        holder.binding.textDate.text = set.date
        holder.binding.textSetInfo.text = "Set ${set.setNumber}: ${set.kg}kg x ${set.reps} reps"
    }

    override fun getItemCount() = sets.size

    class ViewHolder(val binding: ItemExerciseStatBinding) : RecyclerView.ViewHolder(binding.root)
}