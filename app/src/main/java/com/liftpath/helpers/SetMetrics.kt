package com.liftpath.helpers

import com.liftpath.models.ExerciseEntry
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.TrainingSession

/**
 * Single source of truth for "what does this set contribute to a metric".
 *
 * The three set shapes an entry can take:
 *  - weighted reps      → `kg` is the external load, `reps` the count. Volume = kg × reps.
 *  - bodyweight reps    → `kg` is the effective load (body weight + signed extra). Volume = kg × reps.
 *  - timed hold         → `reps` is 0 and `durationSeconds` carries the target. Volume is
 *                         **not** defined: kg × 0 would silently read as zero everywhere, so timed
 *                         sets are excluded from rep-based volume/reps/1RM and measured in
 *                         [holdSeconds] / [loadSeconds] instead.
 *
 * A timed hold may itself be weighted or bodyweight — [ExerciseEntry.isTimedEntry] and
 * [ExerciseEntry.isBodyweightEntry] are independent.
 *
 * Every volume figure in the app should route through here rather than open-coding `kg * reps`,
 * so timed work can never be silently dropped again.
 */
object SetMetrics {

    // ── Per-set contributions ──────────────────────────────────────────────

    /** Rep-based volume in kg. Zero for timed holds — they carry no reps to multiply by. */
    fun volumeKg(e: ExerciseEntry): Float =
        if (e.isTimedEntry()) 0f else e.kg * e.reps

    /** Reps for statistics. Zero for timed holds (whose `reps` field is a placeholder 0 anyway). */
    fun repsForStats(e: ExerciseEntry): Int =
        if (e.isTimedEntry()) 0 else e.reps

    /** Hold duration in seconds; 0 for rep-based sets. */
    fun holdSeconds(e: ExerciseEntry): Int = e.durationSeconds ?: 0

    /**
     * Load-seconds (kg·s) for a timed hold — the closest analogue to volume for isometric work.
     * Zero for rep-based sets. For a bodyweight hold this uses the effective load (body weight +
     * extra), matching how [volumeKg] treats bodyweight rep sets.
     */
    fun loadSeconds(e: ExerciseEntry): Float =
        if (e.isTimedEntry()) e.kg * holdSeconds(e) else 0f

    /**
     * The progressible portion of the load: the signed added/assisted weight for a bodyweight set,
     * or the plain external load for a weighted set.
     */
    fun addedLoadKg(e: ExerciseEntry): Float = e.addedKg ?: e.kg

    // ── Aggregates ─────────────────────────────────────────────────────────

    fun totalVolumeKg(sets: List<ExerciseEntry>): Float =
        sets.fold(0f) { acc, e -> acc + volumeKg(e) }

    fun totalReps(sets: List<ExerciseEntry>): Int =
        sets.sumOf { repsForStats(it) }

    fun totalHoldSeconds(sets: List<ExerciseEntry>): Int =
        sets.sumOf { holdSeconds(it) }

    /** Longest single hold, or null when none of [sets] is a timed hold. */
    fun bestHoldSeconds(sets: List<ExerciseEntry>): Int? =
        sets.filter { it.isTimedEntry() }.maxOfOrNull { holdSeconds(it) }

    fun totalLoadSeconds(sets: List<ExerciseEntry>): Float =
        sets.fold(0f) { acc, e -> acc + loadSeconds(e) }

    fun holdSetCount(sets: List<ExerciseEntry>): Int = sets.count { it.isTimedEntry() }

    /** True when any of [sets] is a timed hold. */
    fun hasTimedWork(sets: List<ExerciseEntry>): Boolean = sets.any { it.isTimedEntry() }

    // ── Set selection ──────────────────────────────────────────────────────

    /**
     * The working sets of a session: warmups excluded via [ExerciseEntry.isEffectivelyWarmup] so
     * legacy RPE-6 warmups are dropped too.
     */
    fun workingSets(session: TrainingSession): List<ExerciseEntry> =
        session.exercises.filterNot { it.isEffectivelyWarmup() }

    /** Working sets that carry rep-based load — the input to volume, top-set and 1RM maths. */
    fun repBasedSets(sets: List<ExerciseEntry>): List<ExerciseEntry> =
        sets.filterNot { it.isTimedEntry() }

    fun timedSets(sets: List<ExerciseEntry>): List<ExerciseEntry> =
        sets.filter { it.isTimedEntry() }
}

/**
 * Resolves how a set for a given exercise should be logged, from the exercise library rather than
 * from an existing entry. Used by the log-set launchers; [ExerciseEntry.isBodyweightEntry] /
 * [ExerciseEntry.isTimedEntry] remain the source of truth when editing an already-logged set.
 */
object ExerciseModeResolver {

    fun isBodyweight(library: List<ExerciseLibraryItem>, exerciseId: Int): Boolean =
        library.find { it.id == exerciseId }?.isBodyweight == true

    fun isTimeBased(library: List<ExerciseLibraryItem>, exerciseId: Int): Boolean =
        library.find { it.id == exerciseId }?.isTimeBased == true
}
