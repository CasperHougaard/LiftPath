package com.liftpath.helpers

import com.liftpath.models.ExerciseSet
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
}

