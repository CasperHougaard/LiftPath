package com.liftpath.watch

/**
 * Wire format between LiftPath and the Fenix 8 Connect IQ companion.
 *
 * Two platform constraints shape this, and explain why it looks nothing like the rest of the
 * app's models:
 *
 *  - Connect IQ messages are size-limited (a few KB, varying by device) and Garmin Connect
 *    Mobile re-serialises the payload in transit. So this is a *projection* of the live
 *    session, trimmed to what a watch screen can show — never a copy of `ActiveWorkoutDraft`.
 *  - Monkey C dictionaries cost meaningfully more watch memory than arrays. Each exercise is
 *    therefore a positional array rather than a keyed map. The `EX_*` index constants below
 *    are the single definition of that order; `SessionModel.mc` on the watch mirrors them,
 *    and the two must be changed together or fields silently shift by one.
 *
 * The phone is authoritative. The watch sends commands and receives whole states; nothing is
 * merged, so there is no conflict to resolve and no ordering guarantee to maintain.
 */
object WatchProtocol {

    /** Bumped when key names or the `EX_*` order change. The watch drops states it cannot read. */
    const val VERSION = 1

    // --- state: phone -> watch ---

    const val KEY_VERSION = "v"
    const val KEY_ACTIVE = "a"
    const val KEY_REST_REMAINING = "r"
    const val KEY_EXERCISES = "x"

    /** Index order within each entry of [KEY_EXERCISES]. Mirrored in `SessionModel.mc`. */
    const val EX_ID = 0
    const val EX_NAME = 1
    const val EX_SETS_DONE = 2
    const val EX_SETS_TARGET = 3
    const val EX_REPS_TARGET = 4
    const val EX_SUGGESTED_KG = 5
    const val EX_BODYWEIGHT = 6
    const val EX_FIELD_COUNT = 7

    // --- commands: watch -> phone ---

    const val KEY_COMMAND = "c"
    const val CMD_LOG_SET = "log"
    const val CMD_REST_START = "rest"
    const val CMD_REST_STOP = "rstop"
    const val CMD_SYNC = "sync"

    const val KEY_EXERCISE_ID = "id"
    const val KEY_REPS = "rp"
    const val KEY_KG = "kg"
    const val KEY_RPE = "rpe"
    const val KEY_SECONDS = "s"

    /**
     * Caps on the projection. A long session can carry more exercises than this; the watch
     * shows the first [MAX_EXERCISES] and the phone stays the place to see the whole session.
     * Truncating is deliberate — an oversized message is rejected wholesale by the transport,
     * which would look like the watch having silently died.
     */
    const val MAX_EXERCISES = 12
    const val MAX_NAME_LENGTH = 18
}

/** One row of the watch's session list. */
data class WatchExercise(
    val exerciseId: Int,
    val name: String,
    val setsDone: Int,
    /** 0 when the exercise came from a manual add rather than a plan, i.e. open-ended. */
    val setsTarget: Int,
    /** 0 when unknown. Plans store reps as a String range ("8-12"); this is the low end. */
    val repsTarget: Int,
    /** Best guess to pre-fill on the watch: last logged for this exercise, else the plan's. */
    val suggestedKg: Float,
    val isBodyweight: Boolean
)

/** Everything the watch knows about the session. Replaced wholesale on every publish. */
data class WatchState(
    val sessionActive: Boolean,
    val restRemainingSeconds: Int,
    val exercises: List<WatchExercise>
) {
    fun toWire(): Map<String, Any> = mapOf(
        WatchProtocol.KEY_VERSION to WatchProtocol.VERSION,
        WatchProtocol.KEY_ACTIVE to if (sessionActive) 1 else 0,
        WatchProtocol.KEY_REST_REMAINING to restRemainingSeconds,
        WatchProtocol.KEY_EXERCISES to exercises
            .take(WatchProtocol.MAX_EXERCISES)
            .map { ex ->
                listOf(
                    ex.exerciseId,
                    ex.name.take(WatchProtocol.MAX_NAME_LENGTH),
                    ex.setsDone,
                    ex.setsTarget,
                    ex.repsTarget,
                    ex.suggestedKg,
                    if (ex.isBodyweight) 1 else 0
                )
            }
    )

    companion object {
        val IDLE = WatchState(sessionActive = false, restRemainingSeconds = 0, exercises = emptyList())
    }
}

/** A request from the watch. Always explicit about its target — the watch owns its own cursor. */
sealed class WatchCommand {

    data class LogSet(
        val exerciseId: Int,
        val reps: Int,
        val kg: Float,
        val rpe: Float?
    ) : WatchCommand()

    data class StartRest(val seconds: Int) : WatchCommand()

    object StopRest : WatchCommand()

    /** Watch app just opened, or reconnected, and wants the current state. */
    object Sync : WatchCommand()

    companion object {

        /**
         * Parses a payload as delivered by the Connect IQ SDK.
         *
         * Deliberately lenient about numeric types: Monkey C `Number`/`Float` arrive as any of
         * Integer/Long/Float/Double depending on what the watch happened to put in the
         * dictionary, and a strict cast would reject valid messages. Returns null for anything
         * unrecognised rather than throwing — a malformed message from a watch must never take
         * down a workout in progress.
         */
        fun parse(payload: Any?): WatchCommand? {
            val map = payload as? Map<*, *> ?: return null
            return when (map[WatchProtocol.KEY_COMMAND] as? String) {
                WatchProtocol.CMD_LOG_SET -> {
                    val id = map.int(WatchProtocol.KEY_EXERCISE_ID) ?: return null
                    val reps = map.int(WatchProtocol.KEY_REPS) ?: return null
                    LogSet(
                        exerciseId = id,
                        reps = reps,
                        kg = map.float(WatchProtocol.KEY_KG) ?: 0f,
                        rpe = map.float(WatchProtocol.KEY_RPE)
                    )
                }
                WatchProtocol.CMD_REST_START ->
                    StartRest(map.int(WatchProtocol.KEY_SECONDS) ?: return null)
                WatchProtocol.CMD_REST_STOP -> StopRest
                WatchProtocol.CMD_SYNC -> Sync
                else -> null
            }
        }

        private fun Map<*, *>.int(key: String): Int? = (this[key] as? Number)?.toInt()
        private fun Map<*, *>.float(key: String): Float? = (this[key] as? Number)?.toFloat()
    }
}
