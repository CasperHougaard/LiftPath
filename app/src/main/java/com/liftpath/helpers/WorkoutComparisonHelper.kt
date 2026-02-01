package com.liftpath.helpers

import android.content.Context
import com.liftpath.models.*

object WorkoutComparisonHelper {

    /**
     * Calculate summary statistics for a training session
     */
    fun calculateSessionSummary(
        session: TrainingSession,
        allSessions: List<TrainingSession>
    ): WorkoutSummary {
        // Filter out warmup sets
        val workingSets = session.exercises.filterNot { it.isWarmup }
        
        // Calculate total volume (kg × reps)
        val totalVolume = workingSets.sumOf { (it.kg * it.reps).toDouble() }.toFloat()
        
        // Count total sets (excluding warmups)
        val totalSets = workingSets.size
        
        // Count total reps
        val totalReps = workingSets.sumOf { it.reps }
        
        // Count unique exercises
        val exerciseCount = session.exercises.map { it.exerciseId }.distinct().size
        
        // Detect PRs
        val prCount = detectPRs(session, allSessions)
        
        return WorkoutSummary(
            totalVolume = totalVolume,
            totalSets = totalSets,
            totalReps = totalReps,
            exerciseCount = exerciseCount,
            durationSeconds = session.durationSeconds,
            prCount = prCount
        )
    }

    /**
     * Detect how many PRs were achieved in this session
     */
    private fun detectPRs(
        currentSession: TrainingSession,
        allSessions: List<TrainingSession>
    ): Int {
        var prCount = 0
        
        // Get previous sessions (excluding current)
        val previousSessions = allSessions.filter { it.id != currentSession.id }
        
        if (previousSessions.isEmpty()) {
            // First workout - all exercises are PRs
            return currentSession.exercises.map { it.exerciseId }.distinct().size
        }
        
        // Group exercises by ID
        val exerciseGroups = currentSession.exercises
            .groupBy { it.exerciseId }
        
        exerciseGroups.forEach { (exerciseId, currentSets) ->
            val currentWorkingSets = currentSets.filterNot { it.isWarmup }
            if (currentWorkingSets.isEmpty()) return@forEach
            
            // Get the intent for this exercise
            val exerciseIntent = if (currentSession.isLegacySession()) {
                currentSession.getLegacyExerciseIntent(exerciseId)
            } else {
                currentWorkingSets.first().explicitIntent ?: SetIntent.BUILD
            }
            
            // Find previous workouts with this exercise and same intent
            val previousSets = previousSessions
                .flatMap { session ->
                    session.exercises
                        .filter { it.exerciseId == exerciseId }
                        .filterNot { it.isWarmup }
                        .filter { entry ->
                            val prevIntent = if (session.isLegacySession()) {
                                session.getLegacyExerciseIntent(exerciseId)
                            } else {
                                entry.explicitIntent ?: SetIntent.BUILD
                            }
                            prevIntent == exerciseIntent
                        }
                }
            
            if (previousSets.isEmpty()) {
                // First time doing this exercise with this intent
                prCount++
                return@forEach
            }
            
            // Check for volume PR
            val currentVolume = currentWorkingSets.sumOf { (it.kg * it.reps).toDouble() }.toFloat()
            val bestPreviousVolume = previousSets
                .groupBy { findSessionForSet(it, previousSessions) }
                .mapValues { (_, sets) -> sets.sumOf { (it.kg * it.reps).toDouble() }.toFloat() }
                .maxOfOrNull { it.value } ?: 0f
            
            if (currentVolume > bestPreviousVolume) {
                prCount++
                return@forEach
            }
            
            // Check for weight PR (top set)
            val currentTopWeight = currentWorkingSets.maxOfOrNull { it.kg } ?: 0f
            val previousTopWeight = previousSets.maxOfOrNull { it.kg } ?: 0f
            
            if (currentTopWeight > previousTopWeight) {
                prCount++
                return@forEach
            }
            
            // Check for 1RM PR
            val current1RM = currentWorkingSets.mapNotNull { 
                OneRMEstimationHelper.calculateOneRM(it.kg, it.reps, it.rpe)
            }.maxOrNull()
            
            val previous1RM = previousSets.mapNotNull {
                OneRMEstimationHelper.calculateOneRM(it.kg, it.reps, it.rpe)
            }.maxOrNull()
            
            if (current1RM != null && previous1RM != null && current1RM > previous1RM) {
                prCount++
            }
        }
        
        return prCount
    }

    /**
     * Calculate trend data for each exercise
     */
    fun calculateExerciseTrends(
        currentSession: TrainingSession,
        allSessions: List<TrainingSession>,
        exerciseLibrary: List<ExerciseLibraryItem>
    ): List<ExerciseTrendData> {
        val trends = mutableListOf<ExerciseTrendData>()
        
        // Get previous sessions (excluding current)
        val previousSessions = allSessions.filter { it.id != currentSession.id }
        
        // Get exercise stats summaries (includes all-time PRs with dates)
        val exerciseSummaries = ProgressAnalysisHelper.getExerciseStatsSummaries(allSessions, exerciseLibrary)
        val summaryMap = exerciseSummaries.associateBy { it.exerciseId }
        
        // Group current session exercises
        val exerciseGroups = currentSession.exercises.groupBy { it.exerciseId }
        
        exerciseGroups.forEach { (exerciseId, currentSets) ->
            val currentWorkingSets = currentSets.filterNot { it.isWarmup }
            if (currentWorkingSets.isEmpty()) return@forEach
            
            val exerciseName = currentSets.first().exerciseName
            
            // Determine the intent for this exercise
            val exerciseIntent = if (currentSession.isLegacySession()) {
                currentSession.getLegacyExerciseIntent(exerciseId)
            } else {
                currentWorkingSets.first().explicitIntent ?: SetIntent.BUILD
            }
            
            // Calculate current metrics
            val currentVolume = currentWorkingSets.sumOf { (it.kg * it.reps).toDouble() }.toFloat()
            val current1RM = currentWorkingSets.mapNotNull { 
                OneRMEstimationHelper.calculateOneRM(it.kg, it.reps, it.rpe)
            }.maxOrNull()
            val currentTopSet = currentWorkingSets.maxByOrNull { it.kg }?.let { it.kg to it.reps }
            
            // Find previous workout with same exercise and intent
            val previousWorkout = findPreviousSessionForExercise(
                exerciseId, 
                exerciseIntent, 
                currentSession, 
                previousSessions
            )
            
            val (previousVolume, previous1RM, previousTopSet) = if (previousWorkout != null) {
                val prevSets = previousWorkout.exercises
                    .filter { it.exerciseId == exerciseId }
                    .filterNot { it.isWarmup }
                
                val prevVolume = prevSets.sumOf { (it.kg * it.reps).toDouble() }.toFloat()
                val prev1RM = prevSets.mapNotNull {
                    OneRMEstimationHelper.calculateOneRM(it.kg, it.reps, it.rpe)
                }.maxOrNull()
                val prevTopSet = prevSets.maxByOrNull { it.kg }?.let { it.kg to it.reps }
                
                Triple(prevVolume, prev1RM, prevTopSet)
            } else {
                Triple(null, null, null)
            }
            
            // Check if this is a PR
            val isPR = detectExercisePR(
                currentVolume, current1RM, currentTopSet,
                previousVolume, previous1RM, previousTopSet,
                exerciseId, exerciseIntent, previousSessions
            )
            
            // Get PR data from summary (all-time bests)
            val summary = summaryMap[exerciseId]
            
            trends.add(ExerciseTrendData(
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                intent = exerciseIntent,
                currentVolume = currentVolume,
                previousVolume = previousVolume,
                currentEstimated1RM = current1RM,
                previousEstimated1RM = previous1RM,
                currentTopSet = currentTopSet,
                previousTopSet = previousTopSet,
                isPR = isPR,
                prWeight = summary?.bestWeight,
                prWeightDate = summary?.lastPrDate ?: 0L,
                prVolume = summary?.bestVolume,
                prVolumeDate = summary?.lastPrDate ?: 0L,
                pr1RM = summary?.best1RM,
                pr1RMDate = summary?.lastPrDate ?: 0L,
                prReps = summary?.bestRepsRecord,
                prRepsDate = summary?.lastPrDate ?: 0L
            ))
        }
        
        return trends.sortedBy { it.exerciseName }
    }

    /**
     * Find the most recent previous session containing the specified exercise with matching intent
     */
    private fun findPreviousSessionForExercise(
        exerciseId: Int,
        targetIntent: SetIntent,
        currentSession: TrainingSession,
        previousSessions: List<TrainingSession>
    ): TrainingSession? {
        return previousSessions
            .sortedByDescending { it.date }
            .firstOrNull { session ->
                val hasExercise = session.exercises.any { it.exerciseId == exerciseId }
                if (!hasExercise) return@firstOrNull false
                
                // Check if the intent matches
                val sessionIntent = if (session.isLegacySession()) {
                    session.getLegacyExerciseIntent(exerciseId)
                } else {
                    session.exercises
                        .filter { it.exerciseId == exerciseId }
                        .filterNot { it.isWarmup }
                        .firstOrNull()
                        ?.explicitIntent ?: SetIntent.BUILD
                }
                
                sessionIntent == targetIntent
            }
    }

    /**
     * Check if this exercise achieved a PR
     */
    private fun detectExercisePR(
        currentVolume: Float,
        current1RM: Float?,
        currentTopSet: Pair<Float, Int>?,
        previousVolume: Float?,
        previous1RM: Float?,
        previousTopSet: Pair<Float, Int>?,
        exerciseId: Int,
        exerciseIntent: SetIntent,
        previousSessions: List<TrainingSession>
    ): Boolean {
        // If no previous data, it's a PR
        if (previousVolume == null) return true
        
        // Check volume PR
        if (currentVolume > previousVolume) return true
        
        // Check weight PR
        if (currentTopSet != null && previousTopSet != null) {
            if (currentTopSet.first > previousTopSet.first) return true
        }
        
        // Check 1RM PR
        if (current1RM != null && previous1RM != null) {
            if (current1RM > previous1RM) return true
        }
        
        return false
    }

    /**
     * Calculate muscle group trends (aggregate by major muscle groups)
     */
    fun calculateMuscleGroupTrends(
        currentSession: TrainingSession,
        allSessions: List<TrainingSession>,
        exerciseLibrary: List<ExerciseLibraryItem>
    ): List<MuscleGroupTrend> {
        val trends = mutableListOf<MuscleGroupTrend>()
        
        // Define muscle group mappings
        val muscleGroupMap = mapOf(
            "Chest" to listOf(TargetMuscle.CHEST_UPPER, TargetMuscle.CHEST_MIDDLE, TargetMuscle.CHEST_LOWER),
            "Back" to listOf(TargetMuscle.LATS, TargetMuscle.TRAPS_MID, TargetMuscle.TRAPS_UPPER, TargetMuscle.LOWER_BACK),
            "Shoulders" to listOf(TargetMuscle.DELT_FRONT, TargetMuscle.DELT_SIDE, TargetMuscle.DELT_REAR),
            "Arms" to listOf(TargetMuscle.BICEPS, TargetMuscle.TRICEPS_LONG, TargetMuscle.TRICEPS_LATERAL, TargetMuscle.FOREARMS),
            "Legs" to listOf(TargetMuscle.QUADS, TargetMuscle.HAMSTRINGS, TargetMuscle.GLUTES, TargetMuscle.CALVES, 
                TargetMuscle.TIBIALIS, TargetMuscle.ADDUCTORS, TargetMuscle.ABDUCTORS, TargetMuscle.HIPFLEXORS),
            "Core" to listOf(TargetMuscle.ABS, TargetMuscle.OBLIQUES)
        )
        
        muscleGroupMap.forEach { (groupName, muscles) ->
            // Calculate current volume for this muscle group
            val currentVolume = calculateMuscleGroupVolume(currentSession, muscles, exerciseLibrary)
            
            if (currentVolume > 0) {
                // Find previous session(s) that worked these muscles
                val previousVolume = findPreviousMuscleGroupVolume(
                    currentSession,
                    allSessions.filter { it.id != currentSession.id },
                    muscles,
                    exerciseLibrary
                )
                
                val changePercent = if (previousVolume != null && previousVolume > 0) {
                    ((currentVolume - previousVolume) / previousVolume) * 100f
                } else {
                    null
                }
                
                trends.add(MuscleGroupTrend(
                    muscleGroup = groupName,
                    currentVolume = currentVolume,
                    previousVolume = previousVolume,
                    changePercent = changePercent
                ))
            }
        }
        
        return trends
    }

    /**
     * Calculate per-muscle progress for coloring the muscle map
     */
    fun calculateMuscleProgress(
        currentSession: TrainingSession,
        allSessions: List<TrainingSession>,
        exerciseLibrary: List<ExerciseLibraryItem>
    ): Map<TargetMuscle, Float?> {
        val muscleProgressMap = mutableMapOf<TargetMuscle, Float?>()
        
        // Get all unique target muscles from the current session
        val workedMuscles = mutableSetOf<TargetMuscle>()
        currentSession.exercises
            .filter { !it.isWarmup }
            .forEach { entry ->
                val exercise = exerciseLibrary.find { it.id == entry.exerciseId }
                exercise?.let {
                    workedMuscles.addAll(it.primaryTargets)
                    workedMuscles.addAll(it.secondaryTargets)
                }
            }
        
        // For each worked muscle, calculate progress
        workedMuscles.forEach { muscle ->
            val currentVolume = calculateSingleMuscleVolume(currentSession, muscle, exerciseLibrary)
            val previousVolume = findPreviousSingleMuscleVolume(
                currentSession,
                allSessions.filter { it.id != currentSession.id },
                muscle,
                exerciseLibrary
            )
            
            val changePercent = if (previousVolume != null && previousVolume > 0) {
                ((currentVolume - previousVolume) / previousVolume) * 100f
            } else {
                null // First time working this muscle
            }
            
            muscleProgressMap[muscle] = changePercent
        }
        
        return muscleProgressMap
    }

    /**
     * Calculate volume for a specific muscle group
     */
    private fun calculateMuscleGroupVolume(
        session: TrainingSession,
        targetMuscles: List<TargetMuscle>,
        exerciseLibrary: List<ExerciseLibraryItem>
    ): Float {
        return session.exercises
            .filterNot { it.isWarmup }
            .filter { entry ->
                val exercise = exerciseLibrary.find { it.id == entry.exerciseId }
                exercise?.let { ex ->
                    ex.primaryTargets.any { it in targetMuscles } ||
                    ex.secondaryTargets.any { it in targetMuscles }
                } ?: false
            }
            .sumOf { (it.kg * it.reps).toDouble() }
            .toFloat()
    }

    /**
     * Calculate volume for a single muscle
     */
    private fun calculateSingleMuscleVolume(
        session: TrainingSession,
        targetMuscle: TargetMuscle,
        exerciseLibrary: List<ExerciseLibraryItem>
    ): Float {
        return session.exercises
            .filterNot { it.isWarmup }
            .filter { entry ->
                val exercise = exerciseLibrary.find { it.id == entry.exerciseId }
                exercise?.let { ex ->
                    targetMuscle in ex.primaryTargets || targetMuscle in ex.secondaryTargets
                } ?: false
            }
            .sumOf { (it.kg * it.reps).toDouble() }
            .toFloat()
    }

    /**
     * Find previous volume for a muscle group (most recent session that worked these muscles)
     */
    private fun findPreviousMuscleGroupVolume(
        currentSession: TrainingSession,
        previousSessions: List<TrainingSession>,
        targetMuscles: List<TargetMuscle>,
        exerciseLibrary: List<ExerciseLibraryItem>
    ): Float? {
        val previousSession = previousSessions
            .sortedByDescending { it.date }
            .firstOrNull { session ->
                // Check if this session worked any of the target muscles
                session.exercises.any { entry ->
                    val exercise = exerciseLibrary.find { it.id == entry.exerciseId }
                    exercise?.let { ex ->
                        ex.primaryTargets.any { it in targetMuscles } ||
                        ex.secondaryTargets.any { it in targetMuscles }
                    } ?: false
                }
            }
        
        return previousSession?.let {
            calculateMuscleGroupVolume(it, targetMuscles, exerciseLibrary)
        }
    }

    /**
     * Find previous volume for a single muscle
     */
    private fun findPreviousSingleMuscleVolume(
        currentSession: TrainingSession,
        previousSessions: List<TrainingSession>,
        targetMuscle: TargetMuscle,
        exerciseLibrary: List<ExerciseLibraryItem>
    ): Float? {
        val previousSession = previousSessions
            .sortedByDescending { it.date }
            .firstOrNull { session ->
                session.exercises.any { entry ->
                    val exercise = exerciseLibrary.find { it.id == entry.exerciseId }
                    exercise?.let { ex ->
                        targetMuscle in ex.primaryTargets || targetMuscle in ex.secondaryTargets
                    } ?: false
                }
            }
        
        return previousSession?.let {
            calculateSingleMuscleVolume(it, targetMuscle, exerciseLibrary)
        }
    }

    /**
     * Helper to find which session a set belongs to
     */
    private fun findSessionForSet(
        entry: ExerciseEntry,
        sessions: List<TrainingSession>
    ): TrainingSession? {
        return sessions.find { session ->
            session.exercises.any { 
                it.exerciseId == entry.exerciseId && 
                it.setNumber == entry.setNumber &&
                it.kg == entry.kg &&
                it.reps == entry.reps
            }
        }
    }
}
