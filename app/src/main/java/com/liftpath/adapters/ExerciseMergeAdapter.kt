package com.liftpath.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.liftpath.R
import com.liftpath.helpers.CatalogMergeHelper
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.MergeCandidate
import com.liftpath.helpers.MergeKind
import com.liftpath.helpers.showWithTransparentWindow
import com.liftpath.models.BodyRegion
import com.liftpath.models.Mechanics
import com.liftpath.models.MovementPattern
import com.liftpath.models.Tier
import com.liftpath.helpers.lpColor

class ExerciseMergeAdapter(
    private val candidates: MutableList<MergeCandidate>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemViewType(position: Int): Int = when (candidates[position].kind) {
        MergeKind.NEW -> VIEW_TYPE_NEW
        MergeKind.CONFLICT -> VIEW_TYPE_CONFLICT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_NEW -> NewViewHolder(
                inflater.inflate(R.layout.list_item_merge_new, parent, false)
            )
            VIEW_TYPE_CONFLICT -> ConflictViewHolder(
                inflater.inflate(R.layout.list_item_merge_conflict, parent, false)
            )
            else -> error("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val c = candidates[position]
        when (holder) {
            is NewViewHolder -> holder.bind(c)
            is ConflictViewHolder -> holder.bind(c)
        }
    }

    override fun getItemCount(): Int = candidates.size

    fun getCandidatesSnapshot(): List<MergeCandidate> = candidates.toList()

    inner class NewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.text_exercise_name)
        private val checkbox: CheckBox = itemView.findViewById(R.id.checkbox_add)

        fun bind(c: MergeCandidate) {
            name.text = c.catalogItem.name
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = c.userDecision == CatalogMergeHelper.DECISION_ADD
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                c.userDecision = if (isChecked) {
                    CatalogMergeHelper.DECISION_ADD
                } else {
                    CatalogMergeHelper.DECISION_SKIP
                }
            }
            itemView.setOnClickListener {
                checkbox.isChecked = !checkbox.isChecked
            }
        }
    }

    inner class ConflictViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.text_exercise_name)
        private val btnInfo: ImageView = itemView.findViewById(R.id.button_show_diff)
        private val btnKeep: MaterialButton = itemView.findViewById(R.id.button_keep_local)
        private val btnCatalog: MaterialButton = itemView.findViewById(R.id.button_use_catalog)

        fun bind(c: MergeCandidate) {
            name.text = c.catalogItem.name

            fun applySelection(keepSelected: Boolean) {
                val ctx = itemView.context
                val primaryColor = ctx.lpColor(R.attr.lpAccent)
                val transparent = android.graphics.Color.TRANSPARENT
                val white = android.graphics.Color.WHITE

                if (keepSelected) {
                    btnKeep.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
                    btnKeep.setTextColor(white)
                    btnCatalog.backgroundTintList = android.content.res.ColorStateList.valueOf(transparent)
                    btnCatalog.setTextColor(primaryColor)
                } else {
                    btnCatalog.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
                    btnCatalog.setTextColor(white)
                    btnKeep.backgroundTintList = android.content.res.ColorStateList.valueOf(transparent)
                    btnKeep.setTextColor(primaryColor)
                }
            }

            applySelection(c.userDecision == CatalogMergeHelper.DECISION_KEEP_LOCAL)

            btnInfo.setOnClickListener { showDiffDialog(itemView.context, c) }

            btnKeep.setOnClickListener {
                c.userDecision = CatalogMergeHelper.DECISION_KEEP_LOCAL
                applySelection(true)
            }
            btnCatalog.setOnClickListener {
                c.userDecision = CatalogMergeHelper.DECISION_USE_CATALOG
                applySelection(false)
            }
        }

        private fun showDiffDialog(ctx: Context, c: MergeCandidate) {
            val user = c.existingUserItem ?: return
            val cat = c.catalogItem
            val sb = StringBuilder()

            buildDiffLines(user, cat).forEach { line -> sb.appendLine(line) }

            if (cat.note != null) {
                sb.appendLine()
                sb.appendLine("Technique note:")
                sb.append(cat.note)
            }

            val message = sb.toString().trimEnd().ifEmpty { "No field changes detected." }

            DialogHelper.createBuilder(ctx)
                .setTitle(cat.name)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .showWithTransparentWindow()
        }

        private fun buildDiffLines(
            user: com.liftpath.models.ExerciseLibraryItem,
            cat: com.liftpath.models.ExerciseLibraryItem
        ): List<String> {
            val diffs = mutableListOf<String>()

            fun <T> check(label: String, uVal: T?, cVal: T?, display: (T) -> String) {
                if (uVal != cVal) {
                    diffs.add("$label: ${uVal?.let(display) ?: "—"} → ${cVal?.let(display) ?: "—"}")
                }
            }

            check("Region", user.region, cat.region) { (it as BodyRegion).displayName }
            check("Pattern", user.pattern, cat.pattern) { (it as MovementPattern).displayName }
            check("Tier", user.tier, cat.tier) { (it as Tier).displayName }
            check("Mechanics", user.manualMechanics, cat.manualMechanics) { (it as Mechanics).displayName }

            if (user.primaryTargets != cat.primaryTargets) {
                val removed = user.primaryTargets.filter { it !in cat.primaryTargets }
                val added = cat.primaryTargets.filter { it !in user.primaryTargets }
                val parts = mutableListOf<String>()
                if (added.isNotEmpty()) parts.add("+${added.joinToString { it.displayName }}")
                if (removed.isNotEmpty()) parts.add("−${removed.joinToString { it.displayName }}")
                if (parts.isNotEmpty()) diffs.add("Primary targets: ${parts.joinToString(", ")}")
            }

            if (user.secondaryTargets != cat.secondaryTargets) {
                val removed = user.secondaryTargets.filter { it !in cat.secondaryTargets }
                val added = cat.secondaryTargets.filter { it !in user.secondaryTargets }
                val parts = mutableListOf<String>()
                if (added.isNotEmpty()) parts.add("+${added.joinToString { it.displayName }}")
                if (removed.isNotEmpty()) parts.add("−${removed.joinToString { it.displayName }}")
                if (parts.isNotEmpty()) diffs.add("Secondary targets: ${parts.joinToString(", ")}")
            }

            return diffs
        }
    }

    companion object {
        private const val VIEW_TYPE_NEW = 0
        private const val VIEW_TYPE_CONFLICT = 1
    }
}
