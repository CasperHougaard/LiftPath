package com.liftpath.helpers

import com.liftpath.models.ExerciseEntry
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.TrainingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Timed/isometric holds get their own PR track. They must never emit weight, volume or 1RM PRs —
 * those formulas are rep-based and a bodyweight hold's `kg` is the body weight, which would
 * otherwise register as a huge "weight PR" the first time a plank is logged.
 */
class ProgressAnalysisHelperHoldPrTest {

    private val PLANK_ID = 121
    private val library = listOf(
        ExerciseLibraryItem(id = PLANK_ID, name = "Plank"),
        ExerciseLibraryItem(id = 1, name = "Bench Press")
    )

    private fun hold(seconds: Int, kg: Float = 0f, setNumber: Int = 1) = ExerciseEntry(
        exerciseId = PLANK_ID, exerciseName = "Plank", setNumber = setNumber,
        kg = kg, reps = 0, durationSeconds = seconds
    )

    private fun session(date: String, number: Int, vararg sets: ExerciseEntry) = TrainingSession(
        id = "s$number", trainingNumber = number, date = date,
        exercises = sets.toMutableList()
    )

    @Test
    fun firstHoldSeedsBaselineWithoutEmittingPr() {
        val sessions = listOf(session("2026/01/01", 1, hold(60)))
        assertTrue(ProgressAnalysisHelper.getRecentPRs(sessions, library, 3650).isEmpty())
    }

    @Test
    fun longerHoldEmitsTimeHoldPr() {
        val sessions = listOf(
            session("2026/01/01", 1, hold(60)),
            session("2026/01/08", 2, hold(90))
        )
        val prs = ProgressAnalysisHelper.getRecentPRs(sessions, library, 3650)
        assertEquals(1, prs.size)
        assertEquals(ProgressAnalysisHelper.PRType.TIME_HOLD, prs[0].prType)
        assertEquals(90f, prs[0].value, 0.001f)
        assertEquals(60f, prs[0].previousValue!!, 0.001f)
    }

    @Test
    fun shorterHoldEmitsNothing() {
        val sessions = listOf(
            session("2026/01/01", 1, hold(90)),
            session("2026/01/08", 2, hold(60))
        )
        assertTrue(ProgressAnalysisHelper.getRecentPRs(sessions, library, 3650).isEmpty())
    }

    @Test
    fun sameDurationAtHigherLoadIsAPr() {
        // Adding weight to a plank at the same duration is the only other way it can progress.
        val sessions = listOf(
            session("2026/01/01", 1, hold(60, kg = 20f)),
            session("2026/01/08", 2, hold(60, kg = 25f))
        )
        val prs = ProgressAnalysisHelper.getRecentPRs(sessions, library, 3650)
        assertEquals(1, prs.size)
        assertEquals(ProgressAnalysisHelper.PRType.TIME_HOLD, prs[0].prType)
    }

    @Test
    fun sameDurationAtLowerLoadIsNotAPr() {
        val sessions = listOf(
            session("2026/01/01", 1, hold(60, kg = 25f)),
            session("2026/01/08", 2, hold(60, kg = 20f))
        )
        assertTrue(ProgressAnalysisHelper.getRecentPRs(sessions, library, 3650).isEmpty())
    }

    @Test
    fun holdsNeverEmitWeightVolumeOr1rmPrs() {
        // A weighted hold whose load grows a lot: with a rep-based reading this would look like a
        // huge weight PR, but 1RM/weight/volume are undefined for a hold.
        val sessions = listOf(
            session("2026/01/01", 1, hold(60, kg = 10f)),
            session("2026/01/08", 2, hold(120, kg = 40f))
        )
        val types = ProgressAnalysisHelper.getRecentPRs(sessions, library, 3650).map { it.prType }
        assertTrue(
            "only hold PRs expected, got $types",
            types.all { it == ProgressAnalysisHelper.PRType.TIME_HOLD }
        )
    }

    @Test
    fun exerciseWithOnlyHoldPrsAppearsInStatsSummaries() {
        // Regression guard: the eligibility filter used to require a WEIGHT/VOLUME/1RM PR, so an
        // exercise whose only record was a hold never reached the PR page.
        val sessions = listOf(
            session("2026/01/01", 1, hold(60)),
            session("2026/01/08", 2, hold(90))
        )
        val summaries = ProgressAnalysisHelper.getExerciseStatsSummaries(sessions, library)
        val plank = summaries.find { it.exerciseId == PLANK_ID }
        assertNotNull("plank should appear despite having only a hold PR", plank)
        assertEquals(90, plank!!.bestHoldSeconds)
        assertNull(plank.bestWeight)
        assertNull(plank.best1RM)
        assertNull(plank.bestVolume)
        assertTrue(plank.lastHoldPrDate > 0L)
        assertEquals(plank.lastHoldPrDate, plank.lastPrDate)
    }

    @Test
    fun bestHoldOfSessionIsTheLongestSet() {
        val sessions = listOf(
            session("2026/01/01", 1, hold(60, setNumber = 1)),
            session("2026/01/08", 2,
                hold(45, setNumber = 1), hold(75, setNumber = 2), hold(50, setNumber = 3))
        )
        val prs = ProgressAnalysisHelper.getRecentPRs(sessions, library, 3650)
        assertEquals(1, prs.size)
        assertEquals(75f, prs[0].value, 0.001f)
    }
}
