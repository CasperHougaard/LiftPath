package com.liftpath.helpers

import android.util.Log
import java.io.File

/**
 * Where `training_data.json` physically lives.
 *
 * [JsonHelper] owns a good deal of logic that has nothing to do with storage — schema
 * normalization, the family/timed-metric backfills, legacy plan migration, and the
 * corrupt-file recovery path. None of that was reachable from a unit test while the class
 * talked to `File` directly, because a `File` needs a real `Context.filesDir`.
 *
 * This interface is the seam. [FileTrainingDataStore] is the only production implementation and
 * the only place that touches the filesystem; tests substitute an in-memory fake.
 */
interface TrainingDataStore {

    /** Raw JSON, or null when nothing has been persisted yet. */
    fun read(): String?

    /** Persist [contents], atomically — a kill mid-write must not corrupt the existing file. */
    fun write(contents: String)

    /**
     * Move the current contents aside under a timestamped name, leaving no live file behind.
     *
     * Used before the two destructive events: reseeding after a parse failure, and overwriting
     * local data with an imported file. Both immediately [write] afterwards, so a move is as
     * safe as a copy. Best-effort — a failure here must not block the write that follows, since
     * the alternative is an app that cannot start.
     */
    fun archiveCurrent()
}

/** Production store: `<filesDir>/training_data.json`, written through [FileIo.writeAtomically]. */
class FileTrainingDataStore(private val filesDir: File) : TrainingDataStore {

    private val file = File(filesDir, FILE_NAME)

    override fun read(): String? = if (file.exists()) file.readText() else null

    override fun write(contents: String) = FileIo.writeAtomically(file, contents)

    override fun archiveCurrent() {
        try {
            val backupFile = File(filesDir, "$FILE_NAME.bak.${System.currentTimeMillis()}")
            file.renameTo(backupFile)
        } catch (e: Exception) {
            Log.e(TAG, "Could not archive existing training data.", e)
        }
    }

    companion object {
        private const val TAG = "FileTrainingDataStore"
        const val FILE_NAME = "training_data.json"
    }
}
