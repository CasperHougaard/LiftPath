package com.lilfitness.helpers

import com.lilfitness.models.ExerciseEntry
import com.lilfitness.models.TrainingData
import com.lilfitness.models.UserLevel
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import kotlin.math.roundToInt
import kotlin.math.abs

object ProgressionHelper {

    data class ProgressionSettings(
        val userLevel: UserLevel = UserLevel.NOVICE,
        val lookbackCount: Int = 3,
        val roundTo: Float = 1.25f,
        val lightPercent: Float = 0.80f,
        val increaseStep: Float = 2.5f,
        val smallStep: Float = 1.25f,
        val minWeight: Float = 0f,
        val maxIncreasePerSession: Float = 5.0f,
        val maxDecreasePerSession: Float = 10.0f,
        val timeDecayThresholds: List<Int> = listOf(14, 30, 60),
        val timeDecayMultipliers: List<Float> = listOf(0.95f, 0.90f, 0.85f),
        val deloadThreshold: Int = 3,
        val deloadRPEThreshold: Float = 9.0f,
        val deloadPercent: Float = 0.70f,
        val plateauSessionCount: Int = 4,
        val plateauRPEMax: Float = 8.0f,
        val plateauBoost: Float = 1.5f,
        // Recommended sets and reps
        val heavySets: Int = 3,
        val heavyReps: Int = 5,
        val lightSets: Int = 5,
        val lightReps: Int = 10,
        // Rest timer settings
        val restTimerEnabled: Boolean = true,
        val heavyRestSeconds: Int = 150,   // 2.5 minutes
        val lightRestSeconds: Int = 60,    // 1 minute
        val customRestSeconds: Int = 120,  // 2 minutes
        val rpeAdjustmentEnabled: Boolean = true,
        val rpeHighThreshold: Float = 9.0f,  // RPE >= 9.0 adds extra rest
        val rpeHighBonusSeconds: Int = 60,    // Add 1 minute for high RPE
        // RPE deviation adjustment settings
        val rpeDeviationThreshold: Float = 1.0f,  // RPE difference threshold to trigger adjustment
        val rpePositiveAdjustmentSeconds: Int = 30,  // Add seconds when RPE is higher than suggested
        val rpeNegativeAdjustmentSeconds: Int = 15   // Subtract seconds when RPE is lower than suggested
    )

    data class ProgressionSuggestion(
        val exerciseId: Int,
        val exerciseName: String,
        val requestedType: String,
        val lastHeavyWeight: Float?,
        val lastHeavyDate: String?,
        val lastHeavyRpe: Float?,
        val proposedHeavyWeight: Float?,
        val proposedLightWeight: Float?,
        val lookbackCountUsed: Int,
        val avgRpe: Float?,
        val trendDelta: Float,
        val reasoning: String,
        val humanExplanation: String,
        val isFirstTime: Boolean,
        val badge: String? = null,
        val confidenceLevel: String = "medium",
        val isDeloadWeek: Boolean = false,
        val isPlateauDetected: Boolean = false,
        val daysSinceLastWorkout: Int? = null
    )

    fun getSuggestion(
        exerciseId: Int,
        requestedType: String,
        trainingData: TrainingData,
        settings: ProgressionSettings = ProgressionSettings(),
        userLevel: UserLevel = settings.userLevel
    ): ProgressionSuggestion {

        val exerciseName = trainingData.exerciseLibrary
            .find { it.id == exerciseId }?.name ?: "Unknown"

        // Extract all "heavy" workouts for this exercise
        val heavySets = extractHeavySets(exerciseId, trainingData)

        if (heavySets.isEmpty()) {
            return createFirstTimeSuggestion(exerciseId, exerciseName, requestedType)
        }

        // Group by session and get max weight + normalized RPE per session
        val sessions = heavySets
            .groupBy { it.date }
            .map { (date, sets) ->
                val maxWeight = sets.maxOf { it.entry.kg }
                val normalizedRpe = calculateSessionRpe(sets.map { it.entry })
                val hadFailure = sets.any { it.entry.completed == false }
                SessionData(date, maxWeight, normalizedRpe, hadFailure)
            }
            .sortedBy { it.date }
            .takeLast(settings.lookbackCount)

        val lastSession = sessions.last()
        val lookbackCountUsed = sessions.size

        // Calculate days since last workout
        val daysSince = calculateDaysSince(lastSession.date)

        // Calculate confidence level
        val confidence = calculateConfidence(lookbackCountUsed, daysSince)

        // Calculate average RPE
        val avgRpe = sessions.map { it.rpe }.average().toFloat()

        // Calculate trend
        val trendDelta = if (sessions.size > 1) {
            val oldest = sessions.first().weight
            val newest = sessions.last().weight
            (newest - oldest) / (sessions.size - 1)
        } else {
            0f
        }

        // Check for deload need (3+ consecutive high RPE sessions)
        val needsDeload = detectDeloadNeed(sessions, settings)

        // Check for plateau (same weight, low RPE for multiple sessions)
        val isPlateaued = detectPlateau(sessions, settings)

        // Check for recent failure
        val hadRecentFailure = sessions.takeLast(2).any { it.hadFailure }

        // Calculate time decay multiplier
        val timeDecayMultiplier = calculateTimeDecay(daysSince, settings)

        // Determine adjustment based on priority order
        var adjustment = 0f
        var badge: String? = null
        var isDeloadWeek = false
        var reasoningParts = mutableListOf<String>()

        when {
            needsDeload -> {
                // Priority 1: Deload
                val deloadWeight = lastSession.weight * settings.deloadPercent
                adjustment = deloadWeight - lastSession.weight
                badge = "⚠️ DELOAD WEEK"
                isDeloadWeek = true
                reasoningParts.add("Deload triggered (3+ hard sessions at RPE ${String.format("%.1f", avgRpe)})")
                reasoningParts.add("Reducing to 70% of last weight for recovery")
            }
            hadRecentFailure -> {
                // Priority 2: Failure detection
                adjustment = -settings.increaseStep
                badge = "⚠️ FAILED REPS"
                reasoningParts.add("Recent incomplete sets detected")
                reasoningParts.add("Reducing weight by ${settings.increaseStep}kg for next attempt")
            }
            timeDecayMultiplier < 1.0f && !needsDeload -> {
                // Priority 3: Time decay
                val decayedWeight = lastSession.weight * timeDecayMultiplier
                adjustment = decayedWeight - lastSession.weight
                val decayPercent = ((1.0f - timeDecayMultiplier) * 100).toInt()
                badge = "🕐 TIME DECAY"
                reasoningParts.add("${daysSince} days since last workout")
                reasoningParts.add("Applied ${decayPercent}% time decay")
            }
            isPlateaued -> {
                // Priority 4: Plateau boost
                val baseIncrease = if (avgRpe <= 7.0f) settings.increaseStep else settings.smallStep
                adjustment = baseIncrease * settings.plateauBoost
                badge = "📈 PLATEAU BOOST"
                reasoningParts.add("Plateau detected (${settings.plateauSessionCount} sessions at ${lastSession.weight}kg)")
                reasoningParts.add("Applying ${settings.plateauBoost}x boost to break through")
            }
            else -> {
                // Priority 5: Normal RPE progression
                val rpeAdjustment = when {
                    avgRpe <= 7.0f -> settings.increaseStep      // Easy, big jump
                    avgRpe <= 8.5f -> settings.smallStep         // Moderate, small jump
                    avgRpe < 9.5f -> 0f                          // Hard, maintain
                    else -> -settings.increaseStep               // Too hard, reduce
                }

                // Priority 6: Trend adjustment
                val trendAdjustment = when {
                    trendDelta > 0 -> settings.smallStep   // Positive trend, add small bump
                    trendDelta < 0 -> -settings.smallStep  // Negative trend, reduce
                    else -> 0f
                }

                adjustment = rpeAdjustment + trendAdjustment
                
                reasoningParts.add("RPE adjustment: ${if (rpeAdjustment >= 0) "+" else ""}${rpeAdjustment}kg")
                reasoningParts.add("Trend adjustment: ${if (trendAdjustment >= 0) "+" else ""}${trendAdjustment}kg")
            }
        }

        // Priority 7: Apply safeguards (caps)
        val cappedAdjustment = adjustment.coerceIn(
            -settings.maxDecreasePerSession,
            settings.maxIncreasePerSession
        )

        if (cappedAdjustment != adjustment) {
            val limit = if (adjustment > 0) settings.maxIncreasePerSession else settings.maxDecreasePerSession
            reasoningParts.add("Capped at ±${limit}kg for safety")
        }

        // Calculate proposed heavy weight
        val rawHeavy = lastSession.weight + cappedAdjustment
        val proposedHeavy = roundToIncrement(
            maxOf(rawHeavy, settings.minWeight),
            settings.roundTo
        )

        // Calculate proposed light weight
        // IF NOVICE: Calculate 80% of heavy weight
        // IF INTERMEDIATE: Return null (intermediates use different exercises for light days)
        val proposedLight = if (userLevel == UserLevel.NOVICE) {
            roundToIncrement(
                proposedHeavy * settings.lightPercent,
                settings.roundTo
            )
        } else {
            null
        }

        // Add confidence badge
        val confidenceBadge = when (confidence) {
            "high" -> "✅"
            "medium" -> "⚡"
            "low" -> "🤔"
            else -> ""
        }

        // Build reasoning
        val reasoning = buildString {
            append("Last heavy: ${lastSession.weight}kg (RPE ${String.format("%.1f", avgRpe)}). ")
            append(reasoningParts.joinToString(". "))
            append(". Total: ${if (cappedAdjustment >= 0) "+" else ""}${cappedAdjustment}kg.")
        }

        val humanExplanation = when (requestedType) {
            "heavy" -> {
                buildString {
                    when {
                        needsDeload -> {
                            append("⚠️ DELOAD WEEK recommended!\n")
                            append("You've had ${settings.deloadThreshold}+ hard sessions (RPE ${String.format("%.1f", avgRpe)}).\n")
                            append("Take it easy at ${proposedHeavy}kg (70% of last) to recover. ${confidenceBadge}")
                        }
                        hadRecentFailure -> {
                            append("Recent incomplete sets detected.\n")
                            append("Reducing to ${proposedHeavy}kg to build back up. ${confidenceBadge}")
                        }
                        daysSince != null && daysSince >= settings.timeDecayThresholds[0] -> {
                            append("🕐 It's been ${daysSince} days since your last ${exerciseName} workout.\n")
                            val decayPercent = ((1.0f - timeDecayMultiplier) * 100).toInt()
                            append("Adjusted down ${decayPercent}% to ${proposedHeavy}kg for safety. ${confidenceBadge}")
                        }
                        isPlateaued -> {
                            append("📈 Plateau detected!\n")
                            append("You've been at ${lastSession.weight}kg for ${settings.plateauSessionCount} sessions with RPE ${String.format("%.1f", avgRpe)}.\n")
                            append("Time for a bigger push to ${proposedHeavy}kg! ${confidenceBadge}")
                        }
                        avgRpe <= 7.0f -> {
                            append("Last session felt easy (RPE ${String.format("%.1f", avgRpe)}).\n")
                            append("Ready for a bigger jump to ${proposedHeavy}kg! 💪 ${confidenceBadge}")
                        }
                        avgRpe <= 8.5f -> {
                            append("Good progression (RPE ${String.format("%.1f", avgRpe)}).\n")
                            append("Suggesting ${proposedHeavy}kg for next heavy day. ${confidenceBadge}")
                        }
                        avgRpe < 9.5f -> {
                            append("Sessions have been challenging (RPE ${String.format("%.1f", avgRpe)}).\n")
                            append("Let's maintain at ${proposedHeavy}kg. ${confidenceBadge}")
                        }
                        else -> {
                            append("Recent sessions were very hard (RPE ${String.format("%.1f", avgRpe)}).\n")
                            append("Consider dropping to ${proposedHeavy}kg to recover. ${confidenceBadge}")
                        }
                    }
                }
            }
            "light" -> {
                if (userLevel == UserLevel.INTERMEDIATE) {
                    "Check history for this specific variation. ${confidenceBadge}"
                } else {
                    "Based on ${proposedHeavy}kg heavy weight. Suggesting ${proposedLight}kg for light day (80%). ${confidenceBadge}"
                }
            }
            else -> "Custom workout - no suggestion."
        }

        return ProgressionSuggestion(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            requestedType = requestedType,
            lastHeavyWeight = lastSession.weight,
            lastHeavyDate = lastSession.date,
            lastHeavyRpe = avgRpe,
            proposedHeavyWeight = proposedHeavy,
            proposedLightWeight = proposedLight,
            lookbackCountUsed = lookbackCountUsed,
            avgRpe = avgRpe,
            trendDelta = trendDelta,
            reasoning = reasoning,
            humanExplanation = humanExplanation,
            isFirstTime = false,
            badge = badge,
            confidenceLevel = confidence,
            isDeloadWeek = isDeloadWeek,
            isPlateauDetected = isPlateaued,
            daysSinceLastWorkout = daysSince
        )
    }

    private fun detectDeloadNeed(sessions: List<SessionData>, settings: ProgressionSettings): Boolean {
        if (sessions.size < settings.deloadThreshold) return false
        
        // Take last N sessions
        val recentSessions = sessions.takeLast(settings.deloadThreshold)
        
        // Check if all are high RPE
        return recentSessions.all { it.rpe >= settings.deloadRPEThreshold }
    }

    private fun detectPlateau(sessions: List<SessionData>, settings: ProgressionSettings): Boolean {
        if (sessions.size < settings.plateauSessionCount) return false
        
        // Take last N sessions
        val recentSessions = sessions.takeLast(settings.plateauSessionCount)
        
        // Check if all same weight
        val weights = recentSessions.map { it.weight }.distinct()
        if (weights.size != 1) return false
        
        // Check if RPE is low (weight is easy)
        val avgRpe = recentSessions.map { it.rpe }.average().toFloat()
        if (avgRpe > settings.plateauRPEMax) return false
        
        // Check no failures
        return !recentSessions.any { it.hadFailure }
    }

    private fun calculateTimeDecay(daysSince: Int?, settings: ProgressionSettings): Float {
        if (daysSince == null) return 1.0f
        
        // Find appropriate multiplier based on days
        for (i in settings.timeDecayThresholds.indices.reversed()) {
            if (daysSince >= settings.timeDecayThresholds[i]) {
                return settings.timeDecayMultipliers[i]
            }
        }
        
        return 1.0f  // No decay
    }

    private fun calculateConfidence(sessionCount: Int, daysSince: Int?): String {
        return when {
            sessionCount >= 5 && (daysSince == null || daysSince < 14) -> "high"
            sessionCount >= 3 && (daysSince == null || daysSince < 30) -> "medium"
            else -> "low"
        }
    }

    private fun calculateDaysSince(lastDateStr: String): Int? {
        return try {
            val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US)
            val lastDate = dateFormat.parse(lastDateStr) ?: return null
            val today = Date()
            val diffMillis = today.time - lastDate.time
            val days = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
            maxOf(0, days)  // Don't return negative days
        } catch (e: Exception) {
            null  // Parse error, continue without time decay
        }
    }

    // Calculate session RPE (handle null RPE by using completed flag)
    private fun calculateSessionRpe(sets: List<ExerciseEntry>): Float {
        val rpes = sets.map { set ->
            when {
                set.rpe != null -> set.rpe
                set.completed == false -> 9.5f  // Incomplete = hard
                set.completed == true -> 8.0f   // Completed but no RPE = moderate
                else -> 8.0f  // Legacy data (no RPE, no completed) = moderate
            }
        }
        return rpes.average().toFloat()
    }

    private fun extractHeavySets(exerciseId: Int, trainingData: TrainingData): List<SetWithDate> {
        return trainingData.trainings.flatMap { session ->
            session.exercises
                .filter {
                    it.exerciseId == exerciseId &&
                        (it.workoutType == "heavy" || it.workoutType == null)
                }
                .map { SetWithDate(it, session.date) }
        }
    }

    private fun roundToIncrement(value: Float, increment: Float): Float {
        return (value / increment).roundToInt() * increment
    }

    /**
     * Suggests an RPE value based on user level and workout type.
     * 
     * @param userLevel The user's training level (NOVICE or INTERMEDIATE)
     * @param workoutType The workout type ("heavy", "light", or "custom")
     * @return Suggested RPE value (6.0-10.0), or null if custom workout
     */
    fun suggestRpe(userLevel: UserLevel, workoutType: String): Float? {
        return when (workoutType) {
            "heavy" -> {
                when (userLevel) {
                    UserLevel.NOVICE -> 8.0f      // Novice: RPE 8 (leaving 2 reps in tank)
                    UserLevel.INTERMEDIATE -> 8.5f // Intermediate: RPE 8.5 (leaving 1-2 reps in tank)
                }
            }
            "light" -> {
                when (userLevel) {
                    UserLevel.NOVICE -> 7.0f      // Novice: RPE 7 (leaving 3 reps in tank)
                    UserLevel.INTERMEDIATE -> 7.5f // Intermediate: RPE 7.5 (leaving 2-3 reps in tank)
                }
            }
            else -> null // Custom workout - no suggestion
        }
    }

    private fun createFirstTimeSuggestion(
        exerciseId: Int,
        exerciseName: String,
        requestedType: String
    ): ProgressionSuggestion {
        val explanation = when (requestedType) {
            "heavy" -> "First time doing $exerciseName! Start with a weight you can do for 3 sets of 5 reps at RPE 7-8. 🤔"
            "light" -> "First time doing $exerciseName! Start with a weight you can do for 5 sets of 10 reps at RPE 7-8. 🤔"
            else -> "First time doing $exerciseName! Start with a comfortable weight. 🤔"
        }

        return ProgressionSuggestion(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            requestedType = requestedType,
            lastHeavyWeight = null,
            lastHeavyDate = null,
            lastHeavyRpe = null,
            proposedHeavyWeight = null,
            proposedLightWeight = null,
            lookbackCountUsed = 0,
            avgRpe = null,
            trendDelta = 0f,
            reasoning = "No history",
            humanExplanation = explanation,
            isFirstTime = true,
            badge = null,
            confidenceLevel = "low",
            isDeloadWeek = false,
            isPlateauDetected = false,
            daysSinceLastWorkout = null
        )
    }

    private data class SetWithDate(val entry: ExerciseEntry, val date: String)
    private data class SessionData(val date: String, val weight: Float, val rpe: Float, val hadFailure: Boolean)
}

