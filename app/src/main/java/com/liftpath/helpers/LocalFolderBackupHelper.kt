package com.liftpath.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.liftpath.models.BackupBundle
import java.io.IOException

/**
 * Writes backup bundles into a folder the user picked with the system document picker.
 *
 * The point of routing through SAF rather than a hardcoded path is that the picker can
 * target *any* DocumentsProvider — including the Google Drive and OneDrive apps. Choosing a
 * Drive folder there gets the file off the device with no OAuth client, no API key and no
 * Play review, which makes this the destination that works on day one.
 *
 * Snapshots are timestamped and pruned to [BackupSettingsManager.keepCount], so a corrupt
 * write or a bad in-app edit can be rolled back to an earlier day rather than only to the
 * most recent state.
 */
class LocalFolderBackupHelper(context: Context) {

    private val appContext = context.applicationContext
    private val settings = BackupSettingsManager(appContext)

    data class BackupFileInfo(
        val uri: Uri,
        val name: String,
        val lastModifiedMs: Long,
        val sizeBytes: Long
    )

    /**
     * Persists the picker result. Without [takePersistableUriPermission] the grant dies with
     * the process, and the next background backup would fail silently — which is exactly the
     * failure mode this whole feature exists to prevent.
     */
    fun saveFolder(treeUri: Uri): Result<Unit> = runCatching {
        appContext.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val doc = DocumentFile.fromTreeUri(appContext, treeUri)
            ?: throw IOException("Could not open the selected folder")
        if (!doc.canWrite()) throw IOException("No write access to the selected folder")

        settings.folderUri = treeUri.toString()
        settings.folderLabel = doc.name ?: treeUri.lastPathSegment
        settings.lastFolderError = null
    }.onFailure {
        Log.e(TAG, "Could not persist backup folder", it)
    }

    /** True when a folder is configured *and* still writable (the user can revoke access). */
    fun hasWritableFolder(): Boolean = resolveFolder()?.canWrite() == true

    private fun resolveFolder(): DocumentFile? {
        val uri = settings.folderUri ?: return null
        return try {
            DocumentFile.fromTreeUri(appContext, Uri.parse(uri))?.takeIf { it.exists() }
        } catch (e: Exception) {
            Log.e(TAG, "Backup folder URI no longer resolvable", e)
            null
        }
    }

    /**
     * Writes a fresh snapshot and prunes old ones. Returns the file name on success.
     * Records the outcome in settings so the UI can show a stale/failed state without
     * re-running the backup.
     */
    fun backupNow(): Result<String> = runCatching {
        val folder = resolveFolder() ?: throw IOException("No backup folder selected")
        if (!folder.canWrite()) {
            throw IOException("Lost write access to the backup folder — pick it again")
        }

        val timestamp = System.currentTimeMillis()
        val fileName = BackupManager.defaultFileName(timestamp)
        val json = BackupManager.createBundleJson(appContext)

        val target = folder.createFile("application/json", fileName)
            ?: throw IOException("Could not create $fileName in the backup folder")

        try {
            appContext.contentResolver.openOutputStream(target.uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: throw IOException("Could not open $fileName for writing")
        } catch (e: Exception) {
            // A zero-byte file left behind would later look like a valid backup.
            target.delete()
            throw e
        }

        settings.lastFolderBackupMs = timestamp
        settings.lastFolderError = null
        prune(folder)
        fileName
    }.onFailure {
        settings.lastFolderError = it.localizedMessage ?: it.javaClass.simpleName
        Log.e(TAG, "Folder backup failed", it)
    }

    /** Newest first. */
    fun listBackups(): List<BackupFileInfo> {
        val folder = resolveFolder() ?: return emptyList()
        return folder.listFiles()
            .filter { it.isFile && isBackupFile(it.name) }
            .map {
                BackupFileInfo(
                    uri = it.uri,
                    name = it.name.orEmpty(),
                    lastModifiedMs = it.lastModified(),
                    sizeBytes = it.length()
                )
            }
            .sortedByDescending { it.name }
    }

    fun readBundle(uri: Uri): Result<BackupBundle> = runCatching {
        val json = appContext.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: throw IOException("Could not read the selected backup")
        BackupManager.parse(json).getOrThrow()
    }

    /**
     * Deletes the oldest snapshots beyond the keep count. Names embed a sortable timestamp,
     * so lexicographic order is chronological — no need to stat each file.
     */
    private fun prune(folder: DocumentFile) {
        try {
            val keep = settings.keepCount
            val backups = folder.listFiles()
                .filter { it.isFile && isBackupFile(it.name) }
                .sortedByDescending { it.name }
            if (backups.size <= keep) return
            for (stale in backups.drop(keep)) {
                if (!stale.delete()) Log.w(TAG, "Could not prune ${stale.name}")
            }
        } catch (e: Exception) {
            // Pruning is housekeeping — never fail a successful backup over it.
            Log.w(TAG, "Pruning old backups failed", e)
        }
    }

    private fun isBackupFile(name: String?): Boolean =
        name != null && name.startsWith(FILE_PREFIX) && name.endsWith(".json")

    companion object {
        private const val TAG = "LocalFolderBackup"
        private const val FILE_PREFIX = "liftpath_backup_"
    }
}
