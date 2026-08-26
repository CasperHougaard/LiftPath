package com.liftpath.helpers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.liftpath.models.BackupBundle
import com.liftpath.models.BackupPrefEntry
import com.liftpath.models.TrainingData
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Collects every locally persisted byte into a single [BackupBundle] and puts it back again.
 *
 * The app keeps its state as loose JSON files in `filesDir` plus a handful of
 * SharedPreferences files. Backing up only `training_data.json` (as the old ad-hoc export
 * did) loses body-scan history, progression tuning and unit preferences, so this walks the
 * full set. Anything not listed in [BACKED_UP_FILES] / [BACKED_UP_PREFS] is deliberately
 * excluded: caches and the active-workout draft are transient.
 */
object BackupManager {

    private const val TAG = "BackupManager"

    /** Data files in `filesDir`. Absent files are skipped, not treated as an error. */
    val BACKED_UP_FILES = listOf(
        "training_data.json",
        "withings_body_data.json",
        "health_connect_activities.json",
        TriPathStorageHelper.FILE_NAME
    )

    /** SharedPreferences file names (without the `.xml` suffix). */
    val BACKED_UP_PREFS = listOf(
        "main_activity_prefs",
        "catalog_merge_prefs",
        "progression_settings",
        "progress_settings",
        "bodyweight_settings",
        "readiness_settings",
        "health_connect_settings",
        // Whether the TriPath integration is switched on. A user choice, not device wiring —
        // the handshake re-runs on the new phone and corrects itself if TriPath is absent there.
        TriPathConnection.PREFS_NAME,
        // Selected colour palette. Kept in sync with AppearanceManager.PREFS_NAME —
        // it is a user choice, not device wiring, so it should survive a phone swap.
        "appearance_settings",
        // Per-equipment weight ladders. Describes the user's gym, not this device.
        WeightIncrementSettingsManager.PREFS_NAME,
        // Cool-down scope, chosen areas and hold multiplier. How this athlete stretches,
        // not anything about this handset.
        StretchSettingsManager.PREFS_NAME
    )

    /**
     * Prefs that describe *this device's* backup wiring rather than user data. Restoring them
     * onto a new phone would point it at a folder URI it has no permission for, and would
     * overwrite the new device's own sync state — so they are captured for diagnostics but
     * never written back.
     */
    private val PREFS_EXCLUDED_FROM_RESTORE = setOf(BackupSettingsManager.PREFS_NAME)

    private val gson = Gson()

    // ---------------------------------------------------------------- create

    fun createBundle(context: Context): BackupBundle {
        val bundle = BackupBundle(
            createdAtMs = System.currentTimeMillis(),
            appVersionName = appVersionName(context),
            appVersionCode = appVersionCode(context),
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        )

        for (name in BACKED_UP_FILES) {
            val file = File(context.filesDir, name)
            if (!file.exists()) continue
            try {
                bundle.files[name] = file.readText()
            } catch (e: Exception) {
                Log.e(TAG, "Could not read $name for backup", e)
            }
        }

        for (prefsName in BACKED_UP_PREFS) {
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val all = prefs.all
            if (all.isEmpty()) continue
            val entries = mutableMapOf<String, BackupPrefEntry>()
            for ((key, value) in all) {
                encodePref(value)?.let { entries[key] = it }
            }
            if (entries.isNotEmpty()) bundle.prefs[prefsName] = entries
        }

        return bundle
    }

    fun serialize(bundle: BackupBundle): String = gson.toJson(bundle)

    /** Convenience: build and serialize in one step. */
    fun createBundleJson(context: Context): String = serialize(createBundle(context))

    // ----------------------------------------------------------------- read

    /**
     * Parses and validates a bundle. Returns a failure rather than throwing so callers can
     * surface the reason to the user — a truncated cloud download and a bundle from a newer
     * app version need different messages.
     */
    fun parse(json: String): Result<BackupBundle> = runCatching {
        val bundle = try {
            gson.fromJson(json, BackupBundle::class.java)
        } catch (e: JsonSyntaxException) {
            throw IllegalArgumentException("File is not a valid LiftPath backup", e)
        } ?: throw IllegalArgumentException("Backup file is empty")

        if (bundle.formatVersion > BackupBundle.CURRENT_FORMAT_VERSION) {
            throw IllegalArgumentException(
                "This backup was made by a newer version of LiftPath " +
                    "(format ${bundle.formatVersion}). Update the app, then restore."
            )
        }
        if (bundle.files.isEmpty() && bundle.prefs.isEmpty()) {
            throw IllegalArgumentException("Backup contains no data")
        }
        // A bundle without training data is almost certainly the wrong file.
        if (!bundle.files.containsKey("training_data.json")) {
            throw IllegalArgumentException("Backup is missing training data")
        }
        bundle
    }

    /** Human-readable one-liner for confirmation dialogs and status rows. */
    fun describe(bundle: BackupBundle): String {
        val stamp = formatTimestamp(bundle.createdAtMs)
        val training = bundle.files["training_data.json"]
        val data = training?.let {
            try {
                gson.fromJson(it, TrainingData::class.java)
            } catch (e: Exception) {
                null
            }
        }
        return if (data == null) {
            "Backup from $stamp"
        } else {
            val sessions = data.trainings.size
            val plans = data.workoutPlans.size
            val exercises = data.exerciseLibrary.size
            "Backup from $stamp\n$sessions workouts · $plans plans · $exercises exercises"
        }
    }

    /** Number of workouts in a bundle, or null when it can't be determined. */
    fun sessionCount(bundle: BackupBundle): Int? {
        val training = bundle.files["training_data.json"] ?: return null
        return try {
            gson.fromJson(training, TrainingData::class.java)?.trainings?.size
        } catch (e: Exception) {
            null
        }
    }

    // -------------------------------------------------------------- restore

    /**
     * Overwrites local state with [bundle]. The current state is copied into a safety bundle
     * in `filesDir` first, so a restore of the wrong file is itself recoverable.
     *
     * Callers must treat every cached [JsonHelper] as stale afterwards — the simplest correct
     * response is to restart the app.
     */
    fun restore(context: Context, bundle: BackupBundle): Result<Unit> = runCatching {
        writeSafetyCopy(context)

        for ((name, contents) in bundle.files) {
            if (name !in BACKED_UP_FILES) {
                Log.w(TAG, "Skipping unrecognized file in backup: $name")
                continue
            }
            FileIo.writeAtomically(File(context.filesDir, name), contents)
        }

        for ((prefsName, entries) in bundle.prefs) {
            if (prefsName in PREFS_EXCLUDED_FROM_RESTORE) continue
            if (prefsName !in BACKED_UP_PREFS) {
                Log.w(TAG, "Skipping unrecognized prefs in backup: $prefsName")
                continue
            }
            val editor = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit()
            editor.clear()
            for ((key, entry) in entries) {
                applyPref(editor, key, entry)
            }
            editor.commit()
        }
    }.onFailure {
        Log.e(TAG, "Restore failed", it)
    }

    /**
     * Restores [bundle] and restarts the app process — the only correct response, since every
     * screen constructs its own [JsonHelper] with an independent in-memory cache and there is
     * no central invalidation hook. Shared by every restore entry point (manual, from Settings,
     * and the automatic OS-restore prompt) so this sequencing lives in exactly one place.
     */
    fun restoreAndRestart(activity: Activity, bundle: BackupBundle): Result<Unit> =
        restore(activity, bundle).onSuccess { restartAppAfterRestore(activity) }

    fun restartAppAfterRestore(activity: Activity) {
        val launchIntent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
        if (launchIntent == null) {
            activity.finishAffinity()
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(launchIntent)
        activity.finishAffinity()
        exitProcess(0)
    }

    /**
     * Snapshots current state to `filesDir` before a destructive restore. Best-effort: a
     * failure here must not block the restore the user explicitly asked for.
     */
    private fun writeSafetyCopy(context: Context) {
        try {
            val json = createBundleJson(context)
            val target = File(context.filesDir, "pre_restore_backup.json")
            FileIo.writeAtomically(target, json)
            Log.i(TAG, "Wrote pre-restore safety copy (${json.length} chars)")
        } catch (e: Exception) {
            Log.e(TAG, "Could not write pre-restore safety copy", e)
        }
    }

    // ------------------------------------------------------------- prefs io

    private fun encodePref(value: Any?): BackupPrefEntry? = when (value) {
        is Boolean -> BackupPrefEntry(BackupPrefEntry.TYPE_BOOLEAN, value.toString())
        is Int -> BackupPrefEntry(BackupPrefEntry.TYPE_INT, value.toString())
        is Long -> BackupPrefEntry(BackupPrefEntry.TYPE_LONG, value.toString())
        is Float -> BackupPrefEntry(BackupPrefEntry.TYPE_FLOAT, value.toString())
        is String -> BackupPrefEntry(BackupPrefEntry.TYPE_STRING, value)
        is Set<*> -> BackupPrefEntry(
            type = BackupPrefEntry.TYPE_STRING_SET,
            values = value.filterIsInstance<String>()
        )
        else -> {
            Log.w(TAG, "Unsupported preference type: ${value?.javaClass?.name}")
            null
        }
    }

    private fun applyPref(
        editor: android.content.SharedPreferences.Editor,
        key: String,
        entry: BackupPrefEntry
    ) {
        try {
            when (entry.type) {
                BackupPrefEntry.TYPE_BOOLEAN -> editor.putBoolean(key, entry.value.toBoolean())
                BackupPrefEntry.TYPE_INT -> entry.value?.toInt()?.let { editor.putInt(key, it) }
                BackupPrefEntry.TYPE_LONG -> entry.value?.toLong()?.let { editor.putLong(key, it) }
                BackupPrefEntry.TYPE_FLOAT -> entry.value?.toFloat()?.let { editor.putFloat(key, it) }
                BackupPrefEntry.TYPE_STRING -> editor.putString(key, entry.value)
                BackupPrefEntry.TYPE_STRING_SET ->
                    editor.putStringSet(key, entry.values.orEmpty().toSet())
                else -> Log.w(TAG, "Unknown preference type '${entry.type}' for key $key")
            }
        } catch (e: NumberFormatException) {
            Log.w(TAG, "Malformed preference value for key $key", e)
        }
    }

    // -------------------------------------------------------------- helpers

    fun defaultFileName(timestampMs: Long): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "liftpath_backup_${formatter.format(Date(timestampMs))}.json"
    }

    fun formatTimestamp(timestampMs: Long): String {
        if (timestampMs <= 0L) return "never"
        val formatter = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
        return formatter.format(Date(timestampMs))
    }

    private fun appVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    @Suppress("DEPRECATION")
    private fun appVersionCode(context: Context): Int = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            info.versionCode
        }
    } catch (e: Exception) {
        0
    }
}
