package com.liftpath.components

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.liftpath.R
import com.liftpath.helpers.WorkoutPlanMarkdownHelper.UnresolvedExerciseRef
import com.liftpath.models.ExerciseLibraryItem

/**
 * Bottom sheet that asks the user to match imported exercises whose IDs are not in the current library.
 * Each row starts on a best-guess suggestion (by name); the user can Change it to any library exercise or
 * Skip it. On apply, returns a map of raw imported ID -> chosen library ID (or `null` to skip/drop).
 */
class ExerciseRemapBottomSheet : BottomSheetDialogFragment() {

    private var unresolved: List<UnresolvedExerciseRef> = emptyList()
    private var library: List<ExerciseLibraryItem> = emptyList()
    private var onApply: ((Map<Int, Int?>) -> Unit)? = null

    // rawId -> chosen library id, or null meaning "skip". Every unresolved rawId is always present.
    private val mapping = HashMap<Int, Int?>()
    private var adapter: RemapAdapter? = null

    companion object {
        fun newInstance(
            unresolved: List<UnresolvedExerciseRef>,
            library: List<ExerciseLibraryItem>,
            onApply: (Map<Int, Int?>) -> Unit
        ): ExerciseRemapBottomSheet = ExerciseRemapBottomSheet().apply {
            this.unresolved = unresolved
            this.library = library
            this.onApply = onApply
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_exercise_remap, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Seed each row with a best-guess match (may be null when nothing plausible is found).
        unresolved.forEach { ref -> mapping[ref.rawId] = suggestMatch(ref.displayName)?.id }

        val recycler = view.findViewById<RecyclerView>(R.id.recycler_remap)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = RemapAdapter(
            unresolved = unresolved,
            library = library,
            mapping = mapping,
            onChange = { position -> openPicker(position) },
            onSkip = { position ->
                mapping[unresolved[position].rawId] = null
                adapter?.notifyItemChanged(position)
            }
        )
        recycler.adapter = adapter

        view.findViewById<MaterialButton>(R.id.button_remap_apply).setOnClickListener {
            dismiss()
            onApply?.invoke(HashMap(mapping))
        }
    }

    private fun openPicker(position: Int) {
        val ref = unresolved[position]
        ChangeExerciseBottomSheet.newInstance(null, null, library) { picked ->
            mapping[ref.rawId] = picked.id
            adapter?.notifyItemChanged(position)
        }.show(parentFragmentManager, "remap_pick_${ref.rawId}")
    }

    /** Best-effort name match: exact normalized match first, else the item sharing the most word tokens. */
    private fun suggestMatch(importedName: String): ExerciseLibraryItem? {
        val target = normalize(importedName)
        if (target.isBlank()) return null
        library.firstOrNull { normalize(it.name) == target }?.let { return it }

        val targetTokens = target.split(' ').filter { it.isNotBlank() }.toSet()
        if (targetTokens.isEmpty()) return null
        return library
            .map { it to normalize(it.name).split(' ').filter { t -> t.isNotBlank() }.toSet() }
            .map { (item, tokens) -> item to targetTokens.intersect(tokens).size }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun normalize(s: String): String =
        s.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("").trim().replace(Regex("\\s+"), " ")

    private class RemapAdapter(
        private val unresolved: List<UnresolvedExerciseRef>,
        private val library: List<ExerciseLibraryItem>,
        private val mapping: Map<Int, Int?>,
        private val onChange: (Int) -> Unit,
        private val onSkip: (Int) -> Unit
    ) : RecyclerView.Adapter<RemapAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val importedName: TextView = view.findViewById(R.id.text_remap_imported_name)
            val currentMapping: TextView = view.findViewById(R.id.text_remap_current_mapping)
            val changeButton: MaterialButton = view.findViewById(R.id.button_remap_change)
            val skipButton: MaterialButton = view.findViewById(R.id.button_remap_skip)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_exercise_remap, parent, false))

        override fun getItemCount() = unresolved.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val ref = unresolved[position]
            val ctx = holder.itemView.context
            holder.importedName.text = ctx.getString(R.string.remap_row_imported, ref.displayName, ref.rawId)

            val chosenId = mapping[ref.rawId]
            val chosenName = chosenId?.let { id -> library.firstOrNull { it.id == id }?.name }
            holder.currentMapping.text = if (chosenName != null) {
                ctx.getString(R.string.remap_row_mapped, chosenName)
            } else {
                ctx.getString(R.string.remap_row_skipped)
            }

            holder.changeButton.setOnClickListener { onChange(holder.bindingAdapterPosition) }
            holder.skipButton.setOnClickListener { onSkip(holder.bindingAdapterPosition) }
        }
    }
}
