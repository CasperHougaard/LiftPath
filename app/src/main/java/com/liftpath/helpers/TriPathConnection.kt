package com.liftpath.helpers

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Whether TriPath is reachable, and the switch that lets its data influence LiftPath.
 *
 * The integration is off unless three things are true: TriPath is installed, its provider answered
 * a handshake, and the user enabled it in Settings. When any of them fails LiftPath falls back to
 * exactly the behaviour it had before this feature existed — no TriPath card, no Fuel page, and a
 * fatigue curve built from Health Connect alone. [isActive] is the single gate every caller uses.
 */
object TriPathConnection {

    private const val TAG = "TriPathConnection"

    const val PREFS_NAME = "tripath_settings"

    private const val KEY_ENABLED = "enabled"
    private const val KEY_HANDSHAKE_OK = "handshake_ok"
    private const val KEY_HANDSHAKE_TIME = "handshake_time"
    private const val KEY_CONTRACT_VERSION = "contract_version_seen"
    private const val KEY_APP_VERSION = "app_version_seen"
    private const val KEY_LAST_SYNC_TIME = "last_sync_time"

    /** What the provider reported about itself. */
    data class Handshake(
        val contractVersion: Int,
        val appVersionName: String?,
        val workoutCount: Int,
        val latestWorkoutDate: String?,
        val latestWellnessDate: String?
    ) {
        /**
         * True when both apps speak the same contract. A newer TriPath is tolerable (columns are
         * read by name, so additions are ignored); an older one may be missing columns entirely,
         * which is why Settings surfaces the mismatch rather than failing silently.
         */
        val versionMatches: Boolean get() = contractVersion == TriPathContract.CONTRACT_VERSION
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Whether the TriPath app is present. Requires the `<queries>` entry in the manifest. */
    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(TriPathContract.PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * The gate. Cheap and synchronous — no binder call — so it is safe to ask on every screen
     * build. Reflects the last known handshake result, refreshed by [handshake] on each sync.
     */
    fun isActive(context: Context): Boolean =
        isEnabled(context) && isInstalled(context) && prefs(context).getBoolean(KEY_HANDSHAKE_OK, false)

    fun lastSyncTime(context: Context): Long = prefs(context).getLong(KEY_LAST_SYNC_TIME, 0L)

    fun markSynced(context: Context, at: Long) {
        prefs(context).edit().putLong(KEY_LAST_SYNC_TIME, at).apply()
    }

    /** The contract version reported by the last successful handshake, or 0 if never reached. */
    fun lastContractVersion(context: Context): Int = prefs(context).getInt(KEY_CONTRACT_VERSION, 0)

    fun lastAppVersion(context: Context): String? = prefs(context).getString(KEY_APP_VERSION, null)

    /**
     * Queries TriPath's handshake row and caches the result. Returns null when TriPath is absent,
     * the provider refused us, or it failed to answer — all of which read as "not connected".
     *
     * Blocking binder call: run it off the main thread.
     */
    fun handshake(context: Context): Handshake? {
        if (!isInstalled(context)) {
            recordFailure(context)
            return null
        }
        return try {
            context.contentResolver.query(TriPathContract.URI_HANDSHAKE, null, null, null, null)
                .use { cursor ->
                    if (cursor == null || !cursor.moveToFirst()) {
                        recordFailure(context)
                        return null
                    }
                    val result = Handshake(
                        contractVersion = cursor.optInt(TriPathContract.Handshake.CONTRACT_VERSION) ?: 0,
                        appVersionName = cursor.optString(TriPathContract.Handshake.APP_VERSION_NAME),
                        workoutCount = cursor.optInt(TriPathContract.Handshake.WORKOUT_COUNT) ?: 0,
                        latestWorkoutDate = cursor.optString(TriPathContract.Handshake.LATEST_WORKOUT_DATE),
                        latestWellnessDate = cursor.optString(TriPathContract.Handshake.LATEST_WELLNESS_DATE)
                    )
                    prefs(context).edit()
                        .putBoolean(KEY_HANDSHAKE_OK, true)
                        .putLong(KEY_HANDSHAKE_TIME, System.currentTimeMillis())
                        .putInt(KEY_CONTRACT_VERSION, result.contractVersion)
                        .putString(KEY_APP_VERSION, result.appVersionName)
                        .apply()
                    result
                }
        } catch (e: Exception) {
            // SecurityException when the signature/package check rejects us, IllegalArgument when
            // the provider is missing entirely. Neither should ever crash a sync.
            Log.w(TAG, "TriPath handshake failed", e)
            recordFailure(context)
            null
        }
    }

    private fun recordFailure(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_HANDSHAKE_OK, false)
            .putLong(KEY_HANDSHAKE_TIME, System.currentTimeMillis())
            .apply()
    }
}
