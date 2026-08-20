package com.liftpath.helpers

import com.liftpath.models.*

/**
 * Calculates per-session workout summaries and per-exercise trend data for the workout report.
 *
 * Responsibility split (aligned with product rules):
 *  - PR counts in [WorkoutSummary] come from [ProgressAnalysisHelper.getPRsForSession] (all-time canonical PRs).
 *  - Exercise trend rows compare the current session against a per-intent rolling window
 *    (up to [TREND_WINDOW_SIZE] prior sessions with the same exercise and intent).
 *  - "Has a new all-time PR" on each trend row is determined by the canonical engine, not by
 *    comparing against only the immediately previous session.
 */
object WorkoutComparisonHelper {

    private const val TREND_WINDOW_SIZE = 6

    /**
     * Calculate summary statistics for a training session.
     * PR count comes from the canonical all-time PR engine.
     */
    fun calculateSessionSummary(
        session: TrainingSession,
        allSessions: List<TrainingSession>
    ): WorkoutSummary {
        val workingSets = session.exercises.filterNot { it.isWarmup || it.isSpecialSlotEntry() }

        // Timed holds carry no reps, so they contribute to neither volume nor the rep count —
        // their work is reported as hold time instead.
        val totalVolume = SetMetrics.totalVolumeKg(workingSets)
        val totalSets = workingSets.size
        val totalReps = SetMetrics.totalReps(workingSets)
        val exerciseCount = session.exercises.filterNot { it.isSpecialSlotEntry() }.map { it.exerciseId }.distinct().size

        // Canonical all-time PR count for this session
        val prCount = ProgressAnalysisHelper.getPRsForSession(allSessions, session.id).size

        return WorkoutSummary(
            totalVolume = totalVolume,
            totalSets = totalSets,
            totalReps = totalReps,
            exerciseCount = exerciseCount,
            durationSeconds = session.durationSeconds,
            prCount = prCount,
            totalHoldSeconds = SetMetrics.totalHoldSeconds(workingSets),
            holdSetCount = SetMetrics.holdSetCount(workingSets)
        )
    }

    /**
     * Calculate trend data for each exercise in the session.
     *
     * For each exercise the trend compares the current session against the most recent prior session
     * with the same exercise and same intent. The rolling window (up to [TREND_WINDOW_SIZE] sessions)
     * determines [ExerciseTrendData.intentSessionCount] which the adapter uses to decide whether
     * to show a confident trend or a "building baseline" message.
     *
     * All-time PR values come from [ProgressAnalysisHelper.getExerciseStatsSummaries]; whether a
     * new canonical PR was set this session comes from [ProgressAnalysisHelper.getPRsForSession].
     */
    fun calculateExerciseTrends(
        currentSession: TrainingSession,
        allSessions: List<TrainingSession>,
        exerciseLibrary: List<ExerciseLibraryItem>
    ): List<ExerciseTrendData> {
        val trends = mutableListOf<ExerciseTrendData>()
        val previousSessions = allSessions.filter { it.id != currentSession.id }

        // All-time bests per exercise (canonical engine)
        val summaryMap = ProgressAnalysisHelper
            .getExerciseStatsSummaries(allSessions, exerciseLibrary)
            .associateBy { it.exerciseId }

        // Set of exercise IDs that earned a canonical all-time PR in this session
        val sessionPRExerciseIds = ProgressAnalysisHelper
            .getPRsForSession(allSessions, currentSession.id)
            .map { it.exerciseId }
            .toSet()

        currentSession.exercises
            .filterNot { it.isSpecialSlotEntry() }
            .groupBy { it.exerciseId }
            .forEach { (exerciseId, currentSets) ->
                val currentWorkingSets = currentSets.filterNot { it.isWarmup }
                if (currentWorkingSets.isEmpty()) return@forEach

                val exerciseName = currentSets.first().exerciseName
                val exerciseIntent = resolveIntent(currentWorkingSets, currentSession)

                // Volume, 1RM and top set are all rep-based. Timed holds must be excluded from
                // them — otherwise a plank reports 0 kg of volume and a "0.0kg × 0" top set.
                val currentRepSets = SetMetrics.repBasedSets(currentWorkingSets)
                val isTimedExercise = SetMetrics.hasTimedWork(currentWorkingSets) &&
                    currentRepSets.isEmpty()

                // Current session metrics
                val currentVolume = SetMetrics.totalVolumeKg(currentRepSets)
                val current1RM = currentRepSets
                    .mapNotNull { OneRMEstimationHelper.calculateOneRM(it.kg, it.reps, it.rpe) }
                    .maxOrNull()
                val currentTopSet = currentRepSets.maxByOrNull { it.kg }
                    ?.let { it.kg to it.reps }
                val currentBestHold = SetMetrics.bestHoldSeconds(currentWorkingSets)
                val currentTotalHold = SetMetrics.totalHoldSeconds(currentWorkingSets)
                val currentLoadSeconds = SetMetrics.totalLoadSeconds(currentWorkingSets)
                    .takeIf { it > 0f }

                // Per-intent rolling window: up to TREND_WINDOW_SIZE previous sessions
                val intentSessions = findIntentSessions(
                    exerciseId, exerciseIntent, previousSessions, TREND_WINDOW_SIZE
                )
                val intentSessionCount = intentSessions.size
                val previousSession = intentSessions.firstOrNull()

                val prevSets = previousSession?.exercises
                    ?.filter { it.exerciseId == exerciseId }
                    ?.filterNot { it.isWarmup }
                val prevRepSets = prevSets?.let { SetMetrics.repBasedSets(it) }

                val previousVolume = prevRepSets?.let { SetMetrics.totalVolumeKg(it) }
                val previous1RM = prevRepSets
                    ?.mapNotNull { OneRMEstimationHelper.calculateOneRM(it.kg, it.reps, it.rpe) }
                    ?.maxOrNull()
                val previousTopSet = prevRepSets?.maxByOrNull { it.kg }?.let { it.kg to it.reps }
                val previousBestHold = prevSets?.let { SetMetrics.bestHoldSeconds(it) }
                val previousTotalHold = prevSets?.let { SetMetrics.totalHoldSeconds(it) }

                val summary = summaryMap[exerciseId]

                trends.add(
                    ExerciseTrendData(
                        exerciseId = exerciseId,
                        exerciseName = exerciseName,
                        intent = exerciseIntent,
                        currentVolume = currentVolume,
                        previousVolume = previousVolume,
                        currentEstimated1RM = current1RM,
                        previousEstimated1RM = previous1RM,
                        currentTopSet = currentTopSet,
                        previousTopSet = previousTopSet,
                        hasNewAllTimePR = exerciseId in sessionPRExerciseIds,
                        intentSessionCount = intentSessionCount,
                        prWeight = summary?.bestWeight,
                        prWeightDate = summary?.lastWeightPrDate ?: 0L,
                        prVolume = summary?.bestVolume,
                        prVolumeDate = summary?.lastVolumePrDate ?: 0L,
                        pr1RM = summary?.best1RM,
                        pr1RMDate = summary?.last1RMPrDate ?: 0L,
                        isTimedExercise = isTimedExercise,
                        currentBestHoldSeconds = currentBestHold,
                        previousBestHoldSeconds = previousBestHold?.takeIf { it > 0 },
                        currentTotalHoldSeconds = currentTotalHold,
                        previousTotalHoldSeconds = previousTotalHold?.takeIf { it > 0 },
                        currentLoadSeconds = currentLoadSeconds,
                        prHoldSeconds = summary?.bestHoldSeconds,
                        prHoldDate = summary?.lastHoldPrDate ?: 0L
                    )
                )
            }

        return trends.sortedBy { it.exerciseName }
    }

    /**
     * Find up to [maxCount] previous sessions that contain [exerciseId] with [targetIntent],
     * sorted newest first (so [first] is the most recent comparable session).
     */
    private fun findIntentSessions(
        exerciseId: Int,
        targetIntent: SetIntent,
        previousSessions: List<TrainingSession>,
        maxCount: Int
    ): List<TrainingSession> {
        return previousSessions
            .sortedByDescending { it.date }
            .filter { session ->
                session.exercises
                    .filter { it.exerciseId == exerciseId }
                    .filterNot { it.isWarmup }
                    .any { entry -> resolveIntentForEntry(entry, session) == targetIntent }
            }
            .take(maxCount)
    }

    /**
     * Resolve the dominant intent for an exercise across its working sets in a session.
     */
    private fun resolveIntent(
        workingSets: List<ExerciseEntry>,
        session: TrainingSession
    ): SetIntent {
        return if (session.isLegacySession()) {
            session.getLegacyExerciseIntent(workingSets.first().exerciseId)
        } else {
            workingSets.first().explicitIntent ?: SetIntent.BUILD
        }
    }

    private fun resolveIntentForEntry(entry: ExerciseEntry, session: TrainingSession): SetIntent {
        return if (session.isLegacySession()) {
            session.getLegacyExerciseIntent(entry.exerciseId)
        } else {
            entry.explicitIntent ?: SetIntent.BUILD
        }
    }

    /**
     * Calculate muscle group trends (volume vs most recent prior session that worked the group).
     * Used by the workout report muscle section.
     */
    fun calculateMuscleGroupTrends(
        currentSession: TrainingSession,
        allSessions: List<TrainingSession>,
        exerciseLibrary: List<ExerciseLibraryItem>
    ): List<MuscleGroupTrend> {
        val trends = mutableListOf<MuscleGroupTrend>()

        val muscleGroupMap = mapOf(
            "Chest" to listOf(TargetMuscle.CHEST_UPPER, TargetMuscle.CHEST_MIDDLE, TargetMuscle.CHEST_LOWER),
            "Back" to listOf(TargetMuscle.LATS, TargetMuscle.TRAPS_MID, TargetMuscle.TRAPS_UPPER, TargetMuscle.LOWER_BACK),
            "Shoulders" to listOf(TargetMuscle.DELT_FRONT, TargetMuscle.DELT_SIDE, TargetMuscle.DELT_REAR),
            "Arms" to listOf(TargetMuscle.BICEPS, TargetMuscle.TRICEPS_LONG, TargetMuscle.TRICEPS_LATERAL, TargetMuscle.FOREARMS),
            "Legs" to listOf(
                TargetMuscle.QUADS, TargetMuscle.HAMSTRINGS, TargetMuscle.GLUTES,
                TargetMuscle.CALVES, TargetMuscle.TIBIALIS, TargetMuscle.ADDUCTORS,
                TargetMuscle.ABDUCTORS, TargetMuscle.HIPFLEXORS
            ),
            "Core" to listOf(TargetMuscle.ABS, TargetMuscle.OBLIQUES)
        )

        val previousSessions = allSessions.filter { it.id != currentSession.id }

        muscleGroupMap.forEach { (groupName, muscles) ->
            val currentVolume = calculateMuscleGroupVolume(currentSession, muscles, exerciseLibrary)
            if (currentVolume > 0) {
                val previousVolume = findPreviousMuscleGroupVolume(
                    currentSession, previousSessions, muscles, exerciseLibrary
                )
                val changePercent = if (previousVolume != null && previousVolume > 0) {
                    ((currentVolume - previousVolume) / previousVolume) * 100f
                } else null

                trends.add(
                    MuscleGroupTrend(
                        muscleGroup = groupName,
                        currentVolume = currentVolume,
                        previousVolume = previousVolume,
                        changePercent = changePercent
                    )
                )
            }
        }

        return trends
    }

    /**
     * Per-muscle progress map for coloring the workout report muscle map.
     */
    fun calculateMuscleProgress(
        currentSession: TrainingSession,
        allSessions: List<TrainingSession>,
        exerciseLibrary: List<ExerciseLibraryItem>
    ): Map<TargetMuscle, Float?> {
        val muscleProgressMap = mutableMapOf<TargetMuscle, Float?>()
        val previousSessions = allSessions.filter { it.id != currentSession.id }

        val workedMuscles = mutableSetOf<TargetMuscle>()
        currentSession.exercises.filter { !it.isWarmup }.forEach { entry ->
            exerciseLibrary.find { it.id == entry.exerciseId }?.let {
                workedMuscles.addAll(it.primaryTargets)
                workedMuscles.addAll(it.secondaryTargets)
            }
        }

        workedMuscles.forEach { muscle ->
            val currentVolume = calculateSingleMuscleVolume(currentSession, muscle, exerciseLibrary)
            val previousVolume = findPreviousSingleMuscleVolume(
                currentSession, previousSessions, muscle, exerciseLibrary
            )
            muscleProgressMap[muscle] = if (previousVolume != null && previousVolume > 0) {
                ((currentVolume - previousVolume) / previousVolume) * 100f
            } else if (currentVolume == 0f) {
                // Purely isometric work for this muscle (e.g. a core session of only planks) has no
                // rep-based volume to compare, so fall back to time under tension. Without this the
                // muscle is marked "worked" with a null trend and paints as untouched.
                val currentHold = calculateSingleMuscleHoldSeconds(currentSession, muscle, exerciseLibrary)
                val previousHold = findPreviousSingleMuscleHoldSeconds(
                    previousSessions, muscle, exerciseLibrary
                )
                if (currentHold > 0 && previousHold != null && previousHold > 0) {
                    ((currentHold - previousHold).toFloat() / previousHold) * 100f
                } else null
            } else null
        }

        return muscleProgressMap
    }

    /** Sets of [session] that train any of [targetMuscles], warmups excluded. */
    private fun setsForMuscles(
        session: TrainingSession,
        targetMuscles: List<TargetMuscle>,
        exerciseLibrary: List<ExerciseLibraryItem>
    ): List<ExerciseEntry> {
        return session.exercises.filterNot { it.isWarmup }
            .filter { entry ->
                exerciseLibrary.find { it.id == entry.exerciseId }?.let { ex ->
                    ex.primaryTargets.any { it in targetMuscles } ||
                        ex.secondaryTargets.any { it in targetMuscles }
                } ?: false
            }
    }

    private fun calculateMuscleGroupVolume(
        session: TrainingSession,
        targetMuscles: List<TargetMuscle>,
        exerciseLibrary: List<ExerciseLibraryItem>
    ): Float = SetMetrics.totalVolumeKg(setsForMuscles(session, targetMuscles, exerciseLibrary))

    private fun calculateSingleMuscleVolume(
        session: TrainingSession,
        targetMuscle: TargetMuscle,
        exerciseLibrary: List<ExerciseLibraryItem>
    ): Float = SetMetrics.totalVolumeKg(setsForMuscles(session, listOf(targetMuscle), exerciseLibrary))

    private fun calculateSingleMuscleHoldSeconds(
        session: TrainingSession,
        targetMuscle: TargetMuscle,
        exerciseLibrary: List<ExerciseLibraryItem>
    ): Int = SetMetrics.totalHoldSeconds(setsForMuscles(session, listOf(targetMuscle), exerciseLibrary))

    /** Hold seconds for [targetMuscle] in the most recent prior session that trained it isometrically. */
    private fun findPreviousSingleMuscleHoldSeconds(
        previousSessions: List<TrainingSession>,
        targetMuscle: TargetMuscle,
        exerciseLibrary: List<ExerciseLibraryItem>
    ): Int? {
        return previousSessions.sortedByDescending { it.date }
            .firstNotNullOfOrNull { session ->
                calculateSingleMuscleHoldSeconds(session, targetMuscle, exerciseLibrary)
                    .takeIf { it > 0 }
            }
    }

    private fun findPreviousMuscleGroupVolume(
        currentSession: TrainingSession,
        previousSessions: List<TrainingSession>,
        targetMuscles: List<TargetMuscle>,
        exerciseLibrary: List<ExerciseLibraryItem>
    ): Float? {
        val prev = previousSessions.sortedByDescending { it.date }.firstOrNull { session ->
            session.exercises.any { entry ->
                exerciseLibrary.find { it.id == entry.exerciseId }?.let { ex ->
                    ex.primaryTargets.any { it in targetMuscles } ||
                        ex.secondaryTargets.any { it in targetMuscles }
                } ?: false
            }
        }
        return prev?.let { calculateMuscleGroupVolume(it, targetMuscles, exerciseLibrary) }
    }

    private fun findPreviousSingleMuscleVolume(
        currentSession: TrainingSession,
        previousSessions: List<TrainingSession>,
        targetMuscle: TargetMuscle,
        exerciseLibrary: List<ExerciseLibraryItem>
    ): Float? {
        val prev = previousSessions.sortedByDescending { it.date }.firstOrNull { session ->
            session.exercises.any { entry ->
                exerciseLibrary.find { it.id == entry.exerciseId }?.let { ex ->
                    targetMuscle in ex.primaryTargets || targetMuscle in ex.secondaryTargets
                } ?: false
            }
        }
        return prev?.let { calculateSingleMuscleVolume(it, targetMuscle, exerciseLibrary) }
    }
}
