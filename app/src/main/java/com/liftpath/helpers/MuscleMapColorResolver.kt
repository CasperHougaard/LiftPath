package com.liftpath.helpers

import android.content.Context
import com.liftpath.R
import com.liftpath.models.MuscleMapAssets
import com.liftpath.models.TargetMuscle

/**
 * Resolves the display category for each [TargetMuscle], then flattens that down to the
 * mask-drawable categories [MuscleMapRenderer.render] needs — as categories, not colors, so
 * ranking never depends on which palette happens to be active. [colorFor] is the only place
 * a category becomes an actual ARGB int, resolved fresh per render from the active palette.
 */
object MuscleMapColorResolver {

    /** Highlight mode: which role a muscle plays (e.g. an exercise's primary vs secondary target). */
    enum class HighlightRole { DEFAULT, SECONDARY, PRIMARY }

    /** Progress mode: trend category over the lookback window. */
    enum class ProgressCategory { STABLE, FIRST_TIME, UP_SMALL, UP_BIG, DOWN_SMALL, DOWN_BIG }

    /**
     * Effort mode: how hard a muscle was worked recently, as a continuous 0f (untouched) to
     * 1f (the hardest-worked muscle in the window) intensity, rather than a discrete category —
     * "how much", not "which direction".
     */
    fun resolveEffortIntensity(effortByMuscle: Map<TargetMuscle, Float>): Map<TargetMuscle, Float> {
        val result = TargetMuscle.values().associateWithTo(mutableMapOf()) { 0f }
        effortByMuscle.forEach { (muscle, intensity) -> result[muscle] = intensity.coerceIn(0f, 1f) }
        return result
    }

    /** Highlight mode: primary/secondary/default, primary wins when a muscle is in both sets. */
    fun resolveHighlightColors(
        primary: Set<TargetMuscle>,
        secondary: Set<TargetMuscle>
    ): Map<TargetMuscle, HighlightRole> {
        val result = TargetMuscle.values().associateWithTo(mutableMapOf()) { HighlightRole.DEFAULT }
        secondary.forEach { result[it] = HighlightRole.SECONDARY }
        primary.forEach { result[it] = HighlightRole.PRIMARY }
        return result
    }

    /** Progress mode: trend category by sign & magnitude. Absent muscles stay stable/grey. */
    fun resolveProgressColors(progressMap: Map<TargetMuscle, Float?>): Map<TargetMuscle, ProgressCategory> {
        val result = TargetMuscle.values().associateWithTo(mutableMapOf()) { ProgressCategory.STABLE }
        progressMap.forEach { (muscle, progress) -> result[muscle] = resolveProgressCategory(progress) }
        return result
    }

    fun resolveProgressCategory(progress: Float?): ProgressCategory = when {
        progress == null -> ProgressCategory.FIRST_TIME
        progress > 5f -> ProgressCategory.UP_BIG
        progress > 1f -> ProgressCategory.UP_SMALL
        progress < -5f -> ProgressCategory.DOWN_BIG
        progress < -1f -> ProgressCategory.DOWN_SMALL
        else -> ProgressCategory.STABLE
    }

    /**
     * Flattens a per-muscle category map down to per-mask categories, for masks shared by
     * multiple TargetMuscle values (e.g. CHEST_UPPER/MIDDLE/LOWER all use the "chest" mask).
     * When two muscles sharing a mask disagree, [rank] decides which wins — ascending, so the
     * highest-ranked (most prominent) category is applied last and wins the shared mask.
     */
    fun <T> flattenToMaskCategories(
        muscleColors: Map<TargetMuscle, T>,
        rank: (T) -> Int
    ): List<Pair<Int, T>> {
        val maskColor = mutableMapOf<Int, T>()
        muscleColors.entries
            .sortedBy { (_, category) -> rank(category) }
            .forEach { (muscle, category) ->
                MuscleMapAssets.maskResIds[muscle]?.forEach { maskResId ->
                    maskColor[maskResId] = category
                }
            }
        return maskColor.toList()
    }

    /** Default rank for highlight mode: default < secondary < primary. */
    fun highlightRank(role: HighlightRole): Int = when (role) {
        HighlightRole.PRIMARY -> 2
        HighlightRole.SECONDARY -> 1
        HighlightRole.DEFAULT -> 0
    }

    /** Rank for progress mode: a big swing (up or down) wins a shared mask over a small one. */
    fun progressColorRank(category: ProgressCategory): Int = when (category) {
        ProgressCategory.UP_BIG, ProgressCategory.DOWN_BIG -> 3
        ProgressCategory.UP_SMALL, ProgressCategory.DOWN_SMALL -> 2
        ProgressCategory.FIRST_TIME -> 1
        ProgressCategory.STABLE -> 0
    }

    /** Rank for effort mode: the harder-worked muscle wins a shared mask. */
    fun effortRank(intensity: Float): Int = (intensity * 1000).toInt()

    /**
     * Resolves a highlight role to an ARGB color for the active palette. SECONDARY is the
     * accent at reduced alpha rather than `lpAccentWash` — that token is a near-background
     * tinted *fill*, not a legible silhouette tint, and was rendering lighter than the
     * DEFAULT hairline itself, so secondary muscles looked less visible than untouched ones.
     */
    fun colorFor(context: Context, role: HighlightRole): Int = when (role) {
        HighlightRole.PRIMARY -> context.lpColor(R.attr.lpAccent)
        HighlightRole.SECONDARY -> withAlpha(context.lpColor(R.attr.lpAccent), SECONDARY_ALPHA)
        HighlightRole.DEFAULT -> context.lpColor(R.attr.lpHairlineStrong)
    }

    /**
     * Resolves a progress category to an ARGB color for the active palette. Matches the
     * legend on Progress > Overview exactly: strong swings get the full-strength semantic
     * colour, small swings get the same hue at reduced alpha rather than a separate lighter
     * constant, so "small vs big" reads as an intensity difference, not two unrelated colours.
     */
    fun colorFor(context: Context, category: ProgressCategory): Int {
        val positive = context.lpColor(R.attr.lpPositive)
        val negative = context.lpColor(R.attr.lpNegative)
        return when (category) {
            ProgressCategory.UP_BIG -> positive
            ProgressCategory.UP_SMALL -> withAlpha(positive, SMALL_SWING_ALPHA)
            ProgressCategory.DOWN_BIG -> negative
            ProgressCategory.DOWN_SMALL -> withAlpha(negative, SMALL_SWING_ALPHA)
            ProgressCategory.FIRST_TIME -> context.lpColor(R.attr.lpAccent)
            ProgressCategory.STABLE -> context.lpColor(R.attr.lpInkTertiary)
        }
    }

    /**
     * Resolves an effort intensity to an ARGB color: untouched muscles stay the same neutral
     * outline as highlight mode's DEFAULT, worked muscles are lpAccent ramped from a faint wash
     * up to full strength so even one light set stays visible rather than near-transparent.
     */
    fun colorForEffort(context: Context, intensity: Float): Int {
        if (intensity <= 0f) return context.lpColor(R.attr.lpHairlineStrong)
        val alpha = (MIN_EFFORT_ALPHA + intensity.coerceIn(0f, 1f) * (255 - MIN_EFFORT_ALPHA)).toInt()
        return withAlpha(context.lpColor(R.attr.lpAccent), alpha)
    }

    private const val SMALL_SWING_ALPHA = 115 // ~45% — matches the legend dots' alpha="0.45"
    private const val MIN_EFFORT_ALPHA = 70 // ~27% — a single light set should still read, not vanish
    private const val SECONDARY_ALPHA = 140 // ~55% — visibly muted vs PRIMARY, but stronger than the DEFAULT hairline

    private fun withAlpha(@androidx.annotation.ColorInt color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)
}
