package com.liftpath.helpers

import com.liftpath.models.TrainingSession

/**
 * LiftPath's own estimate of what a lifting session cost in calories.
 *
 * This exists to be *compared* rather than trusted: TriPath's energy balance only counts sessions
 * its own sources recorded, so putting an independent estimate next to it makes a gap visible —
 * either LiftPath logged a session TriPath never saw, or the watch recorded one twice.
 *
 * Deliberately the same shape as TriPath's `EnergyBalanceCalculator.estimateActiveCalories`:
 * `(MET − 1) × kg × hours`, using the active portion only so it can be added to a resting
 * baseline without double-counting rest. Two estimates built the same way differ because the
 * *inputs* differ, which is the whole point; two built differently would differ for no reason.
 */
object LiftingBurnEstimator {

    /** Moderate-effort resistance training. Matches the MET TriPath assumes for STRENGTH. */
    private const val STRENGTH_MET = 5.0f

    /** Assumed session length per logged set when a session has no recorded duration. */
    private const val FALLBACK_SECONDS_PER_SET = 180L

    /**
     * Active kcal for one session, or null when bodyweight is unknown — without a weight there is
     * no honest number to show, and a guessed one would invite exactly the false confidence this
     * cross-check exists to prevent.
     */
    fun sessionActiveKcal(session: TrainingSession, bodyWeightKg: Float?): Float? {
        val weight = bodyWeightKg ?: return null
        val seconds = session.durationSeconds
            ?: (session.exercises.size * FALLBACK_SECONDS_PER_SET)
        if (seconds <= 0L) return 0f
        return (STRENGTH_MET - 1f) * weight * (seconds / 3600f)
    }

    /**
     * Total estimated lifting burn per day, keyed by ISO date (`yyyy-MM-dd`) so it joins directly
     * against [TriPathDay.date]. LiftPath stores session dates as `yyyy/MM/dd`.
     */
    fun dailyKcalByIsoDate(
        sessions: List<TrainingSession>,
        bodyWeightKg: Float?
    ): Map<String, Float> {
        if (bodyWeightKg == null) return emptyMap()
        return sessions
            .groupBy { it.date.replace('/', '-') }
            .mapValues { (_, daySessions) ->
                daySessions.sumOf { (sessionActiveKcal(it, bodyWeightKg) ?: 0f).toDouble() }.toFloat()
            }
    }
}
