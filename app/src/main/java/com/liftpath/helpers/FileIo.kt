package com.liftpath.helpers

import android.util.Log
import java.io.File

/**
 * Crash-safe file writing, shared by every path that persists app state.
 *
 * A plain `File.writeText` truncates the target before writing it, so a process kill partway
 * through leaves a half-written file behind. For `training_data.json` — rewritten in full on
 * every logged set — that means the next launch finds unparseable JSON and falls back to seeded
 * defaults, i.e. the user's entire history is gone. Writing to a sibling temp file and renaming
 * over the target makes the swap atomic: the reader sees either the old bytes or the new ones.
 */
object FileIo {

    private const val TAG = "FileIo"

    /** Write via a temp file + rename so a crash mid-write can't leave a half-written file. */
    fun writeAtomically(target: File, contents: String) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(contents)
        if (target.exists() && !target.delete()) {
            Log.w(TAG, "Could not delete ${target.name} before replace")
        }
        if (!temp.renameTo(target)) {
            // Rename can fail on some providers; fall back to a direct write.
            target.writeText(contents)
            temp.delete()
        }
    }

    /** Byte-array counterpart, for gzipped payloads that can't go through [writeText]. */
    fun writeAtomically(target: File, contents: ByteArray) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeBytes(contents)
        if (target.exists() && !target.delete()) {
            Log.w(TAG, "Could not delete ${target.name} before replace")
        }
        if (!temp.renameTo(target)) {
            // Rename can fail on some providers; fall back to a direct write.
            target.writeBytes(contents)
            temp.delete()
        }
    }
}
