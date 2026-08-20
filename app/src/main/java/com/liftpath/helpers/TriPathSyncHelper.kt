package com.liftpath.helpers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Pulls TriPath's day and workout rows into local storage.
 *
 * Mirrors [HealthConnectHelper.autoSyncActivities]: same `Result<Int>` shape, same "fail quietly
 * when the source is unavailable" contract, called from the same on-resume hooks. Nothing here
 * interprets the data — [TriPathFatigueMapper] does that at read time, so a change to the fatigue
 * model does not require a re-sync.
 */
object TriPathSyncHelper {

    private const val TAG = "TriPathSyncHelper"

    /**
     * How far back to pull. LiftPath's fatigue timeline simulates 28 days, and the Fuel page
     * summarises the same window, so anything older would be fetched and never read.
     */
    const val DEFAULT_DAYS_BACK = 28

    /**
     * Queries TriPath and replaces the local cache. Returns the number of days stored.
     *
     * Failure is normal and unremarkable: TriPath not installed, the toggle off, or the provider
     * refusing us. Callers log the result at most.
     */
    suspend fun autoSync(
        context: Context,
        daysBack: Int = DEFAULT_DAYS_BACK
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!TriPathConnection.isEnabled(context)) {
                return@withContext Result.failure(IllegalStateException("TriPath integration disabled"))
            }
            // Doubles as the liveness check that gates every consumer: no handshake, no data.
            TriPathConnection.handshake(context)
                ?: return@withContext Result.failure(IllegalStateException("TriPath did not answer"))

            val to = LocalDate.now()
            val from = to.minusDays(daysBack.toLong())

            val days = queryDays(context, from, to)
            val workouts = queryWorkouts(context, from, to)

            val now = System.currentTimeMillis()
            TriPathStorageHelper(context).write(
                TriPathStorage(lastSyncTime = now, days = days, workouts = workouts)
            )
            TriPathConnection.markSynced(context, now)

            Log.d(TAG, "Synced ${days.size} days and ${workouts.size} workouts from TriPath")
            Result.success(days.size)
        } catch (e: Exception) {
            Log.w(TAG, "TriPath sync failed", e)
            Result.failure(e)
        }
    }

    private fun queryDays(context: Context, from: LocalDate, to: LocalDate): List<TriPathDay> {
        val uri = TriPathContract.URI_DAYS.buildUpon()
            .appendQueryParameter(TriPathContract.QUERY_FROM, from.toString())
            .appendQueryParameter(TriPathContract.QUERY_TO, to.toString())
            .build()

        val result = mutableListOf<TriPathDay>()
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val c = TriPathContract.Days
            while (cursor.moveToNext()) {
                val date = cursor.optString(c.DATE) ?: continue
                result += TriPathDay(
                    date = date,
                    tss = cursor.optInt(c.TSS) ?: 0,
                    ctl = cursor.optFloat(c.CTL) ?: 0f,
                    atl = cursor.optFloat(c.ATL) ?: 0f,
                    tsb = cursor.optFloat(c.TSB) ?: 0f,
                    intakeKcal = cursor.optFloat(c.INTAKE_KCAL),
                    expenditureKcal = cursor.optFloat(c.EXPENDITURE_KCAL),
                    balanceKcal = cursor.optFloat(c.BALANCE_KCAL),
                    weightKg = cursor.optFloat(c.WEIGHT_KG),
                    sleepMinutes = cursor.optInt(c.SLEEP_MINUTES),
                    sleepScore = cursor.optInt(c.SLEEP_SCORE),
                    hrvRmssd = cursor.optFloat(c.HRV_RMSSD),
                    soreness = cursor.optInt(c.SORENESS),
                    mood = cursor.optInt(c.MOOD)
                )
            }
        }
        return result
    }

    private fun queryWorkouts(context: Context, from: LocalDate, to: LocalDate): List<TriPathWorkout> {
        val uri = TriPathContract.URI_WORKOUTS.buildUpon()
            .appendQueryParameter(TriPathContract.QUERY_FROM, from.toString())
            .appendQueryParameter(TriPathContract.QUERY_TO, to.toString())
            .build()

        val result = mutableListOf<TriPathWorkout>()
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val c = TriPathContract.Workouts
            while (cursor.moveToNext()) {
                val connectId = cursor.optString(c.CONNECT_ID) ?: continue
                val date = cursor.optString(c.DATE) ?: continue
                result += TriPathWorkout(
                    connectId = connectId,
                    date = date,
                    type = cursor.optString(c.TYPE) ?: "OTHER",
                    durationMinutes = cursor.optInt(c.DURATION_MINUTES) ?: 0,
                    avgHeartRate = cursor.optInt(c.AVG_HR),
                    calories = cursor.optInt(c.CALORIES),
                    tss = cursor.optInt(c.TSS),
                    distanceMeters = cursor.optFloat(c.DISTANCE_M),
                    hrZoneJson = cursor.optString(c.HR_ZONE_JSON),
                    startMillis = cursor.optLong(c.START_MILLIS),
                    endMillis = cursor.optLong(c.END_MILLIS)
                )
            }
        }
        return result
    }
}
