package com.liftpath.helpers

import com.liftpath.models.Laterality
import com.liftpath.models.TargetMuscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the Context-free half of the cool-down settings: the hold maths and the enum's
 * fallback. Both are read on the screen that greets the user immediately after a workout, which
 * is the worst possible place for a crash — the session is saved by then, but the report has not
 * been shown yet.
 */
class StretchSettingsManagerTest {

    @Test
    fun `1x leaves every authored duration untouched`() {
        for (stretch in DefaultStretchesHelper.ALL_STRETCHES) {
            assertEquals(
                "${stretch.name} changed at 1×",
                stretch.durationSeconds,
                StretchSettingsManager.scaledHold(stretch.durationSeconds, 1f)
            )
        }
    }

    @Test
    fun `scaling is proportional, so longer stretches stay longer`() {
        // 30s and 45s at 1.5× — the point of a multiplier over a fixed value.
        assertEquals(45, StretchSettingsManager.scaledHold(30, 1.5f))
        assertEquals(68, StretchSettingsManager.scaledHold(45, 1.5f))
        assertEquals(60, StretchSettingsManager.scaledHold(30, 2f))
        assertEquals(15, StretchSettingsManager.scaledHold(30, 0.5f))
    }

    @Test
    fun `a half-second result rounds rather than truncating`() {
        // 25 × 1.5 = 37.5
        assertEquals(38, StretchSettingsManager.scaledHold(25, 1.5f))
    }

    @Test
    fun `no multiplier can scale a hold below the floor`() {
        assertEquals(5, StretchSettingsManager.scaledHold(1, 0.5f))
        assertEquals(5, StretchSettingsManager.scaledHold(0, 0.5f))
    }

    @Test
    fun `every offered multiplier keeps the shortest stretch usable`() {
        val shortest = DefaultStretchesHelper.ALL_STRETCHES.minOf { it.durationSeconds }
        for (scale in StretchSettingsManager.HOLD_SCALES) {
            assertTrue(
                "$scale× drops the shortest stretch below 5s",
                StretchSettingsManager.scaledHold(shortest, scale) >= 5
            )
        }
    }

    @Test
    fun `an unknown or absent scope falls back to Auto rather than throwing`() {
        assertEquals(StretchScope.AUTO, StretchScope.fromPrefValue(null))
        assertEquals(StretchScope.AUTO, StretchScope.fromPrefValue("moderate"))
        assertEquals(StretchScope.AUTO, StretchScope.fromPrefValue(""))
    }

    @Test
    fun `every scope round-trips through its pref value`() {
        for (scope in StretchScope.values()) {
            assertEquals(scope, StretchScope.fromPrefValue(scope.prefValue))
        }
    }

    /**
     * The reason [StretchScope.FULL] reads `ALL_STRETCHES` directly instead of passing every
     * muscle to [DefaultStretchesHelper.getStretchesFor]: that helper is a greedy set-cover, so
     * it drops any stretch whose muscles an earlier one already covers. If this ever stops being
     * true the FULL branch can be simplified — until then, "full body" has to bypass it.
     */
    @Test
    fun `a set-cover of every muscle is not the whole catalogue`() {
        val covered = DefaultStretchesHelper.getStretchesFor(TargetMuscle.values().toSet())
        assertTrue(
            "getStretchesFor(all muscles) now returns everything — StretchScope.FULL could use it",
            covered.size < DefaultStretchesHelper.ALL_STRETCHES.size
        )
    }

    /** The estimate on the setup screen counts a unilateral stretch as two holds, matching the
     *  cool-down's own "Stretch 3 of 12" label. Guards the assumption that both sides exist. */
    @Test
    fun `unilateral stretches are held twice`() {
        val unilateral = DefaultStretchesHelper.ALL_STRETCHES
            .filter { it.laterality == Laterality.UNILATERAL }
        assertTrue("no unilateral stretches to count", unilateral.isNotEmpty())

        val steps = DefaultStretchesHelper.ALL_STRETCHES
            .sumOf { stretch -> DefaultStretchesHelper.holdCount(stretch) }
        assertEquals(DefaultStretchesHelper.ALL_STRETCHES.size + unilateral.size, steps)
    }
}
