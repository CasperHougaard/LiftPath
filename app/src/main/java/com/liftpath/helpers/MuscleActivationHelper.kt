package com.liftpath.helpers

import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.TargetMuscle

/**
 * Helper class for calculating muscle activation states from exercises.
 * Handles primary/secondary muscle tracking and identifies missing muscle groups.
 */
object MuscleActivationHelper {

    /**
     * Data class representing the activation state of muscles in a workout.
     * 
     * Note: If a muscle is Primary in one exercise and Secondary in another,
     * it will appear in both sets. The SVG visualizer should use Primary color
     * when rendering (Primary takes precedence).
     */
    data class MuscleActivationState(
        val primaryMuscles: Set<TargetMuscle>,
        val secondaryMuscles: Set<TargetMuscle>
    ) {
        /**
         * Returns the total number of unique activated muscles (primary or secondary).
         */
        fun getTotalActivated(): Int = (primaryMuscles + secondaryMuscles).size

        /**
         * Returns the total number of possible muscles.
         */
        fun getTotalPossible(): Int = TargetMuscle.values().size

        /**
         * Returns the activation percentage as a float (0.0 to 1.0).
         */
        fun getActivationPercentage(): Float {
            val total = getTotalPossible()
            return if (total > 0) getTotalActivated().toFloat() / total else 0f
        }

        /**
         * Returns true if no muscles are activated (empty workout).
         */
        fun isEmpty(): Boolean = primaryMuscles.isEmpty() && secondaryMuscles.isEmpty()
    }

    /**
     * Calculates which muscles are activated (primary and secondary) from a list of exercises.
     * 
     * @param exercises List of exercises in the workout
     * @return MuscleActivationState with primary and secondary muscle sets
     */
    fun getActivatedMuscles(exercises: List<ExerciseLibraryItem>): MuscleActivationState {
        val primary = mutableSetOf<TargetMuscle>()
        val secondary = mutableSetOf<TargetMuscle>()

        exercises.forEach { exercise ->
            // Safely add primary targets (handles empty/null lists)
            exercise.primaryTargets?.let { targets ->
                primary.addAll(targets)
            }
            
            // Safely add secondary targets (handles empty/null lists)
            exercise.secondaryTargets?.let { targets ->
                secondary.addAll(targets)
            }
        }

        return MuscleActivationState(primary, secondary)
    }

    /**
     * Calculates which muscles are NOT activated (missing) from the activation state.
     * 
     * @param activated The current activation state
     * @return MuscleActivationState with missing primary and secondary muscles
     */
    fun getMissingMuscles(activated: MuscleActivationState): MuscleActivationState {
        val allMuscles = TargetMuscle.values().toSet()
        
        // Missing primary = all muscles that are not in primary set
        // Missing secondary = all muscles that are not in secondary set
        val missingPrimary = allMuscles - activated.primaryMuscles
        val missingSecondary = allMuscles - activated.secondaryMuscles

        return MuscleActivationState(missingPrimary, missingSecondary)
    }

    /**
     * Convenience method to get both activated and missing muscles in one call.
     * 
     * @param exercises List of exercises in the workout
     * @return Pair of (activated, missing) MuscleActivationState
     */
    fun calculateMuscleState(exercises: List<ExerciseLibraryItem>): Pair<MuscleActivationState, MuscleActivationState> {
        val activated = getActivatedMuscles(exercises)
        val missing = getMissingMuscles(activated)
        return Pair(activated, missing)
    }
}

