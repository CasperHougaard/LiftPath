package com.liftpath.helpers

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.liftpath.models.WithingsScanEntry
import com.liftpath.models.WithingsStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar

object WithingsHealthConnectHelper {

    private const val TAG = "WithingsHCHelper"

    /** Withings Health Mate package name on Android */
    private const val WITHINGS_PACKAGE = "com.withings.wiscale2"

    val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(LeanBodyMassRecord::class),
        HealthPermission.getReadPermission(BoneMassRecord::class),
        HealthPermission.getReadPermission(BodyWaterMassRecord::class),
        HealthPermission.getReadPermission(BasalMetabolicRateRecord::class)
    )

    fun isAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun getClient(context: Context): HealthConnectClient? =
        if (isAvailable(context)) HealthConnectClient.getOrCreate(context) else null

    /**
     * Syncs Withings body-scan records from Health Connect into local storage.
     * Filters records whose data origin is Withings (com.withings.wiscale2).
     * Merges all record types into per-day [WithingsScanEntry] objects.
     * Returns the count of new entries added.
     */
    suspend fun autoSync(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable(context)) {
                return@withContext Result.failure(Exception("Health Connect not available"))
            }

            val client = getClient(context)
                ?: return@withContext Result.failure(Exception("Health Connect client unavailable"))

            // Check we have at least the weight permission — others may not be granted
            val granted = client.permissionController.getGrantedPermissions()
            if (HealthPermission.getReadPermission(WeightRecord::class) !in granted) {
                return@withContext Result.failure(Exception("Withings permissions not granted"))
            }

            // Look back 2 years to capture full history
            val endTime = Instant.now()
            val startTime = endTime.minus(730, ChronoUnit.DAYS)
            val filter = TimeRangeFilter.between(startTime, endTime)

            // Per-day bucket: dateKey -> mutable snapshot
            val buckets = mutableMapOf<String, MutableMap<String, Any>>()

            fun dayKey(instant: Instant): String {
                val local = instant.atZone(ZoneId.systemDefault()).toLocalDate()
                return "${local.year}-${local.monthValue.toString().padStart(2,'0')}-${local.dayOfMonth.toString().padStart(2,'0')}"
            }

            // --- Weight ---
            if (HealthPermission.getReadPermission(WeightRecord::class) in granted) {
                runCatching {
                    client.readRecords(ReadRecordsRequest(WeightRecord::class, filter)).records
                        .filter { it.metadata.dataOrigin.packageName == WITHINGS_PACKAGE }
                        .forEach { r ->
                            buckets.getOrPut(dayKey(r.time)) { mutableMapOf("dateMs" to r.time.toEpochMilli()) }["weightKg"] = r.weight.inKilograms
                        }
                }.onFailure { Log.w(TAG, "Could not read WeightRecord", it) }
            }

            // --- Body Fat ---
            if (HealthPermission.getReadPermission(BodyFatRecord::class) in granted) {
                runCatching {
                    client.readRecords(ReadRecordsRequest(BodyFatRecord::class, filter)).records
                        .filter { it.metadata.dataOrigin.packageName == WITHINGS_PACKAGE }
                        .forEach { r ->
                            val day = dayKey(r.time)
                            val bucket = buckets.getOrPut(day) { mutableMapOf("dateMs" to r.time.toEpochMilli()) }
                            bucket["bodyFatPct"] = r.percentage.value
                        }
                }.onFailure { Log.w(TAG, "Could not read BodyFatRecord", it) }
            }

            // --- Lean Body Mass ---
            if (HealthPermission.getReadPermission(LeanBodyMassRecord::class) in granted) {
                runCatching {
                    client.readRecords(ReadRecordsRequest(LeanBodyMassRecord::class, filter)).records
                        .filter { it.metadata.dataOrigin.packageName == WITHINGS_PACKAGE }
                        .forEach { r ->
                            val day = dayKey(r.time)
                            val bucket = buckets.getOrPut(day) { mutableMapOf("dateMs" to r.time.toEpochMilli()) }
                            bucket["leanBodyMassKg"] = r.mass.inKilograms
                        }
                }.onFailure { Log.w(TAG, "Could not read LeanBodyMassRecord", it) }
            }

            // --- Bone Mass ---
            if (HealthPermission.getReadPermission(BoneMassRecord::class) in granted) {
                runCatching {
                    client.readRecords(ReadRecordsRequest(BoneMassRecord::class, filter)).records
                        .filter { it.metadata.dataOrigin.packageName == WITHINGS_PACKAGE }
                        .forEach { r ->
                            val day = dayKey(r.time)
                            val bucket = buckets.getOrPut(day) { mutableMapOf("dateMs" to r.time.toEpochMilli()) }
                            bucket["boneMassKg"] = r.mass.inKilograms
                        }
                }.onFailure { Log.w(TAG, "Could not read BoneMassRecord", it) }
            }

            // --- Body Water Mass ---
            if (HealthPermission.getReadPermission(BodyWaterMassRecord::class) in granted) {
                runCatching {
                    client.readRecords(ReadRecordsRequest(BodyWaterMassRecord::class, filter)).records
                        .filter { it.metadata.dataOrigin.packageName == WITHINGS_PACKAGE }
                        .forEach { r ->
                            val day = dayKey(r.time)
                            val bucket = buckets.getOrPut(day) { mutableMapOf("dateMs" to r.time.toEpochMilli()) }
                            bucket["bodyWaterMassKg"] = r.mass.inKilograms
                        }
                }.onFailure { Log.w(TAG, "Could not read BodyWaterMassRecord", it) }
            }

            // --- Basal Metabolic Rate ---
            if (HealthPermission.getReadPermission(BasalMetabolicRateRecord::class) in granted) {
                runCatching {
                    client.readRecords(ReadRecordsRequest(BasalMetabolicRateRecord::class, filter)).records
                        .filter { it.metadata.dataOrigin.packageName == WITHINGS_PACKAGE }
                        .forEach { r ->
                            val day = dayKey(r.time)
                            val bucket = buckets.getOrPut(day) { mutableMapOf("dateMs" to r.time.toEpochMilli()) }
                            bucket["bmrKcal"] = r.basalMetabolicRate.inKilocaloriesPerDay
                        }
                }.onFailure { Log.w(TAG, "Could not read BasalMetabolicRateRecord", it) }
            }

            if (buckets.isEmpty()) {
                // Nothing found — make sure storage reflects clean sync
                val storageHelper = WithingsStorageHelper(context)
                val storage = storageHelper.read()
                storage.lastSyncTime = System.currentTimeMillis()
                storageHelper.write(storage)
                return@withContext Result.success(0)
            }

            // Convert buckets to WithingsScanEntry list sorted newest-first
            val newEntries = buckets.values.map { b ->
                WithingsScanEntry(
                    dateMs = b["dateMs"] as? Long ?: 0L,
                    weightKg = (b["weightKg"] as? Double),
                    bodyFatPct = (b["bodyFatPct"] as? Double),
                    leanBodyMassKg = (b["leanBodyMassKg"] as? Double),
                    boneMassKg = (b["boneMassKg"] as? Double),
                    bodyWaterMassKg = (b["bodyWaterMassKg"] as? Double),
                    bmrKcal = (b["bmrKcal"] as? Double)
                )
            }.sortedByDescending { it.dateMs }

            // Persist — replace all entries (full refresh from source-of-truth)
            val storageHelper = WithingsStorageHelper(context)
            val storage = WithingsStorage(
                lastSyncTime = System.currentTimeMillis(),
                entries = newEntries.toMutableList()
            )
            storageHelper.write(storage)

            Log.d(TAG, "Synced ${newEntries.size} Withings scan entries")
            Result.success(newEntries.size)

        } catch (e: Exception) {
            Log.e(TAG, "autoSync failed", e)
            Result.failure(e)
        }
    }

    /** True if Withings has ever synced data to local cache. */
    fun hasData(context: Context): Boolean = WithingsStorageHelper(context).hasData()
}
