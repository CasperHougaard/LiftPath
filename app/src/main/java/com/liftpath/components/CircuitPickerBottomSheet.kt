package com.liftpath.components

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.liftpath.R
import com.liftpath.helpers.CircuitStore
import com.liftpath.models.CircuitTemplate
import com.liftpath.models.ExerciseLibraryItem

/**
 * Picks one of the saved circuits, or offers to author a new one.
 *
 * Windowing is copied from [AddSpecialBottomSheet] so the two sheets behave identically — the
 * active workout opens one straight after the other.
 */
class CircuitPickerBottomSheet : DialogFragment() {

    private var circuits: List<CircuitTemplate> = emptyList()
    private var library: List<ExerciseLibraryItem> = emptyList()
    private var onCircuitSelected: ((CircuitTemplate) -> Unit)? = null
    private var onNewCircuit: (() -> Unit)? = null

    companion object {
        /** Beyond this many rows the list scrolls instead of growing. */
        private const val MAX_VISIBLE_ROWS = 4

        fun newInstance(
            circuits: List<CircuitTemplate>,
            library: List<ExerciseLibraryItem>,
            onCircuitSelected: (CircuitTemplate) -> Unit,
            onNewCircuit: () -> Unit
        ): CircuitPickerBottomSheet = CircuitPickerBottomSheet().apply {
            this.circuits = circuits
            this.library = library
            this.onCircuitSelected = onCircuitSelected
            this.onNewCircuit = onNewCircuit
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), R.style.ThemeOverlay_Fitness_BottomSheetDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.let { androidx.core.view.WindowCompat.setDecorFitsSystemWindows(it, false) }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            val params = window.attributes
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.gravity = Gravity.BOTTOM
            params.dimAmount = 0.5f
            window.attributes = params
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_circuit_picker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val recycler = view.findViewById<RecyclerView>(R.id.recycler_view_circuit_picker)
        val empty = view.findViewById<TextView>(R.id.text_picker_empty)

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = PickerAdapter()
        if (circuits.size > MAX_VISIBLE_ROWS) {
            // A row is roughly three text lines plus padding; measuring the first child would
            // need a layout pass we don't have yet, so cap on the row estimate instead.
            val rowHeight = resources.getDimensionPixelSize(R.dimen.lp_circuit_picker_row_height)
            recycler.layoutParams = recycler.layoutParams.apply {
                height = rowHeight * MAX_VISIBLE_ROWS
            }
        }

        recycler.visibility = if (circuits.isEmpty()) View.GONE else View.VISIBLE
        empty.visibility = if (circuits.isEmpty()) View.VISIBLE else View.GONE

        view.findViewById<MaterialButton>(R.id.button_new_circuit).setOnClickListener {
            dismiss()
            onNewCircuit?.invoke()
        }
    }

    private inner class PickerAdapter : RecyclerView.Adapter<PickerViewHolder>() {
        private val nameById = library.associate { it.id to it.name }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PickerViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_circuit_pick, parent, false)
        )

        override fun onBindViewHolder(holder: PickerViewHolder, position: Int) {
            val circuit = circuits[position]
            holder.name.text = circuit.name
            holder.summary.text = CircuitStore.formatSummary(
                circuit.suggestedRounds,
                circuit.restBetweenRoundsSeconds
            )
            val names = circuit.items.mapNotNull { nameById[it.exerciseId] }
            holder.stations.text = if (names.isEmpty()) {
                getString(R.string.circuit_no_stations)
            } else {
                names.joinToString(" · ")
            }
            holder.card.setOnClickListener {
                dismiss()
                onCircuitSelected?.invoke(circuit)
            }
        }

        override fun getItemCount() = circuits.size
    }

    private class PickerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.card_circuit_pick)
        val name: TextView = view.findViewById(R.id.text_pick_name)
        val summary: TextView = view.findViewById(R.id.text_pick_summary)
        val stations: TextView = view.findViewById(R.id.text_pick_stations)
    }
}
