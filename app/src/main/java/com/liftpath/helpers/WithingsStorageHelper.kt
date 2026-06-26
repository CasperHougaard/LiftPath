package com.liftpath.helpers

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.liftpath.models.WithingsStorage
import java.io.File

class WithingsStorageHelper(private val context: Context) {

    private val gson = Gson()
    private val file = File(context.filesDir, "withings_body_data.json")
    private val TAG = "WithingsStorageHelper"

    fun read(): WithingsStorage {
        if (!file.exists()) {
            val default = WithingsStorage()
            write(default)
            return default
        }
        return try {
            gson.fromJson(file.readText(), WithingsStorage::class.java)
                ?: WithingsStorage()
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
}
