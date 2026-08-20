package com.liftpath.helpers

import com.liftpath.models.ExerciseEntry
import com.liftpath.models.TrainingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks [LiftingBurnEstimator] to the same `(MET − 1) × kg × hours` shape as TriPath's
 * `EnergyBalanceCalculator.estimateActiveCalories`, so the two stay comparable — see the
 * TriPath Integration Contract in CLAUDE.md.
 */
class LiftingBurnEstimatorTest {

    private fun session(
        durationSeconds: Long? = null,
        exerciseCount: Int = 1,
        date: String = "2026/08/19"
    ) = TrainingSession(
        trainingNumber = 1,
        date = date,
        exercises = MutableList(exerciseCount) {
            ExerciseEntry(exerciseId = it, exerciseName = "Bench Press", setNumber = 1, kg = 60f, reps = 8)
        },
        durationSeconds = durationSeconds
    )

    // ── sessionActiveKcal ──────────────────────────────────────────────────

    @Test
    fun sessionActiveKcal_noWeight_isNull() {
        assertNull(LiftingBurnEstimator.sessionActiveKcal(session(durationSeconds = 3600), null))
    }

    @Test
    fun sessionActiveKcal_matchesTriPathFormula() {
        // TriPath: (MET - 1) * weightKg * hours, MET 5.0 for STRENGTH.
        val expected = (5.0f - 1f) * 80f * 1f
        assertEquals(expected, LiftingBurnEstimator.sessionActiveKcal(session(durationSeconds = 3600), 80f)!!, 0.01f)
    }

    @Test
    fun sessionActiveKcal_zeroDuration_isZero() {
        assertEquals(0f, LiftingBurnEstimator.sessionActiveKcal(session(durationSeconds = 0), 80f)!!, 0.001f)
    }

    @Test
    fun sessionActiveKcal_noDuration_fallsBackToSetCount() {
        // 3 sets * 180s fallback = 540s = 0.15h.
        val expected = (5.0f - 1f) * 80f * (540f / 3600f)
        assertEquals(
            expected,
            LiftingBurnEstimator.sessionActiveKcal(session(durationSeconds = null, exerciseCount = 3), 80f)!!,
            0.01f
        )
    }

    // ── dailyKcalByIsoDate ─────────────────────────────────────────────────

    @Test
    fun dailyKcalByIsoDate_noWeight_isEmpty() {
        assertTrue(LiftingBurnEstimator.dailyKcalByIsoDate(listOf(session()), null).isEmpty())
    }

    @Test
    fun dailyKcalByIsoDate_convertsSlashDateToIso() {
        val result = LiftingBurnEstimator.dailyKcalByIsoDate(listOf(session(date = "2026/08/19")), 80f)
        assertTrue(result.containsKey("2026-08-19"))
    }

    @Test
    fun dailyKcalByIsoDate_sumsMultipleSessionsSameDay() {
        val sessions = listOf(
            session(durationSeconds = 3600, date = "2026/08/19"),
            session(durationSeconds = 1800, date = "2026/08/19")
        )
        val expected = (5.0f - 1f) * 80f * 1f + (5.0f - 1f) * 80f * 0.5f
        assertEquals(expected, LiftingBurnEstimator.dailyKcalByIsoDate(sessions, 80f).getValue("2026-08-19"), 0.01f)
    }
}
