package com.liftpath.helpers

import com.liftpath.models.BodyRegion
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.MovementPattern
import com.liftpath.models.TargetMuscle
import com.liftpath.models.Tier

object DefaultExercisesHelper {
    fun getPopularDefaults(): List<ExerciseLibraryItem> {
        return listOf(
            // ==============================================================================
            // LEGACY EXERCISES (IDs 1-99)
            // Preserved to match your historical data
            // ==============================================================================

            ExerciseLibraryItem(
                id = 1,
                name = "Deadlift (Barbell)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.HINGE,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.HAMSTRINGS, TargetMuscle.GLUTES, TargetMuscle.LOWER_BACK),
                secondaryTargets = listOf(TargetMuscle.TRAPS_UPPER, TargetMuscle.FOREARMS, TargetMuscle.QUADS, TargetMuscle.ADDUCTORS)
            ),
            ExerciseLibraryItem(
                id = 2,
                name = "Back Squat (Barbell)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.SQUAT,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.QUADS, TargetMuscle.GLUTES, TargetMuscle.ADDUCTORS),
                secondaryTargets = listOf(TargetMuscle.LOWER_BACK, TargetMuscle.ABS)
            ),
            ExerciseLibraryItem(
                id = 4,
                name = "Bicep Curl (Dumbbell)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_ELBOW_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.BICEPS),
                secondaryTargets = listOf(TargetMuscle.FOREARMS)
            ),
            ExerciseLibraryItem(
                id = 5,
                name = "Triceps Pushdown (Cable)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_ELBOW_EXTENSION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.TRICEPS_LATERAL),
                secondaryTargets = listOf()
            ),
            ExerciseLibraryItem(
                id = 7,
                name = "Bench Press (Barbell)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_HORIZONTAL,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.CHEST_MIDDLE, TargetMuscle.TRICEPS_LATERAL, TargetMuscle.DELT_FRONT),
                secondaryTargets = listOf(TargetMuscle.CHEST_UPPER)
            ),
            ExerciseLibraryItem(
                id = 8,
                name = "Split Squat (Barbell)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.LUNGE,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.QUADS, TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.ADDUCTORS, TargetMuscle.CALVES)
            ),
            ExerciseLibraryItem(
                id = 9,
                name = "Calf Raise (Machine)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.ISOLATION_PLANTAR_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.CALVES),
                secondaryTargets = listOf()
            ),
            ExerciseLibraryItem(
                id = 10,
                name = "Decline Bench Press (Barbell)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_HORIZONTAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.CHEST_LOWER, TargetMuscle.TRICEPS_LATERAL),
                secondaryTargets = listOf(TargetMuscle.DELT_FRONT)
            ),
            ExerciseLibraryItem(
                id = 11,
                name = "Incline Dumbbell Press",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_HORIZONTAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.CHEST_UPPER, TargetMuscle.DELT_FRONT),
                secondaryTargets = listOf(TargetMuscle.TRICEPS_LATERAL)
            ),
            ExerciseLibraryItem(
                id = 12,
                name = "Seated Cable Row",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PULL_HORIZONTAL,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.LATS, TargetMuscle.TRAPS_MID),
                secondaryTargets = listOf(TargetMuscle.BICEPS, TargetMuscle.DELT_REAR)
            ),
            ExerciseLibraryItem(
                id = 13,
                name = "Triceps Extension (Single Arm)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_ELBOW_EXTENSION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.TRICEPS_LONG),
                secondaryTargets = listOf()
            ),
            ExerciseLibraryItem(
                id = 14,
                name = "Machine Shoulder Press",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_VERTICAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.DELT_FRONT, TargetMuscle.DELT_SIDE),
                secondaryTargets = listOf(TargetMuscle.TRICEPS_LATERAL, TargetMuscle.TRAPS_UPPER)
            ),
            ExerciseLibraryItem(
                id = 15,
                name = "Dips (Bodyweight)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_VERTICAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.CHEST_LOWER, TargetMuscle.TRICEPS_LATERAL),
                secondaryTargets = listOf(TargetMuscle.DELT_FRONT)
            ),
            ExerciseLibraryItem(
                id = 16,
                name = "Abdominal Crunch (Machine)",
                region = BodyRegion.CORE,
                pattern = MovementPattern.CORE_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.ABS),
                secondaryTargets = listOf()
            ),
            ExerciseLibraryItem(
                id = 17,
                name = "Bench Press (Paused)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_HORIZONTAL,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.CHEST_MIDDLE, TargetMuscle.TRICEPS_LATERAL),
                secondaryTargets = listOf(TargetMuscle.DELT_FRONT)
            ),
            ExerciseLibraryItem(
                id = 18,
                name = "Leg Curl (Machine)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.ISOLATION_KNEE_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.HAMSTRINGS),
                secondaryTargets = listOf()
            ),

            // ==============================================================================
            // EXPANDED LIBRARY (IDs 100+)
            // The "Coach" set covering all body parts/patterns
            // ==============================================================================

            // --- UPPER BODY: PUSH ---
            ExerciseLibraryItem(
                id = 100,
                name = "Overhead Press (Barbell)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_VERTICAL,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.DELT_FRONT, TargetMuscle.TRICEPS_LATERAL),
                secondaryTargets = listOf(TargetMuscle.TRAPS_UPPER, TargetMuscle.ABS)
            ),
            ExerciseLibraryItem(
                id = 118,
                name = "Dumbbell Shoulder Press (Seated)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_VERTICAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.DELT_FRONT, TargetMuscle.DELT_SIDE),
                secondaryTargets = listOf(TargetMuscle.TRICEPS_LATERAL)
            ),
            ExerciseLibraryItem(
                id = 131,
                name = "Push Up",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_HORIZONTAL,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.CHEST_MIDDLE, TargetMuscle.TRICEPS_LATERAL),
                secondaryTargets = listOf(TargetMuscle.ABS, TargetMuscle.DELT_FRONT)
            ),
            ExerciseLibraryItem(
                id = 117,
                name = "Pec Deck / Machine Fly",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_HORIZONTAL,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.CHEST_MIDDLE),
                secondaryTargets = listOf(TargetMuscle.DELT_FRONT)
            ),
            ExerciseLibraryItem(
                id = 129,
                name = "Cable Crossover",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_SHOULDER_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.CHEST_LOWER, TargetMuscle.CHEST_MIDDLE),
                secondaryTargets = listOf()
            ),
            ExerciseLibraryItem(
                id = 116,
                name = "Incline Dumbbell Fly",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_SHOULDER_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.CHEST_UPPER),
                secondaryTargets = listOf(TargetMuscle.DELT_FRONT)
            ),

            // --- UPPER BODY: PULL ---
            ExerciseLibraryItem(
                id = 101,
                name = "Pull Up (Bodyweight)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PULL_VERTICAL,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.LATS, TargetMuscle.BICEPS),
                secondaryTargets = listOf(TargetMuscle.TRAPS_MID, TargetMuscle.FOREARMS)
            ),
            ExerciseLibraryItem(
                id = 102,
                name = "Chin Up",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PULL_VERTICAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.LATS, TargetMuscle.BICEPS),
                secondaryTargets = listOf(TargetMuscle.FOREARMS)
            ),
            ExerciseLibraryItem(
                id = 106,
                name = "Lat Pulldown (Wide Grip)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PULL_VERTICAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.LATS, TargetMuscle.TRAPS_MID),
                secondaryTargets = listOf(TargetMuscle.BICEPS, TargetMuscle.DELT_REAR)
            ),
            ExerciseLibraryItem(
                id = 128,
                name = "Barbell Row (Pendlay)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PULL_HORIZONTAL,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.LATS, TargetMuscle.TRAPS_MID, TargetMuscle.LOWER_BACK),
                secondaryTargets = listOf(TargetMuscle.BICEPS, TargetMuscle.DELT_REAR)
            ),
            ExerciseLibraryItem(
                id = 107,
                name = "Dumbbell Row",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PULL_HORIZONTAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.LATS, TargetMuscle.TRAPS_MID),
                secondaryTargets = listOf(TargetMuscle.BICEPS, TargetMuscle.FOREARMS)
            ),
            ExerciseLibraryItem(
                id = 124,
                name = "Barbell Shrug",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PULL_VERTICAL,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.TRAPS_UPPER),
                secondaryTargets = listOf(TargetMuscle.FOREARMS)
            ),

            // --- UPPER BODY: ISOLATION (Arms/Shoulders) ---
            ExerciseLibraryItem(
                id = 109,
                name = "Lateral Raise (Dumbbell)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_SHOULDER_ABDUCTION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.DELT_SIDE),
                secondaryTargets = listOf(TargetMuscle.TRAPS_UPPER)
            ),
            ExerciseLibraryItem(
                id = 108,
                name = "Face Pull (Cable)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_SHOULDER_EXTENSION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.DELT_REAR, TargetMuscle.TRAPS_MID),
                secondaryTargets = listOf(TargetMuscle.BICEPS, TargetMuscle.TRAPS_UPPER)
            ),
            ExerciseLibraryItem(
                id = 119,
                name = "Reverse Fly (Dumbbell)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_SHOULDER_EXTENSION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.DELT_REAR),
                secondaryTargets = listOf(TargetMuscle.TRAPS_MID)
            ),
            ExerciseLibraryItem(
                id = 112,
                name = "Skullcrusher (EZ Bar)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_ELBOW_EXTENSION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.TRICEPS_LONG, TargetMuscle.TRICEPS_LATERAL),
                secondaryTargets = listOf()
            ),
            ExerciseLibraryItem(
                id = 113,
                name = "Hammer Curl (Dumbbell)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_ELBOW_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.BICEPS, TargetMuscle.FOREARMS),
                secondaryTargets = listOf()
            ),
            ExerciseLibraryItem(
                id = 130,
                name = "Preacher Curl (EZ Bar)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_ELBOW_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.BICEPS),
                secondaryTargets = listOf()
            ),

            // --- LOWER BODY: SQUAT/QUADS ---
            ExerciseLibraryItem(
                id = 114,
                name = "Front Squat (Barbell)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.SQUAT,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.QUADS, TargetMuscle.ABS, TargetMuscle.TRAPS_MID),
                secondaryTargets = listOf(TargetMuscle.GLUTES)
            ),
            ExerciseLibraryItem(
                id = 127,
                name = "Goblet Squat",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.SQUAT,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.QUADS, TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.ABS, TargetMuscle.TRAPS_MID)
            ),
            ExerciseLibraryItem(
                id = 104,
                name = "Leg Press",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.SQUAT,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.QUADS, TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.ADDUCTORS)
            ),
            ExerciseLibraryItem(
                id = 110,
                name = "Leg Extension (Machine)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.ISOLATION_KNEE_EXTENSION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.QUADS),
                secondaryTargets = listOf()
            ),

            // --- LOWER BODY: HINGE/HAMSTRINGS/GLUTES ---
            ExerciseLibraryItem(
                id = 103,
                name = "Romanian Deadlift (Barbell)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.HINGE,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.HAMSTRINGS, TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.LOWER_BACK, TargetMuscle.FOREARMS)
            ),
            ExerciseLibraryItem(
                id = 123,
                name = "Sumo Deadlift",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.HINGE,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.HAMSTRINGS, TargetMuscle.GLUTES, TargetMuscle.QUADS),
                secondaryTargets = listOf(TargetMuscle.ADDUCTORS, TargetMuscle.LOWER_BACK)
            ),
            ExerciseLibraryItem(
                id = 111,
                name = "Hip Thrust (Barbell)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.HINGE,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.HAMSTRINGS, TargetMuscle.ABS)
            ),
            ExerciseLibraryItem(
                id = 126,
                name = "Glute Bridge (Barbell)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.HINGE,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.HAMSTRINGS)
            ),

            // --- LOWER BODY: LUNGE/UNILATERAL ---
            ExerciseLibraryItem(
                id = 105,
                name = "Bulgarian Split Squat (Dumbbell)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.LUNGE,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.QUADS, TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.ADDUCTORS, TargetMuscle.ABS)
            ),
            ExerciseLibraryItem(
                id = 115,
                name = "Walking Lunges",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.LUNGE,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.QUADS, TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.CALVES, TargetMuscle.ABS)
            ),

            // --- FULL BODY / CORE ---
            ExerciseLibraryItem(
                id = 125,
                name = "Farmer's Walk",
                region = BodyRegion.FULL,
                pattern = MovementPattern.CARRY,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.FOREARMS, TargetMuscle.TRAPS_UPPER),
                secondaryTargets = listOf(TargetMuscle.ABS, TargetMuscle.CALVES)
            ),
            ExerciseLibraryItem(
                id = 120,
                name = "Hanging Leg Raise",
                region = BodyRegion.CORE,
                pattern = MovementPattern.CORE_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.ABS),
                secondaryTargets = listOf(TargetMuscle.FOREARMS)
            ),
            ExerciseLibraryItem(
                id = 122,
                name = "Cable Woodchopper",
                region = BodyRegion.CORE,
                pattern = MovementPattern.CORE_STABILITY,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.OBLIQUES),
                secondaryTargets = listOf(TargetMuscle.ABS)
            ),
            ExerciseLibraryItem(
                id = 121,
                name = "Plank",
                region = BodyRegion.CORE,
                pattern = MovementPattern.CORE_STABILITY,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.ABS, TargetMuscle.OBLIQUES),
                secondaryTargets = listOf()
            )
        )
    }
}