package com.liftpath.helpers

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

data class CatalogMergePrefs(
    val lastOfferedCatalogVersion: Int = 0,
    /** normalized exercise name -> KEEP_LOCAL or USE_CATALOG */
    val conflictDecisions: Map<String, String> = emptyMap()
)

class CatalogMergePrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val gson = Gson()

    fun getPrefs(): CatalogMergePrefs {
        val json = prefs.getString(KEY_PREFS, null)
        return if (json != null) {
            try {
                gson.fromJson(json, CatalogMergePrefs::class.java)
            } catch (_: Exception) {
                CatalogMergePrefs()
            }
        } else {
            CatalogMergePrefs()
        }
    }

    fun savePrefs(prefsData: CatalogMergePrefs) {
        // commit() so version/decisions survive immediate process death after the user taps OK
        prefs.edit().putString(KEY_PREFS, gson.toJson(prefsData)).commit()
    }

    fun resetForLibraryReset() {
        savePrefs(
            CatalogMergePrefs(
                lastOfferedCatalogVersion = DefaultExercisesHelper.CATALOG_VERSION,
                conflictDecisions = emptyMap()
            )
        )
    }

    companion object {
        private const val PREFS_NAME = "catalog_merge_prefs"
        private const val KEY_PREFS = "prefs"
    }
}
