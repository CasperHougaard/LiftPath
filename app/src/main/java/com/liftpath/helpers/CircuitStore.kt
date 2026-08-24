package com.liftpath.helpers

import com.liftpath.models.CircuitInstance
import com.liftpath.models.CircuitItem
import com.liftpath.models.CircuitLog
import com.liftpath.models.CircuitTemplate
import com.liftpath.models.ExerciseEntry
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.GroupType
import com.liftpath.models.Laterality
import com.liftpath.models.PlanExerciseSlot
import com.liftpath.models.SetIntent
import com.liftpath.models.TrainingData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything that turns a stored [CircuitTemplate] into a live [CircuitInstance] and a finished
 * round into [ExerciseEntry] rows.
 *
 * Pure functions over data — no `Context`, so the round-to-sets translation (the part that must not
 * drift) is unit-testable. Persistence goes through [JsonHelper] at the call sites.
 */
object CircuitStore {

    /**
     * Circuit sets are logged as FLUSH.
     *
     * The intent drives rest defaults and, more importantly, which past sessions a set is compared
     * against. Metabolic circuit work has nothing to say about a heavy triple of the same lift, so
     * filing it under the app's high-rep/low-load intent keeps both trends honest.
     */
    val CIRCUIT_INTENT = SetIntent.FLUSH

    private const val DATE_PATTERN = "yyyy/MM/dd"

    // ───────────────────────────────────────────────────────── stored templates

    /** The saved circuits, never null — [TrainingData.circuits] is nullable for Gson's sake. */
    fun circuits(data: TrainingData): List<CircuitTemplate> = data.circuits ?: emptyList()

    fun find(data: TrainingData, circuitId: String?): CircuitTemplate? =
        circuitId?.let { id -> circuits(data).find { it.id == id } }

    /** Case-insensitive name lookup, used by markdown import and by default-circuit seeding. */
    fun findByName(data: TrainingData, name: String): CircuitTemplate? {
        val key = name.trim().lowercase()
        return circuits(data).find { it.name.trim().lowercase() == key }
    }

    /** Adds or replaces [template] by id. Mutates [data]; the caller writes it back. */
    fun upsert(data: TrainingData, template: CircuitTemplate) {
        val list = data.circuits ?: mutableListOf<CircuitTemplate>().also { data.circuits = it }
        val index = list.indexOfFirst { it.id == template.id }
        if (index >= 0) list[index] = template else list.add(template)
    }

    fun delete(data: TrainingData, circuitId: String) {
        data.circuits?.removeAll { it.id == circuitId }
    }

    fun today(): String = SimpleDateFormat(DATE_PATTERN, Locale.US).format(Date())

    // ───────────────────────────────────────────────────────── template → session

    /** Snapshots [template] into a session instance, carrying its own suggestion and rest. */
    fun templateToInstance(template: CircuitTemplate): CircuitInstance = CircuitInstance(
        templateId = template.id,
        name = template.name,
        suggestedRounds = template.suggestedRounds,
        restBetweenRoundsSeconds = template.restBetweenRoundsSeconds,
        items = template.items
    )

    /**
     * Snapshots [template] as reached through a plan [slot].
     *
     * The slot's `setsTarget` *replaces* the template's round suggestion rather than falling back to
     * it: a plan that leaves the field blank is saying "run this circuit as long as you like", which
     * is a real instruction, not a missing one. Rest does fall back — a blank rest field is nothing
     * but an omission.
     */
    fun templateToInstance(
        template: CircuitTemplate,
        slot: PlanExerciseSlot
    ): CircuitInstance = CircuitInstance(
        templateId = template.id,
        name = template.name,
        suggestedRounds = slot.setsTarget,
        restBetweenRoundsSeconds = slot.restTimeSeconds?.takeIf { it > 0 }
            ?: template.restBetweenRoundsSeconds,
        items = template.items
    )

    fun instanceToLog(instance: CircuitInstance): CircuitLog = CircuitLog(
        instanceId = instance.instanceId,
        templateId = instance.templateId,
        name = instance.name,
        roundsCompleted = instance.completedRounds,
        roundWorkSeconds = instance.roundWorkSeconds,
        restBetweenRoundsSeconds = instance.restBetweenRoundsSeconds
    )

    // ───────────────────────────────────────────────────────── round → sets

    /** What the user entered for one station in one round. */
    data class StationInput(
        val itemId: String,
        val exerciseId: Int,
        /** External load, or the *added* load for a bodyweight station. Null when not applicable. */
        val kg: Float? = null,
        val reps: Int? = null,
        val durationSeconds: Int? = null,
        val skipped: Boolean = false
    )

    /**
     * One [ExerciseEntry] per station the user actually did, all sharing the circuit's group id.
     *
     * `setNumber` is the round number, unbounded — a circuit's fifth round is set 5, whatever the
     * template suggested. `kg` stays "total effective load" for a bodyweight station, matching the
     * contract documented on [ExerciseEntry.kg].
     */
    fun entriesForRound(
        instance: CircuitInstance,
        round: Int,
        inputs: List<StationInput>,
        library: List<ExerciseLibraryItem>,
        bodyweightKg: Float?,
        workoutType: String? = null
    ): List<ExerciseEntry> = inputs.filterNot { it.skipped }.mapNotNull { input ->
        val exercise = library.find { it.id == input.exerciseId } ?: return@mapNotNull null
        val isBodyweight = exercise.isBodyweight
        val bw = if (isBodyweight) bodyweightKg else null
        val added = input.kg ?: 0f

        ExerciseEntry(
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            setNumber = round,
            kg = if (bw != null) bw + added else added,
            reps = input.reps ?: 0,
            workoutType = workoutType,
            explicitIntent = CIRCUIT_INTENT,
            groupId = instance.instanceId,
            groupType = GroupType.CIRCUIT,
            bodyweightKg = bw,
            addedKg = if (bw != null) added else null,
            // Only set for a genuinely timed station: a non-null durationSeconds is what makes
            // ExerciseEntry.isTimedEntry() true, and a rep set must not claim to be a hold.
            durationSeconds = input.durationSeconds?.takeIf { it > 0 && exercise.isTimeBased }
        )
    }

    /**
     * The inputs to show pre-filled for [round]: what was done in the previous round of this same
     * circuit, else the item's own target. `entries` is the session's set list so far.
     */
    fun prefillForRound(
        instance: CircuitInstance,
        round: Int,
        entries: List<ExerciseEntry>,
        library: List<ExerciseLibraryItem>
    ): List<StationInput> {
        val previous = entries
            .filter { it.groupId == instance.instanceId && it.setNumber < round }
            .groupBy { it.exerciseId }
            .mapValues { (_, sets) -> sets.maxByOrNull { it.setNumber } }

        return instance.items.map { item ->
            val exercise = library.find { it.id == item.exerciseId }
            val last = previous[item.exerciseId]
            StationInput(
                itemId = item.id,
                exerciseId = item.exerciseId,
                kg = when {
                    last != null -> if (last.isBodyweightEntry()) last.addedKg else last.kg
                    else -> item.targetKg
                },
                reps = last?.reps?.takeIf { it > 0 } ?: item.targetReps?.let(::leadingInt),
                durationSeconds = when {
                    last?.durationSeconds != null -> last.durationSeconds
                    exercise?.isTimeBased == true -> item.targetDurationSeconds
                    else -> null
                }
            )
        }
    }

    /** "12-15" → 12, "10+" → 10, "AMRAP" → null. Same tolerance as the plan reps field. */
    fun leadingInt(raw: String): Int? =
        raw.trimStart().takeWhile { it.isDigit() }.toIntOrNull()

    // ───────────────────────────────────────────────────────── display

    /**
     * `60kg × 12` · `×12 / side` · `0:45` — what a station is aiming for, from the template.
     *
     * A bodyweight station's [CircuitItem.targetKg] is *added* load, not the total, so it renders
     * signed (`+10kg` / `−5kg` for assisted) — otherwise it reads exactly like a weighted
     * exercise's total load and the two are indistinguishable.
     */
    fun formatTarget(item: CircuitItem, exercise: ExerciseLibraryItem?): String {
        val parts = mutableListOf<String>()
        item.targetKg?.takeIf { it != 0f }?.let { kg ->
            val label = if (exercise?.isBodyweight == true) {
                val sign = if (kg > 0f) "+" else "−"
                "$sign${SetFormatter.trimNum(kotlin.math.abs(kg))}kg"
            } else {
                "${SetFormatter.trimNum(kg)}kg"
            }
            parts.add("$label ×")
        }
        when {
            exercise?.isTimeBased == true && item.targetDurationSeconds != null ->
                return RestTimerHelper.formatDuration(item.targetDurationSeconds)
            !item.targetReps.isNullOrBlank() -> parts.add(
                if (parts.isEmpty()) "×${item.targetReps}" else item.targetReps
            )
            item.targetDurationSeconds != null ->
                return RestTimerHelper.formatDuration(item.targetDurationSeconds)
        }
        if (parts.isEmpty()) return ""
        val suffix = if (exercise?.laterality == Laterality.UNILATERAL) " / side" else ""
        return parts.joinToString(" ") + suffix
    }

    /** `~3 rounds · 90s rest`, or just the rest when the circuit carries no round suggestion. */
    fun formatSummary(suggestedRounds: Int?, restSeconds: Int): String {
        val rest = "${restSeconds}s rest"
        return if (suggestedRounds != null) "~$suggestedRounds rounds · $rest" else rest
    }
}
