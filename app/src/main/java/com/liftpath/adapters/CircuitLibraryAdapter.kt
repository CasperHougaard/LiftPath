package com.liftpath.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.liftpath.R
import com.liftpath.helpers.CircuitStore
import com.liftpath.models.CircuitTemplate
import com.liftpath.models.ExerciseLibraryItem

/**
 * The Circuits segment of the Library tab. Rows, not cards — see the note in
 * `list_item_circuit_library.xml`.
 */
class CircuitLibraryAdapter(
    private var circuits: List<CircuitTemplate>,
    private val onEditClicked: (CircuitTemplate) -> Unit,
    private val onDeleteClicked: (CircuitTemplate) -> Unit,
    private var exerciseNames: Map<Int, String> = emptyMap()
) : RecyclerView.Adapter<CircuitLibraryAdapter.CircuitViewHolder>() {

    /** How many station names fit before the row starts summarising with "+n". */
    private val stationNameLimit = 3

    class CircuitViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.text_circuit_name)
        val summary: TextView = view.findViewById(R.id.text_circuit_summary)
        val stations: TextView = view.findViewById(R.id.text_circuit_stations)
        val editButton: MaterialCardView = view.findViewById(R.id.button_edit_circuit)
        val deleteButton: MaterialCardView = view.findViewById(R.id.button_delete_circuit)
        val divider: View = view.findViewById(R.id.divider_circuit_row)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CircuitViewHolder =
        CircuitViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_circuit_library, parent, false)
        )

    override fun onBindViewHolder(holder: CircuitViewHolder, position: Int) {
        val circuit = circuits[position]
        holder.name.text = circuit.name
        holder.summary.text = CircuitStore.formatSummary(
            circuit.suggestedRounds,
            circuit.restBetweenRoundsSeconds
        )

        val names = circuit.items.map { exerciseNames[it.exerciseId] ?: "" }.filter { it.isNotEmpty() }
        holder.stations.text = when {
            names.isEmpty() -> holder.itemView.context.getString(R.string.circuit_no_stations)
            names.size <= stationNameLimit -> names.joinToString(" · ")
            else -> names.take(stationNameLimit).joinToString(" · ") +
                " · +${names.size - stationNameLimit}"
        }

        // Same reason as ExerciseLibraryAdapter: the row owns its hairline, so the last row
        // must hide it or it would sit against the enclosing card's edge.
        holder.divider.visibility = if (position == circuits.size - 1) View.GONE else View.VISIBLE

        holder.editButton.setOnClickListener { onEditClicked(circuit) }
        holder.deleteButton.setOnClickListener { onDeleteClicked(circuit) }
        holder.itemView.setOnClickListener { onEditClicked(circuit) }
    }

    override fun getItemCount() = circuits.size

    fun update(newCircuits: List<CircuitTemplate>, library: List<ExerciseLibraryItem>) {
        circuits = newCircuits
        exerciseNames = library.associate { it.id to it.name }
        notifyDataSetChanged()
    }
}
