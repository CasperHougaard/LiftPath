package com.liftpath.helpers

import android.net.Uri

/**
 * The read-only surface TriPath exposes to LiftPath.
 *
 * Mirror of `com.tripath.data.local.share.TriPathShareContract` — two sideloaded apps, no shared
 * module, so the file is duplicated rather than depended on. [CONTRACT_VERSION] is what lets
 * either side notice the other is stale instead of silently reading absent columns.
 *
 * **Bump [CONTRACT_VERSION] in BOTH files whenever a column is added, removed or re-typed.**
 */
object TriPathContract {

    const val AUTHORITY = "com.tripath.share"

    /** TriPath's application id. LiftPath must declare this in `<queries>` to see the provider. */
    const val PACKAGE = "com.tripath"

    const val CONTRACT_VERSION = 1

    const val PATH_HANDSHAKE = "handshake"
    const val PATH_DAYS = "days"
    const val PATH_WORKOUTS = "workouts"

    /** Inclusive ISO-8601 date bounds (`yyyy-MM-dd`). */
    const val QUERY_FROM = "from"
    const val QUERY_TO = "to"

    val URI_HANDSHAKE: Uri = Uri.parse("content://$AUTHORITY/$PATH_HANDSHAKE")
    val URI_DAYS: Uri = Uri.parse("content://$AUTHORITY/$PATH_DAYS")
    val URI_WORKOUTS: Uri = Uri.parse("content://$AUTHORITY/$PATH_WORKOUTS")

    object Handshake {
        const val CONTRACT_VERSION = "contract_version"
        const val APP_VERSION_NAME = "app_version_name"
        const val WORKOUT_COUNT = "workout_count"
        const val LATEST_WORKOUT_DATE = "latest_workout_date"
        const val LATEST_WELLNESS_DATE = "latest_wellness_date"
    }

    object Days {
        const val DATE = "date"
        const val TSS = "tss"
        const val CTL = "ctl"
        const val ATL = "atl"
        const val TSB = "tsb"
        const val INTAKE_KCAL = "intake_kcal"
        const val EXPENDITURE_KCAL = "expenditure_kcal"
        const val BALANCE_KCAL = "balance_kcal"
        const val WEIGHT_KG = "weight_kg"
        const val SLEEP_MINUTES = "sleep_minutes"
        const val SLEEP_SCORE = "sleep_score"
        const val HRV_RMSSD = "hrv_rmssd"
        const val SORENESS = "soreness"
        const val MOOD = "mood"
    }

    object Workouts {
        /**
         * Health Connect `ExerciseSessionRecord.metadata.id` — the same string LiftPath stores as
         * [ExternalActivity.id], so the two sources deduplicate on an exact match.
         */
        const val CONNECT_ID = "connect_id"
        const val DATE = "date"

        /** RUN, BIKE, SWIM, STRENGTH, WALK, HIKE, OTHER. */
        const val TYPE = "type"

        const val DURATION_MINUTES = "duration_minutes"
        const val AVG_HR = "avg_hr"
        const val CALORIES = "calories"
        const val TSS = "tss"
        const val DISTANCE_M = "distance_m"
        const val HR_ZONE_JSON = "hr_zone_json"
        const val START_MILLIS = "start_millis"
        const val END_MILLIS = "end_millis"
    }
}
