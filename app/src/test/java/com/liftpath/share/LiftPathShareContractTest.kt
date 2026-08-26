package com.liftpath.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract is duplicated verbatim in TriPath. Nothing in the build system can enforce that, so
 * these tests pin the values a one-sided edit would change.
 *
 * [EXPECTED_SCHEMA_HASH] is the load-bearing assertion: TriPath's own
 * `LiftPathShareContractTest` pins the same literal, so editing a column on one side turns that
 * side's build red instead of leaving two APKs that disagree at runtime about what `duration_seconds`
 * means. When a column genuinely changes, both literals and both `CONTRACT_VERSION`s move together.
 */
class LiftPathShareContractTest {

    private companion object {
        /**
         * Hash of the current column signatures. **Do not "fix" this to make a build pass** — a
         * mismatch means the contract changed, and the other app's copy has to change with it.
         */
        const val EXPECTED_SCHEMA_HASH = "-5313bb2c"
    }

    @Test
    fun `schema hash is stable across repeated calls`() {
        assertEquals(LiftPathShareContract.schemaHash(), LiftPathShareContract.schemaHash())
    }

    @Test
    fun `schema hash matches the value TriPath's copy of this contract pins`() {
        assertEquals(EXPECTED_SCHEMA_HASH, LiftPathShareContract.schemaHash())
    }

    @Test
    fun `schema hash changes when a column is renamed`() {
        val renamed = LiftPathShareContract.Sets.SPEC.map {
            if (it.name == LiftPathShareContract.Sets.RPE) {
                it.copy(name = "rate_of_perceived_exertion")
            } else {
                it
            }
        }
        assertNotEquals(signatureFor(LiftPathShareContract.Sets.SPEC), signatureFor(renamed))
    }

    /** A `Long` that quietly became an `Int` reads fine and truncates. Names alone would miss it. */
    @Test
    fun `schema hash changes when a column is re-typed`() {
        val retyped = LiftPathShareContract.Sets.SPEC.map {
            if (it.name == LiftPathShareContract.Sets.KG) it.copy(type = "TEXT") else it
        }
        assertNotEquals(signatureFor(LiftPathShareContract.Sets.SPEC), signatureFor(retyped))
    }

    @Test
    fun `schema hash changes when a column changes nullability`() {
        val loosened = LiftPathShareContract.Sessions.SPEC.map {
            if (it.name == LiftPathShareContract.Sessions.PLAN_NAME) it.copy(nullable = false) else it
        }
        assertNotEquals(signatureFor(LiftPathShareContract.Sessions.SPEC), signatureFor(loosened))
    }

    @Test
    fun `capability tokens cover set-level lifting data and the exercise catalog`() {
        assertTrue(LiftPathShareContract.CAPABILITIES.contains(LiftPathShareContract.CAP_LIFT_SETS_V1))
        assertTrue(LiftPathShareContract.CAPABILITIES.contains(LiftPathShareContract.CAP_LIFT_CATALOG_V1))
    }

    /** Every SPEC entry must be reachable through COLUMNS, or the provider writes a row it never fills. */
    @Test
    fun `every path's COLUMNS and SPEC describe the same columns`() {
        assertEquals(
            LiftPathShareContract.Sessions.COLUMNS.toSet(),
            LiftPathShareContract.Sessions.SPEC.map { it.name }.toSet()
        )
        assertEquals(
            LiftPathShareContract.Sets.COLUMNS.toSet(),
            LiftPathShareContract.Sets.SPEC.map { it.name }.toSet()
        )
        assertEquals(
            LiftPathShareContract.Exercises.COLUMNS.toSet(),
            LiftPathShareContract.Exercises.SPEC.map { it.name }.toSet()
        )
    }

    /** Mirrors the per-path building block inside [LiftPathShareContract.schemaHash]. */
    private fun signatureFor(spec: List<LiftPathShareContract.ColumnSpec>): String =
        spec.sortedBy { it.name }
            .joinToString(",") { "${it.name}:${it.type}:${if (it.nullable) "1" else "0"}" }
}
