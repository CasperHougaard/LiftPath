package com.liftpath.share

import android.net.Uri

/**
 * The read-only surface LiftPath exposes to TriPath: set-level lifting detail (RPE, tier, target
 * muscles) TriPath's own training-load model has no other way to see.
 *
 * Duplicated verbatim in TriPath (`com.tripath.data.local.liftpath.LiftPathShareContract`) — two
 * sideloaded apps, no shared module — so [CONTRACT_VERSION], [schemaHash] and [CAPABILITIES] are
 * what let either side notice the other is stale instead of silently reading absent columns.
 *
 * **Bump [CONTRACT_VERSION] in BOTH files whenever a column is added, removed or re-typed, and
 * keep every `SPEC` list identical between the two copies.** `LiftPathShareContractTest` asserts
 * the hash this side produces, so a one-sided edit fails the build rather than the integration.
 */
object LiftPathShareContract {

    const val AUTHORITY = "com.liftpath.share"

    /** LiftPath's own application id, for install-detection. */
    const val PACKAGE = "com.liftpath"

    /** Only this package may query the provider; see LiftPathShareProvider.assertCallerAllowed. */
    const val CONSUMER_PACKAGE = "com.tripath"

    const val CONTRACT_VERSION = 1

    /** Cheap liveness + version/schema probe. One row, always. */
    const val PATH_HANDSHAKE = "handshake"

    /** One row per lifting session in the requested range. */
    const val PATH_SESSIONS = "sessions"

    /** One row per logged set (warm-ups included, flagged) in the requested range. */
    const val PATH_SETS = "sets"

    /** One row per exercise in LiftPath's library — the whole catalog, no date range. */
    const val PATH_EXERCISES = "exercises"

    /** Inclusive ISO-8601 date bounds (`yyyy-MM-dd`) for [PATH_SESSIONS] and [PATH_SETS]. */
    const val QUERY_FROM = "from"
    const val QUERY_TO = "to"

    // Lazy on purpose. `Uri.parse` is an Android framework call, and the unmocked android.jar used
    // by JVM unit tests throws from it — eagerly initialising these would make the whole object
    // unloadable in a plain unit test, taking the schema-hash and capability checks down with it.
    // Nothing on device notices: the URIs are built on first use instead of at class load.
    val URI_HANDSHAKE: Uri by lazy { Uri.parse("content://$AUTHORITY/$PATH_HANDSHAKE") }
    val URI_SESSIONS: Uri by lazy { Uri.parse("content://$AUTHORITY/$PATH_SESSIONS") }
    val URI_SETS: Uri by lazy { Uri.parse("content://$AUTHORITY/$PATH_SETS") }
    val URI_EXERCISES: Uri by lazy { Uri.parse("content://$AUTHORITY/$PATH_EXERCISES") }

    /**
     * Capability tokens this build of the contract can serve. Consumers negotiate on these, so an
     * unknown or missing token hides a feature rather than crashing on it. `_V1` suffixes let a
     * future incompatible reshaping of the same feature ship as a new token alongside the old one.
     */
    const val CAP_LIFT_SETS_V1 = "LIFT_SETS_V1"
    const val CAP_LIFT_CATALOG_V1 = "LIFT_CATALOG_V1"
    val CAPABILITIES: List<String> = listOf(CAP_LIFT_SETS_V1, CAP_LIFT_CATALOG_V1)

    object Handshake {
        const val CONTRACT_VERSION = "contract_version"
        const val SCHEMA_HASH = "schema_hash"
        const val CAPABILITIES = "capabilities"
        const val APP_VERSION_NAME = "app_version_name"
        const val SESSION_COUNT = "session_count"
        const val LATEST_SESSION_DATE = "latest_session_date"

        val COLUMNS = arrayOf(
            CONTRACT_VERSION, SCHEMA_HASH, CAPABILITIES, APP_VERSION_NAME,
            SESSION_COUNT, LATEST_SESSION_DATE
        )
    }

    object Sessions {
        const val ID = "id"
        const val DATE = "date"
        const val START_MILLIS = "start_millis"
        const val DURATION_SECONDS = "duration_seconds"
        const val PLAN_NAME = "plan_name"
        const val DOMINANT_INTENT = "dominant_intent"
        const val TOTAL_SETS = "total_sets"

        val COLUMNS = arrayOf(
            ID, DATE, START_MILLIS, DURATION_SECONDS, PLAN_NAME, DOMINANT_INTENT, TOTAL_SETS
        )

        val SPEC = listOf(
            ColumnSpec(ID, "TEXT", nullable = false),
            ColumnSpec(DATE, "TEXT", nullable = false),
            ColumnSpec(START_MILLIS, "INTEGER", nullable = true),
            ColumnSpec(DURATION_SECONDS, "INTEGER", nullable = true),
            ColumnSpec(PLAN_NAME, "TEXT", nullable = true),
            ColumnSpec(DOMINANT_INTENT, "TEXT", nullable = true),
            ColumnSpec(TOTAL_SETS, "INTEGER", nullable = false)
        )
    }

    object Sets {
        const val SESSION_ID = "session_id"
        const val EXERCISE_ID = "exercise_id"
        const val SET_NUMBER = "set_number"
        const val KG = "kg"
        const val REPS = "reps"
        const val RPE = "rpe"
        const val IS_WARMUP = "is_warmup"
        const val INTENT = "intent"
        const val DURATION_SECONDS = "duration_seconds"
        const val BODYWEIGHT_KG = "bodyweight_kg"

        val COLUMNS = arrayOf(
            SESSION_ID, EXERCISE_ID, SET_NUMBER, KG, REPS, RPE, IS_WARMUP, INTENT,
            DURATION_SECONDS, BODYWEIGHT_KG
        )

        val SPEC = listOf(
            ColumnSpec(SESSION_ID, "TEXT", nullable = false),
            ColumnSpec(EXERCISE_ID, "INTEGER", nullable = false),
            ColumnSpec(SET_NUMBER, "INTEGER", nullable = false),
            ColumnSpec(KG, "REAL", nullable = false),
            ColumnSpec(REPS, "INTEGER", nullable = false),
            ColumnSpec(RPE, "REAL", nullable = true),
            ColumnSpec(IS_WARMUP, "INTEGER", nullable = false),
            ColumnSpec(INTENT, "TEXT", nullable = true),
            ColumnSpec(DURATION_SECONDS, "INTEGER", nullable = true),
            ColumnSpec(BODYWEIGHT_KG, "REAL", nullable = true)
        )
    }

    object Exercises {
        const val ID = "id"
        const val NAME = "name"
        const val REGION = "region"
        const val TIER = "tier"
        const val PATTERN = "pattern"
        const val MECHANICS = "mechanics"
        const val PRIMARY_TARGETS = "primary_targets"
        const val SECONDARY_TARGETS = "secondary_targets"

        val COLUMNS = arrayOf(
            ID, NAME, REGION, TIER, PATTERN, MECHANICS, PRIMARY_TARGETS, SECONDARY_TARGETS
        )

        val SPEC = listOf(
            ColumnSpec(ID, "INTEGER", nullable = false),
            ColumnSpec(NAME, "TEXT", nullable = false),
            ColumnSpec(REGION, "TEXT", nullable = true),
            ColumnSpec(TIER, "TEXT", nullable = true),
            ColumnSpec(PATTERN, "TEXT", nullable = true),
            ColumnSpec(MECHANICS, "TEXT", nullable = true),
            ColumnSpec(PRIMARY_TARGETS, "TEXT", nullable = false),
            ColumnSpec(SECONDARY_TARGETS, "TEXT", nullable = false)
        )
    }

    /** One column's canonical signature: name, SQL-ish type, nullability. */
    data class ColumnSpec(val name: String, val type: String, val nullable: Boolean)

    /**
     * Stable hash over every path's column signature, sorted, so a rename, a re-type or a
     * nullability change is caught even if [CONTRACT_VERSION] was never bumped. Deliberately
     * `String.hashCode()` rather than a cryptographic hash — not collision-proof, but specified
     * deterministic by the Kotlin/Java language spec, which is all two independently-built APKs
     * comparing a signature string need.
     */
    fun schemaHash(): String {
        val signature = listOf(
            PATH_SESSIONS to Sessions.SPEC,
            PATH_SETS to Sets.SPEC,
            PATH_EXERCISES to Exercises.SPEC
        ).joinToString("|") { (path, spec) ->
            val cols = spec.sortedBy { it.name }
                .joinToString(",") { "${it.name}:${it.type}:${if (it.nullable) "1" else "0"}" }
            "$path:$cols"
        }
        return signature.hashCode().toString(16)
    }
}
