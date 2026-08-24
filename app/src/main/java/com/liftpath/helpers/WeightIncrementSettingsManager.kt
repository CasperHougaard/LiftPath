package com.liftpath.helpers

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

/**
 * Persists the user's per-equipment weight ladders.
 *
 * Kept in its own prefs file rather than as a field on `ProgressionHelper.ProgressionSettings`
 * for two reasons. `ProgressionSettingsActivity` rebuilds that object from only the fields its
 * UI binds, so anything stored there is reset by an unrelated save. And a `Map` field on a
 * Gson-deserialized data class arrives `null` from any older stored JSON regardless of its
 * Kotlin default — see the note on [EquipmentIncrementTable].
 *
 * Registered in `BackupManager.BACKED_UP_PREFS`: these are user choices about their gym, not
 * device wiring, so they should survive a phone swap.
 */
class WeightIncrementSettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val gson = Gson()

    /** Never throws: a corrupt file degrades to the built-in defaults rather than losing a session. */
    fun getTable(): EquipmentIncrementTable {
        val json = prefs.getString(KEY_TABLE, null) ?: return EquipmentIncrementTable()
        return try {
            gson.fromJson(json, EquipmentIncrementTable::class.java) ?: EquipmentIncrementTable()
        } catch (_: Exception) {
            EquipmentIncrementTable()
        }
    }

    fun saveTable(table: EquipmentIncrementTable) {
        prefs.edit().putString(KEY_TABLE, gson.toJson(table)).apply()
    }

    /** Drops all overrides; [WeightIncrementHelper.BUILT_IN] applies again. */
    fun resetToDefaults() {
        prefs.edit().remove(KEY_TABLE).apply()
    }

    companion object {
        const val PREFS_NAME = "equipment_increments"
        private const val KEY_TABLE = "table"
    }
}
