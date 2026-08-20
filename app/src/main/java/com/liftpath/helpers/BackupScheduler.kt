package com.liftpath.helpers

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.liftpath.workers.BackupWorker
import java.util.concurrent.TimeUnit

/**
 * Decides *when* backups run.
 *
 * Hooked into [JsonHelper.writeTrainingData] rather than into the finish-workout path,
 * because training data is written from a dozen places (editing history, importing plans,
 * merging the catalog) and any of them can be the change worth saving. Every write marks
 * the data dirty and re-arms a delayed job, so a workout that logs forty sets produces one
 * backup a few minutes after the last set instead of forty uploads mid-session.
 */
object BackupScheduler {

    private const val TAG = "BackupScheduler"

    private const val WORK_DEBOUNCED = "liftpath_backup_debounced"
    private const val WORK_PERIODIC = "liftpath_backup_periodic"
    private const val WORK_MANUAL = "liftpath_backup_manual"

    /** How long the app waits for changes to settle before backing up. */
    private const val DEBOUNCE_MINUTES = 10L

    /** Staleness threshold for the zero-login OS snapshot on the write-triggered path. */
    private const val SNAPSHOT_MAX_AGE_MS = 10 * 60 * 1000L

    /**
     * Re-enqueueing on every single write would be wasteful — WorkManager writes to its own
     * database each time. Coalescing in memory keeps the common case (many writes in one
     * session) to one enqueue per minute; the delayed job is what actually batches them.
     */
    private const val ENQUEUE_THROTTLE_MS = 60_000L

    @Volatile
    private var lastEnqueueMs = 0L

    /**
     * Records that training data changed and arms a delayed backup. Safe to call from any
     * thread and from hot paths; never throws.
     */
    fun onDataChanged(context: Context) {
        val appContext = context.applicationContext
        try {
            val settings = BackupSettingsManager(appContext)
            settings.lastDataChangeMs = System.currentTimeMillis()

            // Unconditional: the zero-login OS snapshot has no setup and no toggle, unlike
            // the opt-in folder destination gated below.
            CloudSnapshotStore.refreshInBackground(appContext, SNAPSHOT_MAX_AGE_MS)

            if (!settings.autoBackupEnabled || !settings.hasAnyDestination()) return

            val now = System.currentTimeMillis()
            if (now - lastEnqueueMs < ENQUEUE_THROTTLE_MS) return
            lastEnqueueMs = now

            val request = OneTimeWorkRequestBuilder<BackupWorker>()
                .setInitialDelay(DEBOUNCE_MINUTES, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .setConstraints(networkConstraints(appContext))
                .build()

            // REPLACE, so each further change pushes the backup out rather than queueing
            // another one — the job fires once the user stops making changes.
            WorkManager.getInstance(appContext)
                .enqueueUniqueWork(WORK_DEBOUNCED, ExistingWorkPolicy.REPLACE, request)
        } catch (e: Exception) {
            // Backup scheduling must never take down a data write.
            Log.e(TAG, "Could not schedule backup", e)
        }
    }

    /**
     * Registers the daily safety-net backup. Idempotent — call from app start.
     *
     * The daily run matters for the case the debounced job can't cover: the user opens the
     * app, changes nothing, and the phone dies the next day. It also retries destinations
     * that were offline when the debounced job ran.
     */
    fun ensurePeriodicBackup(context: Context) {
        val appContext = context.applicationContext
        try {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .setConstraints(networkConstraints(appContext))
                .build()

            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                WORK_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not schedule periodic backup", e)
        }
    }

    /**
     * Runs a backup as soon as possible, ignoring the auto-backup switch. Used by the
     * "Back up now" button so an explicit request is always honoured.
     */
    fun backupNow(context: Context) {
        val appContext = context.applicationContext
        try {
            val request = OneTimeWorkRequestBuilder<BackupWorker>()
                .setInputData(Data.Builder().putBoolean(BackupWorker.KEY_MANUAL, true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .setConstraints(networkConstraints(appContext))
                .build()

            WorkManager.getInstance(appContext)
                .enqueueUniqueWork(WORK_MANUAL, ExistingWorkPolicy.REPLACE, request)
        } catch (e: Exception) {
            Log.e(TAG, "Could not start manual backup", e)
        }
    }

    fun cancelAll(context: Context) {
        try {
            val workManager = WorkManager.getInstance(context.applicationContext)
            workManager.cancelUniqueWork(WORK_DEBOUNCED)
            workManager.cancelUniqueWork(WORK_PERIODIC)
        } catch (e: Exception) {
            Log.e(TAG, "Could not cancel backup work", e)
        }
    }

    /**
     * Drive needs the network; a folder on local storage does not. Requiring connectivity
     * when only a local folder is configured would stall backups on a phone in airplane mode.
     */
    private fun networkConstraints(context: Context): Constraints {
        val needsNetwork = BackupSettingsManager(context).driveEnabled
        return Constraints.Builder()
            .setRequiredNetworkType(if (needsNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
            .build()
    }
}
