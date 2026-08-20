package com.liftpath.helpers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.GsonBuilder
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Moves app data between [JsonHelper] and user-picked SAF documents.
 *
 * Split out of `JsonHelper` so that class is only about `training_data.json` — reading, parsing,
 * migrating and persisting it — and needs no `Context` at all. Everything here is the opposite
 * concern: a `ContentResolver`, a `Uri` the user chose in the system file picker, and no
 * knowledge of the schema beyond what it asks [JsonHelper] and [WorkoutPlanMarkdownHelper] for.
 *
 * Every function returns a [Result] rather than throwing; the failure is logged here and the
 * caller decides what to show.
 */
class TrainingDataTransfer(
    private val context: Context,
    private val jsonHelper: JsonHelper
) {

    private val resolver get() = context.contentResolver

    // ---------------------------------------------------------------- export

    /** Write the full `training_data.json` to a user-picked location. */
    fun exportTrainingData(destinationUri: Uri): Result<Unit> = runCatching {
        writeText(destinationUri, jsonHelper.snapshotJson())
    }.onFailure {
        Log.e(TAG, "Failed to export training data", it)
    }

    /** Write just the exercise library, pretty-printed, to a user-picked location. */
    fun exportExerciseLibrary(destinationUri: Uri): Result<Unit> = runCatching {
        val prettyGson = GsonBuilder().setPrettyPrinting().create()
        writeText(destinationUri, prettyGson.toJson(jsonHelper.readTrainingData().exerciseLibrary))
    }.onFailure {
        Log.e(TAG, "Failed to export exercise library", it)
    }

    /** Write a pre-built text/markdown document (e.g. the AI export) to a user-picked location. */
    fun exportAiMarkdown(destinationUri: Uri, markdown: String): Result<Unit> = runCatching {
        writeText(destinationUri, markdown)
    }.onFailure {
        Log.e(TAG, "Failed to export AI markdown", it)
    }

    /** Export the full exercise catalog + plan spec to a user-picked .md file. */
    fun exportWorkoutPlanSpec(destinationUri: Uri): Result<Unit> = runCatching {
        val markdown = WorkoutPlanMarkdownHelper.buildSpecMarkdown(jsonHelper.readTrainingData())
        writeText(destinationUri, markdown)
    }.onFailure {
        Log.e(TAG, "Failed to export workout plan spec", it)
    }

    // ---------------------------------------------------------------- import

    /**
     * Replace all local training data with the contents of [sourceUri]. The outgoing data is
     * archived first — see [JsonHelper.replaceAllFromJson].
     */
    fun importTrainingData(sourceUri: Uri): Result<Unit> = runCatching {
        jsonHelper.replaceAllFromJson(readText(sourceUri))
    }.onFailure {
        Log.e(TAG, "Failed to import training data", it)
    }

    /**
     * Parse AI-generated plan(s) from a .md file. Returns the parsed plans together with any exercise
     * references whose IDs are not in the current library (so the caller can offer a remap). The plans
     * still contain those slots; call [WorkoutPlanMarkdownHelper.applyRemap] before persisting.
     */
    fun importWorkoutPlans(sourceUri: Uri): Result<WorkoutPlanMarkdownHelper.PlanImportResult> = runCatching {
        val markdown = readText(sourceUri)
        val trainingData = jsonHelper.readTrainingData()
        WorkoutPlanMarkdownHelper.parsePlansFromMarkdown(
            markdown, trainingData.exerciseLibrary, CircuitStore.circuits(trainingData)
        )
    }.onFailure {
        Log.e(TAG, "Failed to import workout plans", it)
    }

    // ------------------------------------------------------------------- io

    private fun writeText(destinationUri: Uri, contents: String) {
        resolver.openOutputStream(destinationUri)?.use { outputStream ->
            outputStream.write(contents.toByteArray(StandardCharsets.UTF_8))
            outputStream.flush()
        } ?: throw IOException("Unable to open destination")
    }

    private fun readText(sourceUri: Uri): String =
        resolver.openInputStream(sourceUri)
            ?.bufferedReader(StandardCharsets.UTF_8)
            ?.use { it.readText() }
            ?: throw IOException("Unable to read source")

    companion object {
        private const val TAG = "TrainingDataTransfer"
    }
}
