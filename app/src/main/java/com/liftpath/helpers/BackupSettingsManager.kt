package com.liftpath.helpers

import android.content.Context

/**
 * Persisted backup/sync wiring: where snapshots go, when they last succeeded, and why they
 * last failed. Deliberately excluded from [BackupManager]'s restore set — this describes the
 * device, not the user's training history.
 */
class BackupSettingsManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ------------------------------------------------------------ folder

    /** Persisted SAF tree URI of the user-picked backup folder, or null if none chosen. */
    var folderUri: String?
        get() = prefs.getString(KEY_FOLDER_URI, null)
        set(value) = prefs.edit().putString(KEY_FOLDER_URI, value).apply()

    /** Display name of the picked folder, cached so the UI needn't re-resolve the URI. */
    var folderLabel: String?
        get() = prefs.getString(KEY_FOLDER_LABEL, null)
        set(value) = prefs.edit().putString(KEY_FOLDER_LABEL, value).apply()

    var lastFolderBackupMs: Long
        get() = prefs.getLong(KEY_LAST_FOLDER_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_FOLDER_MS, value).apply()

    var lastFolderError: String?
        get() = prefs.getString(KEY_LAST_FOLDER_ERROR, null)
        set(value) = prefs.edit().putString(KEY_LAST_FOLDER_ERROR, value).apply()

    /** How many timestamped snapshots to keep in the folder before pruning the oldest. */
    var keepCount: Int
        get() = prefs.getInt(KEY_KEEP_COUNT, DEFAULT_KEEP_COUNT)
        set(value) = prefs.edit().putInt(KEY_KEEP_COUNT, value.coerceIn(3, 200)).apply()

    // ------------------------------------------------------------- drive

    /** True once the user has connected a Google account via [DriveAuthHelper]. */
    var driveEnabled: Boolean
        get() = prefs.getBoolean(KEY_DRIVE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_DRIVE_ENABLED, value).apply()

    var lastDriveBackupMs: Long
        get() = prefs.getLong(KEY_LAST_DRIVE_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_DRIVE_MS, value).apply()

    var lastDriveError: String?
        get() = prefs.getString(KEY_LAST_DRIVE_ERROR, null)
        set(value) = prefs.edit().putString(KEY_LAST_DRIVE_ERROR, value).apply()

    /** Cached id of the "LiftPath Backups" Drive folder, so each backup needn't search for it. */
    var driveFolderId: String?
        get() = prefs.getString(KEY_DRIVE_FOLDER_ID, null)
        set(value) = prefs.edit().putString(KEY_DRIVE_FOLDER_ID, value).apply()

    // ------------------------------------------------------------ general

    /** Master switch for automatic (post-change and daily) backups. Manual runs ignore it. */
    var autoBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_ENABLED, value).apply()

    /**
     * Set whenever training data is written. Compared against the last successful backup so
     * the UI can warn when unsaved changes have been sitting around.
     */
    var lastDataChangeMs: Long
        get() = prefs.getLong(KEY_LAST_CHANGE_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHANGE_MS, value).apply()

    /** True when at least one backup destination is configured. */
    fun hasAnyDestination(): Boolean = folderUri != null || driveEnabled

    /** True when data changed after the most recent successful backup to any destination. */
    fun hasPendingChanges(): Boolean =
        lastDataChangeMs > maxOf(lastFolderBackupMs, lastDriveBackupMs)

    fun clearFolder() {
        prefs.edit()
            .remove(KEY_FOLDER_URI)
            .remove(KEY_FOLDER_LABEL)
            .remove(KEY_LAST_FOLDER_MS)
            .remove(KEY_LAST_FOLDER_ERROR)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "backup_settings"
        const val DEFAULT_KEEP_COUNT = 20

        private const val KEY_FOLDER_URI = "folder_uri"
        private const val KEY_FOLDER_LABEL = "folder_label"
        private const val KEY_LAST_FOLDER_MS = "last_folder_backup_ms"
        private const val KEY_LAST_FOLDER_ERROR = "last_folder_error"
        private const val KEY_KEEP_COUNT = "keep_count"
        private const val KEY_AUTO_ENABLED = "auto_backup_enabled"
        private const val KEY_LAST_CHANGE_MS = "last_data_change_ms"
        private const val KEY_DRIVE_ENABLED = "drive_enabled"
        private const val KEY_LAST_DRIVE_MS = "last_drive_backup_ms"
        private const val KEY_LAST_DRIVE_ERROR = "last_drive_error"
        private const val KEY_DRIVE_FOLDER_ID = "drive_folder_id"
    }
}
