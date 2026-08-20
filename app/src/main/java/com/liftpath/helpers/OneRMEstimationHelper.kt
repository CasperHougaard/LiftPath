package com.liftpath.helpers

import com.liftpath.models.ExerciseSet
import com.liftpath.models.ExerciseEntry
import com.liftpath.models.SetIntent
import com.liftpath.utils.WorkoutTypeFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

data class OneRMEstimationResult(
    val current1RM: Float,
    val expected1RM: Float,
    val projectionDate: Date,
    val improvementKg: Float,
    val improvementPercent: Float,
    val isQualified: Boolean,
    val warnings: List<String>
)

data class SessionMetrics(
    val date: String,
    val workoutType: String?,
    val oneRM: Float?,
    val volume: Float,
    val efficiency: Float?
)

enum class TrendDirection {
    UP,
    STABLE,
    DOWN
}

data class TrendResult(
    val slope: Float,
    val percentageChange: Float,
    val sessionCount: Int,
    val trendDirection: TrendDirection,
    val confidence: Float
)

object OneRMEstimationHelper {

    /**
     * Calculate 1RM using hybrid formula selection based on rep count with RPE normalization.
     * Implements RPE normalization: if RPE is provided, calculates effective reps to account for
     * reps in reserve, making the 1RM estimate more accurate for submaximal sets.
     * 
     * @param weight Weight lifted in kg
     * @param actualReps Number of repetitions actually performed
     * @param rpe Optional RPE (Rate of Perceived Exertion) value (1-10 scale)
     * @return Estimated 1RM in kg, or null if invalid/unreliable
     */
    fun calculateOneRM(weight: Float, actualReps: Int, rpe: Float? = null): Float? {
        // Timed/isometric holds carry no reps (reps == 0); a rep-based 1RM is meaningless for them.
        if (actualReps <= 0) return null

        // Rule A: Filter out sets with RPE < 6.5 (too light to be predictive)
        if (rpe != null && rpe < 6.5f) {
            return null
        }
        
        // Calculate effective reps using RPE normalization if available
        val effectiveReps = if (rpe != null) {
            // Reps In Reserve (RIR) = 10 - RPE
            val repsInReserve = 10f - rpe
            // Effective Reps = Actual Reps + RIR (what it would be at failure)
            (actualReps + repsInReserve).toInt()
        } else {
            // No RPE provided: assume set was near failure (standard behavior)
            actualReps
        }
        
        // Discard sets with effective reps > 15 (statistically unreliable for 1RM estimation)
        if (effectiveReps > 15) return null
        
        if (effectiveReps <= 0) return weight
        if (effectiveReps == 1) return weight
        
        return when {
            effectiveReps <= 8 -> {
                // Epley's formula: 1RM = w × (1 + r/30)
                // Better for lower rep ranges (≤8 reps)
                weight * (1 + effectiveReps / 30f)
            }
            else -> {
                // Brzycki's formula: 1RM = w × (36 / (37 - r))
                // More conservative for higher rep ranges (9-15 reps)
                // Prevents overestimation from burnout/endurance sets
                if (effectiveReps >= 37) return null // Invalid for Brzycki
                weight * (36f / (37f - effectiveReps))
            }
        }
    }

    /**
     * Estimate 1RM progression using weighted linear regression with exponential decay.
     * Implements key improvements:
     * 1. RPE normalization: Uses effective reps (actual + RIR) when RPE is provided
     * 2. Intensity filtering: Excludes sets with RPE < 6.5 (too light to be predictive)
     * 3. Hybrid 1RM formula selection (Epley for ≤8 reps, Brzycki for 9-15 reps)
     * 4. Weighted regression with recency bias (recent data weighted more heavily)
     * 5. Damped projections based on time horizon (diminishing returns for longer projections)
     */
    fun estimate1RMProgression(
        sets: List<ExerciseSet>,
        sessionWorkoutTypes: Map<String, String>, // Map of date -> workoutType from TrainingSession.defaultWorkoutType
        projectionMonths: Int,
        minDataPoints: Int,
        recentDataWindowDays: Int
    ): OneRMEstimationResult? {
        if (sets.isEmpty()) return null

        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val calendar = Calendar.getInstance()

        // Calculate 1RM for each session using hybrid formula with RPE normalization
        // Rule A: Sets with RPE < 6.5 are filtered out in calculateOneRM()
        // Rule B: Light/Deload/Warmup sessions without RPE are filtered upstream
        
        // First pass: Calculate 1RM for all sessions, preserving workout type info
        data class SessionData(val date: Date, val max1RM: Float, val workoutType: String?)
        
        // Group sets by date - workout type comes from session (TrainingSession.defaultWorkoutType)
        val sessionsWithType = sets.groupBy { it.date }
            .mapNotNull { (dateStr, sessionSets) ->
                val date = try {
                    dateFormat.parse(dateStr)
                } catch (e: Exception) {
                    null
                }
                if (date != null) {
                    // Get workout type from session map (lookup by date)
                    // Look up the raw workout type, then normalize it
                    val rawWorkoutType = sessionWorkoutTypes[dateStr]
                    val workoutType = WorkoutTypeFormatter.normalize(rawWorkoutType)
                    
                    // Calculate 1RM for each set with RPE normalization
                    val valid1RMs = sessionSets.mapNotNull { set ->
                        calculateOneRM(set.kg, set.reps, set.rpe)
                    }
                    if (valid1RMs.isNotEmpty()) {
                        val max1RM = valid1RMs.maxOrNull() ?: 0f
                        SessionData(date, max1RM, workoutType)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            .sortedBy { it.date }
        
        if (sessionsWithType.isEmpty()) return null
        
        // Combine all sessions (heavy and light) for the final data
        val oneRMPerSession = sessionsWithType
            .sortedBy { it.date }
            .map { Pair(it.date, it.max1RM) }

        if (oneRMPerSession.isEmpty()) return null
        

        val current1RM = oneRMPerSession.last().second
        val currentDate = oneRMPerSession.last().first

        // Check data quality
        val warnings = mutableListOf<String>()
        val uniqueSessions = oneRMPerSession.size

        if (uniqueSessions < minDataPoints) {
            warnings.add("Limited data: Estimation based on only $uniqueSessions session${if (uniqueSessions > 1) "s" else ""}")
        }

        // Check recent data
        val daysSinceLastSession = ((System.currentTimeMillis() - currentDate.time) / (1000 * 60 * 60 * 24)).toInt()
        if (daysSinceLastSession > recentDataWindowDays) {
            warnings.add("No recent data: Last session was $daysSinceLastSession days ago")
        }

        // Check data consistency (variance)
        val oneRMValues = oneRMPerSession.map { it.second }
        val mean = oneRMValues.average().toFloat()
        val variance = oneRMValues.map { (it - mean).pow(2) }.average().toFloat()
        val stdDev = sqrt(variance)
        val coefficientOfVariation = if (mean > 0) stdDev / mean else 0f

        if (coefficientOfVariation > 0.15f) { // More than 15% variation
            warnings.add("Inconsistent progression: Results may vary")
        }

        // Need at least 2 data points for regression
        if (oneRMPerSession.size < 2) {
            return OneRMEstimationResult(
                current1RM = current1RM,
                expected1RM = current1RM,
                projectionDate = currentDate,
                improvementKg = 0f,
                improvementPercent = 0f,
                isQualified = false,
                warnings = warnings + listOf("Insufficient data for estimation")
            )
        }

        // Perform weighted linear regression with exponential decay (recency bias)
        val today = Date()
        val regression = performWeightedLinearRegression(oneRMPerSession, today)

        // Calculate projection date
        calendar.time = currentDate
        calendar.add(Calendar.MONTH, projectionMonths)
        val projectionDate = calendar.time

        // Project forward with damping factor (diminishing returns)
        val daysToProject = ((projectionDate.time - currentDate.time) / (1000 * 60 * 60 * 24)).toFloat()
        
        // Get damping factor based on projection months
        val dampingFactor = getDampingFactor(projectionMonths)
        
        // Calculate undamped projection first
        val undampedProjection = regression.slope * daysToProject + regression.intercept
        // Apply damping: future gain is reduced based on time horizon
        val projectedGain = (undampedProjection - current1RM) * dampingFactor
        val expected1RM = current1RM + projectedGain

        val improvementKg = expected1RM - current1RM
        val improvementPercent = if (current1RM > 0) (improvementKg / current1RM) * 100f else 0f

        val isQualified = uniqueSessions >= minDataPoints && 
                         daysSinceLastSession <= recentDataWindowDays &&
                         coefficientOfVariation <= 0.15f

        return OneRMEstimationResult(
            current1RM = current1RM,
            expected1RM = expected1RM,
            projectionDate = projectionDate,
            improvementKg = improvementKg,
            improvementPercent = improvementPercent,
            isQualified = isQualified,
            warnings = warnings
        )
    }

    /**
     * Damping factor constants based on projection time horizon.
     * Applies the Law of Diminishing Returns - longer projections are more conservative.
     */
    private fun getDampingFactor(projectionMonths: Int): Float {
        return when {
            projectionMonths <= 1 -> 1.0f    // No damping for 1 month
            projectionMonths <= 2 -> 0.9f    // 10% reduction for 2 months
            projectionMonths <= 3 -> 0.8f    // 20% reduction for 3 months
            projectionMonths <= 6 -> 0.5f    // 50% reduction for 6 months (highly conservative)
            else -> 0.3f                      // Very conservative for > 6 months
        }
    }

    private data class RegressionResult(
        val slope: Float,
        val intercept: Float,
        val standardError: Float,
        val meanX: Float,
        val sumSquaredDeviations: Float
    )

    /**
     * Perform weighted linear regression with exponential decay weights.
     * Implements recency bias - recent sessions have more influence than older ones.
     * 
     * Weight formula: w_i = e^(-λ × days_ago), where λ = 0.02
     * This means data from ~35 days ago has 50% influence.
     * 
     * @param data List of (date, 1RM) pairs
     * @param today Reference date for calculating days_ago
     * @return RegressionResult with weighted slope, intercept, and statistics
     */
    private fun performWeightedLinearRegression(
        data: List<Pair<Date, Float>>,
        today: Date
    ): RegressionResult {
        // Lambda constant for exponential decay (0.02 = ~35 day half-life)
        val lambda = 0.02f
        
        // Convert dates to days since first date (for X-axis)
        val firstDate = data.first().first
        val xValues = data.map { ((it.first.time - firstDate.time) / (1000 * 60 * 60 * 24)).toFloat() }
        val yValues = data.map { it.second }
        
        // Calculate weights based on days ago (exponential decay)
        // exp() returns Double, explicitly convert to Double list
        val weights: List<Double> = data.map { (date, _) ->
            val daysAgo = ((today.time - date.time) / (1000 * 60 * 60 * 24)).toFloat()
            exp(-lambda * daysAgo.toDouble())
        }
        
        val n = data.size
        val sumWeights: Double = weights.sum()
        
        // Calculate weighted means using explicit iteration to avoid type inference issues
        var sumWX = 0.0
        var sumWY = 0.0
        for (i in xValues.indices) {
            sumWX += xValues[i] * weights[i]
            sumWY += yValues[i] * weights[i]
        }
        val meanXWeighted = (sumWX / sumWeights).toFloat()
        val meanYWeighted = (sumWY / sumWeights).toFloat()
        
        // Calculate weighted slope: m = Σw_i(x_i - x̄_w)(y_i - ȳ_w) / Σw_i(x_i - x̄_w)²
        var numerator = 0.0
        var denominator = 0.0
        for (i in xValues.indices) {
            val xDiff = (xValues[i] - meanXWeighted).toDouble()
            val yDiff = (yValues[i] - meanYWeighted).toDouble()
            numerator += weights[i] * xDiff * yDiff
            denominator += weights[i] * xDiff * xDiff
        }
        
        val slope = if (denominator != 0.0) {
            (numerator / denominator).toFloat()
        } else {
            0f
        }
        
        // Calculate weighted intercept: b = ȳ_w - m × x̄_w
        val intercept = meanYWeighted - slope * meanXWeighted
        
        // Calculate weighted standard error for confidence intervals
        var sumWeightedSquaredResiduals = 0.0
        for (i in yValues.indices) {
            val predicted = slope * xValues[i] + intercept
            val residual = (yValues[i] - predicted).toDouble()
            sumWeightedSquaredResiduals += weights[i] * residual * residual
        }
        
        // Effective sample size for weighted regression
        var sumWeightSquared = 0.0
        for (weight in weights) {
            sumWeightSquared += weight * weight
        }
        val effectiveN = if (sumWeightSquared > 0.0) {
            (sumWeights * sumWeights / sumWeightSquared).toFloat()
        } else {
            n.toFloat()
        }
        
        val standardError = if (effectiveN > 2f && sumWeights > 0.0) {
            sqrt((sumWeightedSquaredResiduals / (effectiveN - 2f) / sumWeights).toFloat())
        } else {
            0f
        }
        
        // Calculate weighted sum of squared deviations (for confidence interval calculation)
        var weightedSumSquaredDeviations = 0.0
        for (i in xValues.indices) {
            val xDiff = (xValues[i] - meanXWeighted).toDouble()
            weightedSumSquaredDeviations += weights[i] * xDiff * xDiff
        }
        
        return RegressionResult(
            slope = slope,
            intercept = intercept,
            standardError = standardError,
            meanX = meanXWeighted,
            sumSquaredDeviations = weightedSumSquaredDeviations.toFloat()
        )
    }

    /**
     * Calculate total volume per session (sum of weight × reps for all sets).
     * Includes ALL session types (Heavy, Light, Custom) as volume is cumulative.
     * 
     * @param sets List of ExerciseSet objects
     * @param sessionWorkoutTypes Map of date string -> workout type
     * @return Map of date string -> total volume in kg
     */
    fun calculateVolumePerSession(
        sets: List<ExerciseSet>,
        sessionWorkoutTypes: Map<String, String>
    ): Map<String, Float> {
        return sets.groupBy { it.date }
            .mapValues { (_, sessionSets) ->
                // Timed holds carry no reps, so they contribute no rep-based volume.
                sessionSets.filterNot { it.isTimedSet() }
                    .sumOf { (it.kg * it.reps).toDouble() }.toFloat()
            }
    }

    /**
     * Calculate efficiency score per session: (weight × reps) / RPE for the top set.
     * Top set is defined as the set with highest volume (weight × reps) in the session.
     * 
     * @param sets List of ExerciseSet objects
     * @param sessionWorkoutTypes Map of date string -> workout type
     * @return Map of date string -> efficiency score (null if no RPE data for top set)
     */
    fun calculateEfficiencyPerSession(
        sets: List<ExerciseSet>,
        sessionWorkoutTypes: Map<String, String>
    ): Map<String, Float?> {
        return sets.groupBy { it.date }
            .mapValues { (_, sessionSets) ->
                // Find set with highest volume (weight × reps); a hold has no rep volume.
                val topSet = sessionSets.filterNot { it.isTimedSet() }
                    .maxByOrNull { it.kg * it.reps }
                
                if (topSet != null && topSet.rpe != null && topSet.rpe > 0) {
                    // Calculate efficiency: (weight × reps) / RPE
                    (topSet.kg * topSet.reps) / topSet.rpe
                } else {
                    null
                }
            }
    }

    /**
     * Calculate 1RM per session, filtering by STRENGTH intent only.
     * Note: Sets passed to this function should already be filtered to STRENGTH intent.
     * This function calculates 1RM for all provided sets (assumes they're all STRENGTH intent).
     * 
     * @param sets List of ExerciseSet objects (should be pre-filtered to STRENGTH intent)
     * @param sessionWorkoutTypes Map of date string -> workout type (kept for legacy compatibility)
     * @param includeLightSessions Legacy parameter (ignored, kept for backward compatibility)
     * @return Map of date string -> max 1RM for that session
     */
    fun calculateOneRMPerSession(
        sets: List<ExerciseSet>,
        sessionWorkoutTypes: Map<String, String>,
        includeLightSessions: Boolean = false  // Legacy parameter, ignored
    ): Map<String, Float> {
        // All sets passed should already be STRENGTH intent (filtered by caller)
        // Just calculate 1RM for all provided sets
        return sets.groupBy { it.date }
            .mapNotNull { (dateStr, sessionSets) ->
                // Calculate 1RM for each set with RPE normalization
                val valid1RMs = sessionSets.mapNotNull { set ->
                    calculateOneRM(set.kg, set.reps, set.rpe)
                }
                
                if (valid1RMs.isNotEmpty()) {
                    val max1RM = valid1RMs.maxOrNull() ?: 0f
                    Pair(dateStr, max1RM)
                } else {
                    null
                }
            }
            .toMap()
    }

    /**
     * Find matching session for "apples to apples" comparison.
     * Finds the most recent session (before excludeDate) with matching workout type.
     * If no matching workout type found, returns most recent previous session.
     * 
     * @param sessions List of SessionMetrics sorted by date (most recent first)
     * @param targetWorkoutType Workout type to match (null to match any)
     * @param excludeDate Date to exclude (typically the last session date)
     * @return Matching SessionMetrics or null if no match found
     */
    fun findMatchingSessionForComparison(
        sessions: List<SessionMetrics>,
        targetWorkoutType: String?,
        excludeDate: String
    ): SessionMetrics? {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val excludeDateObj = try {
            dateFormat.parse(excludeDate)
        } catch (e: Exception) {
            return null
        }
        
        // First try to find matching workout type
        if (targetWorkoutType != null) {
            val matching = sessions.firstOrNull { session ->
                val sessionDate = try {
                    dateFormat.parse(session.date)
                } catch (e: Exception) {
                    null
                }
                sessionDate != null && 
                sessionDate.before(excludeDateObj) &&
                WorkoutTypeFormatter.normalize(session.workoutType) == targetWorkoutType
            }
            if (matching != null) return matching
        }
        
        // Fallback: return most recent previous session
        return sessions.firstOrNull { session ->
            val sessionDate = try {
                dateFormat.parse(session.date)
            } catch (e: Exception) {
                null
            }
            sessionDate != null && sessionDate.before(excludeDateObj)
        }
    }

    /**
     * Calculate trend analysis over last 4-6 sessions using simple linear regression.
     * 
     * @param sessions List of SessionMetrics sorted by date (oldest to newest)
     * @param metricType "volume", "strength", or "efficiency"
     * @return TrendResult with slope, percentage change, and trend direction, or null if insufficient data
     */
    fun calculateTrend(
        sessions: List<SessionMetrics>,
        metricType: String
    ): TrendResult? {
        if (sessions.size < 4) return null

        // Get last 4-6 sessions (prefer 6, but use what's available)
        val sessionsToAnalyze = sessions.takeLast(minOf(6, sessions.size))
        if (sessionsToAnalyze.size < 4) return null

        // Extract metric values
        val values = sessionsToAnalyze.mapNotNull { session ->
            when (metricType.lowercase()) {
                "volume" -> session.volume
                "strength" -> session.oneRM
                "efficiency" -> session.efficiency
                else -> null
            }
        }.filter { it != null && it > 0 }

        if (values.size < 4) return null

        // Simple linear regression: y = mx + b
        // x = session index (0, 1, 2, ...)
        // y = metric value
        val n = values.size
        val xValues = (0 until n).map { it.toFloat() }
        val yValues = values.map { it.toFloat() }

        val meanX = xValues.average().toFloat()
        val meanY = yValues.average().toFloat()

        var numerator = 0.0
        var denominator = 0.0
        for (i in xValues.indices) {
            val xDiff = (xValues[i] - meanX).toDouble()
            val yDiff = (yValues[i] - meanY).toDouble()
            numerator += xDiff * yDiff
            denominator += xDiff * xDiff
        }

        val slope = if (denominator != 0.0) {
            (numerator / denominator).toFloat()
        } else {
            0f
        }

        // Calculate percentage change
        val firstValue = yValues.first()
        val lastValue = yValues.last()
        val percentageChange = if (firstValue > 0) {
            ((lastValue - firstValue) / firstValue) * 100f
        } else {
            0f
        }

        // Calculate R² for confidence
        var sumSquaredResiduals = 0.0
        var sumSquaredTotal = 0.0
        for (i in yValues.indices) {
            val predicted = slope * xValues[i] + (meanY - slope * meanX)
            val residual = (yValues[i] - predicted).toDouble()
            sumSquaredResiduals += residual * residual
            val totalDiff = (yValues[i] - meanY).toDouble()
            sumSquaredTotal += totalDiff * totalDiff
        }
        val rSquared = if (sumSquaredTotal > 0) {
            (1.0 - sumSquaredResiduals / sumSquaredTotal).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

        // Determine trend direction
        val trendDirection = when {
            slope > 0 && percentageChange > 5f -> TrendDirection.UP
            slope < 0 && percentageChange < -5f -> TrendDirection.DOWN
            else -> TrendDirection.STABLE
        }

        return TrendResult(
            slope = slope,
            percentageChange = percentageChange,
            sessionCount = n,
            trendDirection = trendDirection,
            confidence = rSquared
        )
    }

    /**
     * Calculate aggregated volume per session for a group of exercises.
     * The sets should already be filtered to only include exercises in the group.
     * 
     * @param groupSets List of ExerciseSet objects for exercises in the group
     * @param sessionWorkoutTypes Map of date string -> workout type
     * @return Map of date string -> total aggregated volume in kg
     */
    fun calculateGroupVolumePerSession(
        groupSets: List<ExerciseSet>,
        sessionWorkoutTypes: Map<String, String>
    ): Map<String, Float> {
        return calculateVolumePerSession(groupSets, sessionWorkoutTypes)
    }

    /**
     * Calculate Relative Strength Index for a group of exercises.
     * Normalizes each exercise to its baseline (average of last 4 sessions) and averages across group.
     * 
     * @param exerciseMetrics Map of exercise name -> Map of date -> 1RM for that exercise
     * @param sessionWorkoutTypes Map of date string -> workout type
     * @return Map of date string -> Relative Strength Index (100 = baseline)
     */
    fun calculateGroupRelativeStrengthIndex(
        exerciseMetrics: Map<String, Map<String, Float>>,
        sessionWorkoutTypes: Map<String, String>
    ): Map<String, Float> {
        if (exerciseMetrics.isEmpty()) return emptyMap()

        // For each exercise, calculate baseline (average of last 4 sessions)
        val exerciseBaselines = exerciseMetrics.mapValues { (_, oneRMPerSession) ->
            val sortedSessions = oneRMPerSession.values.sorted()
            val sessionsToAverage = sortedSessions.takeLast(4)
            if (sessionsToAverage.isNotEmpty()) {
                sessionsToAverage.average().toFloat()
            } else {
                0f
            }
        }

        // Get all unique session dates
        val allDates = exerciseMetrics.values.flatMap { it.keys }.distinct().sorted()

        // Calculate normalized index for each session
        return allDates.associateWith { date ->
            val normalizedIndices = exerciseMetrics.mapNotNull { (exerciseName, oneRMPerSession) ->
                val oneRM = oneRMPerSession[date] ?: return@mapNotNull null
                val baseline = exerciseBaselines[exerciseName] ?: return@mapNotNull null
                if (baseline > 0) {
                    (oneRM / baseline) * 100f
                } else {
                    null
                }
            }.filterNotNull()

            if (normalizedIndices.isNotEmpty()) {
                normalizedIndices.average().toFloat()
            } else {
                100f // Default to baseline if no data
            }
        }
    }

    /**
     * Generate coach's summary report based on trend analysis.
     * 
     * @param volumeTrend TrendResult for volume, or null
     * @param strengthTrend TrendResult for strength, or null
     * @param efficiencyTrend TrendResult for efficiency, or null
     * @param sessionCount Total number of sessions analyzed
     * @return Formatted advice string
     */
    fun generateCoachReport(
        volumeTrend: TrendResult?,
        strengthTrend: TrendResult?,
        efficiencyTrend: TrendResult?,
        sessionCount: Int
    ): String {
        val volumeUp = volumeTrend?.trendDirection == TrendDirection.UP
        val volumeDown = volumeTrend?.trendDirection == TrendDirection.DOWN
        val volumeStable = volumeTrend?.trendDirection == TrendDirection.STABLE || volumeTrend == null

        val strengthUp = strengthTrend?.trendDirection == TrendDirection.UP
        val strengthDown = strengthTrend?.trendDirection == TrendDirection.DOWN
        val strengthStable = strengthTrend?.trendDirection == TrendDirection.STABLE || strengthTrend == null

        val efficiencyUp = efficiencyTrend?.trendDirection == TrendDirection.UP
        val efficiencyDown = efficiencyTrend?.trendDirection == TrendDirection.DOWN
        val efficiencyStable = efficiencyTrend?.trendDirection == TrendDirection.STABLE || efficiencyTrend == null

        return when {
            // Excellent progress across all dimensions
            volumeUp && strengthUp && (efficiencyUp || efficiencyStable) -> {
                "Excellent progress across all dimensions! Your volume, strength, and efficiency are all trending up. Keep this momentum going."
            }
            
            // Volume building phase
            volumeUp && strengthStable && (efficiencyStable || efficiencyTrend == null) -> {
                "Volume building phase - your total work capacity is increasing. Strength gains typically follow volume accumulation. Stay consistent."
            }
            
            // Potential overreaching
            volumeUp && strengthDown -> {
                "Volume is increasing but strength is declining. This may indicate overreaching. Consider a deload week to allow recovery and supercompensation."
            }
            
            // Strength gains without efficiency
            strengthUp && efficiencyDown -> {
                "Strength is improving, but efficiency is declining. Your gains may be coming from increased effort rather than true adaptation. Focus on technique and consider a lighter week."
            }
            
            // Efficiency improving (good sign)
            efficiencyUp && (strengthStable || strengthUp) -> {
                "Your efficiency is improving - you're getting stronger with less effort. This is a great sign of neurological adaptation. Keep training smart."
            }
            
            // All stable
            volumeStable && strengthStable && efficiencyStable -> {
                "Maintaining current level across all metrics. Consider progressive overload - add weight, reps, or sets to continue making progress."
            }
            
            // All trending down
            volumeDown && strengthDown && (efficiencyDown || efficiencyStable) -> {
                "All metrics are trending down. A deload week is strongly recommended. Allow your body to recover - this will set you up for better gains afterward."
            }
            
            // Volume down but strength stable
            volumeDown && strengthStable -> {
                "Volume has decreased but strength is maintained. This could be a planned deload or indicate fatigue. Monitor recovery and adjust training accordingly."
            }
            
            // Mixed signals
            else -> {
                val parts = mutableListOf<String>()
                if (volumeUp) parts.add("volume is trending up")
                if (strengthUp) parts.add("strength is improving")
                if (efficiencyUp) parts.add("efficiency is increasing")
                if (volumeDown) parts.add("volume is declining")
                if (strengthDown) parts.add("strength is decreasing")
                if (efficiencyDown) parts.add("efficiency is dropping")
                
                if (parts.isEmpty()) {
                    "Insufficient data for trend analysis. Keep training consistently to build a meaningful progress picture."
                } else {
                    "Mixed signals: ${parts.joinToString(", ")}. Review your training program and recovery to optimize all dimensions of progress."
                }
            }
        }
    }

    /**
     * Data class representing metrics for a specific intent within a session.
     */
    data class IntentMetrics(
        val oneRM: Float?,           // For STRENGTH
        val volume: Float,           // For BUILD (weight * reps)
        val totalReps: Int,          // For FLUSH
        val avgRPE: Float?,
        val setCount: Int
    )

    /**
     * Calculate metrics per session grouped by intent.
     * Returns Map<date, Map<SetIntent, IntentMetrics>>
     * 
     * @param allSets List of all exercise sets for a specific exercise
     * @param sessionWorkoutTypes Map of session date to workout type
     * @return Map of date to intent-grouped metrics
     */
    fun calculateMetricsPerSessionByIntent(
        allSets: List<ExerciseSet>,
        sessionWorkoutTypes: Map<String, String>
    ): Map<String, Map<SetIntent, IntentMetrics>> {
        val result = mutableMapOf<String, MutableMap<SetIntent, IntentMetrics>>()
        
        // Group sets by date
        val setsByDate = allSets.groupBy { it.date }
        
        setsByDate.forEach { (date, sets) ->
            val workoutType = sessionWorkoutTypes[date]
            val intentMap = mutableMapOf<SetIntent, IntentMetrics>()
            
            // Group sets by intent
            val setsByIntent = sets.groupBy { set ->
                inferIntent(set.reps, set.rpe, workoutType)
            }
            
            setsByIntent.forEach { (intent, intentSets) ->
                // Calculate metrics for this intent
                val oneRMs = intentSets.mapNotNull { set ->
                    calculateOneRM(set.kg, set.reps, set.rpe)
                }
                val maxOneRM = oneRMs.maxOrNull()
                
                val repSets = intentSets.filterNot { it.isTimedSet() }
                val volume = repSets.sumOf { (it.kg * it.reps).toDouble() }.toFloat()
                val totalReps = repSets.sumOf { it.reps }
                
                val rpes = intentSets.mapNotNull { it.rpe }
                val avgRPE = if (rpes.isNotEmpty()) rpes.average().toFloat() else null
                
                intentMap[intent] = IntentMetrics(
                    oneRM = maxOneRM,
                    volume = volume,
                    totalReps = totalReps,
                    avgRPE = avgRPE,
                    setCount = intentSets.size
                )
            }
            
            result[date] = intentMap
        }
        
        return result
    }

    /**
     * Infer intent from set properties (mirrors getEffectiveIntent logic from ExerciseEntry).
     * Used only when we have ExerciseSet (no explicitIntent) - i.e. legacy/aggregated data.
     * For legacy data, RPE 6 was used to denote warmup. New data uses isWarmup flag.
     */
    private fun inferIntent(reps: Int, rpe: Float?, workoutType: String?): SetIntent {
        // Legacy only: RPE 6 = warmup (ExerciseSet has no explicitIntent, so this path is legacy)
        if (rpe == 6.0f) return SetIntent.WARMUP
        
        return when (workoutType?.lowercase()) {
            "heavy" -> if (reps <= 7) SetIntent.STRENGTH else SetIntent.BUILD
            "light" -> if (reps >= 15) SetIntent.FLUSH else SetIntent.BUILD
            else -> when {
                reps <= 6 -> SetIntent.STRENGTH
                reps <= 15 -> SetIntent.BUILD
                else -> SetIntent.FLUSH
            }
        }
    }
}

