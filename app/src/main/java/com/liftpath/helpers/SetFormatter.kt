package com.liftpath.helpers

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat
import com.liftpath.R
import com.liftpath.models.ExerciseEntry
import java.util.Locale

/**
 * Single renderer for a logged set, covering all four quadrants of
 * (weighted | bodyweight) × (reps | time):
 *
 *  - weighted reps    → `60kg × 8 reps`
 *  - bodyweight reps  → `80.5 +10kg × 8 reps`   (body weight muted, signed extra colored)
 *  - weighted hold    → `1:30 + 20kg`
 *  - bodyweight hold  → `1:30 @ 80.5 +10kg`     (never `+80.5kg` — that would read as external load)
 *
 * A plain bodyweight hold with no added weight renders as just `1:30`, with no load clutter.
 *
 * Replaces three near-identical formatters that had diverged (`TrainingDetailAdapter`,
 * `ActiveExercisesAdapter`, `AiExportHelper`), none of which could render a bodyweight hold.
 *
 * Composition ([segments]) is pure Kotlin and independent of Android, so it is unit-testable and
 * shared by both the spanned and the plain-text renderings — the two can't drift apart.
 */
object SetFormatter {

    /** How a [Segment] should be emphasised when rendered with spans. */
    enum class Emphasis {
        /** Normal body text. */
        NORMAL,
        /** Muted — the body-weight portion of a bodyweight set. */
        MUTED,
        /** Bold green — weight added on top of body weight. */
        ADDED,
        /** Bold red — assistance subtracted from body weight. */
        ASSISTED
    }

    data class Segment(val text: String, val emphasis: Emphasis = Emphasis.NORMAL)

    /** Trims a trailing `.0`: 60.0 -> "60", 62.5 -> "62.5". */
    fun trimNum(v: Float): String =
        if (v % 1 == 0f) v.toInt().toString() else v.toString()

    /** Body weight and total load are always shown to 1 decimal. */
    private fun format1(v: Float): String = String.format(Locale.US, "%.1f", v)

    // ── Composition (pure) ─────────────────────────────────────────────────

    /**
     * The load portion, without any unit suffix.
     *
     * @param omitUninformative returns nothing when the load says nothing the metric doesn't
     *   already: a bodyweight set with no extra weight, or a weighted set carrying no load. Used by
     *   holds, where an unloaded plank should read as plain `1:30` rather than `1:30 + 0kg`.
     */
    fun loadSegments(e: ExerciseEntry, omitUninformative: Boolean = false): List<Segment> {
        if (!e.isBodyweightEntry()) {
            if (omitUninformative && e.kg <= 0f) return emptyList()
            return listOf(Segment(trimNum(e.kg)))
        }

        val bw = e.bodyweightKg ?: 0f
        val added = e.addedKg ?: 0f
        if (added == 0f && omitUninformative) return emptyList()

        val out = mutableListOf(Segment(format1(bw), Emphasis.MUTED))
        if (added != 0f) {
            val sign = if (added > 0f) "+" else "−"
            val mag = if (added < 0f) -added else added
            out += Segment(
                " $sign${trimNum(mag)}",
                if (added > 0f) Emphasis.ADDED else Emphasis.ASSISTED
            )
        }
        return out
    }

    /**
     * A full set line: `<prefix><body><suffix>`.
     *
     * @param spaceBeforeUnit inserts a space before `kg` (`"80 kg × 8"` vs `"80kg × 8"`) to match
     *   the two existing spacings in the active-workout list.
     * @param repsUnit appends the word "reps" after the count (history detail does, the active list
     *   doesn't).
     * @param compact drops the `kg` unit and the spaces around `×`, for the collapsed summary.
     */
    fun segments(
        e: ExerciseEntry,
        prefix: String = "",
        suffix: String = "",
        spaceBeforeUnit: Boolean = false,
        repsUnit: Boolean = false,
        compact: Boolean = false
    ): List<Segment> {
        val out = mutableListOf<Segment>()
        if (prefix.isNotEmpty()) out += Segment(prefix)

        if (e.isTimedEntry()) {
            out += Segment(RestTimerHelper.formatDuration(SetMetrics.holdSeconds(e)))
            val load = loadSegments(e, omitUninformative = true)
            if (load.isNotEmpty()) {
                // "@" for a bodyweight hold (the load IS the body), "+" for external weight added
                // to an otherwise unloaded hold.
                out += Segment(
                    when {
                        e.isBodyweightEntry() -> " @ "
                        compact -> " +"
                        else -> " + "
                    }
                )
                out += load
                if (!compact) out += Segment(if (spaceBeforeUnit) " kg" else "kg")
            }
            if (suffix.isNotEmpty()) out += Segment(suffix)
            return out
        }

        out += loadSegments(e)
        if (compact) {
            out += Segment("×${e.reps}")
        } else {
            out += Segment(if (spaceBeforeUnit) " kg" else "kg")
            out += Segment(" × ${e.reps}")
            if (repsUnit) out += Segment(" reps")
        }
        if (suffix.isNotEmpty()) out += Segment(suffix)
        return out
    }

    /** Plain text of [segments], with no styling. */
    fun plain(segments: List<Segment>): String = segments.joinToString("") { it.text }

    /** Convenience: the plain text of a full set line. */
    fun setLinePlain(
        e: ExerciseEntry,
        prefix: String = "",
        suffix: String = "",
        repsUnit: Boolean = false
    ): String = plain(segments(e, prefix = prefix, suffix = suffix, repsUnit = repsUnit))

    // ── Android rendering ──────────────────────────────────────────────────

    private fun render(context: Context, segments: List<Segment>): CharSequence {
        val b = SpannableStringBuilder()
        segments.forEach { seg ->
            val start = b.length
            b.append(seg.text)
            when (seg.emphasis) {
                Emphasis.NORMAL -> Unit
                Emphasis.MUTED -> b.setSpan(
                    ForegroundColorSpan(context.lpColor(R.attr.lpInkSecondary)),
                    start, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                Emphasis.ADDED, Emphasis.ASSISTED -> {
                    val colorAttr = if (seg.emphasis == Emphasis.ADDED) {
                        R.attr.lpPositive
                    } else {
                        R.attr.lpNegative
                    }
                    b.setSpan(
                        ForegroundColorSpan(context.lpColor(colorAttr)),
                        start, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    b.setSpan(StyleSpan(Typeface.BOLD), start, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        return b
    }

    fun loadSpan(
        context: Context,
        e: ExerciseEntry,
        omitUninformative: Boolean = false
    ): CharSequence = render(context, loadSegments(e, omitUninformative))

    fun setLine(
        context: Context,
        e: ExerciseEntry,
        prefix: String = "",
        suffix: String = "",
        spaceBeforeUnit: Boolean = false,
        repsUnit: Boolean = false
    ): CharSequence = render(
        context,
        segments(e, prefix, suffix, spaceBeforeUnit, repsUnit)
    )

    /** The tightest form, for the collapsed active-workout summary: `62.5×8` / `80.5 +10×8` / `1:30`. */
    fun compact(context: Context, e: ExerciseEntry): CharSequence =
        render(context, segments(e, compact = true))

    /**
     * Plain-text load cell for markdown / AI export — explicit about what the number is, so a model
     * reading the export can't confuse body weight with external load.
     *
     * `62.5` · `BW80.5+10=90.5` · `BW80.5-20=60.5` · `BW80.5=80.5`
     */
    fun loadCellPlain(e: ExerciseEntry): String {
        if (!e.isBodyweightEntry()) return trimNum(e.kg)
        val base = trimNum(e.bodyweightKg ?: 0f)
        val added = e.addedKg ?: 0f
        return when {
            added > 0f -> "BW$base+${trimNum(added)}=${trimNum(e.kg)}"
            added < 0f -> "BW$base-${trimNum(-added)}=${trimNum(e.kg)}"
            else -> "BW$base=${trimNum(e.kg)}"
        }
    }
}
