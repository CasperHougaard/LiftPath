package com.liftpath.helpers

import com.liftpath.models.ExerciseLibraryItem
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
        NONE            // No suggestion (FLUSH intent or warmup)
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
        settings: ProgressionSettings = ProgressionSettings(),
        incrementTable: EquipmentIncrementTable? = null
    ): IntentSuggestion {
        // WARMUP and UNKNOWN - no progression suggestions
        if (intent == SetIntent.WARMUP || intent == SetIntent.UNKNOWN) {
            return createNoSuggestion(exerciseId, intent)
        }

        val libItem = trainingData.exerciseLibrary.find { it.id == exerciseId }
        val rule = WeightIncrementHelper.resolve(libItem, incrementTable)
        val ctx = WeightContext(
            rule = rule,
            // Three kinds of exercise get reps and RPE but never a kg, because any number would
            // be wrong rather than merely imprecise: a band has no ladder, a bodyweight lift
            // progresses on `addedKg` (which the history pipeline does not yet separate from body
            // weight), and a timed hold progresses on seconds.
            canSuggestWeight = rule.hasLadder &&
                libItem?.isBodyweight != true &&
                libItem?.isTimeBased != true
        )

        // FLUSH - dedicated suggestion logic
        if (intent == SetIntent.FLUSH) {
            return calculateFlushSuggestion(exerciseId, trainingData, settings, ctx)
        }

        // Get history with cross-intent fallback
        val aggregatedSessions = getHistoryWithFallback(exerciseId, intent, trainingData, settings, ctx)

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
                libItem,
                ctx,
                selectedSession.isEstimated
            )
        }

        // Check for failure
        if (selectedSession.hadFailure) {
            return createRetrySuggestion(exerciseId, intent, selectedSession, settings, ctx, selectedSession.isEstimated)
        }

        // Normal progression based on intent
        return when (intent) {
            SetIntent.STRENGTH, SetIntent.BUILD ->
                calculateProgression(exerciseId, intent, selectedSession, settings, ctx, selectedSession.isEstimated)
            else -> createNoSuggestion(exerciseId, intent)
        }
    }

    /**
     * Everything the progression math needs to know about the exercise itself, resolved once at
     * the top of [getIntentSuggestion] so the leaf strategies never re-derive it.
     */
    private data class WeightContext(
        val rule: WeightIncrementRule,
        /** False => emit reps and RPE only, never a kg. */
        val canSuggestWeight: Boolean
    )

    private const val DEFAULT_SUGGESTED_SETS = 3

    /** RPE at or above which STRENGTH stops adding reps and consolidates instead. */
    private const val STRENGTH_CONSOLIDATE_RPE = 9.5f

    /** BUILD's equivalent. Lower than STRENGTH's on purpose — see [IntentTuning]. */
    private const val BUILD_CONSOLIDATE_RPE = 9.0f

    /**
     * The per-intent constants that used to be the *only* difference between two ~90-line copies
     * of the same three-branch decision.
     *
     * [consolidateRpe] deliberately stays a hardcoded constant rather than a settings field:
     * `ProgressionSettings` is Gson-deserialized, so a newly added `Float` arrives as `0f` on
     * every existing install, and `lastRpe >= 0f` would turn every suggestion into CONSOLIDATE.
     */
    private data class IntentTuning(
        val minReps: Int,
        val maxReps: Int,
        val targetRpe: Float,
        val increaseRpeThreshold: Float,
        val consolidateRpe: Float
    )

    private fun tuningFor(intent: SetIntent, s: ProgressionSettings): IntentTuning = when (intent) {
        SetIntent.STRENGTH -> IntentTuning(
            minReps = s.strengthMinReps,
            maxReps = s.strengthMaxReps,
            targetRpe = s.strengthTargetRpe,
            increaseRpeThreshold = s.strengthIncreaseRpeThreshold,
            consolidateRpe = STRENGTH_CONSOLIDATE_RPE
        )
        else -> IntentTuning(
            minReps = s.buildMinReps,
            maxReps = s.buildMaxReps,
            targetRpe = s.buildTargetRpe,
            increaseRpeThreshold = s.buildIncreaseRpeThreshold,
            consolidateRpe = BUILD_CONSOLIDATE_RPE
        )
    }

    /**
     * Renders a suggestion as the one line the user reads, in the active-workout row, the log
     * screen's hint card and the AI export alike.
     *
     * Consumers render [IntentSuggestion.displayText] verbatim and append the badge themselves,
     * so the weight has to live in here — which is also why this is the single place that decides
     * how a suggestion reads.
     */
    private fun formatSuggestion(
        weightKg: Float?,
        reps: Int?,
        rpe: Float?,
        isEstimated: Boolean,
        sets: Int? = null
    ): String {
        val suffix = if (isEstimated) " (est.)" else ""
        val rpePart = rpe?.let { " @ RPE ${WeightIncrementHelper.format(it)}" } ?: ""
        val setsPrefix = sets?.let { "${it}×" } ?: ""
        return when {
            weightKg != null && reps != null ->
                "$setsPrefix${WeightIncrementHelper.format(weightKg)} kg × $reps$rpePart$suffix"
            reps != null -> "aim for $reps reps$rpePart$suffix"
            else -> ""
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
    
    /**
     * The shared STRENGTH/BUILD decision: increase the load, hold it and shed a little RPE, or
     * hold it and add a rep.
     *
     * This was two near-identical functions differing only in the constants now carried by
     * [IntentTuning]. The one asymmetry worth preserving is the consolidate ceiling — 9.5 for
     * STRENGTH, 9.0 for BUILD — so an RPE of 9.2 adds a rep on a heavy triple but backs off on a
     * hypertrophy set.
     *
     * Weight handling follows one rule: **snap what the app computed, never what the user lifted.**
     * A BUILD session's representative weight is an average across sets and can land anywhere, so
     * it is snapped onto the equipment's ladder; a STRENGTH session's is a real logged set, so it
     * is left exactly as lifted — somebody running 1 kg microplates at 61 kg must not be told 60.
     */
    private fun calculateProgression(
        exerciseId: Int,
        intent: SetIntent,
        last: AggregatedSession,
        settings: ProgressionSettings,
        ctx: WeightContext,
        isEstimated: Boolean = false
    ): IntentSuggestion {
        val tuning = tuningFor(intent, settings)
        val lastRpe = last.representativeRpe
        val lastReps = last.representativeReps

        // A derived weight (a BUILD average, a cross-intent estimate) is snapped; a measured one
        // is passed through untouched.
        val heldWeight = if (last.isWeightDerived) {
            WeightIncrementHelper.snap(last.representativeWeight, ctx.rule)
        } else {
            last.representativeWeight
        }

        fun suggestion(
            action: WeightAction,
            weight: Float?,
            reps: Int,
            badge: String?
        ) = IntentSuggestion(
            exerciseId = exerciseId,
            intent = intent,
            suggestedSets = DEFAULT_SUGGESTED_SETS,
            suggestedReps = reps,
            suggestedRpe = tuning.targetRpe,
            weightAction = action,
            displayText = formatSuggestion(weight, reps, tuning.targetRpe, isEstimated),
            badge = badge,
            isFirstTime = false,
            isEstimated = isEstimated,
            lastWeight = last.representativeWeight,
            lastReps = lastReps,
            lastRpe = lastRpe,
            suggestedWeight = weight
        )

        return when {
            // Hit the top of the rep range with RPE to spare -> next rung up, reps back to the bottom.
            lastReps >= tuning.maxReps && lastRpe < tuning.increaseRpeThreshold ->
                suggestion(
                    action = WeightAction.INCREASE,
                    weight = ctx.weightOrNull(WeightIncrementHelper.nextUp(heldWeight, ctx.rule)),
                    reps = tuning.minReps,
                    badge = "LEVEL UP"
                )

            // Ground it out -> same load, same reps, chase a lower RPE.
            lastRpe >= tuning.consolidateRpe ->
                suggestion(
                    action = WeightAction.MAINTAIN,
                    weight = ctx.weightOrNull(heldWeight),
                    reps = lastReps,
                    badge = "CONSOLIDATE"
                )

            // Mid-range -> same load, one more rep (badge drops once the range is capped).
            else -> {
                val nextReps = minOf(lastReps + 1, tuning.maxReps)
                suggestion(
                    action = WeightAction.MAINTAIN,
                    weight = ctx.weightOrNull(heldWeight),
                    reps = nextReps,
                    badge = if (nextReps > lastReps) "ADD REP" else null
                )
            }
        }
    }

    /** [kg] when this exercise can carry a numeric target, null when it can only carry reps/RPE. */
    private fun WeightContext.weightOrNull(kg: Float): Float? = if (canSuggestWeight) kg else null

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
        settings: ProgressionSettings,
        ctx: WeightContext
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

        // 2. Base suggestion: 50% of 1RM, on whatever this equipment can actually load. This used
        //    to round to a flat 0.5 kg, which is a weight no cable stack or dumbbell rack offers.
        var suggestedWeight = WeightIncrementHelper.snap(estimated1RM * settings.flush1RMPercent, ctx.rule)

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
                // One rung rather than settings.flushWeightIncrementKg: a flat +2.5 on top of a
                // snapped value lands between pins on a 5 kg stack.
                suggestedWeight = WeightIncrementHelper.nextUp(suggestedWeight, ctx.rule)
            }
        }

        val offeredWeight = ctx.weightOrNull(suggestedWeight)

        return IntentSuggestion(
            exerciseId = exerciseId,
            intent = SetIntent.FLUSH,
            suggestedSets = targetSets,
            suggestedReps = targetReps,
            suggestedRpe = targetRpe,
            weightAction = WeightAction.MAINTAIN,
            displayText = formatSuggestion(offeredWeight, targetReps, targetRpe, isEstimated = false, sets = targetSets),
            badge = null,
            isFirstTime = false,
            isEstimated = false,
            lastWeight = offeredWeight,
            lastReps = targetReps,
            lastRpe = targetRpe,
            suggestedWeight = offeredWeight
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
        
        // No weight, deliberately. There is no history to derive one from, and prefilling the
        // equipment minimum would drop "5 kg" into the field for a first machine exercise where
        // it would get saved without a second look. An empty field forces a considered entry.
        return IntentSuggestion(
            exerciseId = exerciseId,
            intent = intent,
            suggestedSets = DEFAULT_SUGGESTED_SETS,
            suggestedReps = minReps,
            suggestedRpe = targetRpe,
            weightAction = WeightAction.START_LIGHT,
            displayText = "Start light, ${formatSuggestion(null, minReps, targetRpe, isEstimated = false)}",
            badge = "NEW",
            isFirstTime = true,
            isEstimated = false
        )
    }
    
    /**
     * Check if muscle groups for this exercise have been worked by other exercises since the given date
     */
    private fun hasMuscleGroupActivity(
        exercise: ExerciseLibraryItem?,
        sinceDate: String,
        trainingData: TrainingData
    ): Boolean {
        if (exercise == null) return false
        val exerciseId = exercise.id
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
        libItem: ExerciseLibraryItem?,
        ctx: WeightContext,
        isEstimated: Boolean = false
    ): IntentSuggestion {
        // Check if muscle groups have been worked
        val muscleGroupsWorked = hasMuscleGroupActivity(libItem, last.date, trainingData)

        // Calculate decay multiplier
        val decayMultiplier = if (muscleGroupsWorked) {
            // Minimal decay: 5% reduction
            0.95f
        } else {
            // Normal time decay based on days
            calculateTimeDecay(daysSince, settings)
        }

        // Apply decay, then put it back on the ladder: a multiplier produces arbitrary floats
        // (72.5 × 0.9 = 65.25) and the result is a load the user has to actually rack.
        val decayedWeight = WeightIncrementHelper.snap(
            last.representativeWeight * decayMultiplier, ctx.rule
        )
        val (minReps, _) = getRepRange(intent, settings)
        val targetRpe = getTargetRpe(intent, settings)

        // For old data, suggest starting at min reps with decayed weight
        val suggestedReps = minReps
        val suggestedSets = DEFAULT_SUGGESTED_SETS

        return IntentSuggestion(
            exerciseId = exerciseId,
            intent = intent,
            suggestedSets = suggestedSets,
            suggestedReps = suggestedReps,
            suggestedRpe = targetRpe,
            weightAction = WeightAction.MAINTAIN,  // Using decayed weight, not increasing
            displayText = formatSuggestion(
                ctx.weightOrNull(decayedWeight), suggestedReps, targetRpe, isEstimated, suggestedSets
            ),
            badge = null,  // No special badge for old data
            isFirstTime = false,
            isEstimated = isEstimated,
            // lastWeight stays undecayed: it is what was actually lifted. The decayed figure goes
            // in suggestedWeight, which is what callers prefill from. Previously the decay reached
            // only the display text, so the kg field was filled with the *undecayed* weight while
            // the hint above it advised a lighter one.
            lastWeight = last.representativeWeight,
            lastReps = last.representativeReps,
            lastRpe = last.representativeRpe,
            suggestedWeight = ctx.weightOrNull(decayedWeight)
        )
    }
    
    // --- Helper: Failed last time ---
    private fun createRetrySuggestion(
        exerciseId: Int,
        intent: SetIntent,
        last: AggregatedSession,
        settings: ProgressionSettings,
        ctx: WeightContext,
        isEstimated: Boolean = false
    ): IntentSuggestion {
        val targetRpe = getTargetRpe(intent, settings)
        // Same policy as elsewhere: only snap a weight the app computed.
        val retryWeight = if (last.isWeightDerived) {
            WeightIncrementHelper.snap(last.representativeWeight, ctx.rule)
        } else {
            last.representativeWeight
        }

        return IntentSuggestion(
            exerciseId = exerciseId,
            intent = intent,
            suggestedSets = DEFAULT_SUGGESTED_SETS,
            suggestedReps = last.representativeReps,
            suggestedRpe = targetRpe,
            weightAction = WeightAction.MAINTAIN,
            displayText = formatSuggestion(
                ctx.weightOrNull(retryWeight), last.representativeReps, targetRpe, isEstimated
            ),
            badge = "RETRY",
            isFirstTime = false,
            isEstimated = isEstimated,
            lastWeight = last.representativeWeight,
            lastReps = last.representativeReps,
            lastRpe = last.representativeRpe,
            suggestedWeight = ctx.weightOrNull(retryWeight)
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
        settings: ProgressionSettings,
        ctx: WeightContext
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
                convertSession(aggregated, fromIntent = fallbackIntent, toIntent = primaryIntent, settings, ctx)
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
        settings: ProgressionSettings,
        ctx: WeightContext
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
        
        // Snap here rather than downstream: a raw `1RM × percent` is an arbitrary float (116.67 ×
        // 0.70 = 81.67) that used to be prefilled verbatim into the kg field. Fixing it at the
        // source means every branch that consumes this session already has a loadable number.
        val estimatedWeight = WeightIncrementHelper.snap(source1RM * targetPercent, ctx.rule)

        return session.copy(
            representativeWeight = estimatedWeight,
            representativeReps = targetMinReps,
            estimated1RM = source1RM,  // Keep original 1RM for comparison
            isEstimated = true,
            sourceIntent = fromIntent,
            isWeightDerived = true
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
        val sourceIntent: SetIntent? = null,
        /**
         * True when [representativeWeight] was computed rather than lifted — a BUILD average or a
         * cross-intent estimate. Only a derived weight gets snapped onto the equipment ladder;
         * snapping a real logged set would overwrite what the user actually put on the bar.
         */
        val isWeightDerived: Boolean = false
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
                    hadFailure = sets.any { it.hadFailure },
                    // A mean across sets lands anywhere (65/70/70 -> 68.333…), so it must be
                    // snapped before it can be offered as a load.
                    isWeightDerived = true
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

    // ============================================================================================
    // 1RM ESTIMATION
    // ============================================================================================
    
    /**
     * Estimate 1RM using Epley formula: 1RM = weight × (1 + reps / 30)
     */
    private fun estimate1RM(weight: Float, reps: Int): Float {
        return weight * (1 + reps / 30f)
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
    
}
