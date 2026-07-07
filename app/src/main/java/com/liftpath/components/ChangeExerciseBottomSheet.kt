package com.liftpath.components

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.liftpath.R
import com.liftpath.helpers.FamilySlotResolver
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.MovementPattern

/**
 * Bottom sheet for changing the exercise assigned to a family slot during an active workout.
 *
 * Exercises are sorted so family-compatible ones appear first (same familyId), then
 * same movementPattern, then all others. The user can pick any exercise — not just
 * the compatible ones.
 */
class ChangeExerciseBottomSheet : BottomSheetDialogFragment() {

    private var familyId: String? = null
    private var movementPattern: MovementPattern? = null
    private var library: List<ExerciseLibraryItem> = emptyList()
    private var onSelected: ((ExerciseLibraryItem) -> Unit)? = null

    companion object {
        fun newInstance(
            familyId: String?,
            movementPattern: MovementPattern?,
            library: List<ExerciseLibraryItem>,
            onSelected: (ExerciseLibraryItem) -> Unit
        ): ChangeExerciseBottomSheet {
            return ChangeExerciseBottomSheet().apply {
                this.familyId = familyId
                this.movementPattern = movementPattern
                this.library = library
                this.onSelected = onSelected
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_change_exercise, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sorted = FamilySlotResolver.sortedCandidates(familyId, movementPattern, library)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_change_exercise)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = ExercisePickerAdapter(sorted) { exercise ->
            dismiss()
            onSelected?.invoke(exercise)
        }
    }

    private class ExercisePickerAdapter(
        private val items: List<ExerciseLibraryItem>,
        private val onClick: (ExerciseLibraryItem) -> Unit
    ) : RecyclerView.Adapter<ExercisePickerAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val illustration: ImageView = view.findViewById(R.id.image_exercise_illustration)
            val name: TextView = view.findViewById(R.id.text_exercise_picker_name)
            val subtitle: TextView = view.findViewById(R.id.text_exercise_picker_subtitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_exercise_picker, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.illustration.setImageResource(item.illustrationRes ?: R.drawable.ic_dumbbell)
            holder.name.text = item.name
            val subtitleParts = mutableListOf<String>()
            item.equipment?.name?.let { subtitleParts.add(it.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() }) }
            item.pattern?.name?.let { subtitleParts.add(it.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() }) }
            holder.subtitle.text = subtitleParts.joinToString(" · ")
            holder.subtitle.visibility = if (subtitleParts.isEmpty()) View.GONE else View.VISIBLE
            holder.itemView.setOnClickListener { onClick(item) }
        }
    }
}
