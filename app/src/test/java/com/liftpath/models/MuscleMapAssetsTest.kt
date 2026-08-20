package com.liftpath.models

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guarantees every TargetMuscle has a mask mapping with resolvable drawables — replaces the old
 * runtime checkMissingMuscleIds() diagnostic with a compile/test-time check.
 */
class MuscleMapAssetsTest {

    @Test
    fun `every TargetMuscle has a mask mapping`() {
        val missing = TargetMuscle.values().filterNot { MuscleMapAssets.maskResIds.containsKey(it) }
        assertTrue("TargetMuscle values missing a mask mapping: $missing", missing.isEmpty())
    }

    @Test
    fun `every TargetMuscle maps to at least one resolvable drawable`() {
        TargetMuscle.values().forEach { muscle ->
            val maskResIds = MuscleMapAssets.maskResIds[muscle]
            assertTrue("No masks defined for $muscle", !maskResIds.isNullOrEmpty())
            maskResIds!!.forEach { resId ->
                assertNotEquals("Unresolvable drawable id for $muscle", 0, resId)
            }
        }
    }
}
