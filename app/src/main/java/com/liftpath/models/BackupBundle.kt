package com.liftpath.models

/**
 * Serialized snapshot of everything LiftPath persists locally: the JSON data files in
 * `filesDir` plus every SharedPreferences file the app writes.
 *
 * This is the unit that gets written to a cloud folder or uploaded to Google Drive, so it
 * has to stay self-describing — a bundle produced by a newer build may be restored by an
 * older one after a phone swap. [formatVersion] gates that; readers refuse anything newer
 * than they understand rather than silently dropping fields.
 */
data class BackupBundle(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val createdAtMs: Long = 0L,
    val appVersionName: String = "",
    val appVersionCode: Int = 0,
    val deviceModel: String = "",
    /** File name in `filesDir` -> raw file contents. */
    val files: MutableMap<String, String> = mutableMapOf(),
    /** SharedPreferences file name -> key -> typed value. */
    val prefs: MutableMap<String, MutableMap<String, BackupPrefEntry>> = mutableMapOf()
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

/**
 * A single SharedPreferences value. SharedPreferences is typed and Gson would collapse
 * everything to Double/String on the way back, so the type is recorded explicitly.
 */
data class BackupPrefEntry(
    val type: String = TYPE_STRING,
    val value: String? = null,
    /** Only populated for [TYPE_STRING_SET]. */
    val values: List<String>? = null
) {
    companion object {
        const val TYPE_STRING = "string"
        const val TYPE_INT = "int"
        const val TYPE_LONG = "long"
        const val TYPE_FLOAT = "float"
        const val TYPE_BOOLEAN = "boolean"
        const val TYPE_STRING_SET = "stringSet"
    }
}
