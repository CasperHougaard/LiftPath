package com.liftpath.helpers

import com.liftpath.models.CircuitInstance
import com.liftpath.models.CircuitItem
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CircuitStoreTest {

    private val library = listOf(
        ExerciseLibraryItem(id = 1, name = "Hip Thrust (Barbell)"),
        ExerciseLibraryItem(id = 2, name = "Wall Sit", exerciseType = ExerciseType.BODYWEIGHT, targetMetric = com.liftpath.models.ExerciseTargetMetric.TIME)
    )

    private val instance = CircuitInstance(
        templateId = "t1",
        name = "Lower Lactate",
        suggestedRounds = 3,
        restBetweenRoundsSeconds = 90,
        items = listOf(
            CircuitItem(id = "i1", exerciseId = 1, targetReps = "12", targetKg = 60f),
            CircuitItem(id = "i2", exerciseId = 2, targetDurationSeconds = 45)
        )
    )

    @Test
    fun entriesForRound_oneEntryPerStation_withCorrectSetNumberAndGroup() {
        val inputs = listOf(
            CircuitStore.StationInput(itemId = "i1", exerciseId = 1, kg = 60f, reps = 12),
            CircuitStore.StationInput(itemId = "i2", exerciseId = 2, durationSeconds = 40)
        )

        val entries = CircuitStore.entriesForRound(instance, round = 2, inputs = inputs, library = library, bodyweightKg = 70f)

        assertEquals(2, entries.size)
        entries.forEach {
            assertEquals(2, it.setNumber)
            assertEquals(instance.instanceId, it.groupId)
            assertEquals(com.liftpath.models.GroupType.CIRCUIT, it.groupType)
        }

        val hipThrust = entries.first { it.exerciseId == 1 }
        assertEquals(60f, hipThrust.kg)
        assertNull(hipThrust.bodyweightKg)

        val wallSit = entries.first { it.exerciseId == 2 }
        assertEquals(40, wallSit.durationSeconds)
        assertEquals(70f, wallSit.bodyweightKg)
    }

    @Test
    fun entriesForRound_skippedStationsAreExcluded() {
        val inputs = listOf(
            CircuitStore.StationInput(itemId = "i1", exerciseId = 1, kg = 60f, reps = 12, skipped = true),
            CircuitStore.StationInput(itemId = "i2", exerciseId = 2, durationSeconds = 40)
        )

        val entries = CircuitStore.entriesForRound(instance, round = 1, inputs = inputs, library = library, bodyweightKg = null)

        assertEquals(1, entries.size)
        assertEquals(2, entries[0].exerciseId)
    }

    @Test
    fun entriesForRound_roundPastSuggestedCount_numbersIdenticallyToRoundOne() {
        val inputs = listOf(
            CircuitStore.StationInput(itemId = "i1", exerciseId = 1, kg = 60f, reps = 12),
            CircuitStore.StationInput(itemId = "i2", exerciseId = 2, durationSeconds = 45)
        )

        // instance.suggestedRounds is 3; round 5 must behave exactly like round 1 — no clamping.
        val round1 = CircuitStore.entriesForRound(instance, round = 1, inputs = inputs, library = library, bodyweightKg = null)
        val round5 = CircuitStore.entriesForRound(instance, round = 5, inputs = inputs, library = library, bodyweightKg = null)

        assertEquals(round1.size, round5.size)
        assertEquals(1, round1[0].setNumber)
        assertEquals(5, round5[0].setNumber)
        assertEquals(round1[0].kg, round5[0].kg)
    }

    @Test
    fun instanceToLog_reportsRoundsActuallyRun() {
        val finished = instance.copy(completedRounds = 5, roundWorkSeconds = listOf(60, 62, 58, 61, 59))

        val log = CircuitStore.instanceToLog(finished)

        assertEquals(5, log.roundsCompleted)
        assertEquals(5, log.roundWorkSeconds.size)
        // Never clamped to the template's suggestion of 3.
        assertTrue(log.roundsCompleted > (finished.suggestedRounds ?: 0))
    }
}
