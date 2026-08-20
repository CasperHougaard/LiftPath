package com.liftpath.helpers

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.File

/**
 * Local cache of what TriPath last handed over.
 *
 * Mirrors [HealthConnectStorageHelper]: the provider is queried on a sync, never on a screen
 * build, so readiness and Progress read this file instead of making a binder call on the main
 * thread — and keep working while TriPath is mid-update or its data is briefly unreadable.
 *
 * Registered in `BackupManager.BACKED_UP_FILES`.
 */
class TriPathStorageHelper(private val context: Context) {

    private val gson = Gson()
    private val file = File(context.filesDir, FILE_NAME)

    fun read(): TriPathStorage {
        if (!file.exists()) return TriPathStorage()

        return try {
            gson.fromJson(file.readText(), TriPathStorage::class.java) ?: TriPathStorage()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading or parsing $FILE_NAME. Starting from empty.", e)
            try {
                file.renameTo(File(context.filesDir, "$FILE_NAME.bak.${System.currentTimeMillis()}"))
            } catch (backupEx: Exception) {
                Log.e(TAG, "Could not back up corrupt file.", backupEx)
            }
            TriPathStorage()
        }
    }

    fun write(storage: TriPathStorage) {
        try {
            file.writeText(gson.toJson(storage))
            Log.d(TAG, "Stored ${storage.days.size} days and ${storage.workouts.size} workouts")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing $FILE_NAME", e)
        }
    }

    /** True when a sync has ever produced something worth showing. Gates the Fuel page. */
    fun hasData(): Boolean = read().days.isNotEmpty()

    companion object {
        const val FILE_NAME = "tripath_data.json"
        private const val TAG = "TriPathStorageHelper"
    }
}

/**
 * One calendar day as TriPath sees it: training load, recovery and energy joined by date.
 * Every metric is nullable — a day may have a run but no sleep record, or intake but no weight.
 */
data class TriPathDay(
    /** `yyyy-MM-dd`. */
    val date: String,
    val tss: Int = 0,
    /** Chronic Training Load — fitness. */
    val ctl: Float = 0f,
    /** Acute Training Load — fatigue. */
    val atl: Float = 0f,
    /** Training Stress Balance — form, `ctl - atl`. */
    val tsb: Float = 0f,
    val intakeKcal: Float? = null,
    /** TDEE: resting baseline + training burn. */
    val expenditureKcal: Float? = null,
    val balanceKcal: Float? = null,
    val weightKg: Float? = null,
    val sleepMinutes: Int? = null,
    val sleepScore: Int? = null,
    val hrvRmssd: Float? = null,
    /** 1–10, higher is worse. */
    val soreness: Int? = null,
    /** 1–10, higher is better. */
    val mood: Int? = null
)

/**
 * One session from TriPath. [connectId] is the Health Connect record id, which is also the id
 * LiftPath stores for its own Health Connect activities — that is what makes deduplication exact.
 */
data class TriPathWorkout(
    val connectId: String,
    val date: String,
    /** RUN, BIKE, SWIM, STRENGTH, WALK, HIKE, OTHER. */
    val type: String,
    val durationMinutes: Int,
    val avgHeartRate: Int? = null,
    val calories: Int? = null,
    val tss: Int? = null,
    val distanceMeters: Float? = null,
    /** JSON object of zone name → seconds, straight from TriPath. */
    val hrZoneJson: String? = null,
    val startMillis: Long? = null,
    val endMillis: Long? = null
)

data class TriPathStorage(
    var lastSyncTime: Long = 0L,
    var days: List<TriPathDay> = emptyList(),
    var workouts: List<TriPathWorkout> = emptyList()
)
