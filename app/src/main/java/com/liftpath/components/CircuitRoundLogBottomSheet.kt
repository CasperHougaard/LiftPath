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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.liftpath.R
import com.liftpath.helpers.CircuitStore
import com.liftpath.helpers.SetFormatter
import com.liftpath.models.CircuitItem
import com.liftpath.models.ExerciseLibraryItem

/**
 * Logs one round of a running circuit: a station row per exercise, pre-filled from the previous
 * round (or the template's target on round one).
 *
 * Windowing copied from [CircuitPickerBottomSheet] so every sheet in the circuit flow behaves
 * the same.
 */
class CircuitRoundLogBottomSheet : DialogFragment() {

    private var items: List<CircuitItem> = emptyList()
    private var library: List<ExerciseLibraryItem> = emptyList()
    private var round: Int = 1
    private var prefill: List<CircuitStore.StationInput> = emptyList()
    private var bodyweightKg: Float? = null
    private var onSaved: ((List<CircuitStore.StationInput>) -> Unit)? = null
    private var onLater: (() -> Unit)? = null

    private class RowRefs(
        val item: CircuitItem,
        val kgEdit: EditText,
        val repsEdit: EditText,
        val timeEdit: EditText,
        val isTimed: Boolean
    )

    private val rows = mutableListOf<RowRefs>()

    companion object {
        fun newInstance(
            items: List<CircuitItem>,
            library: List<ExerciseLibraryItem>,
            round: Int,
            prefill: List<CircuitStore.StationInput>,
            bodyweightKg: Float?,
            onSaved: (List<CircuitStore.StationInput>) -> Unit,
            onLater: () -> Unit
        ): CircuitRoundLogBottomSheet = CircuitRoundLogBottomSheet().apply {
            this.items = items
            this.library = library
            this.round = round
            this.prefill = prefill
            this.bodyweightKg = bodyweightKg
            this.onSaved = onSaved
            this.onLater = onLater
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
    ): View = inflater.inflate(R.layout.bottom_sheet_circuit_round_log, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        view.findViewById<TextView>(R.id.text_round_log_title).text =
            getString(R.string.circuit_log_round_title, round)

        val stationsContainer = view.findViewById<LinearLayout>(R.id.layout_log_stations)
        val inflater = LayoutInflater.from(requireContext())
        val prefillById = prefill.associateBy { it.itemId }

        items.forEachIndexed { index, item ->
            val row = inflater.inflate(R.layout.list_item_circuit_round_log, stationsContainer, false)
            val exercise = library.find { it.id == item.exerciseId }

            row.findViewById<TextView>(R.id.text_log_number).text = (index + 1).toString()
            row.findViewById<TextView>(R.id.text_log_name).text = exercise?.name
                ?: getString(R.string.circuit_unknown_exercise, item.exerciseId)
            row.findViewById<TextView>(R.id.text_log_target).text = CircuitStore.formatTarget(item, exercise)

            row.findViewById<TextView>(R.id.label_log_kg).text = if (exercise?.isBodyweight == true) {
                bodyweightKg?.let { getString(R.string.circuit_log_label_added_kg_bw, SetFormatter.trimNum(it)) }
                    ?: getString(R.string.circuit_log_label_added_kg)
            } else {
                getString(R.string.circuit_station_hint_kg)
            }

            val kgEdit = row.findViewById<EditText>(R.id.edit_log_kg)
            val repsLayout = row.findViewById<View>(R.id.layout_log_reps)
            val timeLayout = row.findViewById<View>(R.id.layout_log_time)
            val repsEdit = row.findViewById<EditText>(R.id.edit_log_reps)
            val timeEdit = row.findViewById<EditText>(R.id.edit_log_time)

            val input = prefillById[item.id]
            input?.kg?.let { kgEdit.setText(SetFormatter.trimNum(it)) }

            val isTimed = exercise?.isTimeBased == true
            if (isTimed) {
                repsLayout.visibility = View.GONE
                timeLayout.visibility = View.VISIBLE
                input?.durationSeconds?.let { timeEdit.setText(it.toString()) }
            } else {
                input?.reps?.let { repsEdit.setText(it.toString()) }
            }

            rows.add(RowRefs(item, kgEdit, repsEdit, timeEdit, isTimed))
            stationsContainer.addView(row)
        }

        view.findViewById<MaterialButton>(R.id.button_save_round).setOnClickListener {
            val inputs = rows.map { r ->
                CircuitStore.StationInput(
                    itemId = r.item.id,
                    exerciseId = r.item.exerciseId,
                    kg = r.kgEdit.text.toString().toFloatOrNull(),
                    reps = if (!r.isTimed) r.repsEdit.text.toString().toIntOrNull() else null,
                    durationSeconds = if (r.isTimed) r.timeEdit.text.toString().toIntOrNull() else null
                )
            }
            dismiss()
            onSaved?.invoke(inputs)
        }

        view.findViewById<MaterialButton>(R.id.button_round_later).setOnClickListener {
            dismiss()
            onLater?.invoke()
        }
    }
}
