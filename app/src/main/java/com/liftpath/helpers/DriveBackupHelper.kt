package com.liftpath.helpers

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.liftpath.models.BackupBundle
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Uploads and lists backup bundles in a "LiftPath Backups" folder in the user's own Google
 * Drive, using the `drive.file` token from [DriveAuthHelper] — LiftPath can only see files it
 * created itself, never the rest of the user's Drive.
 *
 * Talks to the Drive v3 REST API directly over [HttpURLConnection] rather than pulling in the
 * Drive client library, which drags in a large chunk of `google-api-client`/`guava` for the
 * handful of calls this app ever makes, all from a background worker or Settings.
 *
 * Every method here blocks on network I/O — callers must run them off the main thread, the same
 * convention [LocalFolderBackupHelper] uses.
 */
class DriveBackupHelper(context: Context) {

    private val appContext = context.applicationContext
    private val settings = BackupSettingsManager(appContext)
    private val gson = Gson()

    data class DriveFileInfo(val id: String, val name: String, val sizeBytes: Long)

    fun backupNow(token: String): Result<String> = runCatching {
        val folderId = ensureFolder(token)
        val fileName = BackupManager.defaultFileName(System.currentTimeMillis())
        val json = BackupManager.createBundleJson(appContext)

        uploadFile(token, folderId, fileName, json)

        settings.lastDriveBackupMs = System.currentTimeMillis()
        settings.lastDriveError = null
        prune(token, folderId)
        fileName
    }.onFailure {
        settings.lastDriveError = it.localizedMessage ?: it.javaClass.simpleName
        Log.e(TAG, "Drive backup failed", it)
    }

    /** Newest first. */
    fun listBackups(token: String): Result<List<DriveFileInfo>> = runCatching {
        listFiles(token, ensureFolder(token)).sortedByDescending { it.name }
    }

    fun downloadBundle(token: String, fileId: String): Result<BackupBundle> = runCatching {
        BackupManager.parse(downloadFile(token, fileId)).getOrThrow()
    }

    // ------------------------------------------------------------- folder

    /** Finds LiftPath's Drive folder, creating it on first use, and caches the id. */
    private fun ensureFolder(token: String): String {
        settings.driveFolderId?.let { cached ->
            if (fileExists(token, cached)) return cached
        }
        val folderId = findFolder(token) ?: createFolder(token)
        settings.driveFolderId = folderId
        return folderId
    }

    private fun findFolder(token: String): String? {
        val query =
            "name = '$FOLDER_NAME' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val url = "$FILES_URL?q=${encode(query)}&fields=files(id,name)&spaces=drive"
        val body = request(token, url, "GET") ?: return null
        val files = JsonParser.parseString(body).asJsonObject.getAsJsonArray("files")
        return files?.firstOrNull()?.asJsonObject?.get("id")?.asString
    }

    private fun createFolder(token: String): String {
        val metadata = JsonObject().apply {
            addProperty("name", FOLDER_NAME)
            addProperty("mimeType", "application/vnd.google-apps.folder")
        }
        val body = request(
            token = token,
            url = "$FILES_URL?fields=id",
            method = "POST",
            contentType = "application/json; charset=UTF-8",
            payload = gson.toJson(metadata).toByteArray(Charsets.UTF_8)
        ) ?: throw IOException("Could not create the Drive backup folder")
        return JsonParser.parseString(body).asJsonObject.get("id").asString
    }

    private fun fileExists(token: String, fileId: String): Boolean = try {
        request(token, "$FILES_URL/$fileId?fields=id,trashed", "GET")
            ?.let { !JsonParser.parseString(it).asJsonObject.get("trashed").asBoolean }
            ?: false
    } catch (e: Exception) {
        false
    }

    // --------------------------------------------------------------- files

    private fun listFiles(token: String, folderId: String): List<DriveFileInfo> {
        val query = "'$folderId' in parents and name contains '$FILE_PREFIX' and trashed = false"
        val url = "$FILES_URL?q=${encode(query)}&fields=files(id,name,size)&spaces=drive&pageSize=1000"
        val body = request(token, url, "GET") ?: return emptyList()
        val files = JsonParser.parseString(body).asJsonObject.getAsJsonArray("files") ?: return emptyList()
        return files.map {
            val obj = it.asJsonObject
            DriveFileInfo(
                id = obj.get("id").asString,
                name = obj.get("name").asString,
                sizeBytes = obj.get("size")?.asLong ?: 0L
            )
        }
    }

    private fun uploadFile(token: String, folderId: String, name: String, json: String) {
        val boundary = "liftpath-${System.currentTimeMillis()}"
        val metadata = JsonObject().apply {
            addProperty("name", name)
            add("parents", gson.toJsonTree(listOf(folderId)))
        }
        val body = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(gson.toJson(metadata))
            append("\r\n--$boundary\r\n")
            append("Content-Type: application/json\r\n\r\n")
            append(json)
            append("\r\n--$boundary--")
        }
        request(
            token = token,
            url = "$UPLOAD_URL?uploadType=multipart&fields=id",
            method = "POST",
            contentType = "multipart/related; boundary=$boundary",
            payload = body.toByteArray(Charsets.UTF_8)
        ) ?: throw IOException("Drive upload of $name failed")
    }

    private fun downloadFile(token: String, fileId: String): String {
        val connection = (URL("$FILES_URL/$fileId?alt=media").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        return try {
            if (connection.responseCode !in 200..299) {
                throw IOException("Drive download failed (HTTP ${connection.responseCode})")
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Deletes the oldest snapshots beyond [BackupSettingsManager.keepCount]. */
    private fun prune(token: String, folderId: String) {
        try {
            val stale = listFiles(token, folderId).sortedByDescending { it.name }.drop(settings.keepCount)
            for (file in stale) {
                request(token, "$FILES_URL/${file.id}", "DELETE")
            }
        } catch (e: Exception) {
            // Pruning is housekeeping — never fail a successful backup over it.
            Log.w(TAG, "Pruning old Drive backups failed", e)
        }
    }

    // ------------------------------------------------------------- transport

    private fun request(
        token: String,
        url: String,
        method: String,
        contentType: String? = null,
        payload: ByteArray? = null
    ): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $token")
            contentType?.let { setRequestProperty("Content-Type", it) }
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        try {
            payload?.let {
                connection.doOutput = true
                connection.outputStream.use { out -> out.write(it) }
            }
            if (connection.responseCode !in 200..299) {
                val error = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                throw IOException("Drive request to $url failed (HTTP ${connection.responseCode}): $error")
            }
            if (method == "DELETE") return null
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val TAG = "DriveBackupHelper"
        private const val FOLDER_NAME = "LiftPath Backups"
        private const val FILE_PREFIX = "liftpath_backup_"
        private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private const val TIMEOUT_MS = 30_000
    }
}
