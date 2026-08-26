package com.liftpath.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract is duplicated verbatim in TriPath (`com.tripath.data.local.share.TriPathShareContract`).
 * Nothing in either build system can enforce that, so these tests pin the values a one-sided edit
 * changes.
 *
 * [EXPECTED_SCHEMA_HASH] is the load-bearing one: TriPath's copy pins the same literal, so editing
 * a column on one side turns that side's build red instead of leaving two APKs that agree on the
 * version number and disagree about what a column contains.
 */
class TriPathContractTest {

    private companion object {
        /**
         * Hash of the current column signatures, pinned identically in TriPath's
         * `TriPathShareContractTest`. **Do not "fix" this to make a build pass** — a mismatch means
         * the contract changed, and the other app's copy has to change with it.
         */
        const val EXPECTED_SCHEMA_HASH = "1f50c308"
    }

    @Test
    fun `schema hash matches the value TriPath's copy of this contract pins`() {
        assertEquals(EXPECTED_SCHEMA_HASH, TriPathContract.schemaHash())
    }

    @Test
    fun `schema hash is stable across repeated calls`() {
        assertEquals(TriPathContract.schemaHash(), TriPathContract.schemaHash())
    }

    @Test
    fun `contract version is 2 now that readiness is consumed`() {
        assertEquals(2, TriPathContract.CONTRACT_VERSION)
    }

    @Test
    fun `schema hash changes when a column is renamed`() {
        val renamed = TriPathContract.Readiness.SPEC.map {
            if (it.name == TriPathContract.Readiness.SCORE) it.copy(name = "readiness_score") else it
        }
        assertNotEquals(signatureFor(TriPathContract.Readiness.SPEC), signatureFor(renamed))
    }

    @Test
    fun `schema hash changes when a column is re-typed`() {
        val retyped = TriPathContract.Days.SPEC.map {
            if (it.name == TriPathContract.Days.TARGET_KCAL) it.copy(type = "INTEGER") else it
        }
        assertNotEquals(signatureFor(TriPathContract.Days.SPEC), signatureFor(retyped))
    }

    @Test
    fun `every path's COLUMNS and SPEC describe the same columns`() {
        assertEquals(
            TriPathContract.Days.COLUMNS.toSet(),
            TriPathContract.Days.SPEC.map { it.name }.toSet()
        )
        assertEquals(
            TriPathContract.Workouts.COLUMNS.toSet(),
            TriPathContract.Workouts.SPEC.map { it.name }.toSet()
        )
        assertEquals(
            TriPathContract.Readiness.COLUMNS.toSet(),
            TriPathContract.Readiness.SPEC.map { it.name }.toSet()
        )
    }

    /**
     * A capability the consumer does not recognise must hide a feature, not crash on it — that is
     * the whole reason for negotiating on tokens rather than on a version number.
     */
    @Test
    fun `an unknown capability is simply absent rather than an error`() {
        val handshake = TriPathConnection.Handshake(
            contractVersion = TriPathContract.CONTRACT_VERSION,
            schemaHash = TriPathContract.schemaHash(),
            capabilities = listOf("SOMETHING_FROM_THE_FUTURE_V9"),
            appVersionName = "9.9",
            workoutCount = 0,
            latestWorkoutDate = null,
            latestWellnessDate = null
        )
        assertTrue(!handshake.hasCapability(TriPathContract.CAP_READINESS_V1))
        assertTrue(handshake.hasCapability("SOMETHING_FROM_THE_FUTURE_V9"))
        assertTrue(handshake.versionMatches)
        assertTrue(handshake.schemaMatches)
    }

    /** A drifted schema must be visible even when the version number was never bumped. */
    @Test
    fun `a mismatched schema hash is reported even when the version agrees`() {
        val handshake = TriPathConnection.Handshake(
            contractVersion = TriPathContract.CONTRACT_VERSION,
            schemaHash = "deadbeef",
            capabilities = TriPathContract.CAPABILITIES,
            appVersionName = "1.0",
            workoutCount = 0,
            latestWorkoutDate = null,
            latestWellnessDate = null
        )
        assertTrue(handshake.versionMatches)
        assertTrue(!handshake.schemaMatches)
    }

    private fun signatureFor(spec: List<TriPathContract.ColumnSpec>): String =
        spec.sortedBy { it.name }
            .joinToString(",") { "${it.name}:${it.type}:${if (it.nullable) "1" else "0"}" }
}
