package com.liftpath.helpers

import android.net.Uri

/**
 * The read-only surface TriPath exposes to LiftPath.
 *
 * TriPath owns the whole picture — every discipline's load, fuelling, sleep, recovery — so LiftPath
 * no longer computes its own view of any of it. It reads the verdict from here and renders it,
 * keeping its own model only as an offline fallback for when TriPath is absent.
 *
 * This file is duplicated verbatim in TriPath (`com.tripath.data.local.share.TriPathShareContract`) — two
 * sideloaded apps, no shared module — so [CONTRACT_VERSION], [schemaHash] and [CAPABILITIES] are
 * what let either side notice the other is stale instead of silently reading absent columns.
 *
 * **Bump [CONTRACT_VERSION] in BOTH files whenever a column is added, removed or re-typed, and keep
 * every `SPEC` list identical between the two copies.** `TriPathContractTest` pins the schema
 * hash on both sides, so a one-sided edit fails a build rather than the integration.
 */
object TriPathContract {

    const val AUTHORITY = "com.tripath.share"

    /** TriPath's application id. LiftPath must declare this in `<queries>` to see the provider. */
    const val PACKAGE = "com.tripath"

    /** Only this package may query the provider; see TriPathShareProvider.assertCallerAllowed. */
    const val CONSUMER_PACKAGE = "com.liftpath"

    /**
     * 1: handshake, days, workouts.
     * 2: adds the readiness path, fuelling columns on days, and schema-hash/capability negotiation.
     */
    const val CONTRACT_VERSION = 2

    /** Cheap liveness + version/schema probe. One row, always. */
    const val PATH_HANDSHAKE = "handshake"

    /** One row per calendar day in the requested range, joined across every data source. */
    const val PATH_DAYS = "days"

    /** One row per non-ignored workout session in the requested range. */
    const val PATH_WORKOUTS = "workouts"

    /** A single row: the current readiness verdict, its drivers and its per-channel detail. */
    const val PATH_READINESS = "readiness"

    /** Inclusive ISO-8601 date bounds (`yyyy-MM-dd`) for [PATH_DAYS] and [PATH_WORKOUTS]. */
    const val QUERY_FROM = "from"
    const val QUERY_TO = "to"

    // Lazy on purpose: `Uri.parse` throws against the unmocked android.jar used by JVM unit tests,
    // and eager initialisation would make this whole object unloadable in one. Nothing on device
    // notices — the URIs are built on first use rather than at class load.
    val URI_HANDSHAKE: Uri by lazy { Uri.parse("content://$AUTHORITY/$PATH_HANDSHAKE") }
    val URI_DAYS: Uri by lazy { Uri.parse("content://$AUTHORITY/$PATH_DAYS") }
    val URI_WORKOUTS: Uri by lazy { Uri.parse("content://$AUTHORITY/$PATH_WORKOUTS") }
    val URI_READINESS: Uri by lazy { Uri.parse("content://$AUTHORITY/$PATH_READINESS") }

    /**
     * What this build can serve. Consumers negotiate on these rather than on the version number, so
     * a token they do not recognise hides a feature instead of crashing on a missing column.
     *
     * The `_JSON_V1` tokens exist because a JSON payload can change shape without its column name
     * moving — the schema hash cannot see inside `drivers_json`, so the token is what guards it.
     */
    const val CAP_READINESS_V1 = "READINESS_V1"
    const val CAP_MUSCLE_FRESHNESS_V1 = "MUSCLE_FRESHNESS_V1"
    const val CAP_NUTRITION_TARGETS_V1 = "NUTRITION_TARGETS_V1"
    const val CAP_DRIVERS_JSON_V1 = "DRIVERS_JSON_V1"
    const val CAP_DISCIPLINE_VERDICTS_JSON_V1 = "DISCIPLINE_VERDICTS_JSON_V1"

    val CAPABILITIES: List<String> = listOf(
        CAP_READINESS_V1,
        CAP_MUSCLE_FRESHNESS_V1,
        CAP_NUTRITION_TARGETS_V1,
        CAP_DRIVERS_JSON_V1,
        CAP_DISCIPLINE_VERDICTS_JSON_V1
    )

    /** Version stamped inside every JSON payload, checked by the parser on the other side. */
    const val JSON_PAYLOAD_VERSION = 1

    object Handshake {
        const val CONTRACT_VERSION = "contract_version"
        const val SCHEMA_HASH = "schema_hash"
        const val CAPABILITIES = "capabilities"
        const val APP_VERSION_NAME = "app_version_name"
        const val WORKOUT_COUNT = "workout_count"
        const val LATEST_WORKOUT_DATE = "latest_workout_date"
        const val LATEST_WELLNESS_DATE = "latest_wellness_date"

        val COLUMNS = arrayOf(
            CONTRACT_VERSION,
            SCHEMA_HASH,
            CAPABILITIES,
            APP_VERSION_NAME,
            WORKOUT_COUNT,
            LATEST_WORKOUT_DATE,
            LATEST_WELLNESS_DATE
        )
    }

    object Days {
        const val DATE = "date"

        /** Summed Training Stress Score for the day. */
        const val TSS = "tss"

        /** Banister Chronic Training Load (fitness), 42-day EWMA. */
        const val CTL = "ctl"

        /** Banister Acute Training Load (fatigue), 7-day EWMA. */
        const val ATL = "atl"

        /** Training Stress Balance (form) = ctl − atl. */
        const val TSB = "tsb"

        const val INTAKE_KCAL = "intake_kcal"

        /** Total daily energy expenditure. Null when demographics/weight are incomplete. */
        const val EXPENDITURE_KCAL = "expenditure_kcal"

        /** intake − expenditure. Null when either side is missing. */
        const val BALANCE_KCAL = "balance_kcal"

        /** Forward-filled last known body weight on or before the day. */
        const val WEIGHT_KG = "weight_kg"

        const val SLEEP_MINUTES = "sleep_minutes"
        const val SLEEP_SCORE = "sleep_score"

        /** Morning HRV (RMSSD, ms) from the day's wellness log or the watch. */
        const val HRV_RMSSD = "hrv_rmssd"

        /** Subjective soreness, 1–10. Higher is worse. */
        const val SORENESS = "soreness"

        /** Subjective mood, 1–10. Higher is better. */
        const val MOOD = "mood"

        // ---- Added in contract version 2 ----

        /** Goal- and training-aware energy target for the day. */
        const val TARGET_KCAL = "target_kcal"
        const val TARGET_PROTEIN_G = "target_protein_g"
        const val TARGET_CARBS_G = "target_carbs_g"
        const val TARGET_FAT_G = "target_fat_g"

        /** Which equation produced the resting rate behind the day's expenditure. */
        const val TDEE_SOURCE = "tdee_source"

        /** (intake − exercise) / kg fat-free mass. A screening signal, never a diagnosis. */
        const val ENERGY_AVAILABILITY = "energy_availability"

        /** Non-exercise steps for the day, which set the day's NEAT multiplier. */
        const val STEPS = "steps"

        val COLUMNS = arrayOf(
            DATE, TSS, CTL, ATL, TSB,
            INTAKE_KCAL, EXPENDITURE_KCAL, BALANCE_KCAL, WEIGHT_KG,
            SLEEP_MINUTES, SLEEP_SCORE, HRV_RMSSD, SORENESS, MOOD,
            TARGET_KCAL, TARGET_PROTEIN_G, TARGET_CARBS_G, TARGET_FAT_G,
            TDEE_SOURCE, ENERGY_AVAILABILITY, STEPS
        )

        val SPEC = listOf(
            ColumnSpec(DATE, "TEXT", nullable = false),
            ColumnSpec(TSS, "INTEGER", nullable = false),
            ColumnSpec(CTL, "REAL", nullable = false),
            ColumnSpec(ATL, "REAL", nullable = false),
            ColumnSpec(TSB, "REAL", nullable = false),
            ColumnSpec(INTAKE_KCAL, "REAL", nullable = true),
            ColumnSpec(EXPENDITURE_KCAL, "REAL", nullable = true),
            ColumnSpec(BALANCE_KCAL, "REAL", nullable = true),
            ColumnSpec(WEIGHT_KG, "REAL", nullable = true),
            ColumnSpec(SLEEP_MINUTES, "INTEGER", nullable = true),
            ColumnSpec(SLEEP_SCORE, "INTEGER", nullable = true),
            ColumnSpec(HRV_RMSSD, "REAL", nullable = true),
            ColumnSpec(SORENESS, "INTEGER", nullable = true),
            ColumnSpec(MOOD, "INTEGER", nullable = true),
            ColumnSpec(TARGET_KCAL, "REAL", nullable = true),
            ColumnSpec(TARGET_PROTEIN_G, "REAL", nullable = true),
            ColumnSpec(TARGET_CARBS_G, "REAL", nullable = true),
            ColumnSpec(TARGET_FAT_G, "REAL", nullable = true),
            ColumnSpec(TDEE_SOURCE, "TEXT", nullable = true),
            ColumnSpec(ENERGY_AVAILABILITY, "REAL", nullable = true),
            ColumnSpec(STEPS, "INTEGER", nullable = true)
        )
    }

    object Workouts {
        /**
         * Health Connect `ExerciseSessionRecord.metadata.id`. LiftPath stores the same string as
         * `ExternalActivity.id`, so the two sources deduplicate on an exact match.
         */
        const val CONNECT_ID = "connect_id"

        const val DATE = "date"

        /** `WorkoutType` name: RUN, BIKE, SWIM, STRENGTH, WALK, HIKE, OTHER. */
        const val TYPE = "type"

        const val DURATION_MINUTES = "duration_minutes"
        const val AVG_HR = "avg_hr"
        const val CALORIES = "calories"
        const val TSS = "tss"
        const val DISTANCE_M = "distance_m"

        /** JSON object of zone name → seconds, e.g. `{"Z1":300,"Z4":600}`. */
        const val HR_ZONE_JSON = "hr_zone_json"

        /** Real session bounds from the raw Health Connect record; null if it was pruned. */
        const val START_MILLIS = "start_millis"
        const val END_MILLIS = "end_millis"

        val COLUMNS = arrayOf(
            CONNECT_ID, DATE, TYPE, DURATION_MINUTES, AVG_HR, CALORIES, TSS,
            DISTANCE_M, HR_ZONE_JSON, START_MILLIS, END_MILLIS
        )

        val SPEC = listOf(
            ColumnSpec(CONNECT_ID, "TEXT", nullable = false),
            ColumnSpec(DATE, "TEXT", nullable = false),
            ColumnSpec(TYPE, "TEXT", nullable = false),
            ColumnSpec(DURATION_MINUTES, "INTEGER", nullable = false),
            ColumnSpec(AVG_HR, "INTEGER", nullable = true),
            ColumnSpec(CALORIES, "INTEGER", nullable = true),
            ColumnSpec(TSS, "INTEGER", nullable = true),
            ColumnSpec(DISTANCE_M, "REAL", nullable = true),
            ColumnSpec(HR_ZONE_JSON, "TEXT", nullable = true),
            ColumnSpec(START_MILLIS, "INTEGER", nullable = true),
            ColumnSpec(END_MILLIS, "INTEGER", nullable = true)
        )
    }

    /**
     * The readiness verdict. One row.
     *
     * Freshness columns are 0–100 per strain channel, where 100 means the tissue is back at the
     * athlete's habitual load. They are separate columns rather than a JSON blob because LiftPath
     * renders them as four bars and a schema hash can see a column but not a blob's contents.
     */
    object Readiness {
        const val SCORE = "score"
        const val BAND = "band"
        const val ACTION = "action"

        const val LOWER_IMPACT_FRESHNESS = "lower_impact_freshness"
        const val LOWER_MUSCULAR_FRESHNESS = "lower_muscular_freshness"
        const val UPPER_MUSCULAR_FRESHNESS = "upper_muscular_freshness"
        const val SYSTEMIC_FRESHNESS = "systemic_freshness"

        /** JSON object of channel name → hours until it returns to baseline. */
        const val HOURS_TO_FRESH_JSON = "hours_to_fresh_json"

        /** JSON array of `{label, detail, impact}`, worst first. The "why" behind the score. */
        const val DRIVERS_JSON = "drivers_json"

        /** JSON array of `{discipline, action, reason}`. */
        const val DISCIPLINE_VERDICTS_JSON = "discipline_verdicts_json"

        /** JSON object of muscle-group name → freshness 0–100. */
        const val MUSCLE_FRESHNESS_JSON = "muscle_freshness_json"

        /** One-line summary safe to show verbatim. */
        const val GUIDANCE = "guidance"

        /**
         * This week's load against last week's, as a percentage. **Descriptive only** — a ratio of
         * recent to chronic load is widely quoted as an injury predictor and that claim has not held
         * up, so it must not gate anything on either side.
         */
        const val WEEKLY_LOAD_RAMP_PCT = "weekly_load_ramp_pct"

        const val COMPUTED_AT = "computed_at"

        val COLUMNS = arrayOf(
            SCORE, BAND, ACTION,
            LOWER_IMPACT_FRESHNESS, LOWER_MUSCULAR_FRESHNESS,
            UPPER_MUSCULAR_FRESHNESS, SYSTEMIC_FRESHNESS,
            HOURS_TO_FRESH_JSON, DRIVERS_JSON, DISCIPLINE_VERDICTS_JSON,
            MUSCLE_FRESHNESS_JSON, GUIDANCE, WEEKLY_LOAD_RAMP_PCT, COMPUTED_AT
        )

        val SPEC = listOf(
            ColumnSpec(SCORE, "INTEGER", nullable = false),
            ColumnSpec(BAND, "TEXT", nullable = false),
            ColumnSpec(ACTION, "TEXT", nullable = false),
            ColumnSpec(LOWER_IMPACT_FRESHNESS, "INTEGER", nullable = true),
            ColumnSpec(LOWER_MUSCULAR_FRESHNESS, "INTEGER", nullable = true),
            ColumnSpec(UPPER_MUSCULAR_FRESHNESS, "INTEGER", nullable = true),
            ColumnSpec(SYSTEMIC_FRESHNESS, "INTEGER", nullable = true),
            ColumnSpec(HOURS_TO_FRESH_JSON, "TEXT", nullable = true),
            ColumnSpec(DRIVERS_JSON, "TEXT", nullable = true),
            ColumnSpec(DISCIPLINE_VERDICTS_JSON, "TEXT", nullable = true),
            ColumnSpec(MUSCLE_FRESHNESS_JSON, "TEXT", nullable = true),
            ColumnSpec(GUIDANCE, "TEXT", nullable = true),
            ColumnSpec(WEEKLY_LOAD_RAMP_PCT, "REAL", nullable = true),
            ColumnSpec(COMPUTED_AT, "INTEGER", nullable = false)
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
            PATH_DAYS to Days.SPEC,
            PATH_WORKOUTS to Workouts.SPEC,
            PATH_READINESS to Readiness.SPEC
        ).joinToString("|") { (path, spec) ->
            val cols = spec.sortedBy { it.name }
                .joinToString(",") { "${it.name}:${it.type}:${if (it.nullable) "1" else "0"}" }
            "$path:$cols"
        }
        return signature.hashCode().toString(16)
    }
}
