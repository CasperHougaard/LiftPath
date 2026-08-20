package com.liftpath.helpers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.liftpath.models.ExerciseTargetMetric
import com.liftpath.models.PlanExerciseSelectionType
import com.liftpath.models.PlanExerciseSlot
import com.liftpath.models.SetIntent
import com.liftpath.models.TrainingData
import com.liftpath.models.WorkoutPlan
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Reads and writes `training_data.json`, the app's single persisted data file.
 *
 * There is exactly one instance per process, held by `LiftPathApplication` — see the KDoc on
 * [readTrainingData] for why that matters. Storage is reached through a [TrainingDataStore] so
 * the parsing, migration and recovery logic below can be unit-tested without a `Context`.
 */
class JsonHelper(
    private val store: TrainingDataStore,
    /**
     * Invoked after every successful write. In production this arms the debounced backup;
     * tests pass a no-op so they never touch WorkManager.
     */
    private val onDataChanged: () -> Unit
) {

    constructor(context: Context) : this(
        FileTrainingDataStore(context.applicationContext.filesDir),
        // Single choke point for every data change in the app — arms a debounced backup
        // so no write can escape without eventually reaching the configured destinations.
        { BackupScheduler.onDataChanged(context.applicationContext) }
    )

    private val gson = Gson()
    private val TAG = "JsonHelper"

    /** In-memory copy of the last read/write; avoids re-parsing JSON on every screen (e.g. each list row). */
    @Volatile
    private var cachedTrainingData: TrainingData? = null

    /** Drop cache so the next [readTrainingData] loads from disk (e.g. after another screen wrote the file). */
    fun invalidateTrainingDataCache() {
        cachedTrainingData = null
    }

    /**
     * The current training data, parsed from disk on first call and cached thereafter.
     *
     * Callers may mutate the returned object in place and then hand it back to
     * [writeTrainingData] — that is the established idiom across the app. Because there is one
     * shared instance, a mutation is visible to every screen immediately, which is what makes
     * the data consistent app-wide. The corollary is that a caller which mutates and then
     * *abandons* the edit would leak it; such a caller must copy first instead.
     */
    fun readTrainingData(): TrainingData {
        cachedTrainingData?.let { return it }

        // 1. NO FILE (Fresh Install)
        val json = store.read()
        if (json == null) {
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

            // If the file is corrupt, move it aside so the user keeps a recovery chance.
            store.archiveCurrent()

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
        if (data.circuits == null) data.circuits = mutableListOf()

        // Idempotent family migrations — each function only fills null fields, safe to re-run
        ensureDefaultFamiliesExist(data)
        ensureLibraryFamilyMappingsExist(data)
        backfillFamilyIdSnapshotsIfMissing(data)
        backfillTimedTargetMetricIfMissing(data)
        refreshIllustrationResFromDefaults(data)
        DefaultCircuitsHelper.seedIfNeeded(data)

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

    // Flags the known isometric default-catalog exercises (Plank, Side Plank) as TIME-based on installs
    // whose persisted library predates the targetMetric field. Only fills items whose metric is still
    // null, so a user who deliberately set REPS isn't overridden.
    private fun backfillTimedTargetMetricIfMissing(data: TrainingData) {
        val timedIds = DefaultExercisesHelper.DEFAULT_TIMED_EXERCISE_IDS
        val needsUpdate = data.exerciseLibrary.any { it.targetMetric == null && it.id in timedIds }
        if (!needsUpdate) return
        for (i in data.exerciseLibrary.indices) {
            val exercise = data.exerciseLibrary[i]
            if (exercise.targetMetric == null && exercise.id in timedIds) {
                data.exerciseLibrary[i] = exercise.copy(targetMetric = ExerciseTargetMetric.TIME)
            }
        }
    }

    // Re-resolves illustrationRes on default-catalog exercises from the CURRENT build's defaults.
    // illustrationRes stores a compiled R.drawable id, which is NOT stable across app builds — a value
    // persisted by an older build can resolve to an unrelated drawable after resources shift (e.g. a
    // library dependency adds drawables). So we always overwrite it from defaults[id], never trust the
    // stored int. Custom exercises (id not in the default catalog) are left untouched.
    private fun refreshIllustrationResFromDefaults(data: TrainingData) {
        val defaults = DefaultExercisesHelper.getPopularDefaults().associateBy { it.id }
        for (i in data.exerciseLibrary.indices) {
            val exercise = data.exerciseLibrary[i]
            val def = defaults[exercise.id] ?: continue
            if (exercise.illustrationRes != def.illustrationRes) {
                data.exerciseLibrary[i] = exercise.copy(illustrationRes = def.illustrationRes)
            }
        }
    }

    fun writeTrainingData(trainingData: TrainingData) {
        try {
            val json = gson.toJson(trainingData)
            store.write(json)
            cachedTrainingData = trainingData
            onDataChanged()
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

    // ------------------------------------------------------- bulk transfer
    // Used by TrainingDataTransfer to move whole-file contents in and out. Kept here rather
    // than exposing the store, so the destructive replace stays with the data's owner.

    /** The persisted JSON as it sits on disk, seeding and writing defaults first if absent. */
    fun snapshotJson(): String {
        store.read()?.let { return it }
        val freshData = TrainingData()
        freshData.exerciseLibrary.addAll(DefaultExercisesHelper.getPopularDefaults())
        writeTrainingData(freshData)
        return store.read() ?: throw IOException("Unable to read training data")
    }

    /**
     * Replace all training data with [json]. Parses before touching anything, so an invalid
     * file fails without destroying what's there; the outgoing data is then archived under a
     * timestamped name so a mistaken import is itself recoverable.
     */
    fun replaceAllFromJson(json: String) {
        val data = gson.fromJson(json, TrainingData::class.java)
            ?: throw IllegalArgumentException("Invalid training data")
        store.archiveCurrent()
        writeTrainingData(data)
    }
}
