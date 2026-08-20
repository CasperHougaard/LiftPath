package com.liftpath.helpers

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.liftpath.models.BackupBundle
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Maintains the one file Android's OS-level Auto Backup is allowed to see
 * (`res/xml/data_extraction_rules.xml` allow-lists only `cloud_backup/`): a gzipped,
 * versioned snapshot of [BackupManager]'s curated bundle, refreshed often enough that a lost
 * phone never costs more than a few minutes of history.
 *
 * Always exactly one file, overwritten in place — unlike [LocalFolderBackupHelper]'s
 * timestamped history, there is no pruning here and none is needed. Don't turn this into a
 * series of dated snapshots; that's what reintroduces the quota risk this file exists to avoid.
 *
 * Deliberately not gated by [BackupSettingsManager.autoBackupEnabled] — that switch governs
 * only the opt-in local-folder destination. This one has no setup and no toggle, mirroring
 * how Auto Backup itself isn't something an app's own settings screen can turn off.
 */
object CloudSnapshotStore {

    private const val TAG = "CloudSnapshotStore"
    private const val DIR_NAME = "cloud_backup"
    private const val SNAPSHOT_FILE = "snapshot.json.gz"
    private const val META_FILE = "snapshot_meta.json"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val gson = Gson()

    data class SnapshotMeta(
        val writtenAtMs: Long = 0L,
        val appVersionCode: Int = 0,
        val sizeBytes: Long = 0L,
        val sessionCount: Int? = null
    )

    fun snapshotFile(context: Context): File = File(dir(context), SNAPSHOT_FILE)
    private fun metaFile(context: Context): File = File(dir(context), META_FILE)
    private fun dir(context: Context): File = File(context.filesDir, DIR_NAME)

    fun hasSnapshot(context: Context): Boolean = snapshotFile(context).exists()

    /**
     * Fire-and-forget refresh, skipped if a restore decision is outstanding (refreshing then
     * would overwrite the just-restored snapshot with a fresh, empty-state one before the user
     * ever sees the prompt) or if the existing snapshot is younger than [maxAgeMillis].
     */
    fun refreshInBackground(context: Context, maxAgeMillis: Long) {
        val appContext = context.applicationContext
        scope.launch {
            if (RestoreCoordinator.hasPendingRestore()) return@launch
            val age = System.currentTimeMillis() - (readMeta(appContext)?.writtenAtMs ?: 0L)
            if (age < maxAgeMillis) return@launch
            refreshNow(appContext)
        }
    }

    /** Builds a fresh bundle, gzips it, and writes it atomically. Safe to call directly for a
     *  manual "back up now" or a bounded, blocking refresh on app-background. */
    suspend fun refreshNow(context: Context): Result<Unit> = runCatching {
        if (RestoreCoordinator.hasPendingRestore()) return@runCatching

        val appContext = context.applicationContext
        val folder = dir(appContext)
        folder.mkdirs()

        val bundle = BackupManager.createBundle(appContext)
        val gzipped = gzip(BackupManager.serialize(bundle).toByteArray(Charsets.UTF_8))
        FileIo.writeAtomically(snapshotFile(appContext), gzipped)

        val meta = SnapshotMeta(
            writtenAtMs = System.currentTimeMillis(),
            appVersionCode = appVersionCode(appContext),
            sizeBytes = gzipped.size.toLong(),
            sessionCount = BackupManager.sessionCount(bundle)
        )
        FileIo.writeAtomically(metaFile(appContext), gson.toJson(meta))

        android.app.backup.BackupManager(appContext).dataChanged()
    }.onFailure {
        Log.e(TAG, "Could not refresh cloud snapshot", it)
    }

    fun readMeta(context: Context): SnapshotMeta? = try {
        val file = metaFile(context)
        if (!file.exists()) null else gson.fromJson(file.readText(), SnapshotMeta::class.java)
    } catch (e: Exception) {
        null
    }

    fun readBundle(context: Context): Result<BackupBundle> = runCatching {
        val bytes = snapshotFile(context).readBytes()
        val json = String(gunzip(bytes), Charsets.UTF_8)
        BackupManager.parse(json).getOrThrow()
    }

    // ------------------------------------------------------------------- gzip

    internal fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    internal fun gunzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(bytes.inputStream()).use { it.readBytes() }

    @Suppress("DEPRECATION")
    private fun appVersionCode(context: Context): Int = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            info.versionCode
        }
    } catch (e: Exception) {
        0
    }
}
