package com.lilfitness.helpers

import com.lilfitness.models.ExerciseEntry
import com.lilfitness.models.ExerciseLibraryItem
import com.lilfitness.models.MovementPattern
import com.lilfitness.models.Tier
import com.lilfitness.models.UserLevel

object WorkoutGenerator {
    
    /**
     * Merges user exercises with default exercises, prioritizing user exercises if duplicates exist.
     * Duplicates are determined by matching names (case-insensitive).
     */
    private fun mergeExercises(
        userExercises: List<ExerciseLibraryItem>,
        defaultExercises: List<ExerciseLibraryItem>
    ): List<ExerciseLibraryItem> {
        val merged = userExercises.toMutableList()
        val userExerciseNames = userExercises.map { it.name.lowercase() }.toSet()
        
        // Add default exercises that don't have matching names in user's library
        defaultExercises.forEach { defaultExercise ->
            if (!userExerciseNames.contains(defaultExercise.name.lowercase())) {
                merged.add(defaultExercise)
            }
        }
        
        return merged
    }

    /**
     * Generates a list of exercises for a workout session with balanced tier distribution.
     * Exercises are returned without sets - user will add sets manually.
     * Automatically merges user exercises with default exercises.
     * 
     * @param userLevel The user's training level (NOVICE or INTERMEDIATE)
     * @param sessionType The type of session ("heavy" or "light")
     * @param userExercises The user's exercise library
     * @param defaultExercises Optional default exercises to merge in (if null, will get from DefaultExercisesHelper)
     * @param settings Optional progression settings (not used for exercise selection, kept for compatibility)
     * @return Pair of (selected exercises, default exercises that were selected) - default exercises need to be added to user's library
     */
    fun generateFullWorkout(
        userLevel: UserLevel,
        sessionType: String,
        userExercises: List<ExerciseLibraryItem>,
        defaultExercises: List<ExerciseLibraryItem>? = null,
        settings: ProgressionHelper.ProgressionSettings? = null
    ): Pair<List<ExerciseLibraryItem>, List<ExerciseLibraryItem>> {
        // Merge user exercises with default exercises
        val defaults = defaultExercises ?: DefaultExercisesHelper.getPopularDefaults()
        val allExercises = mergeExercises(userExercises, defaults)
        
        // Track which exercises came from defaults (ID >= 100 or not in user's library)
        val userExerciseIds = userExercises.map { it.id }.toSet()
        val userExerciseNames = userExercises.map { it.name.lowercase() }.toSet()
        
        val selectedExercises = mutableListOf<ExerciseLibraryItem>()
        
        when (userLevel) {
            UserLevel.NOVICE -> {
                // NOVICE: Linear progression - same exercises for heavy and light
                // Structure: Tier 1 main lifts + Tier 2 assistance + Tier 3 accessories
                
                // Tier 1: Main compound movements (3-4 exercises)
                val tier1Exercises = selectExercisesByTier(allExercises, Tier.TIER_1, sessionType)
                val mainLifts = listOf(
                    MovementPattern.SQUAT,
                    MovementPattern.HINGE,
                    MovementPattern.PUSH_HORIZONTAL,
                    MovementPattern.PULL_VERTICAL
                )
                
                mainLifts.forEach { pattern ->
                    val exercise = tier1Exercises.firstOrNull { it.pattern == pattern }
                    if (exercise != null) {
                        selectedExercises.add(exercise)
                    }
                }
                
                // Tier 2: Assistance work (1-2 exercises) - only on heavy days for volume
                if (sessionType == "heavy") {
                    val tier2Exercises = selectExercisesByTier(allExercises, Tier.TIER_2, sessionType)
                    val assistancePatterns = listOf(
                        MovementPattern.PULL_HORIZONTAL,
                        MovementPattern.PUSH_VERTICAL
                    )
                    
                    assistancePatterns.forEach { pattern ->
                        val exercise = tier2Exercises.firstOrNull { it.pattern == pattern }
                        if (exercise != null && selectedExercises.size < 6) { // Limit total exercises
                            selectedExercises.add(exercise)
                        }
                    }
                }
                
                // Tier 3: Isolation/Accessories (1-2 exercises)
                val tier3Exercises = selectExercisesByTier(allExercises, Tier.TIER_3, sessionType)
                val accessoryPatterns = listOf(
                    MovementPattern.ISOLATION_ARMS,
                    MovementPattern.CORE
                )
                
                accessoryPatterns.forEach { pattern ->
                    val exercise = tier3Exercises.firstOrNull { it.pattern == pattern }
                    if (exercise != null && selectedExercises.size < 7) { // Limit total exercises
                        selectedExercises.add(exercise)
                    }
                }
            }
            
            UserLevel.INTERMEDIATE -> {
                // INTERMEDIATE: Periodized - different exercises for heavy vs light
                // Heavy: Tier 1 main lifts
                // Light: Tier 2 variations
                
                if (sessionType == "heavy") {
                    // Heavy day: Focus on Tier 1 main lifts
                    val tier1Exercises = selectExercisesByTier(allExercises, Tier.TIER_1, sessionType)
                    val mainLifts = listOf(
                        MovementPattern.SQUAT,
                        MovementPattern.HINGE,
                        MovementPattern.PUSH_HORIZONTAL,
                        MovementPattern.PULL_VERTICAL
                    )
                    
                    mainLifts.forEach { pattern ->
                        val exercise = tier1Exercises.firstOrNull { it.pattern == pattern }
                        if (exercise != null) {
                            selectedExercises.add(exercise)
                        }
                    }
                    
                    // Add 1-2 Tier 2 assistance exercises
                    val tier2Exercises = selectExercisesByTier(allExercises, Tier.TIER_2, sessionType)
                    val assistancePatterns = listOf(
                        MovementPattern.PULL_HORIZONTAL,
                        MovementPattern.PUSH_VERTICAL
                    )
                    
                    assistancePatterns.forEach { pattern ->
                        val exercise = tier2Exercises.firstOrNull { it.pattern == pattern }
                        if (exercise != null && selectedExercises.size < 6) {
                            selectedExercises.add(exercise)
                        }
                    }
                } else {
                    // Light day: Focus on Tier 2 variations
                    val tier2Exercises = selectExercisesByTier(allExercises, Tier.TIER_2, sessionType)
                    val lightDayPatterns = listOf(
                        MovementPattern.SQUAT,
                        MovementPattern.HINGE,
                        MovementPattern.PUSH_HORIZONTAL,
                        MovementPattern.PULL_VERTICAL
                    )
                    
                    lightDayPatterns.forEach { pattern ->
                        val exercise = tier2Exercises.firstOrNull { it.pattern == pattern }
                        if (exercise != null) {
                            selectedExercises.add(exercise)
                        } else {
                            // Fallback to Tier 1 if no Tier 2 exists for this pattern
                            val tier1Exercises = selectExercisesByTier(allExercises, Tier.TIER_1, sessionType)
                            val fallbackExercise = tier1Exercises.firstOrNull { it.pattern == pattern }
                            if (fallbackExercise != null) {
                                selectedExercises.add(fallbackExercise)
                            }
                        }
                    }
                }
                
                // Tier 3: Accessories (1-2 exercises) on both heavy and light days
                val tier3Exercises = selectExercisesByTier(allExercises, Tier.TIER_3, sessionType)
                val accessoryPatterns = listOf(
                    MovementPattern.ISOLATION_ARMS,
                    MovementPattern.CORE
                )
                
                accessoryPatterns.forEach { pattern ->
                    val exercise = tier3Exercises.firstOrNull { it.pattern == pattern }
                    if (exercise != null && selectedExercises.size < 7) {
                        selectedExercises.add(exercise)
                    }
                }
            }
        }
        
        // Separate exercises that came from defaults (not in user's library)
        val defaultExerciseIds = defaults.map { it.id }.toSet()
        val defaultExerciseNames = defaults.map { it.name.lowercase() }.toSet()
        
        val defaultExercisesUsed = selectedExercises.filter { exercise ->
            // Exercise is from defaults if it's in the defaults list (by ID or name)
            // AND not in user's library (by ID and name)
            val isDefault = (defaultExerciseIds.contains(exercise.id) || 
                           defaultExerciseNames.contains(exercise.name.lowercase()))
            val notInUserLibrary = (!userExerciseIds.contains(exercise.id) && 
                                   !userExerciseNames.contains(exercise.name.lowercase()))
            isDefault && notInUserLibrary
        }
        
        return Pair(selectedExercises, defaultExercisesUsed)
    }

    /**
     * Legacy function for single exercise generation (kept for backward compatibility).
     */
    fun generateSession(
        userLevel: UserLevel,
        sessionType: String,
        pattern: MovementPattern,
        allExercises: List<ExerciseLibraryItem>
    ): List<ExerciseEntry> {
        // Step 1: Filter exercises matching the requested MovementPattern
        val matchingExercises = allExercises.filter { it.pattern == pattern }
        
        if (matchingExercises.isEmpty()) {
            return emptyList()
        }
        
        // Step 2: Selection Logic
        val selectedExercise = when (userLevel) {
            UserLevel.NOVICE -> {
                // NOVICE: Always select TIER_1 exercises, regardless of "heavy" or "light"
                matchingExercises.firstOrNull { it.tier == Tier.TIER_1 }
                    ?: matchingExercises.first() // Fallback to first available
            }
            UserLevel.INTERMEDIATE -> {
                // INTERMEDIATE:
                // - IF sessionType == "heavy": Select TIER_1
                // - IF sessionType == "light": Select TIER_2
                // Fallback: If no Tier 2 exists, use Tier 1
                when (sessionType) {
                    "heavy" -> {
                        matchingExercises.firstOrNull { it.tier == Tier.TIER_1 }
                            ?: matchingExercises.first()
                    }
                    "light" -> {
                        matchingExercises.firstOrNull { it.tier == Tier.TIER_2 }
                            ?: matchingExercises.firstOrNull { it.tier == Tier.TIER_1 }
                            ?: matchingExercises.first()
                    }
                    else -> matchingExercises.first()
                }
            }
        }
        
        // Step 3: Return list of ExerciseEntry objects with default sets/reps
        val sets = when (sessionType) {
            "heavy" -> 3
            "light" -> 4
            else -> 3
        }
        
        val reps = when (sessionType) {
            "heavy" -> 5
            "light" -> 10
            else -> 5
        }
        
        return createExerciseSets(selectedExercise, sets, reps, sessionType)
    }
    
    /**
     * Helper function to select exercises by tier, filtering out exercises without required attributes.
     */
    private fun selectExercisesByTier(
        allExercises: List<ExerciseLibraryItem>,
        tier: Tier,
        sessionType: String
    ): List<ExerciseLibraryItem> {
        return allExercises.filter { 
            it.tier == tier && it.pattern != null 
        }.sortedBy { it.name }
    }
    
    /**
     * Helper function to create sets for an exercise.
     */
    private fun createExerciseSets(
        exercise: ExerciseLibraryItem,
        sets: Int,
        reps: Int,
        workoutType: String
    ): List<ExerciseEntry> {
        return (1..sets).map { setNumber ->
            ExerciseEntry(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                setNumber = setNumber,
                kg = 0f, // Weight will be set by user or progression logic
                reps = reps,
                workoutType = workoutType
            )
        }
    }
}

