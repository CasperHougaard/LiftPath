package com.lilfitness.helpers

import com.lilfitness.models.*

object WorkoutGenerator {

    /**
     * Represents a recommended exercise with volume suggestions
     */
    data class RecommendedExercise(
        val exerciseId: Int,
        val exerciseName: String,
        val workoutType: String,
        val recommendedSets: Int,
        val recommendedReps: Int
    )

    /**
     * Generates a full workout based on Focus (Upper/Lower) and Intensity (Heavy/Light).
     * Returns a list of RecommendedExercise objects - exercises that fit the blueprint
     * without pre-filled sets. Sets should be added manually with suggestions shown in tooltips.
     */
    fun generate(
        library: List<ExerciseLibraryItem>,
        userLevel: UserLevel, // Kept for future scaling (e.g. volume adjustments)
        focus: SessionFocus,
        intensity: SessionIntensity
    ): List<RecommendedExercise> {
        
        val blueprint = getBlueprint(focus, intensity)
        val workout = mutableListOf<RecommendedExercise>()

        // To avoid picking the same exercise twice in one session
        val selectedIds = mutableSetOf<Int>()
        val workoutType = if (intensity == SessionIntensity.HEAVY) "heavy" else "light"

        for (slot in blueprint) {
            // 1. Find valid candidates in the library
            val candidates = library.filter { exercise ->
                // Match Pattern (e.g., PUSH_HORIZONTAL)
                slot.patterns.contains(exercise.pattern) &&
                // Match Tier preference (e.g., TIER_1)
                slot.preferredTiers.contains(exercise.tier) &&
                // Avoid duplicates
                !selectedIds.contains(exercise.id)
            }

            // 2. Sort candidates by "Best Match"
            // We prioritize the Tiers in the order defined in the blueprint.
            // e.g. if blueprint prefers TIER_1, those come first.
            val sortedCandidates = candidates.sortedBy { exercise ->
                slot.preferredTiers.indexOf(exercise.tier)
            }

            // 3. Pick exercises
            // For now, we take the top ones. In the future, you could add logic to "rotate" them.
            // If we need random variety, use .shuffled() instead of pure sort.
            val picks = sortedCandidates.take(slot.count)

            picks.forEach { exercise ->
                selectedIds.add(exercise.id)
                
                // 4. Determine Volume (Sets/Reps) - these are suggestions, not actual sets
                // Logic: Heavy days = lower reps, Light days = higher reps.
                // TIER 1 is always lower reps than TIER 3.
                val (sets, reps) = getVolume(intensity, exercise.tier ?: Tier.TIER_1)
                
                // 5. Add exercise with recommendations (no sets created yet)
                workout.add(
                    RecommendedExercise(
                        exerciseId = exercise.id,
                        exerciseName = exercise.name,
                        workoutType = workoutType,
                        recommendedSets = sets,
                        recommendedReps = reps
                    )
                )
            }
        }

        return workout
    }

    // --- INTERNAL LOGIC ---

    private data class SlotBlueprint(
        val patterns: List<MovementPattern>,
        val preferredTiers: List<Tier>,
        val count: Int = 1
    )

    private fun getVolume(intensity: SessionIntensity, tier: Tier): Pair<Int, Int> {
        // Returns Pair(Sets, Reps)
        return if (intensity == SessionIntensity.HEAVY) {
            when (tier) {
                Tier.TIER_1 -> 3 to 5   // Strength work (3x5)
                Tier.TIER_2 -> 3 to 8   // Assistance strength (3x8)
                Tier.TIER_3 -> 3 to 12  // Isolation (3x12)
            }
        } else {
            // Light / Hypertrophy Day
            when (tier) {
                Tier.TIER_1 -> 3 to 10  // Volume work (3x10)
                Tier.TIER_2 -> 3 to 12  // Hypertrophy (3x12)
                Tier.TIER_3 -> 3 to 15  // Metabolic stress (3x15)
            }
        }
    }

    private fun getBlueprint(focus: SessionFocus, intensity: SessionIntensity): List<SlotBlueprint> {
        return when (focus) {
            SessionFocus.UPPER -> getUpperBodyBlueprint(intensity)
            SessionFocus.LOWER -> getLowerBodyBlueprint(intensity)
            SessionFocus.FULL -> getFullBodyBlueprint(intensity)
            else -> getFullBodyBlueprint(intensity) // Default fallback
        }
    }

    // --- BLUEPRINTS ---

    private fun getUpperBodyBlueprint(intensity: SessionIntensity): List<SlotBlueprint> {
        return if (intensity == SessionIntensity.HEAVY) {
            listOf(
                // 1. Main Horizontal Push (e.g. Bench)
                SlotBlueprint(listOf(MovementPattern.PUSH_HORIZONTAL), listOf(Tier.TIER_1), 1),
                // 2. Main Vertical Pull (e.g. Weighted Pullup / Heavy Row)
                SlotBlueprint(listOf(MovementPattern.PULL_VERTICAL, MovementPattern.PULL_HORIZONTAL), listOf(Tier.TIER_1), 1),
                // 3. Secondary Vertical Push (e.g. OHP)
                SlotBlueprint(listOf(MovementPattern.PUSH_VERTICAL), listOf(Tier.TIER_1, Tier.TIER_2), 1),
                // 4. Secondary Pull (Row)
                SlotBlueprint(listOf(MovementPattern.PULL_HORIZONTAL, MovementPattern.PULL_VERTICAL), listOf(Tier.TIER_2), 1),
                // 5. Arms / Shoulders (Isolation)
                SlotBlueprint(
                    listOf(MovementPattern.ISOLATION_ELBOW_FLEXION, MovementPattern.ISOLATION_ELBOW_EXTENSION, MovementPattern.ISOLATION_SHOULDER_ABDUCTION),
                    listOf(Tier.TIER_3), 
                    2
                )
            )
        } else {
            // LIGHT / HYPERTROPHY UPPER
            listOf(
                // 1. Chest Variation (Incline/Dumbbell)
                SlotBlueprint(listOf(MovementPattern.PUSH_HORIZONTAL, MovementPattern.ISOLATION_SHOULDER_FLEXION), listOf(Tier.TIER_2, Tier.TIER_3), 1),
                // 2. Back Volume (Lat Pulldowns/Rows)
                SlotBlueprint(listOf(MovementPattern.PULL_VERTICAL, MovementPattern.PULL_HORIZONTAL), listOf(Tier.TIER_2), 2),
                // 3. Shoulder Volume
                SlotBlueprint(listOf(MovementPattern.PUSH_VERTICAL, MovementPattern.ISOLATION_SHOULDER_ABDUCTION), listOf(Tier.TIER_2, Tier.TIER_3), 2),
                // 4. Arm Pump
                SlotBlueprint(listOf(MovementPattern.ISOLATION_ELBOW_FLEXION, MovementPattern.ISOLATION_ELBOW_EXTENSION), listOf(Tier.TIER_3), 2)
            )
        }
    }

    private fun getLowerBodyBlueprint(intensity: SessionIntensity): List<SlotBlueprint> {
        return if (intensity == SessionIntensity.HEAVY) {
            listOf(
                // 1. Main Squat
                SlotBlueprint(listOf(MovementPattern.SQUAT), listOf(Tier.TIER_1), 1),
                // 2. Main Hinge
                SlotBlueprint(listOf(MovementPattern.HINGE), listOf(Tier.TIER_1), 1),
                // 3. Unilateral / Lunge
                SlotBlueprint(listOf(MovementPattern.LUNGE), listOf(Tier.TIER_2), 1),
                // 4. Calves / Isolation
                SlotBlueprint(listOf(MovementPattern.ISOLATION_PLANTAR_FLEXION, MovementPattern.ISOLATION_KNEE_EXTENSION), listOf(Tier.TIER_3), 1)
            )
        } else {
            // LIGHT / HYPERTROPHY LOWER
            listOf(
                // 1. Quad Focus (Leg Press/Goblet)
                SlotBlueprint(listOf(MovementPattern.SQUAT, MovementPattern.LUNGE), listOf(Tier.TIER_2), 2),
                // 2. Hamstring/Glute Focus (RDL/Curl)
                SlotBlueprint(listOf(MovementPattern.HINGE, MovementPattern.ISOLATION_KNEE_FLEXION), listOf(Tier.TIER_2, Tier.TIER_3), 2),
                // 3. Isolation (Extensions/Calves)
                SlotBlueprint(listOf(MovementPattern.ISOLATION_KNEE_EXTENSION, MovementPattern.ISOLATION_PLANTAR_FLEXION), listOf(Tier.TIER_3), 2)
            )
        }
    }

    private fun getFullBodyBlueprint(intensity: SessionIntensity): List<SlotBlueprint> {
        // Classic Full Body Structure
        return listOf(
            // 1. Squat Pattern
            SlotBlueprint(listOf(MovementPattern.SQUAT, MovementPattern.LUNGE), listOf(Tier.TIER_1, Tier.TIER_2), 1),
            // 2. Hinge Pattern
            SlotBlueprint(listOf(MovementPattern.HINGE), listOf(Tier.TIER_1, Tier.TIER_2), 1),
            // 3. Push Pattern
            SlotBlueprint(listOf(MovementPattern.PUSH_HORIZONTAL, MovementPattern.PUSH_VERTICAL), listOf(Tier.TIER_1, Tier.TIER_2), 1),
            // 4. Pull Pattern
            SlotBlueprint(listOf(MovementPattern.PULL_VERTICAL, MovementPattern.PULL_HORIZONTAL), listOf(Tier.TIER_1, Tier.TIER_2), 1),
            // 5. Core / Carry / Arms
            SlotBlueprint(listOf(MovementPattern.CORE, MovementPattern.CARRY, MovementPattern.ISOLATION_ARMS), listOf(Tier.TIER_3), 1)
        )
    }
}