package com.liftpath.helpers

import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.SetIntent
import com.liftpath.models.TrainingSession
import java.text.SimpleDateFormat
import java.util.*

/**
 * Helper object for analyzing training progress across sessions.
 * Provides PR detection, weekly summaries, and intent distribution analysis.
 */
object ProgressAnalysisHelper {

    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

    /**
     * Get recent PRs across all exercises.
     */
    fun getRecentPRs(
        sessions: List<TrainingSession>,
        exerciseLibrary: List<ExerciseLibraryItem>,
        dayWindow: Int = 30
    ): List<PRRecord> {
        val cutoffDate = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -dayWindow)
        }.time

        val prs = mutableListOf<PRRecord>()
        
        // Track best values per exercise/intent
        val bestByExercise = mutableMapOf<Int, ExerciseBests>()
        
        // Sort sessions by date (oldest first)
        val sortedSessions = sessions.sortedBy { it.date }

        sortedSessions.forEach { session ->
            val sessionDate = try {
                dateFormat.parse(session.date)
            } catch (e: Exception) {
                null
            } ?: return@forEach

            session.exercises.filterNot { it.isWarmup }.forEach { entry ->
                val exerciseId = entry.exerciseId
                val exerciseName = entry.exerciseName
                val intent = entry.getEffectiveIntent(session.defaultWorkoutType)
                
                val current = bestByExercise.getOrPut(exerciseId) { ExerciseBests() }

                // Check for weight PR
                if (entry.kg > (current.maxWeight ?: 0f)) {
                    if (sessionDate.time >= cutoffDate.time) {
                        prs.add(PRRecord(
                            exerciseName = exerciseName,
                            intent = intent,
                            prType = PRType.WEIGHT,
                            value = entry.kg,
                            previousValue = current.maxWeight,
                            date = session.date
                        ))
                    }
                    current.maxWeight = entry.kg
                }

                // Check for 1RM PR (only for strength intent)
                if (intent == SetIntent.STRENGTH) {
                    val oneRM = OneRMEstimationHelper.calculateOneRM(entry.kg, entry.reps, entry.rpe)
                    if (oneRM != null && oneRM > (current.max1RM ?: 0f)) {
                        if (sessionDate.time >= cutoffDate.time) {
                            prs.add(PRRecord(
                                exerciseName = exerciseName,
                                intent = intent,
                                prType = PRType.ONE_RM,
                                value = oneRM,
                                previousValue = current.max1RM,
                                date = session.date
                            ))
                        }
                        current.max1RM = oneRM
                    }
                }

                // Check for volume PR (per session, for build intent)
                if (intent == SetIntent.BUILD) {
                    val volume = entry.kg * entry.reps
                    val sessionVolumeKey = "${exerciseId}_${session.date}"
                    if (!current.sessionVolumes.contains(sessionVolumeKey)) {
                        current.sessionVolumes.add(sessionVolumeKey)
                        val sessionVolume = session.exercises
                            .filter { it.exerciseId == exerciseId && !it.isWarmup }
                            .sumOf { (it.kg * it.reps).toDouble() }
                            .toFloat()
                        
                        if (sessionVolume > (current.maxVolume ?: 0f)) {
                            if (sessionDate.time >= cutoffDate.time) {
                                prs.add(PRRecord(
                                    exerciseName = exerciseName,
                                    intent = intent,
                                    prType = PRType.VOLUME,
                                    value = sessionVolume,
                                    previousValue = current.maxVolume,
                                    date = session.date
                                ))
                            }
                            current.maxVolume = sessionVolume
                        }
                    }
                }
            }
        }

        return prs.distinctBy { "${it.exerciseName}_${it.prType}_${it.date}" }
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

    private data class ExerciseBests(
        var maxWeight: Float? = null,
        var max1RM: Float? = null,
        var maxVolume: Float? = null,
        var maxReps: Int? = null,
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
