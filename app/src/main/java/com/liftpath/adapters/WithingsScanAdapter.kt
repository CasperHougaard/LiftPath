package com.liftpath.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liftpath.databinding.ItemWithingsScanBinding
import com.liftpath.models.WithingsScanEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WithingsScanAdapter(
    private val entries: List<WithingsScanEntry>
) : RecyclerView.Adapter<WithingsScanAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    inner class ViewHolder(val binding: ItemWithingsScanBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWithingsScanBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = entries.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        val b = holder.binding

        b.textScanDate.text = dateFormat.format(Date(entry.dateMs))

        b.textWeight.text = entry.weightKg?.let { String.format(Locale.US, "%.1f kg", it) } ?: "—"
        b.textBodyFat.text = entry.bodyFatPct?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—"
        b.textLeanMass.text = entry.leanBodyMassKg?.let { String.format(Locale.US, "%.1f kg", it) } ?: "—"
        b.textBoneMass.text = entry.boneMassKg?.let { String.format(Locale.US, "%.2f kg", it) } ?: "—"
        b.textBodyWater.text = entry.bodyWaterMassKg?.let { String.format(Locale.US, "%.1f kg", it) } ?: "—"
        b.textBmr.text = entry.bmrKcal?.let { String.format(Locale.US, "%.0f kcal", it) } ?: "—"
    }
}
