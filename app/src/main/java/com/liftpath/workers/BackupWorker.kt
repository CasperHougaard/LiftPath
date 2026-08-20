package com.liftpath.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.liftpath.helpers.BackupSettingsManager
import com.liftpath.helpers.DriveAuthHelper
import com.liftpath.helpers.DriveBackupHelper
import com.liftpath.helpers.LocalFolderBackupHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs a backup to every configured destination. The zero-login OS snapshot
 * ([com.liftpath.helpers.CloudSnapshotStore]) is refreshed independently of this worker — it
 * has no setup and no toggle, so it isn't gated by [BackupSettingsManager.autoBackupEnabled]
 * the way this worker is.
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = BackupSettingsManager(applicationContext)
        val manual = inputData.getBoolean(KEY_MANUAL, false)

        if (!manual && !settings.autoBackupEnabled) {
            Log.i(TAG, "Automatic backup disabled; skipping")
            return Result.success()
        }
        if (!settings.hasAnyDestination()) {
            Log.i(TAG, "No backup destination configured; skipping")
            return Result.success()
        }

        var allOk = true

        if (settings.folderUri != null) {
            withContext(Dispatchers.IO) { LocalFolderBackupHelper(applicationContext).backupNow() }
                .onSuccess { Log.i(TAG, "Folder backup wrote $it") }
                .onFailure {
                    // A revoked folder grant won't fix itself on retry — the user has to
                    // re-pick the folder, and the Settings screen surfaces the error.
                    allOk = false
                    Log.e(TAG, "Folder backup failed", it)
                }
        }

        if (settings.driveEnabled) {
            when (val outcome = DriveAuthHelper.authorize(applicationContext)) {
                is DriveAuthHelper.AuthOutcome.Token -> {
                    withContext(Dispatchers.IO) {
                        DriveBackupHelper(applicationContext).backupNow(outcome.accessToken)
                    }.onSuccess { Log.i(TAG, "Drive backup wrote $it") }
                        .onFailure {
                            allOk = false
                            Log.e(TAG, "Drive backup failed", it)
                        }
                }
                is DriveAuthHelper.AuthOutcome.NeedsConsent ->
                    // Can't show UI from a background worker; the user re-grants next time
                    // they open Settings, where the same authorize() call resolves silently.
                    Log.i(TAG, "Drive needs consent again; skipping until the user revisits Settings")
                is DriveAuthHelper.AuthOutcome.Failure -> {
                    allOk = false
                    Log.e(TAG, "Drive authorization failed", outcome.error)
                }
            }
        }

        return if (allOk) Result.success() else Result.failure()
    }

    companion object {
        private const val TAG = "BackupWorker"
        const val KEY_MANUAL = "manual"
    }
}
