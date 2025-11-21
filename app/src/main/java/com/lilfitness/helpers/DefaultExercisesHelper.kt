package com.lilfitness.helpers

import com.lilfitness.models.ExerciseLibraryItem
import com.lilfitness.models.Mechanics
import com.lilfitness.models.MovementPattern
import com.lilfitness.models.Tier

object DefaultExercisesHelper {
    fun getPopularDefaults(): List<ExerciseLibraryItem> {
        return listOf(
            // LEGS
            ExerciseLibraryItem(100, "Leg Press", null, MovementPattern.SQUAT, Mechanics.COMPOUND, Tier.TIER_2),
            ExerciseLibraryItem(101, "Goblet Squat", null, MovementPattern.SQUAT, Mechanics.COMPOUND, Tier.TIER_2),
            ExerciseLibraryItem(102, "Bulgarian Split Squat", null, MovementPattern.LUNGE, Mechanics.COMPOUND, Tier.TIER_2),
            ExerciseLibraryItem(103, "Leg Extension", null, MovementPattern.SQUAT, Mechanics.ISOLATION, Tier.TIER_3),

            // HINGE
            ExerciseLibraryItem(104, "Romanian Deadlift (Barbell)", null, MovementPattern.HINGE, Mechanics.COMPOUND, Tier.TIER_2),
            ExerciseLibraryItem(105, "Kettlebell Swing", null, MovementPattern.HINGE, Mechanics.COMPOUND, Tier.TIER_2),
            ExerciseLibraryItem(106, "Glute Bridge", null, MovementPattern.HINGE, Mechanics.ISOLATION, Tier.TIER_3),

            // PUSH HORIZONTAL
            ExerciseLibraryItem(107, "Incline Bench Press (Barbell)", null, MovementPattern.PUSH_HORIZONTAL, Mechanics.COMPOUND, Tier.TIER_2),
            ExerciseLibraryItem(108, "Dumbbell Bench Press (Flat)", null, MovementPattern.PUSH_HORIZONTAL, Mechanics.COMPOUND, Tier.TIER_1),
            ExerciseLibraryItem(109, "Push Up", null, MovementPattern.PUSH_HORIZONTAL, Mechanics.COMPOUND, Tier.TIER_2),
            ExerciseLibraryItem(110, "Chest Fly (Machine)", null, MovementPattern.PUSH_HORIZONTAL, Mechanics.ISOLATION, Tier.TIER_3),

            // PUSH VERTICAL
            ExerciseLibraryItem(111, "Overhead Press (Barbell)", null, MovementPattern.PUSH_VERTICAL, Mechanics.COMPOUND, Tier.TIER_1),
            ExerciseLibraryItem(112, "Dumbbell Shoulder Press", null, MovementPattern.PUSH_VERTICAL, Mechanics.COMPOUND, Tier.TIER_2),
            ExerciseLibraryItem(113, "Lateral Raise (Dumbbell)", null, MovementPattern.PUSH_VERTICAL, Mechanics.ISOLATION, Tier.TIER_3),
            ExerciseLibraryItem(114, "Arnold Press", null, MovementPattern.PUSH_VERTICAL, Mechanics.COMPOUND, Tier.TIER_2),

            // PULL VERTICAL
            ExerciseLibraryItem(115, "Pull Up", null, MovementPattern.PULL_VERTICAL, Mechanics.COMPOUND, Tier.TIER_1),
            ExerciseLibraryItem(116, "Lat Pulldown (Wide Grip)", null, MovementPattern.PULL_VERTICAL, Mechanics.COMPOUND, Tier.TIER_2),
            ExerciseLibraryItem(117, "Chin Up", null, MovementPattern.PULL_VERTICAL, Mechanics.COMPOUND, Tier.TIER_2),

            // PULL HORIZONTAL
            ExerciseLibraryItem(118, "Barbell Row (Bent Over)", null, MovementPattern.PULL_HORIZONTAL, Mechanics.COMPOUND, Tier.TIER_1),
            ExerciseLibraryItem(119, "Dumbbell Row (Single Arm)", null, MovementPattern.PULL_HORIZONTAL, Mechanics.COMPOUND, Tier.TIER_2),
            ExerciseLibraryItem(120, "Face Pull", null, MovementPattern.PULL_HORIZONTAL, Mechanics.ISOLATION, Tier.TIER_3),

            // ARMS & CORE
            ExerciseLibraryItem(121, "Hammer Curl", null, MovementPattern.ISOLATION_ARMS, Mechanics.ISOLATION, Tier.TIER_3),
            ExerciseLibraryItem(122, "Skullcrusher", null, MovementPattern.ISOLATION_ARMS, Mechanics.ISOLATION, Tier.TIER_3),
            ExerciseLibraryItem(123, "Plank", null, MovementPattern.CORE, Mechanics.ISOLATION, Tier.TIER_3),
            ExerciseLibraryItem(124, "Hanging Leg Raise", null, MovementPattern.CORE, Mechanics.ISOLATION, Tier.TIER_3)
        )
    }
}