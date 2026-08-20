package com.liftpath.helpers

import com.liftpath.models.SetIntent
import com.liftpath.models.TrainingSession
import com.liftpath.models.WithingsScanEntry
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Correlates training strength progress against body-weight change over the period covered by the
 * body-scan (Withings) data, so the body-scan screen can tell the user whether they're getting
 * genuinely stronger or just heavier.
 *
 * Weight change is a simple endpoint delta of scan weight. Strength change is derived from a
 * Relative Strength Index — each STRENGTH-intent exercise is normalised to its own baseline and
 * averaged across the group ([OneRMEstimationHelper.calculateGroupRelativeStrengthIndex]) — so it
 * stays meaningful even though different lifts are trained on different days.
 */
object StrengthVsWeightHelper {

    private const val MS_PER_DAY = 86_400_000L

    /** A change smaller than this (percent) is treated as "flat" when phrasing the verdict. */
    private const val FLAT_PCT = 1.5f

    data class StrengthVsWeightResult(
        val strengthChangePct: Float,   // % change in the relative-strength index over the window
        val weightChangePct: Float,     // % change in body weight over the window
        val weightChangeKg: Float,      // absolute body-weight delta (kg)
        val windowDays: Int,            // span between first and last scan used
        val strengthSessionsUsed: Int,  // distinct training dates contributing to the strength index
        val verdict: String             // plain-language interpretation
    )

    /**
     * @param trainings all logged sessions (any order)
     * @param scans     Withings scan entries (any order)
     * @return the comparison, or null when there isn't enough weight or strength data to compare.
     */
    fun compute(
        trainings: List<TrainingSession>,
        scans: List<WithingsScanEntry>
    ): StrengthVsWeightResult? {
        // ── Weight side: need at least two dated weigh-ins to measure a change ──────────────
        val weightScans = scans.filter { it.weightKg != null }.sortedBy { it.dateMs }
        if (weightScans.size < 2) return null

        val firstWeight = weightScans.first().weightKg!!.toFloat()
        val lastWeight = weightScans.last().weightKg!!.toFloat()
        if (firstWeight <= 0f) return null

        val windowStartMs = weightScans.first().dateMs
        val windowEndMs = weightScans.last().dateMs
        val weightChangeKg = lastWeight - firstWeight
        val weightChangePct = weightChangeKg / firstWeight * 100f
        val windowDays = ((windowEndMs - windowStartMs) / MS_PER_DAY).toInt()

        // ── Strength side: relative-strength index over the same window ─────────────────────
        val df = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        // ±1 day of slack so scans and same-day sessions at the window edges are both included.
        val startBound = windowStartMs - MS_PER_DAY
        val endBound = windowEndMs + MS_PER_DAY

        // exerciseName -> (session date -> best STRENGTH 1RM that day)
        val perExercise = HashMap<String, HashMap<String, Float>>()
        trainings.forEach { session ->
            val sessionMs = try {
                df.parse(session.date)?.time ?: return@forEach
            } catch (e: Exception) {
                return@forEach
            }
            if (sessionMs < startBound || sessionMs > endBound) return@forEach

            session.exercises.forEach { entry ->
                if (entry.isEffectivelyWarmup()) return@forEach
                if (entry.getEffectiveIntent(session.defaultWorkoutType) != SetIntent.STRENGTH) return@forEach
                val oneRM = OneRMEstimationHelper.calculateOneRM(entry.kg, entry.reps, entry.rpe)
                    ?: return@forEach
                val byDate = perExercise.getOrPut(entry.exerciseName) { HashMap() }
                val prev = byDate[session.date]
                if (prev == null || oneRM > prev) byDate[session.date] = oneRM
            }
        }

        // Only exercises trained on ≥2 days in the window can contribute a trend.
        val exerciseMetrics = perExercise.filterValues { it.size >= 2 }
        if (exerciseMetrics.isEmpty()) return null

        val rsi = OneRMEstimationHelper.calculateGroupRelativeStrengthIndex(exerciseMetrics, emptyMap())
        if (rsi.size < 2) return null

        val sortedIndex = rsi.entries.sortedBy {
            try {
                df.parse(it.key)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
        val firstIndex = sortedIndex.first().value
        val lastIndex = sortedIndex.last().value
        if (firstIndex <= 0f) return null
        val strengthChangePct = (lastIndex - firstIndex) / firstIndex * 100f

        return StrengthVsWeightResult(
            strengthChangePct = strengthChangePct,
            weightChangePct = weightChangePct,
            weightChangeKg = weightChangeKg,
            windowDays = windowDays,
            strengthSessionsUsed = rsi.size,
            verdict = buildVerdict(strengthChangePct, weightChangePct)
        )
    }

    private fun buildVerdict(strengthPct: Float, weightPct: Float): String {
        val sUp = strengthPct > FLAT_PCT
        val sDown = strengthPct < -FLAT_PCT
        val wUp = weightPct > FLAT_PCT
        val wDown = weightPct < -FLAT_PCT
        val wFlat = !wUp && !wDown

        return when {
            sUp && (wDown || wFlat) ->
                "Strength is climbing while your weight is ${if (wDown) "down" else "steady"} — " +
                    "you're getting stronger, not just heavier."
            sUp && wUp && strengthPct > weightPct ->
                "Lean gains: strength is rising faster than your weight, so the added mass is paying off."
            sUp && wUp ->
                "Both are up, but weight is rising a little faster than strength — watch that the extra mass keeps turning into strength."
            !sUp && !sDown && wDown ->
                "You're holding strength while losing weight — relative strength is improving. Solid cut."
            !sUp && !sDown && wUp ->
                "Weight is up but strength is flat — the extra mass isn't translating into strength yet."
            !sUp && !sDown && wFlat ->
                "Strength and weight are both holding steady."
            sDown && wDown ->
                "Both strength and weight are down — expected on a cut; keep the strength loss small."
            sDown && wUp ->
                "Strength is slipping while weight rises — worth reviewing recovery, sleep and programming."
            else -> // sDown && wFlat
                "Strength is trending down even though weight is steady — consider a deload or more recovery."
        }
    }
}
