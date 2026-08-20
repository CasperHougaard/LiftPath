package com.liftpath.helpers

import com.liftpath.models.ExerciseEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers all four set shapes: (weighted | bodyweight) × (reps | time).
 *
 * The central invariant is that a timed hold contributes **no** rep-based volume or reps. Before
 * this existed every caller computed `kg * reps`, which is 0 for a hold, so timed work silently
 * vanished from every total in the app instead of being reported as hold time.
 */
class SetMetricsTest {

    private fun weightedReps(kg: Float, reps: Int) = ExerciseEntry(
        exerciseId = 1, exerciseName = "Bench Press", setNumber = 1, kg = kg, reps = reps
    )

    private fun bodyweightReps(bw: Float, added: Float, reps: Int) = ExerciseEntry(
        exerciseId = 2, exerciseName = "Pull-up", setNumber = 1,
        kg = bw + added, reps = reps, bodyweightKg = bw, addedKg = added
    )

    private fun weightedHold(kg: Float, seconds: Int) = ExerciseEntry(
        exerciseId = 3, exerciseName = "Plank", setNumber = 1,
        kg = kg, reps = 0, durationSeconds = seconds
    )

    private fun bodyweightHold(bw: Float, added: Float, seconds: Int) = ExerciseEntry(
        exerciseId = 4, exerciseName = "Plank", setNumber = 1,
        kg = bw + added, reps = 0,
        bodyweightKg = bw, addedKg = added, durationSeconds = seconds
    )

    // ── Volume ─────────────────────────────────────────────────────────────

    @Test
    fun volume_weightedReps_isLoadTimesReps() {
        assertEquals(480f, SetMetrics.volumeKg(weightedReps(60f, 8)), 0.001f)
    }

    @Test
    fun volume_bodyweightReps_usesEffectiveLoad() {
        // 80 kg body weight + 10 kg belt = 90 kg effective, × 8 reps.
        assertEquals(720f, SetMetrics.volumeKg(bodyweightReps(80f, 10f, 8)), 0.001f)
    }

    @Test
    fun volume_assistedBodyweight_subtractsAssistance() {
        assertEquals(480f, SetMetrics.volumeKg(bodyweightReps(80f, -20f, 8)), 0.001f)
    }

    @Test
    fun volume_timedHold_isZeroNotLoadTimesZero() {
        // Both flavours of hold: neither may contribute rep-based volume, even though a bodyweight
        // hold carries a large `kg` (the body weight).
        assertEquals(0f, SetMetrics.volumeKg(weightedHold(20f, 90)), 0.001f)
        assertEquals(0f, SetMetrics.volumeKg(bodyweightHold(80f, 0f, 90)), 0.001f)
    }

    // ── Reps ───────────────────────────────────────────────────────────────

    @Test
    fun reps_timedHoldContributesNone() {
        assertEquals(0, SetMetrics.repsForStats(weightedHold(20f, 90)))
        assertEquals(8, SetMetrics.repsForStats(weightedReps(60f, 8)))
    }

    // ── Hold metrics ───────────────────────────────────────────────────────

    @Test
    fun holdSeconds_zeroForRepSets() {
        assertEquals(0, SetMetrics.holdSeconds(weightedReps(60f, 8)))
        assertEquals(90, SetMetrics.holdSeconds(weightedHold(20f, 90)))
    }

    @Test
    fun loadSeconds_weightedHold_isLoadTimesDuration() {
        assertEquals(1800f, SetMetrics.loadSeconds(weightedHold(20f, 90)), 0.001f)
    }

    @Test
    fun loadSeconds_repSet_isZero() {
        assertEquals(0f, SetMetrics.loadSeconds(weightedReps(60f, 8)), 0.001f)
    }

    @Test
    fun bestHoldSeconds_nullWhenNoHolds() {
        assertNull(SetMetrics.bestHoldSeconds(listOf(weightedReps(60f, 8))))
    }

    @Test
    fun bestHoldSeconds_picksLongest() {
        val sets = listOf(weightedHold(0f, 45), weightedHold(0f, 90), weightedHold(0f, 60))
        assertEquals(90, SetMetrics.bestHoldSeconds(sets))
    }

    // ── Added load ─────────────────────────────────────────────────────────

    @Test
    fun addedLoad_bodyweightUsesSignedExtra_weightedUsesLoad() {
        assertEquals(10f, SetMetrics.addedLoadKg(bodyweightReps(80f, 10f, 8)), 0.001f)
        assertEquals(-20f, SetMetrics.addedLoadKg(bodyweightReps(80f, -20f, 8)), 0.001f)
        assertEquals(60f, SetMetrics.addedLoadKg(weightedReps(60f, 8)), 0.001f)
    }

    // ── Mixed-session aggregates ───────────────────────────────────────────

    @Test
    fun mixedSession_separatesVolumeFromHoldTime() {
        val sets = listOf(
            weightedReps(60f, 8),            // 480 kg
            bodyweightReps(80f, 10f, 5),     // 450 kg
            weightedHold(20f, 60),           // no volume, 60 s
            bodyweightHold(80f, 0f, 90)      // no volume, 90 s
        )

        assertEquals(930f, SetMetrics.totalVolumeKg(sets), 0.001f)
        assertEquals(13, SetMetrics.totalReps(sets))
        assertEquals(150, SetMetrics.totalHoldSeconds(sets))
        assertEquals(90, SetMetrics.bestHoldSeconds(sets))
        assertEquals(2, SetMetrics.holdSetCount(sets))
        assertTrue(SetMetrics.hasTimedWork(sets))
        assertEquals(2, SetMetrics.repBasedSets(sets).size)
        assertEquals(2, SetMetrics.timedSets(sets).size)
    }

    @Test
    fun repOnlySession_reportsNoTimedWork() {
        val sets = listOf(weightedReps(60f, 8), weightedReps(60f, 6))
        assertFalse(SetMetrics.hasTimedWork(sets))
        assertEquals(0, SetMetrics.totalHoldSeconds(sets))
        assertNull(SetMetrics.bestHoldSeconds(sets))
    }
}
