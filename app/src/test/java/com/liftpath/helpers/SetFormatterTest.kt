package com.liftpath.helpers

import com.liftpath.models.ExerciseEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Asserts the composed text of a set line. Colouring is applied over the same [SetFormatter.Segment]
 * list, so covering composition covers both renderings.
 */
class SetFormatterTest {

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

    private fun line(e: ExerciseEntry, repsUnit: Boolean = false) =
        SetFormatter.setLinePlain(e, repsUnit = repsUnit)

    @Test
    fun weightedReps_rendersLoadAndReps() {
        assertEquals("60kg × 8 reps", line(weightedReps(60f, 8), repsUnit = true))
        assertEquals("62.5kg × 8", line(weightedReps(62.5f, 8)))
    }

    @Test
    fun bodyweightReps_showsBodyWeightAndSignedExtra() {
        assertEquals("80.5 +10kg × 8 reps", line(bodyweightReps(80.5f, 10f, 8), repsUnit = true))
        assertEquals("80.5 −20kg × 8 reps", line(bodyweightReps(80.5f, -20f, 8), repsUnit = true))
    }

    @Test
    fun bodyweightReps_withoutExtra_stillShowsBodyWeight() {
        assertEquals("80.5kg × 8 reps", line(bodyweightReps(80.5f, 0f, 8), repsUnit = true))
    }

    @Test
    fun weightedHold_showsDurationPlusExternalLoad() {
        assertEquals("1:30 + 20kg", line(weightedHold(20f, 90)))
    }

    @Test
    fun unloadedHold_showsDurationOnly() {
        // kg is 0, so there is no load clause to add.
        assertEquals("1:30", line(weightedHold(0f, 90)))
    }

    @Test
    fun bodyweightHold_usesAtNotPlus() {
        // Regression guard: every pre-existing formatter rendered a bodyweight hold's `kg` (which
        // is the body weight) as external load — "1:30 + 80.5kg" — implying a loaded plank.
        val rendered = line(bodyweightHold(80.5f, 10f, 90))
        assertEquals("1:30 @ 80.5 +10kg", rendered)
        assertFalse("body weight must not read as added external load", rendered.contains("+ 80.5"))
    }

    @Test
    fun bodyweightHold_withoutExtra_showsDurationOnly() {
        // A plain plank: body weight adds nothing to "1:30", so it is omitted.
        assertEquals("1:30", line(bodyweightHold(80.5f, 0f, 90)))
    }

    @Test
    fun compactForm_dropsUnitAndSpacing() {
        assertEquals("62.5×8", SetFormatter.plain(SetFormatter.segments(weightedReps(62.5f, 8), compact = true)))
        assertEquals("1:30", SetFormatter.plain(SetFormatter.segments(weightedHold(0f, 90), compact = true)))
        assertEquals("1:30 +20", SetFormatter.plain(SetFormatter.segments(weightedHold(20f, 90), compact = true)))
    }

    @Test
    fun prefixAndSuffixAreApplied() {
        assertEquals("Set 2: 60kg × 8 reps (8.0)", SetFormatter.setLinePlain(
            weightedReps(60f, 8), prefix = "Set 2: ", suffix = " (8.0)", repsUnit = true
        ))
    }

    @Test
    fun loadCellPlain_isExplicitAboutBodyWeight() {
        assertEquals("62.5", SetFormatter.loadCellPlain(weightedReps(62.5f, 8)))
        assertEquals("BW80.5+10=90.5", SetFormatter.loadCellPlain(bodyweightReps(80.5f, 10f, 8)))
        assertEquals("BW80.5-20=60.5", SetFormatter.loadCellPlain(bodyweightReps(80.5f, -20f, 8)))
        assertEquals("BW80.5=80.5", SetFormatter.loadCellPlain(bodyweightReps(80.5f, 0f, 8)))
    }

    @Test
    fun emphasis_marksBodyWeightMutedAndExtraSigned() {
        val added = SetFormatter.loadSegments(bodyweightReps(80.5f, 10f, 8))
        assertEquals(SetFormatter.Emphasis.MUTED, added[0].emphasis)
        assertEquals(SetFormatter.Emphasis.ADDED, added[1].emphasis)

        val assisted = SetFormatter.loadSegments(bodyweightReps(80.5f, -20f, 8))
        assertEquals(SetFormatter.Emphasis.ASSISTED, assisted[1].emphasis)
    }
}
