package com.liftpath.helpers

import androidx.health.connect.client.records.ExerciseSessionRecord
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Turns TriPath's numbers into the shapes LiftPath's readiness model already understands.
 *
 * Pure functions over cached data — no I/O — so the fatigue model can be retuned without
 * re-syncing, and so the mapping is testable in isolation.
 *
 * The upgrade over [HealthConnectHelper.mapRecordToFatigueActivity] is that load now comes from
 * TSS rather than wall-clock minutes. An hour of easy Zone 2 and an hour of threshold intervals
 * were previously identical; they are not, and TriPath already knows the difference.
 */
object TriPathFatigueMapper {

    /**
     * Converts a Training Stress Score into LiftPath's fatigue units. LiftPath's own scale runs to
     * a `cnsMax` of 80 for a hard lifting day, so ~100 TSS (a solid hour of threshold work) landing
     * near 60 keeps a hard run and a hard lift comparable rather than one dwarfing the other.
     */
    private const val TSS_TO_FATIGUE = 0.6f

    /** Per-hour TSS assumed when TriPath has no computed score (rare; mirrors its own fallbacks). */
    private fun fallbackTssPerHour(type: String): Float = when (type) {
        "RUN" -> 50f
        "BIKE" -> 40f
        "SWIM" -> 60f
        "WALK" -> 30f
        "HIKE" -> 40f
        else -> 35f
    }

    /**
     * How a discipline's load lands on the body: (lower, upper, systemic) multipliers.
     *
     * STRENGTH is absent on purpose — that load is LiftPath's own, computed from actual sets and
     * RPE in [ReadinessHelper.calculateFatigueScores]. Counting TriPath's estimate too would
     * double every lifting session.
     */
    private fun split(type: String): Triple<Float, Float, Float> = when (type) {
        "RUN" -> Triple(0.9f, 0.05f, 1.0f)
        "HIKE" -> Triple(0.8f, 0.1f, 0.8f)
        "WALK" -> Triple(0.4f, 0.0f, 0.4f)
        "BIKE" -> Triple(0.7f, 0.0f, 0.8f)
        "SWIM" -> Triple(0.15f, 0.9f, 0.9f)
        else -> Triple(0.4f, 0.4f, 0.8f)
    }

    /** The Health Connect exercise type, so existing type-name and icon lookups keep working. */
    private fun exerciseType(type: String): Int = when (type) {
        "RUN" -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
        "BIKE" -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
        "SWIM" -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL
        "WALK" -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
        "HIKE" -> ExerciseSessionRecord.EXERCISE_TYPE_HIKING
        "STRENGTH" -> ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING
        else -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
    }

    /**
     * Maps one TriPath session onto LiftPath's fatigue timeline, or null when it carries no load
     * LiftPath should count (a strength session, or a zero-duration artefact).
     */
    fun toExternalActivity(workout: TriPathWorkout): ExternalActivity? {
        if (workout.type == "STRENGTH") return null
        if (workout.durationMinutes <= 0 && workout.tss == null) return null

        val hours = workout.durationMinutes / 60f
        val load = (workout.tss?.toFloat() ?: (fallbackTssPerHour(workout.type) * hours))
        if (load <= 0f) return null

        val base = load * TSS_TO_FATIGUE * intensityKicker(workout.hrZoneJson)
        val (lower, upper, systemic) = split(workout.type)

        val endTime = workout.endMillis ?: defaultEndTime(workout)
        val startTime = workout.startMillis ?: (endTime - workout.durationMinutes * 60_000L)

        return ExternalActivity(
            id = workout.connectId,
            startTime = startTime,
            endTime = endTime,
            type = exerciseType(workout.type),
            fatigue = FatigueScores(
                lowerFatigue = base * lower,
                upperFatigue = base * upper,
                systemicFatigue = base * systemic
            )
        )
    }

    /**
     * Extra systemic cost for time spent near threshold. TSS already scales with intensity, but
     * it does not capture that hard intervals cost the central nervous system more than their
     * stress score alone implies. Ranges 1.0 (all easy) to 1.4 (entirely Z4/Z5).
     */
    private fun intensityKicker(hrZoneJson: String?): Float {
        if (hrZoneJson.isNullOrBlank()) return 1f
        return try {
            val json = JSONObject(hrZoneJson)
            var total = 0L
            var hard = 0L
            json.keys().forEach { key ->
                val seconds = json.optLong(key, 0L)
                total += seconds
                if (key.contains("4") || key.contains("5")) hard += seconds
            }
            if (total <= 0L) 1f else 1f + 0.4f * (hard.toFloat() / total)
        } catch (e: Exception) {
            1f
        }
    }

    /**
     * TriPath stores only a date for a session whose raw record has been pruned. Noon matches the
     * convention [ReadinessHelper.calculateContinuousFatigueTimeline] already uses for LiftPath's
     * own workouts, so mixed sources stay consistent on the timeline.
     */
    private fun defaultEndTime(workout: TriPathWorkout): Long {
        val noon = try {
            LocalDate.parse(workout.date).atTime(LocalTime.NOON)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
        return noon + workout.durationMinutes * 60_000L
    }

    // ---- Recovery modifiers -------------------------------------------------------------------

    /**
     * What TriPath's recovery data says about how fast the body is clearing fatigue right now.
     *
     * [recoveryFactor] multiplies [ReadinessConfig.recoverySpeedMultiplier]; [thresholdScale]
     * tightens or loosens the fatigue thresholds. Both are 1.0 when there is nothing to say, which
     * is what makes the whole integration a no-op when TriPath is absent.
     */
    data class Modifier(
        val recoveryFactor: Float = 1f,
        val thresholdScale: Float = 1f,
        val latest: TriPathDay? = null
    ) {
        val isNeutral: Boolean get() = latest == null
    }

    /** Neutral modifier — what every consumer falls back to when TriPath is not connected. */
    val NEUTRAL = Modifier()

    /**
     * Builds the modifier from cached days. Uses the most recent day that carries any recovery
     * signal, so an unlogged morning falls back to yesterday rather than to nothing.
     */
    fun modifierFrom(days: List<TriPathDay>): Modifier {
        if (days.isEmpty()) return NEUTRAL
        val sorted = days.sortedBy { it.date }
        val latest = sorted.lastOrNull {
            it.sleepScore != null || it.sleepMinutes != null || it.hrvRmssd != null ||
                it.soreness != null || it.mood != null
        } ?: return Modifier(thresholdScale = thresholdScale(sorted.last().tsb), latest = sorted.last())

        val factor = (sleepComponent(latest) * hrvComponent(latest, sorted) * subjectiveComponent(latest))
            .coerceIn(0.6f, 1.3f)

        return Modifier(
            recoveryFactor = factor,
            thresholdScale = thresholdScale(sorted.last().tsb),
            latest = latest
        )
    }

    /** A good night speeds recovery, a bad one slows it. Centred on a 60 sleep score / 7 hours. */
    private fun sleepComponent(day: TriPathDay): Float {
        val score = day.sleepScore?.toFloat()
            ?: day.sleepMinutes?.let { it / 480f * 60f } // 8 h maps to a neutral-ish 60
            ?: return 1f
        return (1f + (score - 60f) / 60f * 0.3f).coerceIn(0.8f, 1.2f)
    }

    /**
     * HRV read against the athlete's own 14-day baseline rather than an absolute number — the
     * absolute value is meaningless between people, the deviation is the signal.
     */
    private fun hrvComponent(day: TriPathDay, sorted: List<TriPathDay>): Float {
        val hrv = day.hrvRmssd ?: return 1f
        val baseline = sorted
            .filter { it.date < day.date && it.hrvRmssd != null }
            .takeLast(14)
            .mapNotNull { it.hrvRmssd }
            .takeIf { it.size >= 3 }
            ?.average()
            ?.toFloat()
            ?: return 1f
        if (baseline <= 0f) return 1f
        return (1f + (hrv / baseline - 1f) * 0.8f).coerceIn(0.85f, 1.15f)
    }

    /** Soreness and mood, each 1–10. Soreness is inverted: 10 means wrecked. */
    private fun subjectiveComponent(day: TriPathDay): Float {
        val soreness = day.soreness?.let { 1.1f - (it - 1) / 9f * 0.3f }
        val mood = day.mood?.let { 0.95f + (it - 1) / 9f * 0.15f }
        return when {
            soreness != null && mood != null -> (soreness + mood) / 2f
            soreness != null -> soreness
            mood != null -> mood
            else -> 1f
        }
    }

    /**
     * Training Stress Balance as a readiness input. Deeply negative form means accumulated cardio
     * fatigue LiftPath cannot see from its own logs, so the lifting thresholds tighten; well-rested
     * form loosens them slightly.
     */
    fun thresholdScale(tsb: Float): Float = when {
        tsb <= -30f -> 0.85f
        tsb >= 10f -> 1.10f
        tsb < 0f -> 1f - (-tsb / 30f) * 0.15f
        else -> 1f + (tsb / 10f) * 0.10f
    }
}
