package com.liftpath.helpers

import androidx.annotation.AttrRes
import com.liftpath.R

/**
 * How a readiness figure is *shown*, shared by every surface that shows one.
 *
 * Readiness is rendered in two places that must agree — the Workout tab's summary card and
 * [com.liftpath.activities.ReadinessDashboardActivity] behind it. A user who taps a green score on
 * the home card and lands on an amber one on the dashboard has been told two different things by
 * one app, so the thresholds and the colour mapping live here rather than in either screen.
 *
 * Nothing here computes readiness. TriPath owns that verdict (see the TriPath Integration Contract)
 * and [ReadinessHelper] owns the offline fallback; this only decides which token a number wears.
 */
object ReadinessPresentation {

    /**
     * Freshness at or above this reads as recovered, below [FRESHNESS_CAUTION] as depleted.
     *
     * These are LiftPath's, and only because TriPath bands the *overall* score and not the
     * individual channels — see [bandColorAttr] for the score itself, which must never be
     * re-derived here.
     */
    const val FRESHNESS_READY = 80
    const val FRESHNESS_CAUTION = 50

    /**
     * Token for a 0-100 freshness figure, where 100 is back at the athlete's habitual load.
     * Null (a channel TriPath did not report) is tertiary ink, not a colour — absent is not a state.
     */
    @AttrRes
    fun freshnessColorAttr(freshness: Int?): Int = when {
        freshness == null -> R.attr.lpInkTertiary
        freshness >= FRESHNESS_READY -> R.attr.lpPositive
        freshness >= FRESHNESS_CAUTION -> R.attr.lpAccent
        else -> R.attr.lpNegative
    }

    /**
     * Token for the overall score, taken from TriPath's own band rather than from the number.
     *
     * Running the score back through [freshnessColorAttr] looks equivalent and is not: TriPath
     * bands 75 as READY, while an 80-point cutoff paints it amber — a card that says "Ready" in
     * a caution colour, which is LiftPath quietly second-guessing a verdict it does not own.
     * An unrecognised band falls back to plain ink rather than guessing.
     */
    @AttrRes
    fun bandColorAttr(band: String): Int = when (band.uppercase()) {
        "FRESH", "READY" -> R.attr.lpPositive
        "COMPROMISED" -> R.attr.lpAccent
        "DEPLETED" -> R.attr.lpNegative
        else -> R.attr.lpInk
    }

    /**
     * Token for a raw fatigue value, read against the athlete's calibrated thresholds rather than
     * a fixed scale — a novice's "high" is an advanced lifter's Tuesday.
     *
     * Note the inversion against [freshnessColorAttr]: fatigue is bad when it is *high*.
     */
    @AttrRes
    fun fatigueColorAttr(fatigue: Float, thresholds: ReadinessConfig.Thresholds): Int = when {
        fatigue <= 0f -> R.attr.lpHairlineStrong
        fatigue > thresholds.high -> R.attr.lpNegative
        fatigue >= thresholds.moderate -> R.attr.lpAccent
        else -> R.attr.lpPositive
    }

    /** Token for one of [ActivityStatus]'s three verdicts. */
    @AttrRes
    fun statusColorAttr(status: ActivityStatus): Int = when (status) {
        ActivityStatus.GREEN -> R.attr.lpPositive
        ActivityStatus.YELLOW -> R.attr.lpAccent
        ActivityStatus.RED -> R.attr.lpNegative
    }

    /** Label for one of [ActivityStatus]'s three verdicts. */
    fun statusLabelRes(status: ActivityStatus): Int = when (status) {
        ActivityStatus.GREEN -> R.string.readiness_status_ready
        ActivityStatus.YELLOW -> R.string.readiness_status_caution
        ActivityStatus.RED -> R.string.readiness_status_blocked
    }

    /**
     * `LOWER_IMPACT` reads as "Lower impact". TriPath's enum names cross the provider boundary as
     * raw strings, and an enum name is not user-facing copy.
     */
    fun humanise(raw: String): String =
        raw.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** "6h" up to a day, "2d" past it — an exact hour count stops being useful at that point. */
    fun formatHours(hours: Int): String =
        if (hours >= 24) "${Math.round(hours / 24.0)}d" else "${hours}h"

    /**
     * The four strain channels, in the order they are shown.
     *
     * Ordered worst-case-first for a lifter: legs take impact and muscular load from two different
     * sports, so they get two rows, and systemic sits last because it is the aggregate the others
     * explain. The [key] is TriPath's channel name in [TriPathReadiness.hoursToFresh].
     */
    val CHANNELS: List<Channel> = listOf(
        Channel("LOWER_IMPACT", R.string.readiness_channel_lower_impact) { it.lowerImpactFreshness },
        Channel("LOWER_MUSCULAR", R.string.readiness_channel_lower_muscular) { it.lowerMuscularFreshness },
        Channel("UPPER_MUSCULAR", R.string.readiness_channel_upper) { it.upperMuscularFreshness },
        Channel("SYSTEMIC", R.string.readiness_channel_systemic) { it.systemicFreshness }
    )

    /** One strain channel: what to call it, and how to pull its figure out of a verdict. */
    data class Channel(
        val key: String,
        val labelRes: Int,
        val freshness: (TriPathReadiness) -> Int?
    )
}
