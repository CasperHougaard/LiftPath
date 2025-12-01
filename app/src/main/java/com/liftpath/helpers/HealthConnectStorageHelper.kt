package com.liftpath.helpers

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.File

/**
 * Helper class for managing Health Connect activity storage in JSON format.
 */
class HealthConnectStorageHelper(private val context: Context) {

    private val gson = Gson()
    private val file = File(context.filesDir, "health_connect_activities.json")
    private val TAG = "HealthConnectStorageHelper"

    /**
     * Reads stored Health Connect activities from JSON file.
     * Returns default storage if file doesn't exist or is corrupted.
     */
    fun readStoredActivities(): HealthConnectStorage {
        if (!file.exists()) {
            Log.i(TAG, "No stored Health Connect activities found. Creating default storage.")
            val defaultStorage = HealthConnectStorage(
                retentionDays = 14,
                lastSyncTime = 0L,
                activities = mutableListOf()
            )
            writeStoredActivities(defaultStorage)
            return defaultStorage
        }

        return try {
            val json = file.readText()
            val storage = gson.fromJson(json, HealthConnectStorage::class.java)
                ?: HealthConnectStorage(
                    retentionDays = 14,
                    lastSyncTime = 0L,
                    activities = mutableListOf()
                )
            
            // Ensure retentionDays has a valid default if missing
            if (storage.retentionDays <= 0) {
                storage.retentionDays = 14
            }
            
            storage
        } catch (e: Exception) {
            Log.e(TAG, "Error reading or parsing health_connect_activities.json. Creating default storage.", e)
            
            // Backup corrupt file
            try {
                val backupFile = File(context.filesDir, "health_connect_activities.json.bak.${System.currentTimeMillis()}")
                file.renameTo(backupFile)
            } catch (backupEx: Exception) {
                Log.e(TAG, "Could not back up corrupt file.", backupEx)
            }
            
            // Return default storage
            HealthConnectStorage(
                retentionDays = 14,
                lastSyncTime = 0L,
                activities = mutableListOf()
            )
        }
    }

    /**
     * Writes Health Connect activities to JSON file.
     */
    fun writeStoredActivities(storage: HealthConnectStorage) {
        try {
            val json = gson.toJson(storage)
            file.writeText(json)
            Log.d(TAG, "Successfully wrote ${storage.activities.size} activities to storage")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to health_connect_activities.json", e)
        }
    }

    /**
     * Gets the storage file path (for debugging/info purposes).
     */
    fun getStorageFile(): File {
        return file
    }
}

/**
 * Data class for storing Health Connect activity with metadata.
 */
data class StoredHealthConnectActivity(
    val id: String,
    val startTime: Long,
    val endTime: Long,
    val type: Int,
    val fatigue: FatigueScores,
    val syncedAt: Long,
    val ignored: Boolean = false,
    val ignoreReason: String? = null
) {
    /**
     * Converts to ExternalActivity for use in fatigue calculations.
     */
    fun toExternalActivity(): ExternalActivity {
        return ExternalActivity(
            id = id,
            startTime = startTime,
            endTime = endTime,
            type = type,
            fatigue = fatigue
        )
    }
}

/**
 * Data class for Health Connect storage structure.
 */
data class HealthConnectStorage(
    var retentionDays: Int = 14,
    var lastSyncTime: Long = 0L,
    val activities: MutableList<StoredHealthConnectActivity> = mutableListOf()
)

