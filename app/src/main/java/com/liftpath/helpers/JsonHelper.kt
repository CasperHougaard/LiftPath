package com.liftpath.helpers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.liftpath.models.PlanExerciseSelectionType
import com.liftpath.models.PlanExerciseSlot
import com.liftpath.models.SetIntent
import com.liftpath.models.TrainingData
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

class JsonHelper(private val context: Context) {

    private val gson = Gson()
    private val file = File(context.filesDir, "training_data.json")
    private val TAG = "JsonHelper"

    /** In-memory copy of the last read/write; avoids re-parsing JSON on every screen (e.g. each list row). */
    @Volatile
    private var cachedTrainingData: TrainingData? = null

    /** Drop cache so the next [readTrainingData] loads from disk (e.g. after another screen wrote the file). */
    fun invalidateTrainingDataCache() {
        cachedTrainingData = null
    }

    fun readTrainingData(): TrainingData {
        cachedTrainingData?.let { return it }

        // 1. NO FILE (Fresh Install)
        if (!file.exists()) {
            Log.i(TAG, "No training data found. Creating fresh data with Default Library.")
            val newData = TrainingData()
            // Seed the library immediately
            newData.exerciseLibrary.addAll(DefaultExercisesHelper.getPopularDefaults())
            // Write it to disk so it's saved
            writeTrainingData(newData)
            return cachedTrainingData!!
        }

        // 2. FILE EXISTS (Load it)
        val data = try {
            val json = file.readText()
            val parsed = gson.fromJson(json, TrainingData::class.java) ?: TrainingData()

            // 3. SAFETY CHECK: If library is empty for some reason, re-seed it.
            if (parsed.exerciseLibrary.isEmpty()) {
                Log.w(TAG, "Training data found but library was empty. Re-seeding defaults.")
                parsed.exerciseLibrary.addAll(DefaultExercisesHelper.getPopularDefaults())
                writeTrainingData(parsed)
                return cachedTrainingData!!
            }

            normalizeTrainingData(parsed)
            parsed
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
            return cachedTrainingData!!
        }

        cachedTrainingData = data
        return data
    }

    /**
     * Normalizes a [TrainingData] object after Gson deserialization.
     * Gson uses Unsafe to instantiate objects, bypassing Kotlin constructors, so newly
     * added fields that are absent from old JSON will be null at runtime despite non-null types.
     * This function fixes null collections and migrates legacy plans.
     */
    @Suppress("SENSELESS_COMPARISON")
    private fun normalizeTrainingData(data: TrainingData): TrainingData {
        // Fix null collections caused by Gson bypassing constructors for new fields
        if (data.planSets == null) data.planSets = mutableListOf()
        if (data.planSetProgress == null) data.planSetProgress = mutableListOf()
        if (data.exerciseFamilies == null) data.exerciseFamilies = mutableListOf()

        // Idempotent family migrations — each function only fills null fields, safe to re-run
        ensureDefaultFamiliesExist(data)
        ensureLibraryFamilyMappingsExist(data)
        backfillFamilyIdSnapshotsIfMissing(data)

        // Migrate legacy WorkoutPlans: if a plan has no exerciseConfigs, generate minimal ones
        // from exerciseIds so V2 code can always rely on exerciseConfigs being present
        data.workoutPlans.forEachIndexed { index, plan ->
            if (plan.exerciseConfigs == null && plan.exerciseIds.isNotEmpty()) {
                val migratedConfigs = plan.exerciseIds.map { id ->
                    PlanExerciseSlot(
                        exerciseId = id,
                        selectionType = PlanExerciseSelectionType.SPECIFIC_VARIANT,
                        defaultIntent = SetIntent.BUILD
                    )
                }
                data.workoutPlans[index] = plan.copy(exerciseConfigs = migratedConfigs)
            }
        }
        return data
    }

    // Seeds any DEFAULT_FAMILIES not yet present. Existing entries (including user-created) untouched.
    private fun ensureDefaultFamiliesExist(data: TrainingData) {
        val families = data.exerciseFamilies ?: return
        val existingIds = families.map { it.id }.toSet()
        val missing = DefaultExercisesHelper.DEFAULT_FAMILIES.filter { it.id !in existingIds }
        if (missing.isNotEmpty()) families.addAll(missing)
    }

    // Fills null familyId/equipment/angle/laterality on default-catalog exercises using catalog defaults.
    private fun ensureLibraryFamilyMappingsExist(data: TrainingData) {
        val defaults = DefaultExercisesHelper.getPopularDefaults().associateBy { it.id }
        val needsUpdate = data.exerciseLibrary.any { ex -> ex.familyId == null && defaults.containsKey(ex.id) }
        if (!needsUpdate) return
        for (i in data.exerciseLibrary.indices) {
            val exercise = data.exerciseLibrary[i]
            if (exercise.familyId == null) {
                val def = defaults[exercise.id] ?: continue
                data.exerciseLibrary[i] = exercise.copy(
                    familyId = def.familyId,
                    equipment = exercise.equipment ?: def.equipment,
                    angle = exercise.angle ?: def.angle,
                    laterality = exercise.laterality ?: def.laterality
                )
            }
        }
    }

    // Fills null familyIdSnapshot on ExerciseEntry rows using the current library as source of truth.
    private fun backfillFamilyIdSnapshotsIfMissing(data: TrainingData) {
        val libraryFamilyMap = data.exerciseLibrary.associate { it.id to it.familyId }
        val hasNulls = data.trainings.any { session ->
            session.exercises.any { it.familyIdSnapshot == null && libraryFamilyMap[it.exerciseId] != null }
        }
        if (!hasNulls) return
        for (session in data.trainings) {
            for (i in session.exercises.indices) {
                val entry = session.exercises[i]
                if (entry.familyIdSnapshot == null) {
                    val familyId = libraryFamilyMap[entry.exerciseId] ?: continue
                    session.exercises[i] = entry.copy(familyIdSnapshot = familyId)
                }
            }
        }
    }

    fun writeTrainingData(trainingData: TrainingData) {
        try {
            val json = gson.toJson(trainingData)
            file.writeText(json)
            cachedTrainingData = trainingData
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

    fun exportExerciseLibrary(destinationUri: Uri): Result<Unit> = runCatching {
        val data = readTrainingData()
        val prettyGson = GsonBuilder().setPrettyPrinting().create()
        val json = prettyGson.toJson(data.exerciseLibrary)
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
            outputStream.write(bytes)
            outputStream.flush()
        } ?: throw IOException("Unable to open destination")
    }.onFailure {
        Log.e(TAG, "Failed to export exercise library", it)
    }

    /** Write a pre-built text/markdown document (e.g. the AI export) to a user-picked location. */
    fun exportAiMarkdown(destinationUri: Uri, markdown: String): Result<Unit> = runCatching {
        val bytes = markdown.toByteArray(StandardCharsets.UTF_8)
        context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
            outputStream.write(bytes)
            outputStream.flush()
        } ?: throw IOException("Unable to open destination")
    }.onFailure {
        Log.e(TAG, "Failed to export AI markdown", it)
    }
}