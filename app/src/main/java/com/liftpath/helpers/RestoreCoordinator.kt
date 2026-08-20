package com.liftpath.helpers

import android.content.Context
import android.util.Log
import com.liftpath.models.BackupBundle
import java.io.File

/**
 * Detects a restore performed by Android's OS-level Auto Backup, which gives no callback for
 * it. The trick: [Context.getNoBackupFilesDir] is *never* backed up or restored, while
 * `filesDir` is (see [CloudSnapshotStore]). So a marker file there distinguishes "normal
 * launch" from "the snapshot just appeared from nowhere" — i.e. a restore.
 *
 * The marker is deliberately written only when the user answers the resulting prompt, not at
 * detection time: if the process dies before an answer (backgrounded, low-memory kill, just
 * closing the app), leaving the marker absent means the next cold start simply re-detects and
 * re-asks, rather than losing the offer forever.
 */
object RestoreCoordinator {

    private const val TAG = "RestoreCoordinator"
    private const val MARKER_FILE = "restore_marker"

    @Volatile
    private var pendingBundle: BackupBundle? = null

    @Volatile
    private var decisionOutstanding = false

    /**
     * Call once, synchronously, at process start — before anything that might refresh the
     * snapshot (see [CloudSnapshotStore.refreshInBackground]'s guard on [hasPendingRestore]).
     */
    fun checkOnAppStart(context: Context) {
        val marker = File(context.noBackupFilesDir, MARKER_FILE)
        if (marker.exists()) return // normal launch

        val snapshotFile = CloudSnapshotStore.snapshotFile(context)
        if (!snapshotFile.exists()) {
            // Genuinely fresh install: nothing to offer, nothing to lose either way.
            writeMarker(context)
            return
        }

        CloudSnapshotStore.readBundle(context)
            .onSuccess {
                pendingBundle = it
                decisionOutstanding = true
            }
            .onFailure {
                // Corrupt/truncated snapshot: don't write the marker so this is retried on
                // the next cold start rather than silently given up on forever.
                Log.w(TAG, "Could not parse restored snapshot", it)
            }
    }

    /** Guards every snapshot refresh — a refresh must never overwrite a snapshot that's still
     *  awaiting the user's restore decision with a fresh, empty-state one. */
    fun hasPendingRestore(): Boolean = decisionOutstanding

    /** One-shot consume, read once by the restore prompt. */
    fun consumePendingRestoreBundle(): BackupBundle? = pendingBundle.also { pendingBundle = null }

    /** Called after the user accepts or declines the restore prompt. */
    fun resolve(context: Context) {
        writeMarker(context)
        decisionOutstanding = false
    }

    private fun writeMarker(context: Context) {
        try {
            File(context.noBackupFilesDir, MARKER_FILE).writeText(System.currentTimeMillis().toString())
        } catch (e: Exception) {
            // Best-effort: worst case is a duplicate prompt next launch, a safe failure mode.
            Log.e(TAG, "Could not write restore marker", e)
        }
    }
}
