package com.liftpath.helpers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.liftpath.models.TrainingData
import com.google.gson.Gson
import java.io.File
import java.io.IOException

class JsonHelper(private val context: Context) {

    private val gson = Gson()
    private val file = File(context.filesDir, "training_data.json")
    private val TAG = "JsonHelper"

    fun readTrainingData(): TrainingData {
        // 1. NO FILE (Fresh Install)
        if (!file.exists()) {
            Log.i(TAG, "No training data found. Creating fresh data with Default Library.")
            val newData = TrainingData()
            // Seed the library immediately
            newData.exerciseLibrary.addAll(DefaultExercisesHelper.getPopularDefaults())
            // Write it to disk so it's saved
            writeTrainingData(newData)
            return newData
        }

        // 2. FILE EXISTS (Load it)
        return try {
            val json = file.readText()
            val data = gson.fromJson(json, TrainingData::class.java) ?: TrainingData()

            // 3. SAFETY CHECK: If library is empty for some reason, re-seed it.
            if (data.exerciseLibrary.isEmpty()) {
                Log.w(TAG, "Training data found but library was empty. Re-seeding defaults.")
                data.exerciseLibrary.addAll(DefaultExercisesHelper.getPopularDefaults())
                writeTrainingData(data)
            }

            data
        } catch (e: Exception) {
            Log.e(TAG, "Error reading or parsing training_data.json. Backing up and creating a new data file.", e)
            
            // If the file is corrupt, create a backup and start with a fresh one.
            try {
                val backupFile = File(context.filesDir, "training_data.json.bak.${System.currentTimeMillis()}")
                file.renameTo(backupFile)
            } catch (backupEx: Exception) {
                Log.e(TAG, "Could not back up corrupt file.", backupEx)
            }
            
            // Return fresh data with defaults
            val freshData = TrainingData()
            freshData.exerciseLibrary.addAll(DefaultExercisesHelper.getPopularDefaults())
            writeTrainingData(freshData)
            freshData
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
        // When resetting, we want a clean state BUT with the default library available.
        val freshData = TrainingData()
        freshData.exerciseLibrary.addAll(DefaultExercisesHelper.getPopularDefaults())
        writeTrainingData(freshData)
    }

    fun exportTrainingData(destinationUri: Uri): Result<Unit> = runCatching {
        // Ensure we have a file to export
        if (!file.exists()) {
            val freshData = TrainingData()
            freshData.exerciseLibrary.addAll(DefaultExercisesHelper.getPopularDefaults())
            writeTrainingData(freshData)
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
        
        // Validate parse before overwriting
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