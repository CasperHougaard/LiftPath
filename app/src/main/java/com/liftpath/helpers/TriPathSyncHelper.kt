package com.liftpath.helpers

import android.content.Context
import android.util.Log
import org.json.JSONObject
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
            val handshake = TriPathConnection.handshake(context)
                ?: return@withContext Result.failure(IllegalStateException("TriPath did not answer"))

            val to = LocalDate.now()
            val from = to.minusDays(daysBack.toLong())

            val days = queryDays(context, from, to)
            val workouts = queryWorkouts(context, from, to)
            // Negotiated on the capability token rather than the version number: an older TriPath
            // simply does not advertise readiness, and the page hides instead of erroring.
            val readiness = if (handshake.hasCapability(TriPathContract.CAP_READINESS_V1)) {
                queryReadiness(context)
            } else {
                null
            }

            val now = System.currentTimeMillis()
            TriPathStorageHelper(context).write(
                TriPathStorage(
                    lastSyncTime = now,
                    days = days,
                    workouts = workouts,
                    readiness = readiness
                )
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
                    targetKcal = cursor.optFloat(c.TARGET_KCAL),
                    targetProteinG = cursor.optFloat(c.TARGET_PROTEIN_G),
                    energyAvailability = cursor.optFloat(c.ENERGY_AVAILABILITY),
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

    /**
     * TriPath's readiness verdict. Returns null when the row is absent or unreadable, which reads
     * to every consumer as "fall back to the local model".
     */
    private fun queryReadiness(context: Context): TriPathReadiness? {
        val c = TriPathContract.Readiness
        return context.contentResolver
            .query(TriPathContract.URI_READINESS, null, null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                TriPathReadiness(
                    score = cursor.optInt(c.SCORE) ?: return@use null,
                    band = cursor.optString(c.BAND) ?: return@use null,
                    action = cursor.optString(c.ACTION) ?: return@use null,
                    lowerImpactFreshness = cursor.optInt(c.LOWER_IMPACT_FRESHNESS),
                    lowerMuscularFreshness = cursor.optInt(c.LOWER_MUSCULAR_FRESHNESS),
                    upperMuscularFreshness = cursor.optInt(c.UPPER_MUSCULAR_FRESHNESS),
                    systemicFreshness = cursor.optInt(c.SYSTEMIC_FRESHNESS),
                    hoursToFresh = parseIntMap(cursor.optString(c.HOURS_TO_FRESH_JSON)),
                    drivers = parseDrivers(cursor.optString(c.DRIVERS_JSON)),
                    disciplineVerdicts = parseVerdicts(cursor.optString(c.DISCIPLINE_VERDICTS_JSON)),
                    muscleFreshness = parseIntMap(cursor.optString(c.MUSCLE_FRESHNESS_JSON)),
                    guidance = cursor.optString(c.GUIDANCE),
                    weeklyLoadRampPct = cursor.optFloat(c.WEEKLY_LOAD_RAMP_PCT),
                    computedAt = cursor.optLong(c.COMPUTED_AT) ?: 0L
                )
            }
    }

    /**
     * Payloads carry their own version because a schema hash cannot see inside a JSON string.
     * An unrecognised one is dropped rather than half-parsed — a partly-populated readiness card is
     * worse than none.
     */
    private fun payloadItems(json: String?): JSONObject? {
        if (json.isNullOrBlank()) return null
        return try {
            val root = JSONObject(json)
            if (root.optInt("v", -1) != TriPathContract.JSON_PAYLOAD_VERSION) {
                Log.w(TAG, "Ignoring readiness payload with unsupported version ${root.opt("v")}")
                return null
            }
            root
        } catch (e: Exception) {
            Log.w(TAG, "Unparseable readiness payload", e)
            null
        }
    }

    private fun parseIntMap(json: String?): Map<String, Int> {
        val items = payloadItems(json)?.optJSONObject("items") ?: return emptyMap()
        val result = mutableMapOf<String, Int>()
        items.keys().forEach { key -> result[key] = items.optInt(key) }
        return result
    }

    private fun parseDrivers(json: String?): List<TriPathDriver> {
        val items = payloadItems(json)?.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val item = items.optJSONObject(i) ?: return@mapNotNull null
            TriPathDriver(
                label = item.optString("label"),
                detail = item.optString("detail"),
                impact = item.optDouble("impact", 0.0)
            )
        }
    }

    private fun parseVerdicts(json: String?): List<TriPathDisciplineVerdict> {
        val items = payloadItems(json)?.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val item = items.optJSONObject(i) ?: return@mapNotNull null
            TriPathDisciplineVerdict(
                discipline = item.optString("discipline"),
                action = item.optString("action"),
                reason = item.optString("reason")
            )
        }
    }
}
