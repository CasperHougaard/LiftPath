package com.liftpath.helpers

import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.SetIntent
import com.liftpath.models.TrainingSession
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.round

/**
 * Helper object for analyzing training progress across sessions.
 * Provides PR detection (Hybrid Athlete: Strength, Build, Flush), weekly summaries, and intent distribution.
 */
object ProgressAnalysisHelper {

    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

    /**
     * Get recent PRs across all exercises.
     * Uses 4-rule algorithm: Volume (all intents), 1RM (all intents, effectiveReps <= 15), Weight, Reps at weight (±1 kg).
     * At most one PR per type per exercise per session.
     */
    fun getRecentPRs(
        sessions: List<TrainingSession>,
        exerciseLibrary: List<ExerciseLibraryItem>,
        dayWindow: Int = 30
    ): List<PRRecord> {
        val cutoffTime = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -dayWindow)
        }.timeInMillis

        val (prs, _) = processSessionsForPRs(sessions)
        return prs.filter { pr ->
            try {
                (dateFormat.parse(pr.date)?.time ?: 0L) >= cutoffTime
            } catch (e: Exception) {
                false
            }
        }.distinctBy { "${it.exerciseName}_${it.prType}_${it.date}" }
    }

    /**
     * Get exercise stats summaries for the PR page (Player Stats Card list).
     * One summary per exercise that has at least one PR; sorted by lastPrDate DESC (caller).
     * lastPrDate is timestamp (Long) for flexible formatting (e.g. "2 days ago" / "Oct 24, 2025").
     */
    fun getExerciseStatsSummaries(
        sessions: List<TrainingSession>,
        exerciseLibrary: List<ExerciseLibraryItem>
    ): List<ExerciseStatsSummary> {
        val (_, processed) = processSessionsForPRs(sessions)
        val bestByExercise = processed.bestByExercise
        val lastPrDateByExercise = processed.lastPrDateByExercise

        return bestByExercise.keys
            .filter { exerciseId ->
                val b = bestByExercise[exerciseId]!!
                (b.maxWeight != null && b.maxWeight!! > 0f) ||
                    (b.max1RM != null && b.max1RM!! > 0f) ||
                    (b.maxVolume != null && b.maxVolume!! > 0f) ||
                    b.maxRepsAtWeight.isNotEmpty()
            }
            .map { exerciseId ->
                val b = bestByExercise[exerciseId]!!
                val name = exerciseLibrary.find { it.id == exerciseId }?.name
                    ?: sessions.asSequence()
                        .flatMap { it.exercises.asSequence() }
                        .firstOrNull { it.exerciseId == exerciseId }?.exerciseName
                    ?: "Exercise $exerciseId"
                val bestRepsRecord = if (b.maxRepsAtWeight.isEmpty()) null else {
                    val (reps, actualKg) = b.maxRepsAtWeight.values.maxByOrNull { it.first }!!
                    "${reps} reps @ ${actualKg}kg"
                }
                ExerciseStatsSummary(
                    exerciseId = exerciseId,
                    exerciseName = name,
                    best1RM = b.max1RM,
                    bestWeight = b.maxWeight,
                    bestVolume = b.maxVolume,
                    bestRepsRecord = bestRepsRecord,
                    lastPrDate = lastPrDateByExercise[exerciseId] ?: 0L
                )
            }
    }

    /**
     * Single chronological pass: build PR list (with session dedup) and per-exercise bests + lastPrDate.
     */
    private fun processSessionsForPRs(
        sessions: List<TrainingSession>
    ): Pair<List<PRRecord>, ProcessedBests> {
        val prs = mutableListOf<PRRecord>()
        val bestByExercise = mutableMapOf<Int, ExerciseBests>()
        val lastPrDateByExercise = mutableMapOf<Int, Long>()
        val sortedSessions = sessions.sortedBy { it.date }

        sortedSessions.forEach { session ->
            val sessionDate = try {
                dateFormat.parse(session.date)
            } catch (e: Exception) {
                null
            } ?: return@forEach
            val sessionTime = sessionDate.time

            val sessionBests = mutableMapOf<Int, SessionExerciseBests>()
            val sessionVolumeAccum = mutableMapOf<Int, Float>()

            session.exercises.filterNot { it.isWarmup }.forEach { entry ->
                val exerciseId = entry.exerciseId
                val exerciseName = entry.exerciseName
                val intent = entry.getEffectiveIntent(session.defaultWorkoutType)
                val current = bestByExercise.getOrPut(exerciseId) { ExerciseBests() }
                val sessionBest = sessionBests.getOrPut(exerciseId) { SessionExerciseBests() }

                // Weight PR (all intents)
                if (entry.kg > (sessionBest.bestWeight ?: 0f)) {
                    sessionBest.bestWeight = entry.kg
                }

                // 1RM PR (all intents; helper returns null when effectiveReps > 15 or RPE < 6.5)
                val oneRM = OneRMEstimationHelper.calculateOneRM(entry.kg, entry.reps, entry.rpe)
                if (oneRM != null && oneRM > (sessionBest.best1RM ?: 0f)) {
                    sessionBest.best1RM = oneRM
                }

                // Volume: accumulate per session (all intents)
                sessionVolumeAccum[exerciseId] = (sessionVolumeAccum[exerciseId] ?: 0f) + entry.kg * entry.reps

                // Reps PR: max reps at weight (±1 kg bucket); store actual weight for display
                val bucket = round(entry.kg).toInt()
                val historicalMaxReps = listOf(bucket - 1, bucket, bucket + 1)
                    .mapNotNull { current.maxRepsAtWeight[it]?.first }
                    .maxOrNull() ?: 0
                if (entry.reps > historicalMaxReps) {
                    current.maxRepsAtWeight[bucket] = Pair(entry.reps, entry.kg)
                    if (sessionBest.bestReps == null || entry.reps > sessionBest.bestReps!!.first) {
                        sessionBest.bestReps = Pair(entry.reps, entry.kg)
                        sessionBest.previousRepsForPr = historicalMaxReps
                    }
                }
            }

            // Session volume per exercise (already summed) — one PR per type per session
            sessionVolumeAccum.forEach { (exerciseId, sessionVolume) ->
                val current = bestByExercise.getOrPut(exerciseId) { ExerciseBests() }
                val sessionVolumeKey = "${exerciseId}_${session.date}"
                if (!current.sessionVolumes.contains(sessionVolumeKey) && sessionVolume > (current.maxVolume ?: 0f)) {
                    current.sessionVolumes.add(sessionVolumeKey)
                    val prevVolume = current.maxVolume
                    current.maxVolume = sessionVolume
                    val name = session.exercises.firstOrNull { it.exerciseId == exerciseId }?.exerciseName ?: "Exercise $exerciseId"
                    prs.add(PRRecord(name, intent = SetIntent.BUILD, prType = PRType.VOLUME, value = sessionVolume, previousValue = prevVolume, date = session.date))
                    lastPrDateByExercise[exerciseId] = sessionTime
                }
            }

            // Emit at most one PR per type per exercise for this session
            sessionBests.forEach { (exerciseId, sessionBest) ->
                val current = bestByExercise.getOrPut(exerciseId) { ExerciseBests() }
                val exerciseName = session.exercises.firstOrNull { it.exerciseId == exerciseId }?.exerciseName ?: "Exercise $exerciseId"

                if (sessionBest.bestWeight != null && sessionBest.bestWeight!! > (current.maxWeight ?: 0f)) {
                    val prev = current.maxWeight
                    current.maxWeight = sessionBest.bestWeight
                    prs.add(PRRecord(exerciseName, SetIntent.STRENGTH, PRType.WEIGHT, sessionBest.bestWeight!!, prev, session.date))
                    lastPrDateByExercise[exerciseId] = sessionTime
                }
                if (sessionBest.best1RM != null && sessionBest.best1RM!! > (current.max1RM ?: 0f)) {
                    val prev = current.max1RM
                    current.max1RM = sessionBest.best1RM
                    prs.add(PRRecord(exerciseName, SetIntent.STRENGTH, PRType.ONE_RM, sessionBest.best1RM!!, prev, session.date))
                    lastPrDateByExercise[exerciseId] = sessionTime
                }
                if (sessionBest.bestReps != null) {
                    val (reps, actualKg) = sessionBest.bestReps!!
                    val prevReps = sessionBest.previousRepsForPr ?: 0
                    prs.add(PRRecord(exerciseName, SetIntent.FLUSH, PRType.REPS, reps.toFloat(), prevReps.toFloat(), session.date))
                    lastPrDateByExercise[exerciseId] = sessionTime
                }
            }
        }

        return Pair(prs, ProcessedBests(bestByExercise, lastPrDateByExercise))
    }

    /**
     * Get weekly summary for a given week offset (0 = current week, 1 = last week, etc.)
     */
    fun getWeeklySummary(
        sessions: List<TrainingSession>,
        weekOffset: Int = 0
    ): WeeklySummary {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.WEEK_OF_YEAR, -weekOffset)
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val weekStart = calendar.time

        calendar.add(Calendar.DAY_OF_YEAR, 7)
        val weekEnd = calendar.time

        val weeklySessions = sessions.filter { session ->
            try {
                val date = dateFormat.parse(session.date)
                date != null && date.time >= weekStart.time && date.time < weekEnd.time
            } catch (e: Exception) {
                false
            }
        }

        val totalVolume = weeklySessions.sumOf { session ->
            session.exercises.filterNot { it.isWarmup }
                .sumOf { (it.kg * it.reps).toDouble() }
        }.toFloat()

        // Count PRs
        val prCount = 0 // Simplified - would need full PR tracking

        // Find dominant intent
        val intentCounts = mutableMapOf<SetIntent, Int>()
        weeklySessions.forEach { session ->
            session.exercises.filterNot { it.isWarmup }.forEach { entry ->
                val intent = entry.getEffectiveIntent(session.defaultWorkoutType)
                intentCounts[intent] = (intentCounts[intent] ?: 0) + 1
            }
        }
        val dominantIntent = intentCounts.maxByOrNull { it.value }?.key ?: SetIntent.BUILD

        return WeeklySummary(
            totalVolume = totalVolume,
            sessionCount = weeklySessions.size,
            prCount = prCount,
            dominantIntent = dominantIntent
        )
    }

    /**
     * Get intent distribution as percentages over a time window.
     */
    fun getIntentDistribution(
        sessions: List<TrainingSession>,
        dayWindow: Int = 30
    ): Map<SetIntent, Float> {
        val cutoffDate = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -dayWindow)
        }.time

        val intentCounts = mutableMapOf<SetIntent, Int>()
        var totalSets = 0

        sessions.forEach { session ->
            try {
                val date = dateFormat.parse(session.date) ?: return@forEach
                if (date.time < cutoffDate.time) return@forEach

                session.exercises.filterNot { it.isWarmup }.forEach { entry ->
                    val intent = entry.getEffectiveIntent(session.defaultWorkoutType)
                    if (intent != SetIntent.UNKNOWN && intent != SetIntent.WARMUP) {
                        intentCounts[intent] = (intentCounts[intent] ?: 0) + 1
                        totalSets++
                    }
                }
            } catch (e: Exception) {
                // Skip invalid dates
            }
        }

        if (totalSets == 0) return emptyMap()

        return intentCounts.mapValues { (_, count) ->
            (count.toFloat() / totalSets) * 100f
        }
    }

    // Data classes
    data class PRRecord(
        val exerciseName: String,
        val intent: SetIntent,
        val prType: PRType,
        val value: Float,
        val previousValue: Float? = null,
        val date: String
    )

    /** Per-exercise stats for PR page (Player Stats Card). lastPrDate is timestamp (Long) for flexible formatting. */
    data class ExerciseStatsSummary(
        val exerciseId: Int,
        val exerciseName: String,
        val best1RM: Float?,
        val bestWeight: Float?,
        val bestVolume: Float?,
        val bestRepsRecord: String?,  // e.g. "22 reps @ 52.5kg" (actual weight, not bucket)
        val lastPrDate: Long         // timestamp ms; 0 if no PRs
    )

    data class WeeklySummary(
        val totalVolume: Float,
        val sessionCount: Int,
        val prCount: Int,
        val dominantIntent: SetIntent
    )

    enum class PRType {
        WEIGHT,
        VOLUME,
        ONE_RM,
        REPS
    }

    private data class ProcessedBests(
        val bestByExercise: Map<Int, ExerciseBests>,
        val lastPrDateByExercise: Map<Int, Long>
    )

    private data class SessionExerciseBests(
        var bestWeight: Float? = null,
        var best1RM: Float? = null,
        var bestReps: Pair<Int, Float>? = null,  // reps, actualKg
        var previousRepsForPr: Int? = null
    )

    private data class ExerciseBests(
        var maxWeight: Float? = null,
        var max1RM: Float? = null,
        var maxVolume: Float? = null,
        val maxRepsAtWeight: MutableMap<Int, Pair<Int, Float>> = mutableMapOf(),  // bucket -> (maxReps, actualKg)
        val sessionVolumes: MutableSet<String> = mutableSetOf()
    )

    /**
     * Calculate muscle group trends over the last 3-4 weeks.
     * Returns a map of TargetMuscle to trend percentage (1RM > Volume > Strength priority).
     */
    fun getMuscleTrends(
        sessions: List<TrainingSession>,
        exerciseLibrary: List<ExerciseLibraryItem>,
        weeksBack: Int = 4
    ): Map<com.liftpath.models.TargetMuscle, Float?> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.WEEK_OF_YEAR, -weeksBack)
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val cutoffDate = calendar.time

        val filteredSessions = sessions.filter { session ->
            try {
                val date = dateFormat.parse(session.date)
                date != null && date.time >= cutoffDate.time
            } catch (e: Exception) {
                false
            }
        }.sortedBy { it.date }

        if (filteredSessions.isEmpty()) {
            return emptyMap()
        }

        // Split into two periods (first half vs second half)
        val halfwayPoint = filteredSessions.size / 2
        val firstPeriod = filteredSessions.take(halfwayPoint)
        val secondPeriod = filteredSessions.drop(halfwayPoint)

        // Group by muscle
        val muscleTrends = mutableMapOf<com.liftpath.models.TargetMuscle, MusclePeriodData>()

        fun processPeriod(period: List<TrainingSession>, isFirst: Boolean) {
            period.forEach { session ->
                session.exercises.filterNot { it.isWarmup }.forEach { entry ->
                    val exercise = exerciseLibrary.find { it.id == entry.exerciseId } ?: return@forEach
                    val intent = entry.getEffectiveIntent(session.defaultWorkoutType)
                    
                    // Process all target muscles for this exercise
                    (exercise.primaryTargets + exercise.secondaryTargets).forEach { muscle ->
                        val data = muscleTrends.getOrPut(muscle) { MusclePeriodData() }
                        
                        // Calculate 1RM (for strength sets)
                        if (intent == SetIntent.STRENGTH) {
                            val oneRM = OneRMEstimationHelper.calculateOneRM(entry.kg, entry.reps, entry.rpe)
                            if (oneRM != null) {
                                if (isFirst) {
                                    data.first1RM = maxOf(data.first1RM ?: 0f, oneRM)
                                } else {
                                    data.second1RM = maxOf(data.second1RM ?: 0f, oneRM)
                                }
                            }
                        }
                        
                        // Calculate volume (for build sets)
                        if (intent == SetIntent.BUILD) {
                            val volume = entry.kg * entry.reps
                            if (isFirst) {
                                data.firstVolume = (data.firstVolume ?: 0f) + volume
                            } else {
                                data.secondVolume = (data.secondVolume ?: 0f) + volume
                            }
                        }
                        
                        // Track strength weight (for strength sets)
                        if (intent == SetIntent.STRENGTH) {
                            if (isFirst) {
                                data.firstStrength = maxOf(data.firstStrength ?: 0f, entry.kg)
                            } else {
                                data.secondStrength = maxOf(data.secondStrength ?: 0f, entry.kg)
                            }
                        }
                    }
                }
            }
        }

        processPeriod(firstPeriod, true)
        processPeriod(secondPeriod, false)

        // Calculate trends with priority: 1RM > Volume > Strength
        return muscleTrends.mapValues { (_, data) ->
            // Priority 1: 1RM trend
            if (data.first1RM != null && data.second1RM != null && data.first1RM!! > 0f) {
                val trend = ((data.second1RM!! - data.first1RM!!) / data.first1RM!!) * 100f
                trend
            }
            // Priority 2: Volume trend
            else if (data.firstVolume != null && data.secondVolume != null && data.firstVolume!! > 0f) {
                val trend = ((data.secondVolume!! - data.firstVolume!!) / data.firstVolume!!) * 100f
                trend
            }
            // Priority 3: Strength weight trend
            else if (data.firstStrength != null && data.secondStrength != null && data.firstStrength!! > 0f) {
                val trend = ((data.secondStrength!! - data.firstStrength!!) / data.firstStrength!!) * 100f
                trend
            }
            // First time (no previous data)
            else if (data.second1RM != null || data.secondVolume != null || data.secondStrength != null) {
                null // First time
            }
            // No data
            else {
                null
            }
        }
    }

    private data class MusclePeriodData(
        var first1RM: Float? = null,
        var second1RM: Float? = null,
        var firstVolume: Float? = null,
        var secondVolume: Float? = null,
        var firstStrength: Float? = null,
        var secondStrength: Float? = null
    )
}
