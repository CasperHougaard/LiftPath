package com.liftpath.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the two things about the watch wire format that fail *silently* rather than loudly.
 *
 * Exercises cross the link as positional arrays (Monkey C dictionaries cost real watch memory),
 * so a drift between the `EX_*` constants here and in `Protocol.mc` shifts fields by one instead
 * of erroring — you find out because the watch tells you to lift 3 kg for 60 reps. And commands
 * arrive with loosely-typed numbers, so an over-strict parse rejects valid messages and the
 * watch looks dead.
 *
 * Neither is reachable from an emulator without a paired Fenix, which is exactly why they are
 * worth pinning here.
 */
class WatchProtocolTest {

    private fun exercise(
        id: Int = 7,
        name: String = "Bench Press",
        setsDone: Int = 2,
        setsTarget: Int = 4,
        repsTarget: Int = 8,
        suggestedKg: Float = 60f,
        isBodyweight: Boolean = false
    ) = WatchExercise(id, name, setsDone, setsTarget, repsTarget, suggestedKg, isBodyweight)

    /**
     * The positional contract. If this breaks, `Protocol.mc` must change in the same commit —
     * and `WatchProtocol.VERSION` must be bumped so the watch rejects the old shape rather than
     * misreading it.
     */
    @Test
    fun `exercise fields land at the indices the watch reads`() {
        val wire = WatchState(true, 0, listOf(exercise())).toWire()

        @Suppress("UNCHECKED_CAST")
        val rows = wire[WatchProtocol.KEY_EXERCISES] as List<List<Any>>
        val row = rows.single()

        assertEquals(WatchProtocol.EX_FIELD_COUNT, row.size)
        assertEquals(7, row[WatchProtocol.EX_ID])
        assertEquals("Bench Press", row[WatchProtocol.EX_NAME])
        assertEquals(2, row[WatchProtocol.EX_SETS_DONE])
        assertEquals(4, row[WatchProtocol.EX_SETS_TARGET])
        assertEquals(8, row[WatchProtocol.EX_REPS_TARGET])
        assertEquals(60f, row[WatchProtocol.EX_SUGGESTED_KG])
        assertEquals(0, row[WatchProtocol.EX_BODYWEIGHT])
    }

    @Test
    fun `version travels with every state so the watch can refuse a skew`() {
        val wire = WatchState.IDLE.toWire()
        assertEquals(WatchProtocol.VERSION, wire[WatchProtocol.KEY_VERSION])
        assertEquals(0, wire[WatchProtocol.KEY_ACTIVE])
    }

    /**
     * An oversized message is rejected wholesale by the transport, which on the watch looks
     * identical to the phone having died. Truncating is the lesser evil, but only if it actually
     * happens.
     */
    @Test
    fun `long sessions and long names are capped before sending`() {
        val many = (1..40).map { exercise(id = it, name = "Romanian Deadlift (Dumbbell)") }
        val wire = WatchState(true, 0, many).toWire()

        @Suppress("UNCHECKED_CAST")
        val rows = wire[WatchProtocol.KEY_EXERCISES] as List<List<Any>>

        assertEquals(WatchProtocol.MAX_EXERCISES, rows.size)
        rows.forEach { row ->
            val name = row[WatchProtocol.EX_NAME] as String
            assertTrue(
                "name '$name' exceeds MAX_NAME_LENGTH",
                name.length <= WatchProtocol.MAX_NAME_LENGTH
            )
        }
    }

    /**
     * Monkey C hands the SDK a `Number` or a `Float` and what reaches Java depends on which it
     * happened to be. A strict `as Int` here would drop real sets.
     */
    @Test
    fun `log commands accept whatever numeric type the watch happened to send`() {
        val variants = listOf<Any>(60, 60L, 60f, 60.0)

        variants.forEach { load ->
            val parsed = WatchCommand.parse(
                mapOf(
                    WatchProtocol.KEY_COMMAND to WatchProtocol.CMD_LOG_SET,
                    WatchProtocol.KEY_EXERCISE_ID to 7,
                    WatchProtocol.KEY_REPS to 8,
                    WatchProtocol.KEY_KG to load
                )
            )
            assertEquals(
                "kg sent as ${load.javaClass.simpleName} was not parsed",
                WatchCommand.LogSet(exerciseId = 7, reps = 8, kg = 60f, rpe = null),
                parsed
            )
        }
    }

    @Test
    fun `absent load means bodyweight, not a dropped command`() {
        val parsed = WatchCommand.parse(
            mapOf(
                WatchProtocol.KEY_COMMAND to WatchProtocol.CMD_LOG_SET,
                WatchProtocol.KEY_EXERCISE_ID to 3,
                WatchProtocol.KEY_REPS to 12
            )
        )
        assertEquals(WatchCommand.LogSet(3, 12, 0f, null), parsed)
    }

    @Test
    fun `rest and sync commands round trip`() {
        assertEquals(
            WatchCommand.StartRest(90),
            WatchCommand.parse(
                mapOf(
                    WatchProtocol.KEY_COMMAND to WatchProtocol.CMD_REST_START,
                    WatchProtocol.KEY_SECONDS to 90
                )
            )
        )
        assertEquals(
            WatchCommand.StopRest,
            WatchCommand.parse(mapOf(WatchProtocol.KEY_COMMAND to WatchProtocol.CMD_REST_STOP))
        )
        assertEquals(
            WatchCommand.Sync,
            WatchCommand.parse(mapOf(WatchProtocol.KEY_COMMAND to WatchProtocol.CMD_SYNC))
        )
    }

    /**
     * A malformed message must never reach the logging path. Returning null lets the bridge log
     * and move on; throwing would surface on a binder thread mid-workout.
     */
    @Test
    fun `malformed payloads are rejected rather than thrown`() {
        assertNull(WatchCommand.parse(null))
        assertNull(WatchCommand.parse("not a map"))
        assertNull(WatchCommand.parse(emptyMap<String, Any>()))
        assertNull(WatchCommand.parse(mapOf(WatchProtocol.KEY_COMMAND to "nonsense")))

        // A log command with no exercise cannot be applied to anything.
        assertNull(
            WatchCommand.parse(
                mapOf(
                    WatchProtocol.KEY_COMMAND to WatchProtocol.CMD_LOG_SET,
                    WatchProtocol.KEY_REPS to 8
                )
            )
        )
        // ...nor one with no reps: zero reps is not a set, it is a missing field.
        assertNull(
            WatchCommand.parse(
                mapOf(
                    WatchProtocol.KEY_COMMAND to WatchProtocol.CMD_LOG_SET,
                    WatchProtocol.KEY_EXERCISE_ID to 7
                )
            )
        )
    }
}
