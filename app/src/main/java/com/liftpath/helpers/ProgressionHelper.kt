package com.liftpath.helpers

import com.liftpath.models.ExerciseEntry
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.Tier
import com.liftpath.models.TrainingData
import com.liftpath.models.UserLevel
import com.liftpath.models.SetIntent
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import kotlin.math.roundToInt

object ProgressionHelper {

    // ============================================================================================
    // NEW INTENT-BASED PROGRESSION SYSTEM
    // ============================================================================================
    
    /**
     * Action to take with weight for next set
     */
    enum class WeightAction {
        START_LIGHT,    // First time - no history, start conservatively
        MAINTAIN,       // Keep same weight
        INCREASE,       // Go heavier (user picks increment)
        DECREASE,       // Deload / failed reps
        NONE            // No suggestion (FLUSH intent or warmup)
    }
    
    /**
     * @deprecated Use WeightAction instead for intent-based progression
     */
    @Deprecated("Use WeightAction and intent-based progression instead")
    enum class ProgressionScheme {
        LINEAR_RPE,          // Adjust weight based on difficulty (Best for Tier 1)
        DOUBLE_PROGRESSION,  // Increase reps until target hit, then increase weight (Best for Tier 2/3)
        MAINTENANCE          // Keep same
    }
    
    /**
     * Intent-based progression suggestion
     */
    data class IntentSuggestion(
        val exerciseId: Int,
        val intent: SetIntent,
        
        // Target parameters
        val suggestedSets: Int?,
        val suggestedReps: Int?,
        val suggestedRpe: Float?,
        
        // Weight guidance
        val weightAction: WeightAction,
        
        // Display
        val displayText: String,
        val badge: String?,
        val isFirstTime: Boolean,
        val isEstimated: Boolean = false,  // True if suggestion is from cross-intent fallback
        
        // Last session context (for reference)
        val lastWeight: Float? = null,
        val lastReps: Int? = null,
        val lastRpe: Float? = null,

        // Explicit suggested weight (e.g. for FLUSH: 50% of 1RM)
        val suggestedWeight: Float? = null
    )

    // ============================================================================================
    // SETTINGS
    // ============================================================================================

    data class ProgressionSettings(
        // --- INTENT PROGRESSION SETTINGS (NEW) ---
        // STRENGTH intent (low rep, heavy weight)
        val strengthMinReps: Int = 3,
        val strengthMaxReps: Int = 6,
        val strengthTargetRpe: Float = 8.5f,
        val strengthIncreaseRpeThreshold: Float = 8.0f,  // Suggest increase when RPE < this
        
        // BUILD intent (moderate rep, hypertrophy)
        val buildMinReps: Int = 8,
        val buildMaxReps: Int = 12,
        val buildTargetRpe: Float = 8.0f,
        val buildIncreaseRpeThreshold: Float = 8.0f,
        
        // --- CROSS-INTENT FALLBACK ---
        val intentFallbackDays: Int = 30,  // Days before falling back to other intent
        
        // --- 1RM ESTIMATION PERCENTAGES ---
        val strength1RMPercent: Float = 0.85f,  // ~5 rep max (85% of 1RM)
        val build1RMPercent: Float = 0.70f,     // ~10 rep max (70% of 1RM)

        // --- FLUSH intent (light, high rep, pump) ---
        val flush1RMPercent: Float = 0.5f,       // 50% of 1RM
        val flushTargetReps: Int = 20,
        val flushTargetSets: Int = 2,
        val flushTargetRpe: Float = 6.5f,        // 6-7 RPE midpoint
        val flushRepsToIncrease: Int = 25,       // If hit 25+ reps @ 7 RPE, consider increase
        val flushWeightIncrementKg: Float = 2.5f, // Bump when FLUSH-only progression triggers

        // --- TIME DECAY (kept for detraining detection) ---
        val timeDecayThresholds: List<Int> = listOf(14, 30, 60),
        val timeDecayMultipliers: List<Float> = listOf(0.95f, 0.90f, 0.85f),
        
        // --- DELOAD DETECTION (kept for future use) ---
        val deloadThreshold: Int = 3,
        val deloadRPEThreshold: Float = 9.0f,

        // --- REST TIMER SETTINGS ---
        val restTimerEnabled: Boolean = true,
        val strengthRestSeconds: Int = 180,  // 3 minutes
        val buildRestSeconds: Int = 90,      // 1.5 minutes
        val flushRestSeconds: Int = 45,      // 45 seconds

        // SuperSet timer: transition (first exercise) and rest bonus (second exercise)
        val supersetTransitionSeconds: Int = 35,
        val supersetRestBonusSeconds: Int = 45,

        // RPE Timer Adjustments (Smart Rest)
        val rpeAdjustmentEnabled: Boolean = true,
        val rpeHighThreshold: Float = 9.0f,
        val rpeHighBonusSeconds: Int = 60,
        val rpeDeviationThreshold: Float = 1.0f,
        val rpePositiveAdjustmentSeconds: Int = 30,
        val rpeNegativeAdjustmentSeconds: Int = 15,
        
        // Notification Settings
        val notificationLiveCountdown: Boolean = false,
        val notificationAutoDismissEnabled: Boolean = false,
        val notificationAutoDismissSeconds: Int = 10,
        
        // --- LEGACY FIELDS (kept for migration, will be removed) ---
        @Deprecated("Use intent-specific settings instead")
        val userLevel: UserLevel = UserLevel.NOVICE,
        @Deprecated("Not used")
        val lookbackCount: Int = 3,
        @Deprecated("Not suggesting specific kg anymore")
        val roundTo: Float = 1.25f,
        @Deprecated("Not suggesting specific kg anymore")
        val increaseStep: Float = 2.5f,
        @Deprecated("Not suggesting specific kg anymore")
        val smallStep: Float = 1.25f,
        @Deprecated("Use strengthMinReps/strengthMaxReps instead")
        val heavySets: Int = 3,
        @Deprecated("Use strengthMinReps/strengthMaxReps instead")
        val heavyReps: Int = 5,
        @Deprecated("Use buildMinReps/buildMaxReps instead")
        val lightSets: Int = 4,
        @Deprecated("Use buildMinReps/buildMaxReps instead")
        val lightReps: Int = 10,
        @Deprecated("Use strengthRestSeconds instead")
        val heavyRestSeconds: Int = 150,
        @Deprecated("Use buildRestSeconds instead")
        val lightRestSeconds: Int = 60,
        @Deprecated("Use buildRestSeconds instead")
        val customRestSeconds: Int = 120
    )

    // ============================================================================================
    // NEW INTENT-BASED PROGRESSION API
    // ============================================================================================
    
    /**
     * Get progression suggestion based on intent.
     * This is the new primary API for progression suggestions.
     */
    fun getIntentSuggestion(
        exerciseId: Int,
        intent: SetIntent,
        trainingData: TrainingData,
        settings: ProgressionSettings = ProgressionSettings()
    ): IntentSuggestion {
        // WARMUP and UNKNOWN - no progression suggestions
        if (intent == SetIntent.WARMUP || intent == SetIntent.UNKNOWN) {
            return createNoSuggestion(exerciseId, intent)
        }
        // FLUSH - dedicated suggestion logic
        if (intent == SetIntent.FLUSH) {
            return calculateFlushSuggestion(exerciseId, trainingData, settings)
        }
        
        // Get history with cross-intent fallback
        val aggregatedSessions = getHistoryWithFallback(exerciseId, intent, trainingData, settings)
        
        if (aggregatedSessions.isEmpty()) {
            return createIntentFirstTimeSuggestion(exerciseId, intent, settings)
        }
        
        // Select best session from last 2-3 workouts (with bad day detection)
        val selectedSession = selectBestSession(aggregatedSessions)
        val daysSince = calculateDaysSince(selectedSession.date) ?: 0
        
        // Check for time decay (long break)
        if (daysSince >= settings.timeDecayThresholds.firstOrNull() ?: 14) {
            return createTimeDecaySuggestion(
                exerciseId, 
                intent, 
                selectedSession, 
                daysSince, 
                settings,
                trainingData,  // Pass trainingData for muscle group activity check
                selectedSession.isEstimated
            )
        }
        
        // Check for failure
        if (selectedSession.hadFailure) {
            return createRetrySuggestion(exerciseId, intent, selectedSession, settings, selectedSession.isEstimated)
        }
        
        // Normal progression based on intent
        return when (intent) {
            SetIntent.STRENGTH -> calculateStrengthProgression(exerciseId, selectedSession, settings, selectedSession.isEstimated)
            SetIntent.BUILD -> calculateBuildProgression(exerciseId, selectedSession, settings, selectedSession.isEstimated)
            else -> createNoSuggestion(exerciseId, intent)
        }
    }
    
    /**
     * Get suggested RPE for an intent from settings
     */
    fun getTargetRpe(intent: SetIntent, settings: ProgressionSettings): Float {
        return when (intent) {
            SetIntent.STRENGTH -> settings.strengthTargetRpe
            SetIntent.BUILD -> settings.buildTargetRpe
            SetIntent.FLUSH -> settings.flushTargetRpe  // 6-7 RPE for flush
            else -> 7.5f
        }
    }
    
    /**
     * Get rep range for an intent from settings
     */
    fun getRepRange(intent: SetIntent, settings: ProgressionSettings): Pair<Int, Int> {
        return when (intent) {
            SetIntent.STRENGTH -> settings.strengthMinReps to settings.strengthMaxReps
            SetIntent.BUILD -> settings.buildMinReps to settings.buildMaxReps
            SetIntent.FLUSH -> settings.flushTargetReps to settings.flushTargetReps  // (20, 20)
            else -> 8 to 15  // Default range
        }
    }
    
    /**
     * Select the best session from last 2-3 workouts, detecting and skipping "bad days"
     */
    private fun selectBestSession(
        sessions: List<AggregatedSession>
    ): AggregatedSession {
        if (sessions.isEmpty()) {
            throw IllegalArgumentException("Cannot select from empty session list")
        }
        if (sessions.size == 1) {
            return sessions.first()
        }
        
        // Group by date and take last 3 sessions
        val recentSessions = sessions
            .groupBy { it.date }
            .entries
            .sortedByDescending { it.key }
            .take(3)
            .flatMap { it.value }
            .distinctBy { it.date }
            .sortedByDescending { it.date }
        
        if (recentSessions.size <= 1) {
            return recentSessions.first()
        }
        
        val last = recentSessions[0]
        val previous = recentSessions.drop(1)
        
        if (previous.isEmpty()) {
            return last
        }
        
        // Check if last session was a "bad day"
        val isBadDay = isBadDaySession(last, previous)
        
        return if (isBadDay) {
            // Use best of previous sessions
            previous.maxByOrNull { it.estimated1RM } ?: last
        } else {
            last
        }
    }
    
    /**
     * Detect if a session represents a "bad day"
     * Bad day = 10%+ lower 1RM OR same weight with 1+ RPE higher
     */
    private fun isBadDaySession(
        session: AggregatedSession,
        previousSessions: List<AggregatedSession>
    ): Boolean {
        if (previousSessions.isEmpty()) return false
        
        // Find most similar previous session (same weight if possible)
        val similarSession = previousSessions
            .minByOrNull { kotlin.math.abs(it.representativeWeight - session.representativeWeight) }
            ?: previousSessions.first()
        
        // Rule 1: 10%+ lower 1RM than average of previous
        val avgPrevious1RM = previousSessions.map { it.estimated1RM }.average()
        val threshold1RM = avgPrevious1RM * 0.9f
        if (session.estimated1RM < threshold1RM) {
            return true
        }
        
        // Rule 2: Same weight but 1+ RPE higher (grinding more for same result)
        val weightDiff = kotlin.math.abs(similarSession.representativeWeight - session.representativeWeight)
        val isSameWeight = weightDiff < 0.5f  // Within 0.5kg
        val rpeDiff = session.representativeRpe - similarSession.representativeRpe
        
        if (isSameWeight && rpeDiff >= 1.0f) {
            return true
        }
        
        return false
    }
    
    // --- STRENGTH Progression (3-6 reps, weight-focused) ---
    private fun calculateStrengthProgression(
        exerciseId: Int,
        last: AggregatedSession,
        settings: ProgressionSettings,
        isEstimated: Boolean = false
    ): IntentSuggestion {
        val minReps = settings.strengthMinReps
        val maxReps = settings.strengthMaxReps
        val targetRpe = settings.strengthTargetRpe
        val increaseThreshold = settings.strengthIncreaseRpeThreshold
        
        val lastRpe = last.representativeRpe
        val lastReps = last.representativeReps
        
        return when {
            // Hit max reps AND RPE is manageable -> INCREASE WEIGHT
            lastReps >= maxReps && lastRpe < increaseThreshold -> {
                val suffix = if (isEstimated) " (est.)" else ""
                val displayText = if (isEstimated) {
                    "Increase weight, reset to $minReps reps$suffix"
                } else {
                    "Increase weight, reset to $minReps reps"
                }
                IntentSuggestion(
                    exerciseId = exerciseId,
                    intent = SetIntent.STRENGTH,
                    suggestedSets = 3,
                    suggestedReps = minReps,
                    suggestedRpe = targetRpe,
                    weightAction = WeightAction.INCREASE,
                    displayText = displayText,
                    badge = "LEVEL UP",
                    isFirstTime = false,
                    isEstimated = isEstimated,
                    lastWeight = last.representativeWeight,
                    lastReps = lastReps,
                    lastRpe = lastRpe
                )
            }
            // RPE too high -> suggest same weight, lower RPE
            lastRpe >= 9.5f -> {
                val suffix = if (isEstimated) " (est.)" else ""
                val weightStr = formatWeight(last.representativeWeight)
                val displayText = if (isEstimated) {
                    "Same weight, aim for lower RPE$suffix"
                } else {
                    "${weightStr}kg × $lastReps reps, aim for lower RPE"
                }
                IntentSuggestion(
                    exerciseId = exerciseId,
                    intent = SetIntent.STRENGTH,
                    suggestedSets = 3,
                    suggestedReps = lastReps,
                    suggestedRpe = targetRpe,
                    weightAction = WeightAction.MAINTAIN,
                    displayText = displayText,
                    badge = "CONSOLIDATE",
                    isFirstTime = false,
                    isEstimated = isEstimated,
                    lastWeight = last.representativeWeight,
                    lastReps = lastReps,
                    lastRpe = lastRpe
                )
            }
            // In range -> add rep
            else -> {
                val nextReps = minOf(lastReps + 1, maxReps)
                val suffix = if (isEstimated) " (est.)" else ""
                val weightStr = formatWeight(last.representativeWeight)
                val displayText = if (isEstimated) {
                    "Aim for $nextReps reps$suffix"
                } else {
                    "${weightStr}kg × $nextReps reps"
                }
                IntentSuggestion(
                    exerciseId = exerciseId,
                    intent = SetIntent.STRENGTH,
                    suggestedSets = 3,
                    suggestedReps = nextReps,
                    suggestedRpe = targetRpe,
                    weightAction = WeightAction.MAINTAIN,
                    displayText = displayText,
                    badge = if (nextReps > lastReps) "ADD REP" else null,
                    isFirstTime = false,
                    isEstimated = isEstimated,
                    lastWeight = last.representativeWeight,
                    lastReps = lastReps,
                    lastRpe = lastRpe
                )
            }
        }
    }
    
    // --- BUILD Progression (8-12 reps, double progression) ---
    private fun calculateBuildProgression(
        exerciseId: Int,
        last: AggregatedSession,
        settings: ProgressionSettings,
        isEstimated: Boolean = false
    ): IntentSuggestion {
        val minReps = settings.buildMinReps
        val maxReps = settings.buildMaxReps
        val targetRpe = settings.buildTargetRpe
        val increaseThreshold = settings.buildIncreaseRpeThreshold
        
        val lastRpe = last.representativeRpe
        val lastReps = last.representativeReps
        
        return when {
            // Hit max reps AND RPE is manageable -> INCREASE WEIGHT
            lastReps >= maxReps && lastRpe < increaseThreshold -> {
                val suffix = if (isEstimated) " (est.)" else ""
                val displayText = if (isEstimated) {
                    "Increase weight, reset to $minReps reps$suffix"
                } else {
                    "Increase weight, reset to $minReps reps"
                }
                IntentSuggestion(
                    exerciseId = exerciseId,
                    intent = SetIntent.BUILD,
                    suggestedSets = 3,
                    suggestedReps = minReps,
                    suggestedRpe = targetRpe,
                    weightAction = WeightAction.INCREASE,
                    displayText = displayText,
                    badge = "LEVEL UP",
                    isFirstTime = false,
                    isEstimated = isEstimated,
                    lastWeight = last.representativeWeight,
                    lastReps = lastReps,
                    lastRpe = lastRpe
                )
            }
            // RPE too high -> consolidate
            lastRpe >= 9.0f -> {
                val suffix = if (isEstimated) " (est.)" else ""
                val weightStr = formatWeight(last.representativeWeight)
                val displayText = if (isEstimated) {
                    "Same weight, aim for lower RPE$suffix"
                } else {
                    "${weightStr}kg × $lastReps reps, aim for lower RPE"
                }
                IntentSuggestion(
                    exerciseId = exerciseId,
                    intent = SetIntent.BUILD,
                    suggestedSets = 3,
                    suggestedReps = lastReps,
                    suggestedRpe = targetRpe,
                    weightAction = WeightAction.MAINTAIN,
                    displayText = displayText,
                    badge = "CONSOLIDATE",
                    isFirstTime = false,
                    isEstimated = isEstimated,
                    lastWeight = last.representativeWeight,
                    lastReps = lastReps,
                    lastRpe = lastRpe
                )
            }
            // In range -> add rep
            else -> {
                val nextReps = minOf(lastReps + 1, maxReps)
                val suffix = if (isEstimated) " (est.)" else ""
                val weightStr = formatWeight(last.representativeWeight)
                val displayText = if (isEstimated) {
                    "Aim for $nextReps reps$suffix"
                } else {
                    "${weightStr}kg × $nextReps reps"
                }
                IntentSuggestion(
                    exerciseId = exerciseId,
                    intent = SetIntent.BUILD,
                    suggestedSets = 3,
                    suggestedReps = nextReps,
                    suggestedRpe = targetRpe,
                    weightAction = WeightAction.MAINTAIN,
                    displayText = displayText,
                    badge = if (nextReps > lastReps) "ADD REP" else null,
                    isFirstTime = false,
                    isEstimated = isEstimated,
                    lastWeight = last.representativeWeight,
                    lastReps = lastReps,
                    lastRpe = lastRpe
                )
            }
        }
    }
    
    // --- Helper: No suggestion (WARMUP, etc.) ---
    private fun createNoSuggestion(exerciseId: Int, intent: SetIntent): IntentSuggestion {
        return IntentSuggestion(
            exerciseId = exerciseId,
            intent = intent,
            suggestedSets = null,
            suggestedReps = null,
            suggestedRpe = if (intent == SetIntent.FLUSH) 7.0f else null,
            weightAction = WeightAction.NONE,
            displayText = "",
            badge = null,
            isFirstTime = false,
            isEstimated = false
        )
    }

    // --- FLUSH suggestion: 2 sets × (50% of 1RM) × 20 reps @ 6-7 RPE ---
    private fun calculateFlushSuggestion(
        exerciseId: Int,
        trainingData: TrainingData,
        settings: ProgressionSettings
    ): IntentSuggestion {
        val targetReps = settings.flushTargetReps
        val targetSets = settings.flushTargetSets
        val targetRpe = settings.flushTargetRpe

        // 1. Get 1RM estimate: STRENGTH → BUILD → FLUSH-only
        val estimated1RM = getFlush1RM(exerciseId, trainingData, settings)
        if (estimated1RM == null || estimated1RM <= 0f) {
            return IntentSuggestion(
                exerciseId = exerciseId,
                intent = SetIntent.FLUSH,
                suggestedSets = targetSets,
                suggestedReps = targetReps,
                suggestedRpe = targetRpe,
                weightAction = WeightAction.START_LIGHT,
                displayText = "Start light, 2×20 reps @ RPE 6-7",
                badge = "NEW",
                isFirstTime = true,
                isEstimated = false,
                suggestedWeight = null
            )
        }

        // 2. Base suggestion: 50% of 1RM
        var suggestedWeight = estimated1RM * settings.flush1RMPercent

        // 3. FLUSH-only progression: if last session 25+ reps @ 7 RPE and 1RM unchanged → increase
        val flushHistory = getFlushHistory(exerciseId, trainingData)
        val hasStrengthOrBuildHistory = getFlush1RM(exerciseId, trainingData, settings, fromStrengthOrBuildOnly = true) != null
        if (!hasStrengthOrBuildHistory && flushHistory.size >= 1) {
            val lastSession = flushHistory.last()
            val prevSession = flushHistory.getOrNull(flushHistory.size - 2)
            val lastInferred1RM = estimate1RMFromHighReps(
                lastSession.representativeWeight,
                lastSession.representativeReps,
                lastSession.representativeRpe
            )
            val prevInferred1RM = prevSession?.let {
                estimate1RMFromHighReps(it.representativeWeight, it.representativeReps, it.representativeRpe)
            }
            val repsMet = lastSession.representativeReps >= settings.flushRepsToIncrease
            val rpeMet = lastSession.representativeRpe <= 7.0f
            val oneRMUnchanged = prevInferred1RM == null || kotlin.math.abs(lastInferred1RM - prevInferred1RM) / prevInferred1RM < 0.02f
            if (repsMet && rpeMet && oneRMUnchanged) {
                suggestedWeight += settings.flushWeightIncrementKg
            }
        }

        val weightStr = formatWeight(suggestedWeight)
        val displayText = "2×${weightStr}kg × $targetReps reps @ RPE 6-7"

        return IntentSuggestion(
            exerciseId = exerciseId,
            intent = SetIntent.FLUSH,
            suggestedSets = targetSets,
            suggestedReps = targetReps,
            suggestedRpe = targetRpe,
            weightAction = WeightAction.MAINTAIN,
            displayText = displayText,
            badge = null,
            isFirstTime = false,
            isEstimated = false,
            lastWeight = suggestedWeight,
            lastReps = targetReps,
            lastRpe = targetRpe,
            suggestedWeight = suggestedWeight
        )
    }

    /**
     * Get estimated 1RM for FLUSH suggestion.
     * Priority: STRENGTH history → BUILD history → FLUSH-only (inferred from high-rep sets).
     */
    private fun getFlush1RM(
        exerciseId: Int,
        data: TrainingData,
        settings: ProgressionSettings,
        fromStrengthOrBuildOnly: Boolean = false
    ): Float? {
        if (!fromStrengthOrBuildOnly) {
            val strengthSets = extractRawSets(exerciseId, SetIntent.STRENGTH, data)
            if (strengthSets.isNotEmpty()) {
                val sessions = strengthSets.groupBy { it.date }.map { (date, sets) ->
                    aggregateSessionSets(sets, SetIntent.STRENGTH)
                }.sortedBy { it.date }
                return sessions.maxByOrNull { it.estimated1RM }?.estimated1RM
            }
            val buildSets = extractRawSets(exerciseId, SetIntent.BUILD, data)
            if (buildSets.isNotEmpty()) {
                val sessions = buildSets.groupBy { it.date }.map { (date, sets) ->
                    aggregateSessionSets(sets, SetIntent.BUILD)
                }.sortedBy { it.date }
                return sessions.maxByOrNull { it.estimated1RM }?.estimated1RM
            }
        } else {
            val strengthSets = extractRawSets(exerciseId, SetIntent.STRENGTH, data)
            val buildSets = extractRawSets(exerciseId, SetIntent.BUILD, data)
            if (strengthSets.isNotEmpty() || buildSets.isNotEmpty()) return 1f
            return null
        }
        val flushHistory = getFlushHistory(exerciseId, data)
        return flushHistory.lastOrNull()?.let {
            estimate1RMFromHighReps(it.representativeWeight, it.representativeReps, it.representativeRpe)
        }
    }

    private fun getFlushHistory(exerciseId: Int, data: TrainingData): List<AggregatedSession> {
        val flushSets = extractRawSets(exerciseId, SetIntent.FLUSH, data)
        if (flushSets.isEmpty()) return emptyList()
        return flushSets.groupBy { it.date }
            .map { (_, sets) -> aggregateSessionSets(sets, SetIntent.FLUSH) }
            .sortedBy { it.date }
    }

    /**
     * Estimate 1RM from high-rep FLUSH sets (20-25 reps).
     * Epley extended with RPE normalization.
     */
    private fun estimate1RMFromHighReps(weight: Float, reps: Int, rpe: Float): Float {
        val repsInReserve = 10f - rpe
        val effectiveReps = (reps + repsInReserve).toInt().coerceIn(1, 35)
        return weight * (1 + effectiveReps / 30f)
    }
    
    // --- Helper: First time with this intent ---
    private fun createIntentFirstTimeSuggestion(
        exerciseId: Int,
        intent: SetIntent,
        settings: ProgressionSettings
    ): IntentSuggestion {
        val (minReps, maxReps) = getRepRange(intent, settings)
        val targetRpe = getTargetRpe(intent, settings)
        
        return IntentSuggestion(
            exerciseId = exerciseId,
            intent = intent,
            suggestedSets = 3,
            suggestedReps = minReps,
            suggestedRpe = targetRpe,
            weightAction = WeightAction.START_LIGHT,
            displayText = "Start light, aim for $minReps reps",
            badge = "NEW",
            isFirstTime = true,
            isEstimated = false
        )
    }
    
    /**
     * Check if muscle groups for this exercise have been worked by other exercises since the given date
     */
    private fun hasMuscleGroupActivity(
        exerciseId: Int,
        sinceDate: String,
        trainingData: TrainingData
    ): Boolean {
        val exercise = trainingData.exerciseLibrary.find { it.id == exerciseId } ?: return false
        val targetMuscles = exercise.primaryTargets + exercise.secondaryTargets
        if (targetMuscles.isEmpty()) return false
        
        // Check all training sessions since the date
        val sinceDateObj = parseDate(sinceDate) ?: return false
        
        return trainingData.trainings.any { session ->
            val sessionDate = parseDate(session.date) ?: return@any false
            if (sessionDate <= sinceDateObj) return@any false
            
            // Check if any exercise in this session targets overlapping muscles
            session.exercises.any { entry ->
                if (entry.exerciseId == exerciseId) return@any false  // Skip the same exercise
                
                val otherExercise = trainingData.exerciseLibrary.find { it.id == entry.exerciseId }
                if (otherExercise == null) return@any false
                
                val otherMuscles = otherExercise.primaryTargets + otherExercise.secondaryTargets
                // Check for any overlap
                targetMuscles.any { it in otherMuscles }
            }
        }
    }
    
    // --- Helper: Time decay (long break) ---
    private fun createTimeDecaySuggestion(
        exerciseId: Int,
        intent: SetIntent,
        last: AggregatedSession,
        daysSince: Int,
        settings: ProgressionSettings,
        trainingData: TrainingData,
        isEstimated: Boolean = false
    ): IntentSuggestion {
        val suffix = if (isEstimated) " (est.)" else ""
        
        // Check if muscle groups have been worked
        val muscleGroupsWorked = hasMuscleGroupActivity(exerciseId, last.date, trainingData)
        
        // Calculate decay multiplier
        val decayMultiplier = if (muscleGroupsWorked) {
            // Minimal decay: 5% reduction
            0.95f
        } else {
            // Normal time decay based on days
            calculateTimeDecay(daysSince, settings)
        }
        
        // Apply decay to weight
        val suggestedWeight = last.representativeWeight * decayMultiplier
        val (minReps, maxReps) = getRepRange(intent, settings)
        val targetRpe = getTargetRpe(intent, settings)
        
        // For old data, suggest starting at min reps with decayed weight
        val suggestedReps = minReps
        val suggestedSets = 3
        
        // Format weight (remove decimals if whole number)
        val weightString = if (suggestedWeight % 1 == 0f) {
            suggestedWeight.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", suggestedWeight)
        }
        
        // Build display text with specific values
        val displayText = "${suggestedSets}×${weightString}kg × $suggestedReps reps$suffix"
        
        return IntentSuggestion(
            exerciseId = exerciseId,
            intent = intent,
            suggestedSets = suggestedSets,
            suggestedReps = suggestedReps,
            suggestedRpe = targetRpe,
            weightAction = WeightAction.MAINTAIN,  // Using decayed weight, not increasing
            displayText = displayText,
            badge = null,  // No special badge for old data
            isFirstTime = false,
            isEstimated = isEstimated,
            lastWeight = last.representativeWeight,
            lastReps = last.representativeReps,
            lastRpe = last.representativeRpe
        )
    }
    
    // --- Helper: Failed last time ---
    private fun createRetrySuggestion(
        exerciseId: Int,
        intent: SetIntent,
        last: AggregatedSession,
        settings: ProgressionSettings,
        isEstimated: Boolean = false
    ): IntentSuggestion {
        val targetRpe = getTargetRpe(intent, settings)
        val suffix = if (isEstimated) " (est.)" else ""
        
        return IntentSuggestion(
            exerciseId = exerciseId,
            intent = intent,
            suggestedSets = 3,
            suggestedReps = last.representativeReps,
            suggestedRpe = targetRpe,
            weightAction = WeightAction.MAINTAIN,
            displayText = "Retry same weight$suffix",
            badge = "RETRY",
            isFirstTime = false,
            isEstimated = isEstimated,
            lastWeight = last.representativeWeight,
            lastReps = last.representativeReps,
            lastRpe = last.representativeRpe
        )
    }
    
    // ============================================================================================
    // CROSS-INTENT FALLBACK & HISTORY AGGREGATION
    // ============================================================================================
    
    /**
     * Get history with cross-intent fallback when primary intent data is stale
     */
    private fun getHistoryWithFallback(
        exerciseId: Int,
        primaryIntent: SetIntent,
        data: TrainingData,
        settings: ProgressionSettings
    ): List<AggregatedSession> {
        // Extract and aggregate primary intent history
        val primaryRawSets = extractRawSets(exerciseId, primaryIntent, data)
        
        if (primaryRawSets.isNotEmpty()) {
            // Group by date and aggregate each session
            val primarySessions = primaryRawSets
                .groupBy { it.date }
                .map { (date, sets) -> aggregateSessionSets(sets, primaryIntent) }
                .sortedBy { it.date }
            
            // Check if most recent session is fresh enough
            val mostRecent = primarySessions.maxByOrNull { it.date }
            if (mostRecent != null) {
                val daysSince = calculateDaysSince(mostRecent.date) ?: 0
                if (daysSince < settings.intentFallbackDays) {
                    return primarySessions  // Fresh enough, use it
                }
            }
        }
        
        // Fall back to alternate intent
        val fallbackIntent = when (primaryIntent) {
            SetIntent.STRENGTH -> SetIntent.BUILD
            SetIntent.BUILD -> SetIntent.STRENGTH
            else -> return emptyList()  // No fallback for FLUSH/WARMUP
        }
        
        val fallbackRawSets = extractRawSets(exerciseId, fallbackIntent, data)
        if (fallbackRawSets.isEmpty()) {
            return primaryRawSets
                .groupBy { it.date }
                .map { (date, sets) -> aggregateSessionSets(sets, primaryIntent) }
                .sortedBy { it.date }
        }
        
        // Convert fallback data to primary intent
        val fallbackSessions = fallbackRawSets
            .groupBy { it.date }
            .map { (date, sets) -> 
                val aggregated = aggregateSessionSets(sets, fallbackIntent)
                convertSession(aggregated, fromIntent = fallbackIntent, toIntent = primaryIntent, settings)
            }
            .sortedBy { it.date }
        
        return fallbackSessions
    }
    
    /**
     * Convert a session from one intent to another using 1RM estimation
     */
    private fun convertSession(
        session: AggregatedSession,
        fromIntent: SetIntent,
        toIntent: SetIntent,
        settings: ProgressionSettings
    ): AggregatedSession {
        // Get 1RM from source data
        val source1RM = session.estimated1RM
        
        // Calculate new weight based on target intent percentage
        val targetPercent = when (toIntent) {
            SetIntent.STRENGTH -> settings.strength1RMPercent  // 0.85
            SetIntent.BUILD -> settings.build1RMPercent        // 0.70
            else -> 0.75f
        }
        val (targetMinReps, _) = getRepRange(toIntent, settings)
        
        val estimatedWeight = source1RM * targetPercent
        
        return session.copy(
            representativeWeight = estimatedWeight,
            representativeReps = targetMinReps,
            estimated1RM = source1RM,  // Keep original 1RM for comparison
            isEstimated = true,
            sourceIntent = fromIntent
        )
    }

    // ============================================================================================
    // LEGACY API (kept for backward compatibility during migration)
    // ============================================================================================

    @Deprecated("Use IntentSuggestion instead", ReplaceWith("getIntentSuggestion()"))
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
        val proposedHeavyWeight: Float?
            get() = if (requestedType == "heavy") proposedWeight else null

        val proposedLightWeight: Float?
            get() = if (requestedType == "light") proposedWeight else null

        val lastHeavyRpe: Float?
            get() = lastRpe
    }

    @Deprecated("Use getIntentSuggestion() instead")
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

        // 2. FETCH HISTORY - Map requestedType to intent for filtering
        val targetIntent = when (requestedType.lowercase()) {
            "heavy" -> SetIntent.STRENGTH
            "light" -> SetIntent.BUILD
            else -> SetIntent.BUILD
        }
        val history = extractHistory(exerciseId, targetIntent, trainingData)
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

    /**
     * Raw set data extracted from training history
     */
    private data class RawSetData(
        val date: String,
        val weight: Float,
        val reps: Int,
        val rpe: Float,
        val hadFailure: Boolean
    )
    
    /**
     * Aggregated session data - represents a single workout session
     * with a representative set (best or weighted average depending on intent)
     */
    private data class AggregatedSession(
        val date: String,
        val representativeWeight: Float,  // Best or weighted avg depending on intent
        val representativeReps: Int,
        val representativeRpe: Float,
        val estimated1RM: Float,          // For comparison across sessions
        val hadFailure: Boolean,
        val isEstimated: Boolean = false, // True if from different intent
        val sourceIntent: SetIntent? = null
    )
    
    /**
     * Legacy SessionData - kept for backward compatibility
     * @deprecated Use AggregatedSession instead
     */
    @Deprecated("Use AggregatedSession instead")
    private data class SessionData(
        val date: String,
        val weight: Float,
        val reps: Int,
        val rpe: Float,
        val hadFailure: Boolean
    )

    /**
     * Extract raw sets from training data for a specific exercise and intent
     */
    private fun extractRawSets(id: Int, targetIntent: SetIntent, data: TrainingData): List<RawSetData> {
        return data.trainings.flatMap { session ->
            session.exercises
                .filter { 
                    it.exerciseId == id && 
                    it.getEffectiveIntent(session.defaultWorkoutType) == targetIntent &&
                    !it.isWarmup
                }
                .map { entry ->
                    RawSetData(
                        date = session.date,
                        weight = entry.kg,
                        reps = entry.reps,
                        rpe = entry.rpe ?: 8.0f,
                        hadFailure = entry.completed == false
                    )
                }
        }
    }
    
    /**
     * Legacy extractHistory - kept for backward compatibility
     * @deprecated Use extractRawSets and aggregateSessionSets instead
     */
    @Deprecated("Use extractRawSets and aggregateSessionSets instead")
    private fun extractHistory(id: Int, targetIntent: SetIntent, data: TrainingData): List<SessionData> {
        return extractRawSets(id, targetIntent, data).map {
            SessionData(
                date = it.date,
                weight = it.weight,
                reps = it.reps,
                rpe = it.rpe,
                hadFailure = it.hadFailure
            )
        }
    }
    
    /**
     * Aggregate sets from a single workout session into a representative session
     * STRENGTH: Uses best 1RM set
     * BUILD: Uses RPE-weighted average of all sets
     */
    private fun aggregateSessionSets(
        sets: List<RawSetData>,
        intent: SetIntent
    ): AggregatedSession {
        if (sets.isEmpty()) {
            throw IllegalArgumentException("Cannot aggregate empty set list")
        }
        
        return when (intent) {
            SetIntent.STRENGTH -> {
                // Use set with highest estimated 1RM (best performance)
                val bestSet = sets.maxByOrNull { estimate1RM(it.weight, it.reps) }!!
                AggregatedSession(
                    date = sets.first().date,
                    representativeWeight = bestSet.weight,
                    representativeReps = bestSet.reps,
                    representativeRpe = bestSet.rpe,
                    estimated1RM = estimate1RM(bestSet.weight, bestSet.reps),
                    hadFailure = sets.any { it.hadFailure }
                )
            }
            SetIntent.BUILD -> {
                // RPE-weighted average of all sets
                val rpeWeights = sets.map { (it.rpe - 5f).coerceAtLeast(0.5f) }
                val totalWeight = rpeWeights.sum()
                
                val avgKg = sets.map { it.weight }.average().toFloat()
                val avgReps = (sets.mapIndexed { i, s -> s.reps * rpeWeights[i] }.sum() / totalWeight).roundToInt()
                val avgRpe = sets.mapIndexed { i, s -> s.rpe * rpeWeights[i] }.sum() / totalWeight
                
                AggregatedSession(
                    date = sets.first().date,
                    representativeWeight = avgKg,
                    representativeReps = avgReps,
                    representativeRpe = avgRpe,
                    estimated1RM = estimate1RM(avgKg, avgReps),
                    hadFailure = sets.any { it.hadFailure }
                )
            }
            SetIntent.FLUSH -> {
                // Use set with highest inferred 1RM (estimate1RMFromHighReps with RPE)
                val bestSet = sets.maxByOrNull {
                    estimate1RMFromHighReps(it.weight, it.reps, it.rpe)
                }!!
                val est1RM = estimate1RMFromHighReps(bestSet.weight, bestSet.reps, bestSet.rpe)
                AggregatedSession(
                    date = bestSet.date,
                    representativeWeight = bestSet.weight,
                    representativeReps = bestSet.reps,
                    representativeRpe = bestSet.rpe,
                    estimated1RM = est1RM,
                    hadFailure = sets.any { it.hadFailure }
                )
            }
            else -> {
                // WARMUP etc - just use first set
                val firstSet = sets.first()
                AggregatedSession(
                    date = firstSet.date,
                    representativeWeight = firstSet.weight,
                    representativeReps = firstSet.reps,
                    representativeRpe = firstSet.rpe,
                    estimated1RM = estimate1RM(firstSet.weight, firstSet.reps),
                    hadFailure = sets.any { it.hadFailure }
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
    
    /**
     * Parse date string to Date object
     */
    private fun parseDate(dateStr: String): Date? {
        return try {
            val format = SimpleDateFormat("yyyy/MM/dd", Locale.US)
            format.parse(dateStr)
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
    
    /**
     * Format weight for display (remove decimals if whole number)
     */
    private fun formatWeight(weight: Float): String {
        return if (weight % 1 == 0f) {
            weight.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", weight)
        }
    }
    
    // ============================================================================================
    // 1RM ESTIMATION
    // ============================================================================================
    
    /**
     * Estimate 1RM using Epley formula: 1RM = weight × (1 + reps / 30)
     */
    private fun estimate1RM(weight: Float, reps: Int): Float {
        return weight * (1 + reps / 30f)
    }
    
    /**
     * Calculate weight for target reps from 1RM
     * Reverse of Epley: weight = 1RM × (30 / (30 + targetReps))
     */
    private fun weightFrom1RM(oneRM: Float, targetReps: Int): Float {
        return oneRM * (30f / (30f + targetReps))
    }

    // ============================================================================================
    // RPE SUGGESTION HELPERS
    // ============================================================================================
    
    /**
     * Get suggested RPE for an intent using settings (preferred)
     */
    fun suggestRpe(intent: SetIntent, settings: ProgressionSettings): Float {
        return getTargetRpe(intent, settings)
    }
    
    /**
     * Legacy: Get suggested RPE based on user level (deprecated)
     */
    @Deprecated("Use suggestRpe(intent, settings) instead")
    fun suggestRpe(userLevel: UserLevel, intent: SetIntent): Float {
        return when (intent) {
            SetIntent.STRENGTH -> if (userLevel == UserLevel.NOVICE) 8.0f else 8.5f
            SetIntent.BUILD -> if (userLevel == UserLevel.NOVICE) 7.5f else 8.0f
            SetIntent.FLUSH -> if (userLevel == UserLevel.NOVICE) 7.0f else 7.5f
            else -> 7.5f
        }
    }
    
    /**
     * Legacy: Get suggested RPE based on workout type string (deprecated)
     */
    @Deprecated("Use suggestRpe(intent, settings) instead")
    fun suggestRpe(userLevel: UserLevel, type: String): Float {
        val intent = when (type.lowercase()) {
            "heavy" -> SetIntent.STRENGTH
            "light" -> SetIntent.BUILD
            else -> SetIntent.BUILD
        }
        @Suppress("DEPRECATION")
        return suggestRpe(userLevel, intent)
    }
}