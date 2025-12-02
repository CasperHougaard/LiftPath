package com.liftpath.helpers

import com.liftpath.models.*
import kotlin.math.pow

/**
 * Configuration object for readiness calculations.
 * Now loads from ReadinessSettingsManager.
 */
data class ReadinessConfig(
    val recoverySpeedMultiplier: Float = 1.0f, // 1.0 = Normal, 1.2 = Fast Recovery
    val defaultRPE: Float = 7.0f,
    val allowRunningOnTiredLegs: Boolean = false, // "Impact Tolerance" toggle (strictRunBlocking inverse)
    val thresholds: Thresholds = Thresholds(),
    val ignoreWeekends: Boolean = false
) {
    data class Thresholds(
        val high: Float = 50f,
        val moderate: Float = 30f,
        val cnsMax: Float = 80f
    )

    companion object {
        /**
         * Creates ReadinessConfig from ReadinessSettingsManager settings.
         */
        fun fromSettings(settings: ReadinessSettingsManager.ReadinessSettings): ReadinessConfig {
            val highThreshold = settings.getHighThreshold()
            return ReadinessConfig(
                recoverySpeedMultiplier = settings.recoverySpeedMultiplier,
                defaultRPE = settings.defaultRPE,
                allowRunningOnTiredLegs = !settings.strictRunBlocking,
                thresholds = Thresholds(
                    high = highThreshold,
                    moderate = 30f, // Moderate stays at 30
                    cnsMax = 80f // CNS max stays at 80
                ),
                ignoreWeekends = settings.ignoreFatigueOnWeekends
            )
        }
    }
}

/**
 * Three-score fatigue system: lower, upper, and systemic (sum of both).
 */
data class FatigueScores(
    val lowerFatigue: Float,
    val upperFatigue: Float,
    val systemicFatigue: Float
)

/**
 * Fatigue values at a point in time (lower, upper, systemic).
 */
data class FatigueValues(
    val lowerFatigue: Float,
    val upperFatigue: Float,
    val systemicFatigue: Float
)

/**
 * Timeline data for continuous fatigue simulation.
 */
data class FatigueTimeline(
    val graphPoints: List<Pair<Long, FatigueValues>>, // (timestamp, fatigueValues) for chart
    val dailyEndValues: Map<String, FatigueValues>    // (dateString, fatigueValuesAtEndOfDay) for calendar
)

/**
 * Activity readiness status.
 */
enum class ActivityStatus {
    GREEN,  // Ready to go
    YELLOW, // Caution - proceed with care
    RED     // Blocked - rest required
}

/**
 * Readiness information for a specific activity.
 */
data class ActivityReadiness(
    val status: ActivityStatus,
    val timeUntilFresh: Long? = null, // Milliseconds until recovery, null if already fresh
    val message: String // User-friendly status message
)

object ReadinessHelper {
    /**
     * Derives BodyRegion from primaryTargets when region is missing from exercise.
     */
    fun deriveRegionFromTargets(primaryTargets: List<TargetMuscle>): BodyRegion? {
        if (primaryTargets.isEmpty()) return null

        val lowerBodyTargets = setOf(
            TargetMuscle.QUADS, TargetMuscle.HAMSTRINGS, TargetMuscle.GLUTES,
            TargetMuscle.CALVES, TargetMuscle.TIBIALIS, TargetMuscle.ADDUCTORS,
            TargetMuscle.ABDUCTORS, TargetMuscle.HIPFLEXORS
        )

        val upperBodyTargets = setOf(
            TargetMuscle.CHEST_UPPER, TargetMuscle.CHEST_MIDDLE, TargetMuscle.CHEST_LOWER,
            TargetMuscle.LATS, TargetMuscle.TRAPS_MID, TargetMuscle.TRAPS_UPPER,
            TargetMuscle.LOWER_BACK, TargetMuscle.DELT_FRONT, TargetMuscle.DELT_SIDE,
            TargetMuscle.DELT_REAR, TargetMuscle.BICEPS, TargetMuscle.TRICEPS_LONG,
            TargetMuscle.TRICEPS_LATERAL, TargetMuscle.FOREARMS
        )

        val coreTargets = setOf(
            TargetMuscle.ABS, TargetMuscle.OBLIQUES
        )

        val hasLower = primaryTargets.any { it in lowerBodyTargets }
        val hasUpper = primaryTargets.any { it in upperBodyTargets }
        val hasCore = primaryTargets.any { it in coreTargets }

        return when {
            hasLower && hasUpper -> BodyRegion.FULL
            hasLower -> BodyRegion.LOWER
            hasUpper -> BodyRegion.UPPER
            hasCore -> BodyRegion.CORE
            else -> null
        }
    }

    /**
     * Calculates fatigue scores from the last workout using the three-score system.
     */
    fun calculateFatigueScores(
        session: TrainingSession,
        trainingData: TrainingData,
        config: ReadinessConfig = ReadinessConfig()
    ): FatigueScores {
        var lowerFatigue = 0f
        var upperFatigue = 0f

        // Group exercises by exerciseId to process sets together
        val exercisesGrouped = session.exercises.groupBy { it.exerciseId }

        for ((exerciseId, sets) in exercisesGrouped) {
            val exercise = trainingData.exerciseLibrary.find { it.id == exerciseId }
                ?: continue // Skip if exercise not found in library

            // Determine region
            val region = exercise.region ?: deriveRegionFromTargets(exercise.primaryTargets)
                ?: continue // Skip if region cannot be determined

            // Get tier multiplier
            val tierMultiplier = when (exercise.tier) {
                Tier.TIER_1 -> 1.5f
                Tier.TIER_2 -> 1.2f
                Tier.TIER_3 -> 0.8f
                null -> 1.0f // Default if tier is missing
            }

            // Calculate load for all sets of this exercise
            // Each set contributes: (RPE or defaultRPE) * TierMultiplier
            val setLoad = sets.sumOf { set ->
                val rpe = set.rpe ?: config.defaultRPE
                (rpe * tierMultiplier).toDouble()
            }.toFloat()

            // Distribute load based on region
            when (region) {
                BodyRegion.LOWER -> {
                    lowerFatigue += setLoad
                }
                BodyRegion.UPPER -> {
                    upperFatigue += setLoad
                }
                BodyRegion.FULL -> {
                    // Full body: 70% to lower, 50% to upper
                    lowerFatigue += setLoad * 0.7f
                    upperFatigue += setLoad * 0.5f
                }
                BodyRegion.CORE -> {
                    // CORE exercises contribute minimal systemic load
                    // Could add small amount to both, but for now we'll skip
                }
            }
        }

        val systemicFatigue = lowerFatigue + upperFatigue

        return FatigueScores(
            lowerFatigue = lowerFatigue,
            upperFatigue = upperFatigue,
            systemicFatigue = systemicFatigue
        )
    }

    /**
     * Calculates recovery time in milliseconds based on fatigue level.
     */
    /**
     * Calculates recovery time with "Diminishing Returns" logic.
     * 
     * Why: A workout with 300 fatigue is harder than 100, but it doesn't take 3x as long 
     * to recover. The body parallelizes repair.
     */
    private fun calculateRecoveryTime(fatigue: Float, config: ReadinessConfig): Long {
        val baseHours = when {
            // HIGH FATIGUE (> 50)
            fatigue > config.thresholds.high -> {
                val excess = fatigue - config.thresholds.high
                
                // NEW LOGIC: 
                // 1. Base is 48 hours.
                // 2. We add time for excess, but we CAP the impact or dampen it.
                //    Instead of 0.5h per point, we use 0.15h.
                // 3. We create a "Hard Max" of 96 hours (4 days). 
                //    Unless you are injured, standard physiology rarely takes > 4 days 
                //    to return to baseline for a single session.
                
                val extraHours = (excess * 0.15f).toInt() 
                (48 + extraHours).coerceAtMost(96) // Max 4 days recovery
            }
            
            // MODERATE (30-50)
            fatigue >= config.thresholds.moderate -> 24 
            
            // LOW (< 30)
            else -> 12 
        }

        // Apply Speed Multiplier (High multiplier = Faster recovery = Lower hours)
        val adjustedHours = (baseHours / config.recoverySpeedMultiplier).toInt()
        
        return adjustedHours * 3600_000L
    }

    /**
     * Applies exponential decay (half-life) to a fatigue score based on elapsed time.
     * 
     * @param originalScore The original fatigue score at workout completion
     * @param durationMs The time elapsed since the workout (in milliseconds)
     * @param config The readiness configuration containing recovery parameters
     * @return The decayed fatigue score (0 if fully recovered)
     * 
     * Uses 48-hour half-life exponential decay: decayedScore = originalScore * (0.5 ^ (hoursElapsed / 48.0))
     * This matches biological CNS recovery patterns where recovery is rapid initially, then slows.
     */
    fun getDecayedScore(originalScore: Float, durationMs: Long, config: ReadinessConfig): Float {
        if (originalScore <= 0f) return 0f
        
        // Convert duration to hours
        val hoursElapsed = durationMs.toFloat() / (3600f * 1000f)
        
        // Exponential decay with 48-hour half-life
        // Formula: decayedScore = originalScore * (0.5 ^ (hoursElapsed / 48.0))
        val decayFactor = 0.5.pow(hoursElapsed / 48.0).toFloat()
        return (originalScore * decayFactor).coerceAtLeast(0f)
    }

    /**
     * Gets readiness status for Running/Cycling activities.
     */
    fun getRunCycleStatus(
        fatigueScores: FatigueScores,
        config: ReadinessConfig = ReadinessConfig()
    ): ActivityReadiness {
        // Check weekend override
        if (config.ignoreWeekends && isWeekend()) {
            return ActivityReadiness(
                status = ActivityStatus.GREEN,
                message = "Weekend mode - Warnings disabled"
            )
        }

        val lowerFatigue = fatigueScores.lowerFatigue

        return when {
            lowerFatigue > config.thresholds.high -> {
                // If strict run blocking is enabled (allowRunningOnTiredLegs = false), block it
                if (!config.allowRunningOnTiredLegs) {
                    val recoveryTime = calculateRecoveryTime(lowerFatigue, config)
                    ActivityReadiness(
                        status = ActivityStatus.RED,
                        timeUntilFresh = recoveryTime,
                        message = "Blocked - Rest required"
                    )
                } else {
                    // Allow easy runs even with high fatigue
                    val recoveryTime = calculateRecoveryTime(lowerFatigue, config)
                    ActivityReadiness(
                        status = ActivityStatus.YELLOW,
                        timeUntilFresh = recoveryTime,
                        message = "Caution - Easy Zone 2 only"
                    )
                }
            }
            lowerFatigue >= config.thresholds.moderate -> {
                val recoveryTime = calculateRecoveryTime(lowerFatigue, config)
                ActivityReadiness(
                    status = ActivityStatus.YELLOW,
                    timeUntilFresh = recoveryTime,
                    message = "Caution - Easy Zone 2 only"
                )
            }
            else -> {
                ActivityReadiness(
                    status = ActivityStatus.GREEN,
                    message = "Ready to go"
                )
            }
        }
    }

    /**
     * Gets readiness status for Swimming activities.
     */
    fun getSwimStatus(
        fatigueScores: FatigueScores,
        config: ReadinessConfig = ReadinessConfig()
    ): ActivityReadiness {
        // Check weekend override
        if (config.ignoreWeekends && isWeekend()) {
            return ActivityReadiness(
                status = ActivityStatus.GREEN,
                message = "Weekend mode - Warnings disabled"
            )
        }

        val upperFatigue = fatigueScores.upperFatigue

        return when {
            upperFatigue > config.thresholds.high -> {
                val recoveryTime = calculateRecoveryTime(upperFatigue, config)
                ActivityReadiness(
                    status = ActivityStatus.RED,
                    timeUntilFresh = recoveryTime,
                    message = "Blocked - Rest required"
                )
            }
            upperFatigue >= config.thresholds.moderate -> {
                val recoveryTime = calculateRecoveryTime(upperFatigue, config)
                ActivityReadiness(
                    status = ActivityStatus.YELLOW,
                    timeUntilFresh = recoveryTime,
                    message = "Caution - Easy pace only"
                )
            }
            else -> {
                ActivityReadiness(
                    status = ActivityStatus.GREEN,
                    message = "Ready to go"
                )
            }
        }
    }

    /**
     * Gets readiness status for Lower Body Lifting activities.
     */
    fun getLowerLiftStatus(
        fatigueScores: FatigueScores,
        config: ReadinessConfig = ReadinessConfig()
    ): ActivityReadiness {
        // Check weekend override
        if (config.ignoreWeekends && isWeekend()) {
            return ActivityReadiness(
                status = ActivityStatus.GREEN,
                message = "Weekend mode - Warnings disabled"
            )
        }

        // Check CNS burnout first (systemic fatigue > 80)
        if (fatigueScores.systemicFatigue > config.thresholds.cnsMax) {
            val recoveryTime = calculateRecoveryTime(fatigueScores.systemicFatigue, config)
            return ActivityReadiness(
                status = ActivityStatus.RED,
                timeUntilFresh = recoveryTime,
                message = "CNS burnout - Full rest required"
            )
        }

        // Otherwise check lower fatigue
        val lowerFatigue = fatigueScores.lowerFatigue

        return when {
            lowerFatigue > config.thresholds.high -> {
                val recoveryTime = calculateRecoveryTime(lowerFatigue, config)
                ActivityReadiness(
                    status = ActivityStatus.RED,
                    timeUntilFresh = recoveryTime,
                    message = "Blocked - Rest required"
                )
            }
            lowerFatigue >= config.thresholds.moderate -> {
                val recoveryTime = calculateRecoveryTime(lowerFatigue, config)
                ActivityReadiness(
                    status = ActivityStatus.YELLOW,
                    timeUntilFresh = recoveryTime,
                    message = "Caution - Light work only"
                )
            }
            else -> {
                ActivityReadiness(
                    status = ActivityStatus.GREEN,
                    message = "Ready to go"
                )
            }
        }
    }

    /**
     * Gets readiness status for Upper Body Lifting activities.
     */
    fun getUpperLiftStatus(
        fatigueScores: FatigueScores,
        config: ReadinessConfig = ReadinessConfig()
    ): ActivityReadiness {
        // Check weekend override
        if (config.ignoreWeekends && isWeekend()) {
            return ActivityReadiness(
                status = ActivityStatus.GREEN,
                message = "Weekend mode - Warnings disabled"
            )
        }

        // Check CNS burnout first (systemic fatigue > 80)
        if (fatigueScores.systemicFatigue > config.thresholds.cnsMax) {
            val recoveryTime = calculateRecoveryTime(fatigueScores.systemicFatigue, config)
            return ActivityReadiness(
                status = ActivityStatus.RED,
                timeUntilFresh = recoveryTime,
                message = "CNS burnout - Full rest required"
            )
        }

        // Otherwise check upper fatigue
        val upperFatigue = fatigueScores.upperFatigue

        return when {
            upperFatigue > config.thresholds.high -> {
                val recoveryTime = calculateRecoveryTime(upperFatigue, config)
                ActivityReadiness(
                    status = ActivityStatus.RED,
                    timeUntilFresh = recoveryTime,
                    message = "Blocked - Rest required"
                )
            }
            upperFatigue >= config.thresholds.moderate -> {
                val recoveryTime = calculateRecoveryTime(upperFatigue, config)
                ActivityReadiness(
                    status = ActivityStatus.YELLOW,
                    timeUntilFresh = recoveryTime,
                    message = "Caution - Light work only"
                )
            }
            else -> {
                ActivityReadiness(
                    status = ActivityStatus.GREEN,
                    message = "Ready to go"
                )
            }
        }
    }

    /**
     * Checks if today is a weekend (Saturday or Sunday).
     */
    private fun isWeekend(): Boolean {
        val dayOfWeek = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        return dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY
    }

    /**
     * Calculates decayed fatigue for the last N days.
     * Returns a list of pairs: (dateString, currentDecayedFatigue)
     * 
     * For each day:
     * - If there was a workout, calculates raw fatigue and applies decay based on time elapsed
     * - If no workout, returns 0f (no fatigue)
     * 
     * @param trainingData The training data containing workouts
     * @param config The readiness configuration
     * @param daysBack Number of days to look back (default 7)
     * @return List of (dateString, decayedSystemicFatigue) pairs, ordered from oldest to newest
     */
    fun calculateDecayedFatigueForLastDays(
        trainingData: TrainingData,
        config: ReadinessConfig,
        daysBack: Int = 7
    ): List<Pair<String, Float>> {
        val calendar = java.util.Calendar.getInstance()
        val dateFormat = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
        val now = System.currentTimeMillis()
        val results = mutableListOf<Pair<String, Float>>()

        // Get all workouts grouped by date
        val workoutsByDate = trainingData.trainings.groupBy { it.date }

        // For each of the last N days, including today as the last day
        // daysBack = 7 means: 6 days ago, 5 days ago, ..., 1 day ago, today (7 days total)
        for (i in (daysBack - 1) downTo 0) {
            calendar.time = java.util.Date()
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -i)
            val dateStr = dateFormat.format(calendar.time)
            val dateTime = calendar.timeInMillis

            // Get all workouts for this date (there might be multiple)
            val workouts = workoutsByDate[dateStr] ?: emptyList()
            
            val decayedFatigue = if (workouts.isNotEmpty()) {
                // Calculate total raw fatigue from all workouts on this day
                var totalRawSystemicFatigue = 0f
                workouts.forEach { workout ->
                    val rawScores = calculateFatigueScores(workout, trainingData, config)
                    totalRawSystemicFatigue += rawScores.systemicFatigue
                }
                
                // Calculate elapsed time from workout date (start of day) to now
                val elapsedTime = now - dateTime
                
                // Apply exponential decay (half-life) to the total fatigue
                getDecayedScore(totalRawSystemicFatigue, elapsedTime, config)
            } else {
                // No workout on this day - no fatigue
                0f
            }

            results.add(Pair(dateStr, decayedFatigue))
        }

        return results
    }

    /**
     * Generates data points for a fatigue graph showing accumulated load and decay over time.
     * 
     * How it works (Sum of Residuals):
     * At every hour point in the graph, it looks back at recent workouts.
     * It calculates how much fatigue is LEFT over from each workout using getDecayedScore.
     * It sums them up. If you worked out yesterday (residual 20) and today (acute 60), 
     * the graph will show 80.
     * 
     * @param startTimestamp The start time for the graph X-axis
     * @param endTimestamp The end time for the graph X-axis
     * @param stepSizeMs Resolution of the graph (default 1 hour for smooth curves)
     */
    fun getAccumulatedFatigueCurve(
        startTimestamp: Long,
        endTimestamp: Long,
        trainingData: TrainingData,
        config: ReadinessConfig,
        stepSizeMs: Long = 3600_000L // 1 Hour steps
    ): List<Pair<Long, Float>> {
        val dataPoints = mutableListOf<Pair<Long, Float>>()
        
        // Date format for parsing workout dates
        val dateFormat = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
        
        // 1. Optimization: We only need to look back a certain amount of time.
        // Even with slow recovery, lifting fatigue rarely lasts > 120 hours (5 days).
        // We filter workouts to avoid looping through the entire history for every hour.
        val lookbackWindow = 5 * 24 * 3600_000L 
        
        // Filter for relevant workouts: Any workout that ended BEFORE the current point 
        // but within the lookback window.
        // Since TrainingSession doesn't have endTime, we'll calculate it from the date
        val relevantWorkouts = trainingData.trainings.filter { session ->
            try {
                val workoutDate = dateFormat.parse(session.date) ?: return@filter false
                // Use noon (12:00 PM) as a reasonable workout end time, or add duration if available
                val calendar = java.util.Calendar.getInstance().apply {
                    time = workoutDate
                    set(java.util.Calendar.HOUR_OF_DAY, 12)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val workoutEndTime = calendar.timeInMillis + (session.durationSeconds?.times(1000) ?: 0L)
                workoutEndTime in (startTimestamp - lookbackWindow)..endTimestamp
            } catch (e: Exception) {
                false
            }
        }

        // 2. Iterate through time from start to end
        var currentTime = startTimestamp
        while (currentTime <= endTimestamp) {
            
            var currentTotalFatigue = 0f

            // 3. For this specific moment in time, sum up the residual fatigue from all recent workouts
            relevantWorkouts.forEach { workout ->
                try {
                    val workoutDate = dateFormat.parse(workout.date) ?: return@forEach
                    val calendar = java.util.Calendar.getInstance().apply {
                        time = workoutDate
                        set(java.util.Calendar.HOUR_OF_DAY, 12)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    val workoutEndTime = calendar.timeInMillis + (workout.durationSeconds?.times(1000) ?: 0L)
                    
                    if (workoutEndTime <= currentTime) {
                        val rawScores = calculateFatigueScores(workout, trainingData, config)
                        
                        // How long ago was this workout?
                        val durationSinceWorkout = currentTime - workoutEndTime
                        
                        // Use exponential decay (half-life) logic
                        // This naturally handles "Stacking". If 3 workouts are decaying, 
                        // this adds up their remaining values.
                        val residualFatigue = getDecayedScore(
                            rawScores.systemicFatigue, 
                            durationSinceWorkout, 
                            config
                        )
                        
                        currentTotalFatigue += residualFatigue
                    }
                } catch (e: Exception) {
                    // Skip workouts with invalid dates
                }
            }

            dataPoints.add(Pair(currentTime, currentTotalFatigue))
            currentTime += stepSizeMs
        }

        return dataPoints
    }

    /**
     * Calculates daily fatigue values showing raw fatigue and decayed fatigue for each day.
     * Returns a list with raw fatigue, current decayed fatigue, and accumulated total.
     * 
     * @param trainingData The training data containing workouts
     * @param config The readiness configuration
     * @param daysBack Number of days to look back (default 7)
     * @return List of DailyFatigueData containing date, raw fatigue, decayed fatigue, and accumulated total
     */
    data class DailyFatigueData(
        val date: String,
        val rawFatigue: Float,
        val decayedFatigue: Float,
        val accumulatedTotal: Float // Sum of all residual fatigue from this day and previous days
    )

    fun calculateDailyFatigueWithDecay(
        trainingData: TrainingData,
        config: ReadinessConfig,
        daysBack: Int = 7
    ): List<DailyFatigueData> {
        val calendar = java.util.Calendar.getInstance()
        val dateFormat = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
        val now = System.currentTimeMillis()
        val results = mutableListOf<DailyFatigueData>()

        // Get all workouts grouped by date
        val workoutsByDate = trainingData.trainings.groupBy { it.date }

        var accumulatedTotal = 0f

        // For each of the last N days, including today as the last day
        for (i in (daysBack - 1) downTo 0) {
            calendar.time = java.util.Date()
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -i)
            val dateStr = dateFormat.format(calendar.time)
            val dateTime = calendar.timeInMillis

            // Get all workouts for this date
            val workouts = workoutsByDate[dateStr] ?: emptyList()
            
            val rawFatigue = if (workouts.isNotEmpty()) {
                // Calculate total raw fatigue from all workouts on this day
                var totalRawSystemicFatigue = 0f
                workouts.forEach { workout ->
                    val rawScores = calculateFatigueScores(workout, trainingData, config)
                    totalRawSystemicFatigue += rawScores.systemicFatigue
                }
                totalRawSystemicFatigue
            } else {
                0f
            }

            // Calculate elapsed time from workout date to now
            val elapsedTime = now - dateTime
            
            // Apply exponential decay (half-life)
            val decayedFatigue = if (rawFatigue > 0) {
                getDecayedScore(rawFatigue, elapsedTime, config)
            } else {
                0f
            }

            // Add this day's residual fatigue to accumulated total
            accumulatedTotal += decayedFatigue

            results.add(DailyFatigueData(
                date = dateStr,
                rawFatigue = rawFatigue,
                decayedFatigue = decayedFatigue,
                accumulatedTotal = accumulatedTotal
            ))
        }

        return results
    }

    /**
     * Data class to hold timeline events (both internal workouts and external activities)
     */
    data class TimelineEvent(
        val timestamp: Long,
        val impact: FatigueScores
    )

    /**
     * Calculates continuous fatigue timeline using hour-by-hour simulation (bucket method).
     * Accumulates fatigue from workouts and applies decay each hour.
     * 
     * @param trainingData The training data containing workouts
     * @param config The readiness configuration
     * @param externalActivities Optional list of external activities from Health Connect
     * @return FatigueTimeline with graph points and daily end values
     */
    fun calculateContinuousFatigueTimeline(
        trainingData: TrainingData,
        config: ReadinessConfig,
        externalActivities: List<ExternalActivity> = emptyList()
    ): FatigueTimeline {
        val dateFormat = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
        val now = System.currentTimeMillis()
        
        // Start time: 7 days ago at midnight
        val startCalendar = java.util.Calendar.getInstance().apply {
            time = java.util.Date()
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, -7)
        }
        val startTime = startCalendar.timeInMillis
        
        // End time: Now + 48 hours
        val endTime = now + (48 * 3600_000L)
        
        // Step size: 1 hour
        val stepSizeMs = 3600_000L
        
        // Initialize simulation state - track three separate stacks
        var currentLowerStack = 0f
        var currentUpperStack = 0f
        var currentSystemicStack = 0f
        val graphPoints = mutableListOf<Pair<Long, FatigueValues>>()
        val dailyEndValues = mutableMapOf<String, FatigueValues>()
        
        // Pre-process workouts: map them to their end timestamps
        val workoutsByEndTime = mutableMapOf<Long, MutableList<TrainingSession>>()
        trainingData.trainings.forEach { workout ->
            try {
                val workoutDate = dateFormat.parse(workout.date) ?: return@forEach
                val calendar = java.util.Calendar.getInstance().apply {
                    time = workoutDate
                    set(java.util.Calendar.HOUR_OF_DAY, 12) // Noon as workout start
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                // Add duration if available
                val workoutEndTime = calendar.timeInMillis + (workout.durationSeconds?.times(1000) ?: 0L)
                
                if (workoutEndTime in startTime..endTime) {
                    workoutsByEndTime.getOrPut(workoutEndTime) { mutableListOf() }.add(workout)
                }
            } catch (e: Exception) {
                // Skip workouts with invalid dates
            }
        }

        // Pre-process external activities: map them to their end timestamps
        val externalActivitiesByEndTime = mutableMapOf<Long, MutableList<ExternalActivity>>()
        externalActivities.forEach { activity ->
            // Use the endTime from the activity (when fatigue is applied)
            val activityEndTime = activity.endTime
            
            if (activityEndTime in startTime..endTime) {
                externalActivitiesByEndTime.getOrPut(activityEndTime) { mutableListOf() }.add(activity)
            }
        }
        
        // Track last day processed for daily snapshots
        var lastDayStr = ""
        
        // Hour-by-hour simulation
        var currentTime = startTime
        while (currentTime <= endTime) {
            // 1. ACCUMULATE: Check if any workout ended in this hour
            val hourStart = currentTime
            val hourEnd = currentTime + stepSizeMs
            
            workoutsByEndTime.forEach { (workoutEndTime, workouts) ->
                if (workoutEndTime in hourStart until hourEnd) {
                    // Workout occurred in this hour, add raw fatigue to all three stacks
                    workouts.forEach { workout ->
                        val rawScores = calculateFatigueScores(workout, trainingData, config)
                        currentLowerStack += rawScores.lowerFatigue
                        currentUpperStack += rawScores.upperFatigue
                        currentSystemicStack += rawScores.systemicFatigue
                    }
                }
            }

            // Process external activities in this hour
            externalActivitiesByEndTime.forEach { (activityTime, activities) ->
                if (activityTime in hourStart until hourEnd) {
                    // External activity occurred in this hour, add fatigue to stacks
                    activities.forEach { activity ->
                        currentLowerStack += activity.fatigue.lowerFatigue
                        currentUpperStack += activity.fatigue.upperFatigue
                        currentSystemicStack += activity.fatigue.systemicFatigue
                    }
                }
            }
            
            // 2. DECAY: Apply exponential decay (half-life) to each stack independently
            // Calculate hourly decay factor for 48-hour half-life
            // This results in a 50% drop over 48 hours when applied each hour
            val hourlyDecayFactor = 0.5.pow(1.0 / 48.0).toFloat()
            
            if (currentLowerStack > 0f) {
                currentLowerStack *= hourlyDecayFactor
            }
            
            if (currentUpperStack > 0f) {
                currentUpperStack *= hourlyDecayFactor
            }
            
            if (currentSystemicStack > 0f) {
                currentSystemicStack *= hourlyDecayFactor
            }
            
            // 3. STORE: Add graph point with all three fatigue values
            val currentFatigueValues = FatigueValues(
                lowerFatigue = currentLowerStack,
                upperFatigue = currentUpperStack,
                systemicFatigue = currentSystemicStack
            )
            graphPoints.add(Pair(currentTime, currentFatigueValues))
            
            // 4. DAILY SNAPSHOT: Store end-of-day value
            val currentCalendar = java.util.Calendar.getInstance().apply {
                timeInMillis = currentTime
            }
            val currentDayStr = dateFormat.format(currentCalendar.time)
            val hourOfDay = currentCalendar.get(java.util.Calendar.HOUR_OF_DAY)
            
            // Store value at end of day (23:00 or last hour before midnight)
            if (hourOfDay == 23 || (hourOfDay == 22 && currentTime + stepSizeMs > endTime)) {
                dailyEndValues[currentDayStr] = currentFatigueValues
            } else if (currentDayStr != lastDayStr && lastDayStr.isNotEmpty()) {
                // Store previous day's end value if we just moved to a new day
                val prevCalendar = java.util.Calendar.getInstance().apply {
                    timeInMillis = currentTime - stepSizeMs
                }
                val prevDayStr = dateFormat.format(prevCalendar.time)
                if (!dailyEndValues.containsKey(prevDayStr)) {
                    dailyEndValues[prevDayStr] = FatigueValues(0f, 0f, 0f) // Default if no value stored
                }
            }
            
            lastDayStr = currentDayStr
            currentTime += stepSizeMs
        }
        
        // Ensure all 7 days are in dailyEndValues (even if 0)
        val calendar = java.util.Calendar.getInstance()
        for (i in 6 downTo 0) {
            calendar.time = java.util.Date()
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -i)
            val dateStr = dateFormat.format(calendar.time)
            if (!dailyEndValues.containsKey(dateStr)) {
                dailyEndValues[dateStr] = FatigueValues(0f, 0f, 0f)
            }
        }
        
        return FatigueTimeline(
            graphPoints = graphPoints,
            dailyEndValues = dailyEndValues
        )
    }

    /**
     * Gets current fatigue values from the timeline by finding the last point before or at now,
     * then applying decay based on time elapsed since that point.
     * Returns FatigueScores with current lower, upper, and systemic fatigue values.
     * 
     * @param timeline The fatigue timeline to extract current values from
     * @param config The readiness configuration for decay calculations
     * @return FatigueScores with current fatigue values, or zero values if timeline is empty
     */
    fun getCurrentFatigueFromTimeline(timeline: FatigueTimeline, config: ReadinessConfig): FatigueScores {
        if (timeline.graphPoints.isEmpty()) {
            return FatigueScores(0f, 0f, 0f)
        }
        
        val now = System.currentTimeMillis()
        
        // 1. Find the last known point before or at 'now', or the very last point if all are in past
        val lastPoint = timeline.graphPoints.lastOrNull { it.first <= now } 
            ?: timeline.graphPoints.last() // Use last point if we're after all points
        
        // 2. Calculate time elapsed since that point
        val timeElapsedMs = now - lastPoint.first
        
        // 3. Apply decay for the time gap to get accurate "Right Now" value
        // Use existing getDecayedScore function which handles proper decay logic
        val decayedLower = getDecayedScore(lastPoint.second.lowerFatigue, timeElapsedMs, config)
        val decayedUpper = getDecayedScore(lastPoint.second.upperFatigue, timeElapsedMs, config)
        val decayedSystemic = getDecayedScore(lastPoint.second.systemicFatigue, timeElapsedMs, config)
        
        return FatigueScores(
            lowerFatigue = decayedLower,
            upperFatigue = decayedUpper,
            systemicFatigue = decayedSystemic
        )
    }

    /**
     * Creates mock training data for testing/demo purposes.
     * - 3 days ago: Heavy leg workout (Tier 1, high RPE ~9)
     * - Yesterday: Light run (minimal fatigue, maybe 5-10 systemic)
     */
    fun createMockTrainingData(): TrainingData {
        val dateFormat = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
        val calendar = java.util.Calendar.getInstance()
        
        // 3 days ago - Heavy leg workout
        calendar.time = java.util.Date()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -3)
        val threeDaysAgo = dateFormat.format(calendar.time)
        
        // Yesterday - Light run
        calendar.time = java.util.Date()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val yesterday = dateFormat.format(calendar.time)
        
        // Create mock exercise library (minimal - just what we need)
        val mockExerciseLibrary = listOf(
            ExerciseLibraryItem(
                id = 1,
                name = "Squat",
                pattern = MovementPattern.SQUAT,
                manualMechanics = Mechanics.COMPOUND,
                tier = Tier.TIER_1,
                region = BodyRegion.LOWER,
                primaryTargets = listOf(TargetMuscle.QUADS, TargetMuscle.GLUTES)
            ),
            ExerciseLibraryItem(
                id = 2,
                name = "Deadlift",
                pattern = MovementPattern.HINGE,
                manualMechanics = Mechanics.COMPOUND,
                tier = Tier.TIER_1,
                region = BodyRegion.LOWER,
                primaryTargets = listOf(TargetMuscle.HAMSTRINGS, TargetMuscle.GLUTES)
            ),
            ExerciseLibraryItem(
                id = 3,
                name = "Running",
                pattern = MovementPattern.OTHER,
                manualMechanics = Mechanics.COMPOUND,
                tier = Tier.TIER_3,
                region = BodyRegion.LOWER,
                primaryTargets = listOf(TargetMuscle.QUADS, TargetMuscle.CALVES)
            )
        )
        
        // 3 days ago: Heavy leg workout
        val heavyLegWorkout = TrainingSession(
            trainingNumber = 1,
            date = threeDaysAgo,
            exercises = mutableListOf(
                ExerciseEntry(
                    exerciseId = 1,
                    exerciseName = "Squat",
                    setNumber = 1,
                    kg = 100f,
                    reps = 5,
                    rpe = 9f
                ),
                ExerciseEntry(
                    exerciseId = 1,
                    exerciseName = "Squat",
                    setNumber = 2,
                    kg = 100f,
                    reps = 5,
                    rpe = 9f
                ),
                ExerciseEntry(
                    exerciseId = 1,
                    exerciseName = "Squat",
                    setNumber = 3,
                    kg = 100f,
                    reps = 5,
                    rpe = 9f
                ),
                ExerciseEntry(
                    exerciseId = 2,
                    exerciseName = "Deadlift",
                    setNumber = 1,
                    kg = 150f,
                    reps = 5,
                    rpe = 9f
                ),
                ExerciseEntry(
                    exerciseId = 2,
                    exerciseName = "Deadlift",
                    setNumber = 2,
                    kg = 150f,
                    reps = 5,
                    rpe = 9f
                )
            ),
            defaultWorkoutType = "heavy",
            durationSeconds = 3600L // 1 hour
        )
        
        // Yesterday: Light run
        val lightRun = TrainingSession(
            trainingNumber = 2,
            date = yesterday,
            exercises = mutableListOf(
                ExerciseEntry(
                    exerciseId = 3,
                    exerciseName = "Running",
                    setNumber = 1,
                    kg = 0f,
                    reps = 1,
                    rpe = 5f
                )
            ),
            defaultWorkoutType = "light",
            durationSeconds = 1800L // 30 minutes
        )
        
        return TrainingData(
            exerciseLibrary = mockExerciseLibrary.toMutableList(),
            trainings = mutableListOf(heavyLegWorkout, lightRun),
            workoutPlans = mutableListOf()
        )
    }
}

