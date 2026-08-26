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
    /** Goal- and training-aware targets from TriPath's fuel model. Null before contract v2. */
    val targetKcal: Float? = null,
    val targetProteinG: Float? = null,
    /** (intake − exercise) / kg fat-free mass. A screening signal, never a diagnosis. */
    val energyAvailability: Float? = null,
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
    var workouts: List<TriPathWorkout> = emptyList(),
    /** TriPath's readiness verdict. Null until a sync against a contract-v2 TriPath has run. */
    var readiness: TriPathReadiness? = null
)

/**
 * TriPath's readiness verdict, as handed over rather than recomputed.
 *
 * TriPath sees every discipline, plus sleep, fuelling and body composition; LiftPath sees lifting.
 * So the verdict is TriPath's to make, and this is a carrier for it — nothing here interprets or
 * adjusts the numbers.
 *
 * [drivers] is what makes it usable: a score with no explanation is the thing this replaces.
 */
data class TriPathReadiness(
    val score: Int,
    /** FRESH / READY / COMPROMISED / DEPLETED. */
    val band: String,
    /** GO / MODERATE / EASY / REST. */
    val action: String,
    /** 0-100 per strain channel, where 100 is back at the athlete's habitual load. */
    val lowerImpactFreshness: Int? = null,
    val lowerMuscularFreshness: Int? = null,
    val upperMuscularFreshness: Int? = null,
    val systemicFreshness: Int? = null,
    /** Channel name to hours until it returns to baseline. */
    val hoursToFresh: Map<String, Int> = emptyMap(),
    val drivers: List<TriPathDriver> = emptyList(),
    val disciplineVerdicts: List<TriPathDisciplineVerdict> = emptyList(),
    /** Muscle-group name to freshness 0-100. */
    val muscleFreshness: Map<String, Int> = emptyMap(),
    val guidance: String? = null,
    /**
     * This week's load against last week's, as a percentage. Descriptive only — TriPath is explicit
     * that a recent-to-chronic load ratio is not a validated injury predictor, so it is shown and
     * never acted on.
     */
    val weeklyLoadRampPct: Float? = null,
    val computedAt: Long = 0L
)

/** One reason the score is what it is. Negative [impact] means it is pulling the score down. */
data class TriPathDriver(
    val label: String,
    val detail: String,
    val impact: Double
)

/** Whether one discipline is a good idea today, in TriPath's judgement. */
data class TriPathDisciplineVerdict(
    val discipline: String,
    val action: String,
    val reason: String
)
