package com.liftpath.helpers

import com.liftpath.models.Equipment
import com.liftpath.models.ExerciseLibraryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ladder model, which decides what "go heavier" actually means on each piece of equipment.
 *
 * Two invariants matter more than the individual numbers:
 *  - rungs are anchored at the **minimum**, not at zero, so a barbell reads 20 / 22.5 / 25 and a
 *    kettlebell 8 / 12 / 16 rather than a lattice of bare multiples;
 *  - a rule with no ladder (bands) is the identity everywhere and never divides by zero.
 */
class WeightIncrementHelperTest {

    private fun item(
        equipment: Equipment? = null,
        stepOverride: Float? = null,
        minOverride: Float? = null
    ) = ExerciseLibraryItem(
        id = 1,
        name = "Test Exercise",
        equipment = equipment,
        weightIncrementKgOverride = stepOverride,
        weightMinimumKgOverride = minOverride
    )

    private val barbell = WeightIncrementHelper.BUILT_IN[Equipment.BARBELL]!!
    private val cable = WeightIncrementHelper.BUILT_IN[Equipment.CABLE]!!
    private val dumbbell = WeightIncrementHelper.BUILT_IN[Equipment.DUMBBELL]!!
    private val kettlebell = WeightIncrementHelper.BUILT_IN[Equipment.KETTLEBELL]!!
    private val bands = WeightIncrementHelper.BUILT_IN[Equipment.BANDS]!!

    // ── Resolution ─────────────────────────────────────────────────────────

    @Test
    fun nullEquipmentFallsBackToTodaysStepperBehaviour() {
        val rule = WeightIncrementHelper.resolve(item(equipment = null), null)

        // 2.5 kg from zero is exactly what the hardcoded stepper did, so an unclassified
        // user-created exercise is untouched by this feature.
        assertEquals(WeightIncrementHelper.FALLBACK, rule)
        assertEquals(WeightIncrementHelper.BUILT_IN[Equipment.OTHER], rule)
    }

    @Test
    fun nullItemResolvesToFallback() {
        assertEquals(WeightIncrementHelper.FALLBACK, WeightIncrementHelper.resolve(null, null))
    }

    @Test
    fun builtInAppliesWhenNoTableStored() {
        val rule = WeightIncrementHelper.resolve(item(Equipment.CABLE), null)
        assertEquals(5f, rule.incrementKg, 0.001f)
        assertEquals(5f, rule.minimumKg, 0.001f)
    }

    @Test
    fun storedTableBeatsBuiltIn() {
        val table = EquipmentIncrementTable(
            mapOf(Equipment.CABLE.name to WeightIncrementRule(2.5f, 2.5f))
        )
        val rule = WeightIncrementHelper.resolve(item(Equipment.CABLE), table)

        assertEquals(2.5f, rule.incrementKg, 0.001f)
        assertEquals(2.5f, rule.minimumKg, 0.001f)
    }

    @Test
    fun tableEntriesAreIndependentOfEachOther() {
        // A half-filled table must not blank out the equipment it does not mention.
        val table = EquipmentIncrementTable(
            mapOf(Equipment.CABLE.name to WeightIncrementRule(2.5f, 2.5f))
        )
        val untouched = WeightIncrementHelper.resolve(item(Equipment.BARBELL), table)

        assertEquals(barbell, untouched)
    }

    @Test
    fun perExerciseOverrideBeatsEverything() {
        val table = EquipmentIncrementTable(
            mapOf(Equipment.BARBELL.name to WeightIncrementRule(5f, 25f))
        )
        val rule = WeightIncrementHelper.resolve(
            item(Equipment.BARBELL, stepOverride = 1f, minOverride = 0f), table
        )

        assertEquals(1f, rule.incrementKg, 0.001f)
        assertEquals(0f, rule.minimumKg, 0.001f)
    }

    @Test
    fun partialOverrideInheritsTheOtherField() {
        // Overriding only the step must keep the barbell's 20 kg floor — resolution is
        // field-by-field, not all-or-nothing.
        val rule = WeightIncrementHelper.resolve(item(Equipment.BARBELL, stepOverride = 1f), null)

        assertEquals(1f, rule.incrementKg, 0.001f)
        assertEquals(20f, rule.minimumKg, 0.001f)

        val minOnly = WeightIncrementHelper.resolve(item(Equipment.BARBELL, minOverride = 15f), null)
        assertEquals(2.5f, minOnly.incrementKg, 0.001f)
        assertEquals(15f, minOnly.minimumKg, 0.001f)
    }

    @Test
    fun unknownKeyInStoredTableFallsThroughToBuiltIn() {
        // A table written by a future version naming equipment this build does not have.
        val table = EquipmentIncrementTable(mapOf("PLYO_BOX" to WeightIncrementRule(9f, 9f)))
        assertEquals(barbell, WeightIncrementHelper.resolve(item(Equipment.BARBELL), table))
    }

    @Test
    fun nullRulesMapResolvesCleanly() {
        // The shape Gson produces from JSON written before this field existed.
        assertEquals(barbell, WeightIncrementHelper.resolve(item(Equipment.BARBELL), EquipmentIncrementTable(null)))
    }

    // ── Snapping ───────────────────────────────────────────────────────────

    @Test
    fun snapIsAFixedPointOnGrid() {
        for (kg in listOf(20f, 22.5f, 60f, 82.5f)) {
            assertEquals(kg, WeightIncrementHelper.snap(kg, barbell), 0.001f)
        }
    }

    @Test
    fun snapAnchorsAtTheMinimumNotZero() {
        // Kettlebells are 8/12/16/20. A zero-anchored lattice would put 13 on 12 by luck and 6 on
        // 4 wrongly; anchoring makes both correct.
        assertEquals(12f, WeightIncrementHelper.snap(13f, kettlebell), 0.001f)
        assertEquals(16f, WeightIncrementHelper.snap(15f, kettlebell), 0.001f)
        assertEquals(8f, WeightIncrementHelper.snap(6f, kettlebell), 0.001f)
    }

    @Test
    fun snapNeverGoesBelowTheMinimum() {
        assertEquals(20f, WeightIncrementHelper.snap(0f, barbell), 0.001f)
        assertEquals(20f, WeightIncrementHelper.snap(-50f, barbell), 0.001f)
        assertEquals(8f, WeightIncrementHelper.snap(1f, kettlebell), 0.001f)
    }

    @Test
    fun snapRoundsToNearestRung() {
        assertEquals(60f, WeightIncrementHelper.snap(61f, barbell), 0.001f)
        assertEquals(62.5f, WeightIncrementHelper.snap(62f, barbell), 0.001f)
        // The unrounded BUILD average that currently reaches the kg field as 68.333…
        assertEquals(67.5f, WeightIncrementHelper.snap(68.333f, barbell), 0.001f)
    }

    // ── nextUp ─────────────────────────────────────────────────────────────

    @Test
    fun nextUpIsStrictlyAboveTheInput() {
        // Strictness is what keeps an off-grid weight from stepping *down*.
        for (kg in listOf(20f, 22.5f, 60f, 61f, 82.5f, 100f)) {
            assertTrue(
                "nextUp($kg) must exceed $kg",
                WeightIncrementHelper.nextUp(kg, barbell) > kg
            )
        }
    }

    @Test
    fun nextUpLandsOnGridFromOffGridInput() {
        assertEquals(62.5f, WeightIncrementHelper.nextUp(61f, barbell), 0.001f)
        assertEquals(62.5f, WeightIncrementHelper.nextUp(60f, barbell), 0.001f)
        assertEquals(85f, WeightIncrementHelper.nextUp(82.5f, barbell), 0.001f)
    }

    @Test
    fun nextUpFromNothingIsTheEmptyBar() {
        assertEquals(20f, WeightIncrementHelper.nextUp(0f, barbell), 0.001f)
        assertEquals(5f, WeightIncrementHelper.nextUp(0f, cable), 0.001f)
    }

    @Test
    fun nextUpDiffersByEquipmentForTheSameLift() {
        // The whole point of the feature, in one assertion.
        assertEquals(85f, WeightIncrementHelper.nextUp(82.5f, barbell), 0.001f)
        // Cable rungs are multiples of 5, so 82.5 is between 80 and 85.
        assertEquals(85f, WeightIncrementHelper.nextUp(82.5f, cable), 0.001f)
        assertEquals(64f, WeightIncrementHelper.nextUp(60f, dumbbell), 0.001f)
        assertEquals(12f, WeightIncrementHelper.nextUp(8f, kettlebell), 0.001f)
    }

    // ── prevDown ───────────────────────────────────────────────────────────

    @Test
    fun prevDownIsStrictlyBelowAndFloorsAtMinimum() {
        assertEquals(60f, WeightIncrementHelper.prevDown(62.5f, barbell), 0.001f)
        assertEquals(60f, WeightIncrementHelper.prevDown(61f, barbell), 0.001f)
        assertEquals(20f, WeightIncrementHelper.prevDown(22.5f, barbell), 0.001f)
        assertEquals(20f, WeightIncrementHelper.prevDown(20f, barbell), 0.001f)
        assertEquals(20f, WeightIncrementHelper.prevDown(5f, barbell), 0.001f)
    }

    @Test
    fun nextUpThenPrevDownReturnsToTheStartingRung() {
        for (kg in listOf(20f, 22.5f, 60f, 82.5f)) {
            val up = WeightIncrementHelper.nextUp(kg, barbell)
            assertEquals(kg, WeightIncrementHelper.prevDown(up, barbell), 0.001f)
        }
    }

    // ── The no-ladder sentinel ─────────────────────────────────────────────

    @Test
    fun bandsHaveNoLadderAndNeverDivideByZero() {
        assertFalse(bands.hasLadder)

        // Every operation is the identity — and crucially none of them divides by the 0 step,
        // which is how the old unguarded roundToIncrement would have crashed.
        for (kg in listOf(0f, 12.5f, 61f)) {
            assertEquals(kg, WeightIncrementHelper.snap(kg, bands), 0.001f)
            assertEquals(kg, WeightIncrementHelper.nextUp(kg, bands), 0.001f)
            assertEquals(kg, WeightIncrementHelper.prevDown(kg, bands), 0.001f)
        }
    }

    // ── Coverage guard ─────────────────────────────────────────────────────

    @Test
    fun builtInCoversEveryEquipmentValue() {
        // Adding an 11th Equipment value must not silently fall through to the generic fallback.
        for (equipment in Equipment.values()) {
            assertNotNull(
                "No built-in weight rule for $equipment",
                WeightIncrementHelper.BUILT_IN[equipment]
            )
        }
    }

    @Test
    fun onlyBandsOptOutOfTheLadder() {
        val withoutLadder = Equipment.values().filter { !WeightIncrementHelper.BUILT_IN[it]!!.hasLadder }
        assertEquals(listOf(Equipment.BANDS), withoutLadder)
    }

    // ── Formatting ─────────────────────────────────────────────────────────

    @Test
    fun formatTrimsWholeNumbers() {
        assertEquals("85", WeightIncrementHelper.format(85f))
        assertEquals("82.5", WeightIncrementHelper.format(82.5f))
        assertEquals("68.3", WeightIncrementHelper.format(68.333f))
    }
}
