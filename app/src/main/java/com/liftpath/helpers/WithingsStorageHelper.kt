package com.liftpath.helpers

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.liftpath.models.WithingsStorage
import java.io.File
import java.time.Instant
import java.time.ZoneId

class WithingsStorageHelper(private val context: Context) {

    private val gson = Gson()
    private val file = File(context.filesDir, "withings_body_data.json")
    private val TAG = "WithingsStorageHelper"

    @Suppress("USELESS_ELVIS")
    fun read(): WithingsStorage {
        if (!file.exists()) {
            val default = WithingsStorage()
            write(default)
            return default
        }
        return try {
            val parsed = gson.fromJson(file.readText(), WithingsStorage::class.java)
                ?: WithingsStorage()
            // Gson bypasses Kotlin constructor defaults, so collections absent from an
            // older file (e.g. ignoredDayKeys, added later) deserialize to null. Backfill
            // them so every caller can rely on these being non-null.
            WithingsStorage(
                lastSyncTime = parsed.lastSyncTime,
                entries = parsed.entries ?: mutableListOf(),
                ignoredDayKeys = parsed.ignoredDayKeys ?: mutableSetOf()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error reading withings_body_data.json", e)
            try {
                file.renameTo(File(context.filesDir, "withings_body_data.json.bak.${System.currentTimeMillis()}"))
            } catch (_: Exception) {}
            WithingsStorage()
        }
    }

    fun write(storage: WithingsStorage) {
        try {
            file.writeText(gson.toJson(storage))
        } catch (e: Exception) {
            Log.e(TAG, "Error writing withings_body_data.json", e)
        }
    }

    /** True if any scan entries have been synced. Used to control tab visibility. */
    fun hasData(): Boolean = file.exists() && read().entries.isNotEmpty()

    fun getStorageFile(): File = file

    /**
     * Permanently ignore the scan that falls on [dateMs]'s day: remove it from the cache now and
     * record its day-key so future syncs don't re-add it.
     */
    fun ignoreScan(dateMs: Long) {
        val storage = read()
        val key = dayKey(dateMs)
        storage.ignoredDayKeys.add(key)
        storage.entries.removeAll { dayKey(it.dateMs) == key }
        write(storage)
    }

    companion object {
        /** Stable per-day key (local time) used to match a scan across syncs. */
        fun dayKey(dateMs: Long): String {
            val local = Instant.ofEpochMilli(dateMs).atZone(ZoneId.systemDefault()).toLocalDate()
            return "%04d-%02d-%02d".format(local.year, local.monthValue, local.dayOfMonth)
        }
    }
}
