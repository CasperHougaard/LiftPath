package com.liftpath.helpers

import com.liftpath.models.ExerciseEntry
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.TrainingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The workout report used to render a timed exercise as `0kg` of volume with a `0.0kg × 0` top set,
 * because every metric was `kg * reps` and a hold has no reps.
 */
class WorkoutComparisonHelperTimedTest {

    private val PLANK_ID = 121
    private val BENCH_ID = 1
    private val library = listOf(
        ExerciseLibraryItem(id = PLANK_ID, name = "Plank"),
        ExerciseLibraryItem(id = BENCH_ID, name = "Bench Press")
    )

    private fun hold(seconds: Int, kg: Float = 0f, setNumber: Int = 1) = ExerciseEntry(
        exerciseId = PLANK_ID, exerciseName = "Plank", setNumber = setNumber,
        kg = kg, reps = 0, durationSeconds = seconds
    )

    private fun bench(kg: Float, reps: Int, setNumber: Int = 1) = ExerciseEntry(
        exerciseId = BENCH_ID, exerciseName = "Bench Press", setNumber = setNumber,
        kg = kg, reps = reps
    )

    private fun session(date: String, number: Int, vararg sets: ExerciseEntry) = TrainingSession(
        id = "s$number", trainingNumber = number, date = date,
        exercises = sets.toMutableList()
    )

    @Test
    fun timedOnlySession_reportsHoldTimeNotVolume() {
        val s = session("2026/01/08", 1, hold(60, setNumber = 1), hold(90, setNumber = 2))
        val summary = WorkoutComparisonHelper.calculateSessionSummary(s, listOf(s))

        assertEquals(0f, summary.totalVolume, 0.001f)
        assertEquals(0, summary.totalReps)   // must not count the holds' placeholder 0 reps
        assertEquals(2, summary.totalSets)
        assertEquals(150, summary.totalHoldSeconds)
        assertEquals(2, summary.holdSetCount)
    }

    @Test
    fun mixedSession_keepsVolumeAndHoldTimeSeparate() {
        val s = session("2026/01/08", 1, bench(60f, 8), hold(90))
        val summary = WorkoutComparisonHelper.calculateSessionSummary(s, listOf(s))

        assertEquals(480f, summary.totalVolume, 0.001f)
        assertEquals(8, summary.totalReps)
        assertEquals(90, summary.totalHoldSeconds)
        assertEquals(1, summary.holdSetCount)
    }

    @Test
    fun repOnlySession_reportsNoHoldTile() {
        val s = session("2026/01/08", 1, bench(60f, 8))
        val summary = WorkoutComparisonHelper.calculateSessionSummary(s, listOf(s))
        assertEquals(0, summary.holdSetCount)
        assertEquals(0, summary.totalHoldSeconds)
    }

    @Test
    fun timedTrendRow_hasNoTopSetOr1rmAndCarriesHoldMetrics() {
        val s = session("2026/01/08", 1, hold(60, kg = 20f, setNumber = 1), hold(90, kg = 20f, setNumber = 2))
        val trends = WorkoutComparisonHelper.calculateExerciseTrends(s, listOf(s), library)
        val plank = trends.single { it.exerciseId == PLANK_ID }

        assertTrue(plank.isTimedExercise)
        assertNull("a hold has no top set", plank.currentTopSet)
        assertNull("a hold has no estimated 1RM", plank.currentEstimated1RM)
        assertEquals(0f, plank.currentVolume, 0.001f)
        assertEquals(90, plank.currentBestHoldSeconds)
        assertEquals(150, plank.currentTotalHoldSeconds)
        // 20 kg × 60 s + 20 kg × 90 s
        assertEquals(3000f, plank.currentLoadSeconds!!, 0.001f)
    }

    @Test
    fun unloadedHold_hasNoLoadSeconds() {
        val s = session("2026/01/08", 1, hold(90))
        val plank = WorkoutComparisonHelper.calculateExerciseTrends(s, listOf(s), library)
            .single { it.exerciseId == PLANK_ID }
        assertNull(plank.currentLoadSeconds)
    }

    @Test
    fun repExercise_isNotFlaggedTimedAndKeepsItsMetrics() {
        val s = session("2026/01/08", 1, bench(60f, 8))
        val row = WorkoutComparisonHelper.calculateExerciseTrends(s, listOf(s), library)
            .single { it.exerciseId == BENCH_ID }

        assertFalse(row.isTimedExercise)
        assertEquals(480f, row.currentVolume, 0.001f)
        assertEquals(60f to 8, row.currentTopSet)
        assertNull(row.currentBestHoldSeconds)
    }

    @Test
    fun timedTrend_comparesBestHoldAgainstPriorSession() {
        val prev = session("2026/01/01", 1, hold(60))
        val cur = session("2026/01/08", 2, hold(90))
        val plank = WorkoutComparisonHelper.calculateExerciseTrends(cur, listOf(prev, cur), library)
            .single { it.exerciseId == PLANK_ID }

        assertEquals(90, plank.currentBestHoldSeconds)
        assertEquals(60, plank.previousBestHoldSeconds)
        assertEquals(60, plank.previousTotalHoldSeconds)
    }
}
