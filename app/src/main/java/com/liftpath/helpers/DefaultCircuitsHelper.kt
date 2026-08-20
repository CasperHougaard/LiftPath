package com.liftpath.helpers

import com.liftpath.models.CircuitItem
import com.liftpath.models.CircuitTemplate
import com.liftpath.models.TrainingData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The 6 starter circuits, seeded once their exercises exist in the user's library.
 *
 * Stations are resolved **by normalized exercise name, not id** — [CatalogMergeHelper.applyMerge]
 * assigns a new catalog exercise `maxId + 1`, not its catalog id, so a hardcoded id here would
 * point at the wrong exercise on this device. [seedIfNeeded] is idempotent (tracked by
 * [CircuitTemplate.defaultKey]) and safe to call on every read: a circuit missing most of its
 * stations is skipped rather than added half-empty, and is retried the next time this runs (e.g.
 * once the user accepts the catalog update that adds its bodyweight/band exercises).
 */
object DefaultCircuitsHelper {

    private data class SeedItem(val exerciseName: String, val reps: String? = null, val durationSeconds: Int? = null)

    private data class SeedCircuit(
        val defaultKey: String,
        val name: String,
        val suggestedRounds: Int,
        val restSeconds: Int,
        val items: List<SeedItem>
    )

    private val SEED_CIRCUITS = listOf(
        SeedCircuit(
            defaultKey = "lower_lactate",
            name = "Lower Lactate",
            suggestedRounds = 3,
            restSeconds = 90,
            items = listOf(
                SeedItem("Hip Thrust (Barbell)", reps = "12"),
                SeedItem("Split Squat (Barbell)", reps = "10"),
                SeedItem("Wall Sit", durationSeconds = 45),
                SeedItem("Single-Leg Glute Bridge", reps = "12"),
                SeedItem("Side-Lying Leg Raise", reps = "15")
            )
        ),
        SeedCircuit(
            defaultKey = "glute_band_burner",
            name = "Glute Band Burner",
            suggestedRounds = 4,
            restSeconds = 60,
            items = listOf(
                SeedItem("Standing Hip Abduction (Band)", reps = "15"),
                SeedItem("Glute Kickback (Band)", reps = "15"),
                SeedItem("Lateral Band Walk", reps = "10"),
                SeedItem("Single-Leg Glute Bridge", reps = "12")
            )
        ),
        SeedCircuit(
            defaultKey = "quad_burn",
            name = "Quad Burn",
            suggestedRounds = 3,
            restSeconds = 75,
            items = listOf(
                SeedItem("Bodyweight Squat", reps = "15"),
                SeedItem("Reverse Lunge (Bodyweight)", reps = "10"),
                SeedItem("Wall Sit", durationSeconds = 60),
                SeedItem("Calf Raise (Bodyweight)", reps = "20")
            )
        ),
        SeedCircuit(
            defaultKey = "full_body_metcon",
            name = "Full-Body Metcon",
            suggestedRounds = 4,
            restSeconds = 90,
            items = listOf(
                SeedItem("Goblet Squat", reps = "12"),
                SeedItem("Push Up", reps = "10"),
                SeedItem("Kettlebell Swing", reps = "15"),
                SeedItem("Walking Lunges", reps = "10"),
                SeedItem("Plank", durationSeconds = 45)
            )
        ),
        SeedCircuit(
            defaultKey = "core_circuit",
            name = "Core Circuit",
            suggestedRounds = 3,
            restSeconds = 60,
            items = listOf(
                SeedItem("Plank", durationSeconds = 45),
                SeedItem("Side Plank", durationSeconds = 30),
                SeedItem("Hollow Hold", durationSeconds = 30),
                SeedItem("Russian Twist", reps = "20")
            )
        ),
        SeedCircuit(
            defaultKey = "upper_pump",
            name = "Upper Pump",
            suggestedRounds = 3,
            restSeconds = 75,
            items = listOf(
                SeedItem("Push Up", reps = "12"),
                SeedItem("Dumbbell Row", reps = "10"),
                SeedItem("Lateral Raise (Dumbbell)", reps = "12"),
                SeedItem("Biceps Curl (Dumbbell)", reps = "12")
            )
        )
    )

    /** Adds any starter circuit not yet seeded whose stations mostly resolve against [data]'s library. */
    fun seedIfNeeded(data: TrainingData) {
        val alreadySeeded = data.circuits.orEmpty().mapNotNull { it.defaultKey }.toSet()
        if (SEED_CIRCUITS.all { it.defaultKey in alreadySeeded }) return

        val exerciseByName = data.exerciseLibrary.associateBy { CatalogMergeHelper.normalizeExerciseName(it.name) }
        val today = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())

        for (seed in SEED_CIRCUITS) {
            if (seed.defaultKey in alreadySeeded) continue

            val items = seed.items.mapNotNull { item ->
                val exercise = exerciseByName[CatalogMergeHelper.normalizeExerciseName(item.exerciseName)]
                    ?: return@mapNotNull null
                CircuitItem(
                    exerciseId = exercise.id,
                    targetReps = item.reps,
                    targetDurationSeconds = item.durationSeconds
                )
            }
            // Most of the circuit is missing its exercises — wait for a later read to retry.
            if (items.size < 2) continue

            CircuitStore.upsert(
                data,
                CircuitTemplate(
                    name = seed.name,
                    suggestedRounds = seed.suggestedRounds,
                    restBetweenRoundsSeconds = seed.restSeconds,
                    items = items,
                    createdDate = today,
                    defaultKey = seed.defaultKey
                )
            )
        }
    }
}
