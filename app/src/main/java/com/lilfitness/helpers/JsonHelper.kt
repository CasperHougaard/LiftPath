package com.lilfitness.helpers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.lilfitness.models.TrainingData
import com.google.gson.Gson
import java.io.File
import java.io.IOException

class JsonHelper(private val context: Context) {

    private val gson = Gson()
    private val file = File(context.filesDir, "training_data.json")
    private val TAG = "JsonHelper"

    fun readTrainingData(): TrainingData {
        if (!file.exists()) {
            return TrainingData()
        }
        return try {
            val json = file.readText()
            gson.fromJson(json, TrainingData::class.java) ?: TrainingData()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading or parsing training_data.json. Backing up and creating a new data file.", e)
            // If the file is corrupt, create a backup and start with a fresh one.
            try {
                val backupFile = File(context.filesDir, "training_data.json.bak.${System.currentTimeMillis()}")
                file.renameTo(backupFile)
            } catch (backupEx: Exception) {
                Log.e(TAG, "Could not back up corrupt file.", backupEx)
            }
            TrainingData()
        }
    }

    fun writeTrainingData(trainingData: TrainingData) {
        try {
            val json = gson.toJson(trainingData)
            file.writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to training_data.json", e)
        }
    }

    fun resetTrainingData() {
        writeTrainingData(TrainingData())
    }

    fun exportTrainingData(destinationUri: Uri): Result<Unit> = runCatching {
        if (!file.exists()) {
            writeTrainingData(TrainingData())
        }
        val resolver = context.contentResolver
        resolver.openOutputStream(destinationUri)?.use { outputStream ->
            file.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
            outputStream.flush()
        } ?: throw IOException("Unable to open destination")
    }.onFailure {
        Log.e(TAG, "Failed to export training data", it)
    }

    fun importTrainingData(sourceUri: Uri): Result<Unit> = runCatching {
        val resolver = context.contentResolver
        val json = resolver.openInputStream(sourceUri)?.bufferedReader()?.use { it.readText() }
            ?: throw IOException("Unable to read source")
        val data = gson.fromJson(json, TrainingData::class.java)
            ?: throw IllegalArgumentException("Invalid training data")

        if (file.exists()) {
            val backupFile = File(context.filesDir, "training_data.json.bak.${System.currentTimeMillis()}")
            file.copyTo(backupFile, overwrite = true)
        }
        writeTrainingData(data)
    }.onFailure {
        Log.e(TAG, "Failed to import training data", it)
    }
}

