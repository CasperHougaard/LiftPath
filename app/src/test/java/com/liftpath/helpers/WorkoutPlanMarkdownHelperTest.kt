package com.liftpath.helpers

import com.liftpath.models.CircuitItem
import com.liftpath.models.CircuitTemplate
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.PlanExerciseSelectionType
import com.liftpath.models.PlanSlotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPlanMarkdownHelperTest {

    private val library = listOf(
        ExerciseLibraryItem(id = 1, name = "Bench Press (Barbell)"),
        ExerciseLibraryItem(id = 121, name = "Plank"),
        ExerciseLibraryItem(id = 5, name = "Cable Row")
    )

    private val markdown = """
        ## Plan: Push Day
        Notes: test plan

        | Exercise Name | Exercise ID | Sets | Reps | Intent | RPE Target | Rest (sec) | Notes | Family ID | Time (sec) |
        |---|---|---|---|---|---|---|---|---|---|
        | Bench Press | 1 | 4 | 8-10 | STRENGTH | 8.0 | 120 | cue |  |  |
        | Seated Cable Row | 999 | 3 | 10 | BUILD | 7.0 | 90 |  |  |  |
        | Plank | 121 | 3 |  | BUILD | 7.0 | 60 | brace |  | 45 |
    """.trimIndent()

    @Test
    fun parse_flagsUnknownIdAndKeepsSlotForRemap() {
        val result = WorkoutPlanMarkdownHelper.parsePlansFromMarkdown(markdown, library)

        // The unknown id (999) is reported once, with the informational name from column 1.
        assertEquals(1, result.unresolved.size)
        assertEquals(999, result.unresolved[0].rawId)
        assertEquals("Seated Cable Row", result.unresolved[0].displayName)

        // The plan still contains all three exercise slots (the unknown one is kept, not dropped).
        assertEquals(1, result.plans.size)
        val slots = result.plans[0].exerciseConfigs!!.filter { it.slotType == PlanSlotType.EXERCISE }
        assertEquals(3, slots.size)

        // Timed exercise: Reps blank, Time (sec) set.
        val plank = slots.first { it.exerciseId == 121 }
        assertNull(plank.repsTarget)
        assertEquals(45, plank.durationSeconds)
    }

    @Test
    fun applyRemap_mappingReplacesUnknownId() {
        val parsed = WorkoutPlanMarkdownHelper.parsePlansFromMarkdown(markdown, library)
        val outcome = WorkoutPlanMarkdownHelper.applyRemap(parsed.plans, mapOf(999 to 5))

        assertEquals(1, outcome.plans.size)
        assertTrue(outcome.droppedPlanNames.isEmpty())

        val plan = outcome.plans[0]
        // The unknown slot now points at the chosen library id (5), no slot dropped.
        assertEquals(3, plan.exerciseConfigs!!.count { it.slotType == PlanSlotType.EXERCISE })
        assertEquals(listOf(1, 5, 121), plan.exerciseIds)
        val remapped = plan.exerciseConfigs!!.first { it.exerciseId == 5 }
        assertEquals(PlanExerciseSelectionType.SPECIFIC_VARIANT, remapped.effectiveSelectionType)
    }

    @Test
    fun applyRemap_skipDropsUnknownSlot() {
        val parsed = WorkoutPlanMarkdownHelper.parsePlansFromMarkdown(markdown, library)
        val outcome = WorkoutPlanMarkdownHelper.applyRemap(parsed.plans, mapOf(999 to null))

        // Skipped exercise is removed; the two known exercises survive.
        assertEquals(1, outcome.plans.size)
        val plan = outcome.plans[0]
        assertEquals(listOf(1, 121), plan.exerciseIds)
        assertTrue(plan.exerciseConfigs!!.none { it.exerciseId == 999 })
    }

    @Test
    fun applyRemap_dropsPlanWhenAllExercisesSkipped() {
        val onlyUnknown = """
            ## Plan: Mystery
            | Exercise Name | Exercise ID | Sets | Reps | Intent | RPE Target | Rest (sec) | Notes | Family ID | Time (sec) |
            |---|---|---|---|---|---|---|---|---|---|
            | Ghost Lift | 999 | 3 | 10 | BUILD | 7.0 | 90 |  |  |  |
        """.trimIndent()
        val parsed = WorkoutPlanMarkdownHelper.parsePlansFromMarkdown(onlyUnknown, library)
        assertEquals(1, parsed.unresolved.size)

        val outcome = WorkoutPlanMarkdownHelper.applyRemap(parsed.plans, mapOf(999 to null))
        assertTrue(outcome.plans.isEmpty())
        assertEquals(listOf("Mystery"), outcome.droppedPlanNames)
    }

    @Test
    fun parse_circuitRoundTrip_resolvesRoundsRestAndItems() {
        val markdownWithCircuit = """
            ## Circuits

            ### Circuit: Lower Lactate
            Suggested rounds: 3
            Rest (sec): 90

            | Exercise Name | Exercise ID | Reps | Time (sec) | Load (kg) | Notes |
            |---|---|---|---|---|---|
            | Bench Press | 1 | 12 |  | 20 |  |
            | Plank |  121 |  | 45 |  |  |

            ## Plan: Leg Day

            | Exercise Name | Exercise ID | Sets | Reps | Intent | RPE Target | Rest (sec) | Notes | Family ID | Time (sec) |
            |---|---|---|---|---|---|---|---|---|---|
            | Cable Row | 5 | 4 | 8-10 | STRENGTH | 8.0 | 120 |  |  |  |
            | __circuit__: Lower Lactate |  | 3 |  |  |  | 90 |  |  |  |
        """.trimIndent()

        val result = WorkoutPlanMarkdownHelper.parsePlansFromMarkdown(markdownWithCircuit, library)

        assertEquals(1, result.circuits.size)
        val circuit = result.circuits[0]
        assertEquals("Lower Lactate", circuit.name)
        assertEquals(3, circuit.suggestedRounds)
        assertEquals(90, circuit.restBetweenRoundsSeconds)
        assertEquals(2, circuit.items.size)
        assertEquals(45, circuit.items.first { it.exerciseId == 121 }.targetDurationSeconds)

        assertEquals(1, result.plans.size)
        val slots = result.plans[0].exerciseConfigs!!
        val circuitSlot = slots.first { it.slotType == PlanSlotType.CIRCUIT }
        assertEquals(circuit.id, circuitSlot.circuitId)
        assertEquals(3, circuitSlot.setsTarget)
        assertEquals(90, circuitSlot.restTimeSeconds)
        // Order survives: the circuit row stays after the exercise slot that precedes it.
        assertEquals(listOf(PlanSlotType.EXERCISE, PlanSlotType.CIRCUIT), slots.map { it.slotType })
        assertTrue(result.unresolvedCircuitNames.isEmpty())
    }

    @Test
    fun parse_circuitReusesExistingTemplateByName() {
        val existing = CircuitTemplate(
            id = "existing-id",
            name = "Lower Lactate",
            suggestedRounds = 4,
            restBetweenRoundsSeconds = 60,
            items = listOf(CircuitItem(exerciseId = 1, targetReps = "12"))
        )
        val markdownWithReference = """
            ## Plan: Leg Day

            | Exercise Name | Exercise ID | Sets | Reps | Intent | RPE Target | Rest (sec) | Notes | Family ID | Time (sec) |
            |---|---|---|---|---|---|---|---|---|---|
            | __circuit__: Lower Lactate |  |  |  |  |  | 90 |  |  |  |
        """.trimIndent()

        val result = WorkoutPlanMarkdownHelper.parsePlansFromMarkdown(
            markdownWithReference, library, listOf(existing)
        )

        assertTrue(result.circuits.isEmpty())
        val slot = result.plans[0].exerciseConfigs!!.first { it.slotType == PlanSlotType.CIRCUIT }
        assertEquals("existing-id", slot.circuitId)
    }

    @Test
    fun parse_unresolvedCircuitNameIsReportedAndRowDropped() {
        val markdownWithUnknownCircuit = """
            ## Plan: Leg Day

            | Exercise Name | Exercise ID | Sets | Reps | Intent | RPE Target | Rest (sec) | Notes | Family ID | Time (sec) |
            |---|---|---|---|---|---|---|---|---|---|
            | Cable Row | 5 | 4 | 8-10 | STRENGTH | 8.0 | 120 |  |  |  |
            | __circuit__: Ghost Circuit |  | 3 |  |  |  | 90 |  |  |  |
        """.trimIndent()

        val result = WorkoutPlanMarkdownHelper.parsePlansFromMarkdown(markdownWithUnknownCircuit, library)

        assertEquals(listOf("Ghost Circuit"), result.unresolvedCircuitNames)
        assertTrue(result.plans[0].exerciseConfigs!!.none { it.slotType == PlanSlotType.CIRCUIT })
    }
}
