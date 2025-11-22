package com.lilfitness.helpers

import com.lilfitness.models.ExerciseEntry
import com.lilfitness.models.ExerciseLibraryItem
import com.lilfitness.models.Tier
import com.lilfitness.models.TrainingData
import com.lilfitness.models.UserLevel
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import kotlin.math.roundToInt

object ProgressionHelper {

    enum class ProgressionScheme {
        LINEAR_RPE,          // Adjust weight based on difficulty (Best for Tier 1)
        DOUBLE_PROGRESSION,  // Increase reps until target hit, then increase weight (Best for Tier 2/3)
        MAINTENANCE          // Keep same
    }

    data class ProgressionSettings(
        // --- 1. Progression Logic ---
        val userLevel: UserLevel = UserLevel.NOVICE,
        val lookbackCount: Int = 3,
        val roundTo: Float = 1.25f,
        val increaseStep: Float = 2.5f,
        val smallStep: Float = 1.25f,

        // --- 2. Volume Defaults (Restored for Compatibility) ---
        val heavySets: Int = 3,
        val heavyReps: Int = 5,
        val lightSets: Int = 4,
        val lightReps: Int = 10,

        // --- 3. Time Decay & Logic ---
        val timeDecayThresholds: List<Int> = listOf(14, 30, 60),
        val timeDecayMultipliers: List<Float> = listOf(0.95f, 0.90f, 0.85f),
        val deloadThreshold: Int = 3,
        val deloadRPEThreshold: Float = 9.0f,

        // --- 4. REST TIMER SETTINGS ---
        val restTimerEnabled: Boolean = true,
        val heavyRestSeconds: Int = 150,   // 2.5 minutes
        val lightRestSeconds: Int = 60,    // 1 minute
        val customRestSeconds: Int = 120,  // 2 minutes

        // RPE Timer Adjustments (Smart Rest)
        val rpeAdjustmentEnabled: Boolean = true,
        val rpeHighThreshold: Float = 9.0f,
        val rpeHighBonusSeconds: Int = 60,
        val rpeDeviationThreshold: Float = 1.0f,
        val rpePositiveAdjustmentSeconds: Int = 30,
        val rpeNegativeAdjustmentSeconds: Int = 15
    )

    data class ProgressionSuggestion(
        val exerciseId: Int,
        val exerciseName: String,
        val requestedType: String,

        // The core suggestion
        val proposedWeight: Float?,
        val proposedReps: Int?,

        // Context
        val reasoning: String,
        val humanExplanation: String,
        val isFirstTime: Boolean,
        val badge: String? = null,

        // Legacy / Extra info
        val lastWeight: Float? = null,
        val lastRpe: Float? = null,
        val daysSinceLastWorkout: Int? = null
    ) {
        // --- COMPATIBILITY GETTERS ---
        // These allow old code (like SelectExerciseActivity) to read the new data
        // without needing a full rewrite.
        val proposedHeavyWeight: Float?
            get() = if (requestedType == "heavy") proposedWeight else null

        val proposedLightWeight: Float?
            get() = if (requestedType == "light") proposedWeight else null

        val lastHeavyRpe: Float?
            get() = lastRpe
    }

    fun getSuggestion(
        exerciseId: Int,
        requestedType: String,
        trainingData: TrainingData,
        settings: ProgressionSettings = ProgressionSettings()
    ): ProgressionSuggestion {

        val exercise = trainingData.exerciseLibrary.find { it.id == exerciseId }
        val exerciseName = exercise?.name ?: "Unknown"

        // 1. DETERMINE SCHEME BASED ON TIER
        val scheme = when (exercise?.tier) {
            Tier.TIER_1 -> ProgressionScheme.LINEAR_RPE
            Tier.TIER_2, Tier.TIER_3 -> ProgressionScheme.DOUBLE_PROGRESSION
            else -> ProgressionScheme.LINEAR_RPE // Default / Fallback
        }

        // 2. FETCH HISTORY
        val history = extractHistory(exerciseId, requestedType, trainingData)
            .sortedBy { it.date }

        if (history.isEmpty()) {
            return createFirstTimeSuggestion(exerciseName, requestedType, scheme)
        }

        val lastSession = history.last()
        val daysSince = calculateDaysSince(lastSession.date)

        // 3. CALCULATE SUGGESTION
        return when (scheme) {
            ProgressionScheme.LINEAR_RPE -> calculateLinearProgression(lastSession, settings, exerciseName, requestedType, daysSince)
            ProgressionScheme.DOUBLE_PROGRESSION -> calculateDoubleProgression(lastSession, exerciseName, requestedType, settings, daysSince)
            ProgressionScheme.MAINTENANCE -> createMaintenanceSuggestion(lastSession, exerciseName, requestedType, daysSince)
        }
    }

    // ============================================================================================
    // STRATEGY 1: LINEAR RPE (For Main Lifts)
    // ============================================================================================
    private fun calculateLinearProgression(
        last: SessionData,
        settings: ProgressionSettings,
        name: String,
        type: String,
        daysSince: Int?
    ): ProgressionSuggestion {

        val safeDays = daysSince ?: 0
        var adjustment = 0f
        var badge: String? = null
        val reasoningParts = mutableListOf<String>()

        // 1. Check Time Decay
        val decayMult = calculateTimeDecay(safeDays, settings)
        if (decayMult < 1.0f) {
            val decayed = last.weight * decayMult
            adjustment = decayed - last.weight
            badge = "🕐 TIME DECAY"
            reasoningParts.add("$safeDays days off. -${((1 - decayMult) * 100).toInt()}% reset")
        }
        // 2. Check Failure
        else if (last.hadFailure) {
            adjustment = -settings.increaseStep
            badge = "⚠️ FAILED REPS"
            reasoningParts.add("Failed last time. -${settings.increaseStep}kg to reset")
        }
        // 3. Standard RPE Logic
        else {
            val rpe = last.rpe
            adjustment = when {
                rpe <= 7.0f -> settings.increaseStep     // Easy -> Add 2.5kg
                rpe <= 8.5f -> settings.smallStep        // Moderate -> Add 1.25kg
                rpe < 9.5f -> 0f                         // Hard -> Maintain
                else -> -settings.smallStep              // Grinding -> Back off slightly
            }
            reasoningParts.add("Last RPE ${String.format("%.1f", rpe)}")
        }

        val finalWeight = roundToIncrement(last.weight + adjustment, settings.roundTo)
        val finalReps = last.reps // Keep reps same for Linear

        return ProgressionSuggestion(
            exerciseId = -1,
            exerciseName = name,
            requestedType = type,
            proposedWeight = finalWeight,
            proposedReps = finalReps,
            reasoning = reasoningParts.joinToString(". "),
            humanExplanation = if (adjustment > 0) "Strong work! Add weight. 💪" else "Let's stabilize here.",
            isFirstTime = false,
            badge = badge,
            lastWeight = last.weight,
            lastRpe = last.rpe,
            daysSinceLastWorkout = daysSince
        )
    }

    // ============================================================================================
    // STRATEGY 2: DOUBLE PROGRESSION (For Accessories)
    // ============================================================================================
    private fun calculateDoubleProgression(
        last: SessionData,
        name: String,
        type: String,
        settings: ProgressionSettings,
        daysSince: Int?
    ): ProgressionSuggestion {

        // Use settings reps as target baseline
        val (minReps, maxReps) = if (type == "heavy") {
            (settings.heavyReps - 2) to (settings.heavyReps + 2)
        } else {
            settings.lightReps to (settings.lightReps + 5)
        }

        var newWeight = last.weight
        var newReps = last.reps
        var badge: String? = null
        val reasoning: String

        if (last.hadFailure) {
            reasoning = "Missed reps last time. Retry same weight."
            badge = "🔁 RETRY"
        } else if (last.reps >= maxReps) {
            // Hit Top Range -> LEVEL UP
            newWeight = roundToIncrement(last.weight + settings.smallStep, settings.roundTo)
            newReps = minReps
            reasoning = "Hit $maxReps reps! Increasing weight, resetting to $minReps reps."
            badge = "🚀 LEVEL UP"
        } else {
            // In Range -> Add Reps
            newReps = last.reps + 1
            reasoning = "Build volume. Aim for $newReps reps today."
            badge = "➕ ADD REP"
        }

        return ProgressionSuggestion(
            exerciseId = -1,
            exerciseName = name,
            requestedType = type,
            proposedWeight = newWeight,
            proposedReps = newReps,
            reasoning = reasoning,
            humanExplanation = if (newWeight > last.weight) "You earned a weight increase!" else "Focus on getting that extra rep.",
            isFirstTime = false,
            badge = badge,
            lastWeight = last.weight,
            lastRpe = last.rpe,
            daysSinceLastWorkout = daysSince
        )
    }

    private fun createMaintenanceSuggestion(
        last: SessionData,
        name: String,
        type: String,
        daysSince: Int?
    ): ProgressionSuggestion {
        return ProgressionSuggestion(
            exerciseId = -1, exerciseName = name, requestedType = type,
            proposedWeight = last.weight, proposedReps = last.reps,
            reasoning = "Maintenance Mode", humanExplanation = "Just get the work done.",
            isFirstTime = false, lastWeight = last.weight, daysSinceLastWorkout = daysSince
        )
    }

    // ============================================================================================
    // HELPERS
    // ============================================================================================

    private fun createFirstTimeSuggestion(name: String, type: String, scheme: ProgressionScheme): ProgressionSuggestion {
        val (reps, desc) = when (scheme) {
            ProgressionScheme.LINEAR_RPE -> 5 to "Start light. Aim for 5 clean reps."
            else -> 12 to "Start light. Aim for 12 controlled reps."
        }

        return ProgressionSuggestion(
            exerciseId = -1, exerciseName = name, requestedType = type,
            proposedWeight = null, proposedReps = reps,
            reasoning = "New Exercise",
            humanExplanation = "First time! $desc",
            isFirstTime = true,
            daysSinceLastWorkout = null
        )
    }

    private data class SessionData(
        val date: String,
        val weight: Float,
        val reps: Int,
        val rpe: Float,
        val hadFailure: Boolean
    )

    private fun extractHistory(id: Int, type: String, data: TrainingData): List<SessionData> {
        return data.trainings.flatMap { session ->
            session.exercises
                .filter { it.exerciseId == id && (it.workoutType == type || it.workoutType == null) }
                .map { entry ->
                    SessionData(
                        date = session.date,
                        weight = entry.kg,
                        reps = entry.reps,
                        rpe = entry.rpe ?: 8.0f,
                        hadFailure = entry.completed == false
                    )
                }
        }
    }

    private fun calculateDaysSince(dateStr: String): Int? {
        return try {
            val format = SimpleDateFormat("yyyy/MM/dd", Locale.US)
            val date = format.parse(dateStr) ?: return null
            val diff = Date().time - date.time
            (diff / (1000 * 60 * 60 * 24)).toInt()
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateTimeDecay(days: Int, settings: ProgressionSettings): Float {
        for (i in settings.timeDecayThresholds.indices.reversed()) {
            if (days >= settings.timeDecayThresholds[i]) return settings.timeDecayMultipliers[i]
        }
        return 1.0f
    }

    private fun roundToIncrement(valIn: Float, inc: Float): Float {
        return (valIn / inc).roundToInt() * inc
    }

    // Public Helper for RPE Slider defaults
    fun suggestRpe(userLevel: UserLevel, type: String): Float {
        return if (type == "heavy") {
            if (userLevel == UserLevel.NOVICE) 8.0f else 8.5f
        } else {
            if (userLevel == UserLevel.NOVICE) 7.0f else 7.5f
        }
    }
}