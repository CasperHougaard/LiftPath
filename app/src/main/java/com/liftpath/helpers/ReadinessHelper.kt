package com.liftpath.helpers

import com.liftpath.models.*

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
    private fun calculateRecoveryTime(fatigue: Float, config: ReadinessConfig): Long {
        val baseHours = when {
            fatigue > config.thresholds.high -> 48 // High fatigue: 48 hours
            fatigue >= config.thresholds.moderate -> 24 // Moderate: 24 hours
            else -> 12 // Low: 12 hours
        }
        // Apply recovery speed multiplier
        val adjustedHours = (baseHours / config.recoverySpeedMultiplier).toInt()
        return adjustedHours * 3600_000L // Convert to milliseconds
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
}

