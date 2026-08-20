package com.liftpath.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.liftpath.R
import com.liftpath.helpers.CircuitStore
import com.liftpath.helpers.RestTimerHelper
import com.liftpath.helpers.SetFormatter
import com.liftpath.models.CircuitItem
import com.liftpath.models.ExerciseEntry
import com.liftpath.models.ExerciseLibraryItem

/**
 * The station list inside the running circuit.
 *
 * Read-only apart from the stopwatch on timed stations: entering numbers happens in the round-log
 * sheet during the rest, not here, so nothing on this list can be fumbled mid-round.
 */
class CircuitStationAdapter(
    private var items: List<CircuitItem>,
    private var library: List<ExerciseLibraryItem>,
    private val onStationTimerClicked: (CircuitItem) -> Unit
) : RecyclerView.Adapter<CircuitStationAdapter.StationViewHolder>() {

    /** The most recent set logged per exercise in this circuit, for the "last round" hint. */
    private var lastByExerciseId: Map<Int, ExerciseEntry> = emptyMap()

    /** Item id → seconds counted by the station stopwatch this round, if it was used. */
    private var stationSeconds: Map<String, Int> = emptyMap()

    /** Item id of the station whose stopwatch is currently running, if any. */
    private var runningItemId: String? = null

    class StationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val number: TextView = view.findViewById(R.id.text_station_number)
        val name: TextView = view.findViewById(R.id.text_station_name)
        val logged: TextView = view.findViewById(R.id.text_station_logged)
        val target: TextView = view.findViewById(R.id.text_station_target)
        val timerButton: MaterialCardView = view.findViewById(R.id.button_station_timer)
        val timerIcon: ImageView = view.findViewById(R.id.image_station_timer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder =
        StationViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_circuit_station, parent, false)
        )

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        val item = items[position]
        val exercise = library.find { it.id == item.exerciseId }
        val context = holder.itemView.context

        holder.number.text = (position + 1).toString()
        holder.name.text = exercise?.name
            ?: context.getString(R.string.circuit_unknown_exercise, item.exerciseId)

        val running = runningItemId == item.id
        val counted = stationSeconds[item.id]
        holder.target.text = when {
            running || counted != null -> RestTimerHelper.formatDuration(counted ?: 0)
            else -> CircuitStore.formatTarget(item, exercise)
        }

        val last = lastByExerciseId[item.exerciseId]
        if (last != null) {
            holder.logged.text = context.getString(
                R.string.circuit_station_last_round,
                SetFormatter.setLinePlain(last)
            )
            holder.logged.visibility = View.VISIBLE
        } else {
            holder.logged.visibility = View.GONE
        }

        val isTimed = exercise?.isTimeBased == true
        holder.timerButton.visibility = if (isTimed) View.VISIBLE else View.GONE
        holder.timerIcon.setImageResource(if (running) R.drawable.ic_pause else R.drawable.ic_timer_24)
        holder.timerButton.setOnClickListener { onStationTimerClicked(item) }
    }

    override fun getItemCount() = items.size

    fun update(
        items: List<CircuitItem>,
        library: List<ExerciseLibraryItem>,
        circuitEntries: List<ExerciseEntry>
    ) {
        this.items = items
        this.library = library
        lastByExerciseId = circuitEntries
            .groupBy { it.exerciseId }
            .mapValues { (_, sets) -> sets.maxByOrNull { it.setNumber }!! }
        notifyDataSetChanged()
    }

    /** Reflects the station stopwatch: [runningItemId] null means nothing is counting. */
    fun updateStationTimer(runningItemId: String?, stationSeconds: Map<String, Int>) {
        this.runningItemId = runningItemId
        this.stationSeconds = stationSeconds
        notifyDataSetChanged()
    }

    /** Clears the per-round stopwatch readings when a new round starts. */
    fun resetStationTimers() {
        runningItemId = null
        stationSeconds = emptyMap()
        notifyDataSetChanged()
    }
}
