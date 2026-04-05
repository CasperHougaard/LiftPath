package com.liftpath.helpers

import com.liftpath.models.BodyRegion
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.Mechanics
import com.liftpath.models.MovementPattern
import com.liftpath.models.TargetMuscle
import com.liftpath.models.Tier

object DefaultExercisesHelper {

    const val CATALOG_VERSION = 1

    fun getPopularDefaults(): List<ExerciseLibraryItem> {
        return listOf(
            ExerciseLibraryItem(
                id = 1,
                name = "Deadlift (Barbell)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.HINGE,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.HAMSTRINGS, TargetMuscle.GLUTES, TargetMuscle.LOWER_BACK),
                secondaryTargets = listOf(TargetMuscle.TRAPS_UPPER, TargetMuscle.FOREARMS, TargetMuscle.QUADS),
                note = "Brace core, neutral spine. Push floor away, don't pull."
            ),
            ExerciseLibraryItem(
                id = 2,
                name = "Back Squat (Barbell)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.SQUAT,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.QUADS, TargetMuscle.GLUTES, TargetMuscle.ADDUCTORS),
                secondaryTargets = listOf(TargetMuscle.LOWER_BACK, TargetMuscle.ABS),
                note = "Knees track toes. Depth to parallel or below."
            ),
            ExerciseLibraryItem(
                id = 4,
                name = "Bicep Curl (Dumbbell)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_ELBOW_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.BICEPS),
                secondaryTargets = listOf(TargetMuscle.FOREARMS),
                note = "Control eccentric. No swinging."
            ),
            ExerciseLibraryItem(
                id = 5,
                name = "Triceps Pushdown (Cable)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_ELBOW_EXTENSION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.TRICEPS_LATERAL),
                secondaryTargets = emptyList(),
                note = "Elbows pinned. Full extension at bottom."
            ),
            ExerciseLibraryItem(
                id = 7,
                name = "Bench Press (Barbell)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_HORIZONTAL,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.CHEST_MIDDLE, TargetMuscle.DELT_FRONT, TargetMuscle.TRICEPS_LATERAL),
                secondaryTargets = listOf(TargetMuscle.CHEST_UPPER),
                note = "Retract scapula. Slight arch. Bar to lower chest.",
                manualMechanics = Mechanics.COMPOUND
            ),
            ExerciseLibraryItem(
                id = 8,
                name = "Split Squat (Barbell)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.LUNGE,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.QUADS, TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.ADDUCTORS, TargetMuscle.CALVES),
                note = "Front knee over ankle. Upright torso."
            ),
            ExerciseLibraryItem(
                id = 9,
                name = "Calf Raise (Machine)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.ISOLATION_PLANTAR_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.CALVES),
                secondaryTargets = emptyList(),
                note = "Full range: deep stretch to peak contraction."
            ),
            ExerciseLibraryItem(
                id = 10,
                name = "Decline Bench Press (Barbell)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_HORIZONTAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.CHEST_LOWER, TargetMuscle.TRICEPS_LATERAL),
                secondaryTargets = listOf(TargetMuscle.DELT_FRONT),
                note = "Bar to lower chest. Control descent."
            ),
            ExerciseLibraryItem(
                id = 11,
                name = "Incline Dumbbell Press",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_HORIZONTAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.CHEST_UPPER, TargetMuscle.DELT_FRONT),
                secondaryTargets = listOf(TargetMuscle.TRICEPS_LATERAL),
                note = "30-45° incline. Dumbbells to shoulder level."
            ),
            ExerciseLibraryItem(
                id = 12,
                name = "Seated Cable Row",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PULL_HORIZONTAL,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.LATS, TargetMuscle.TRAPS_MID),
                secondaryTargets = listOf(TargetMuscle.BICEPS, TargetMuscle.DELT_REAR),
                note = "Retract scapula at peak. No excessive torso swing."
            ),
            ExerciseLibraryItem(
                id = 13,
                name = "Triceps Extension (Single Arm)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_ELBOW_EXTENSION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.TRICEPS_LONG),
                secondaryTargets = emptyList(),
                note = "Upper arm stationary. Full extension."
            ),
            ExerciseLibraryItem(
                id = 14,
                name = "Machine Shoulder Press",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_VERTICAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.DELT_FRONT, TargetMuscle.DELT_SIDE),
                secondaryTargets = listOf(TargetMuscle.TRICEPS_LATERAL, TargetMuscle.TRAPS_UPPER),
                note = "Back against pad. Full ROM."
            ),
            ExerciseLibraryItem(
                id = 15,
                name = "Dips (Bodyweight)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_VERTICAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.CHEST_LOWER, TargetMuscle.TRICEPS_LATERAL),
                secondaryTargets = listOf(TargetMuscle.DELT_FRONT),
                note = "Slight forward lean for chest. Control depth."
            ),
            ExerciseLibraryItem(
                id = 16,
                name = "Abdominal Crunch (Machine)",
                region = BodyRegion.CORE,
                pattern = MovementPattern.CORE_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.ABS),
                secondaryTargets = emptyList(),
                note = "Exhale on contraction. Don't pull neck."
            ),
            ExerciseLibraryItem(
                id = 17,
                name = "Bench Press (Paused)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_HORIZONTAL,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.CHEST_MIDDLE, TargetMuscle.TRICEPS_LATERAL),
                secondaryTargets = listOf(TargetMuscle.DELT_FRONT),
                note = "Pause 1-2 sec on chest. Eliminates stretch reflex."
            ),
            ExerciseLibraryItem(
                id = 18,
                name = "Seated Leg Curl (Machine)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.ISOLATION_KNEE_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.HAMSTRINGS),
                secondaryTargets = emptyList(),
                note = "Squeeze hamstrings. Control negative."
            ),
            ExerciseLibraryItem(
                id = 100,
                name = "Overhead Press (Barbell)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_VERTICAL,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.DELT_FRONT, TargetMuscle.TRICEPS_LATERAL),
                secondaryTargets = listOf(TargetMuscle.TRAPS_UPPER, TargetMuscle.ABS),
                note = "Brace core. Bar path straight."
            ),
            ExerciseLibraryItem(
                id = 101,
                name = "Pull Up (Bodyweight)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PULL_VERTICAL,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.LATS, TargetMuscle.BICEPS),
                secondaryTargets = listOf(TargetMuscle.TRAPS_MID, TargetMuscle.FOREARMS),
                note = "Chin over bar. Full hang at bottom."
            ),
            ExerciseLibraryItem(
                id = 102,
                name = "Chin Up",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PULL_VERTICAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.LATS, TargetMuscle.BICEPS),
                secondaryTargets = listOf(TargetMuscle.FOREARMS),
                note = "Palms face you. Squeeze at top."
            ),
            ExerciseLibraryItem(
                id = 103,
                name = "Romanian Deadlift (Barbell)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.HINGE,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.HAMSTRINGS, TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.LOWER_BACK, TargetMuscle.FOREARMS),
                note = "Slight knee bend. Push hips back."
            ),
            ExerciseLibraryItem(
                id = 104,
                name = "Leg Press",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.SQUAT,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.QUADS, TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.ADDUCTORS),
                note = "Feet shoulder-width. Don't lock knees."
            ),
            ExerciseLibraryItem(
                id = 105,
                name = "Bulgarian Split Squat (Dumbbell)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.LUNGE,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.QUADS, TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.ADDUCTORS, TargetMuscle.ABS),
                note = "Narrow stance. Front knee tracks toe."
            ),
            ExerciseLibraryItem(
                id = 106,
                name = "Lat Pulldown (Wide Grip)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PULL_VERTICAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.LATS, TargetMuscle.TRAPS_MID),
                secondaryTargets = listOf(TargetMuscle.BICEPS, TargetMuscle.DELT_REAR),
                note = "Pull to upper chest. Squeeze lats."
            ),
            ExerciseLibraryItem(
                id = 107,
                name = "Dumbbell Row",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PULL_HORIZONTAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.LATS, TargetMuscle.TRAPS_MID),
                secondaryTargets = listOf(TargetMuscle.BICEPS, TargetMuscle.FOREARMS),
                note = "Hinge at hip. Pull elbow past torso."
            ),
            ExerciseLibraryItem(
                id = 108,
                name = "Face Pull (Cable)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_SHOULDER_EXTENSION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.DELT_REAR, TargetMuscle.TRAPS_MID),
                secondaryTargets = listOf(TargetMuscle.BICEPS, TargetMuscle.TRAPS_UPPER),
                note = "External rotation at end. Thumbs back."
            ),
            ExerciseLibraryItem(
                id = 109,
                name = "Lateral Raise (Dumbbell)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_SHOULDER_ABDUCTION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.DELT_SIDE),
                secondaryTargets = listOf(TargetMuscle.TRAPS_UPPER),
                note = "Slight bend in elbow. Lead with elbows."
            ),
            ExerciseLibraryItem(
                id = 110,
                name = "Leg Extension (Machine)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.ISOLATION_KNEE_EXTENSION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.QUADS),
                secondaryTargets = emptyList(),
                note = "Squeeze quads at top. Control descent."
            ),
            ExerciseLibraryItem(
                id = 111,
                name = "Hip Thrust (Barbell)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.HINGE,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.HAMSTRINGS, TargetMuscle.ABS),
                note = "Chin tucked. Full hip extension at top."
            ),
            ExerciseLibraryItem(
                id = 112,
                name = "Skullcrusher (EZ Bar)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_ELBOW_EXTENSION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.TRICEPS_LONG, TargetMuscle.TRICEPS_LATERAL),
                secondaryTargets = emptyList(),
                note = "Upper arms ~45°. Lower to forehead/ear level."
            ),
            ExerciseLibraryItem(
                id = 113,
                name = "Hammer Curl (Dumbbell)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_ELBOW_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.BICEPS, TargetMuscle.FOREARMS),
                secondaryTargets = emptyList(),
                note = "Neutral grip. Control throughout."
            ),
            ExerciseLibraryItem(
                id = 114,
                name = "Front Squat (Barbell)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.SQUAT,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.QUADS, TargetMuscle.ABS),
                secondaryTargets = listOf(TargetMuscle.GLUTES),
                note = "Elbows high. Upright torso."
            ),
            ExerciseLibraryItem(
                id = 115,
                name = "Walking Lunges",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.LUNGE,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.QUADS, TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.CALVES, TargetMuscle.ABS),
                note = "90° angles. Step length for balance."
            ),
            ExerciseLibraryItem(
                id = 116,
                name = "Incline Dumbbell Fly",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_SHOULDER_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.CHEST_UPPER),
                secondaryTargets = listOf(TargetMuscle.DELT_FRONT),
                note = "Slight bend in elbows. Stretch at bottom."
            ),
            ExerciseLibraryItem(
                id = 117,
                name = "Pec Deck / Machine Fly",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_HORIZONTAL,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.CHEST_MIDDLE),
                secondaryTargets = listOf(TargetMuscle.DELT_FRONT),
                note = "Controlled stretch and squeeze."
            ),
            ExerciseLibraryItem(
                id = 118,
                name = "Dumbbell Shoulder Press (Seated)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_VERTICAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.DELT_FRONT, TargetMuscle.DELT_SIDE),
                secondaryTargets = listOf(TargetMuscle.TRICEPS_LATERAL),
                note = "Back support. Full ROM."
            ),
            ExerciseLibraryItem(
                id = 119,
                name = "Reverse Fly (Dumbbell)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_SHOULDER_EXTENSION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.DELT_REAR),
                secondaryTargets = listOf(TargetMuscle.TRAPS_MID),
                note = "Hinge forward. Thumbs point back."
            ),
            ExerciseLibraryItem(
                id = 120,
                name = "Hanging Leg Raise",
                region = BodyRegion.CORE,
                pattern = MovementPattern.CORE_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.ABS),
                secondaryTargets = listOf(TargetMuscle.FOREARMS),
                note = "Control swing. Exhale as legs rise."
            ),
            ExerciseLibraryItem(
                id = 121,
                name = "Plank",
                region = BodyRegion.CORE,
                pattern = MovementPattern.CORE_STABILITY,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.ABS, TargetMuscle.OBLIQUES),
                secondaryTargets = emptyList(),
                note = "Neutral spine. Squeeze glutes."
            ),
            ExerciseLibraryItem(
                id = 122,
                name = "Cable Woodchopper",
                region = BodyRegion.CORE,
                pattern = MovementPattern.CORE_STABILITY,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.OBLIQUES),
                secondaryTargets = listOf(TargetMuscle.ABS),
                note = "Rotate from core. Control both phases."
            ),
            ExerciseLibraryItem(
                id = 123,
                name = "Sumo Deadlift",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.HINGE,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.HAMSTRINGS, TargetMuscle.GLUTES, TargetMuscle.QUADS),
                secondaryTargets = listOf(TargetMuscle.ADDUCTORS, TargetMuscle.LOWER_BACK),
                note = "Wide stance. Vertical torso. Push knees out."
            ),
            ExerciseLibraryItem(
                id = 124,
                name = "Barbell Shrug",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PULL_VERTICAL,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.TRAPS_UPPER),
                secondaryTargets = listOf(TargetMuscle.FOREARMS),
                note = "Full elevation. Hold peak 1 sec."
            ),
            ExerciseLibraryItem(
                id = 125,
                name = "Farmer's Walk",
                region = BodyRegion.FULL,
                pattern = MovementPattern.CARRY,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.FOREARMS, TargetMuscle.TRAPS_UPPER),
                secondaryTargets = listOf(TargetMuscle.CALVES),
                note = "Upright posture. Controlled steps."
            ),
            ExerciseLibraryItem(
                id = 126,
                name = "Glute Bridge (Barbell)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.HINGE,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.HAMSTRINGS),
                note = "Drive through heels. Squeeze glutes at top."
            ),
            ExerciseLibraryItem(
                id = 127,
                name = "Goblet Squat",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.SQUAT,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.QUADS, TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.ABS),
                note = "Elbows inside knees. Push knees out."
            ),
            ExerciseLibraryItem(
                id = 128,
                name = "Barbell Row (Pendlay)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PULL_HORIZONTAL,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.LATS, TargetMuscle.TRAPS_MID, TargetMuscle.LOWER_BACK),
                secondaryTargets = listOf(TargetMuscle.BICEPS, TargetMuscle.DELT_REAR),
                note = "Back parallel. Pull to lower chest."
            ),
            ExerciseLibraryItem(
                id = 129,
                name = "Cable Crossover",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_SHOULDER_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.CHEST_LOWER, TargetMuscle.CHEST_MIDDLE),
                secondaryTargets = emptyList(),
                note = "Slight bend. Squeeze pecs at center."
            ),
            ExerciseLibraryItem(
                id = 130,
                name = "Preacher Curl (EZ Bar)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_ELBOW_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.BICEPS),
                secondaryTargets = emptyList(),
                note = "Upper arms on pad. Full stretch at bottom."
            ),
            ExerciseLibraryItem(
                id = 131,
                name = "Push Up",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_HORIZONTAL,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.CHEST_MIDDLE, TargetMuscle.TRICEPS_LATERAL),
                secondaryTargets = listOf(TargetMuscle.ABS, TargetMuscle.DELT_FRONT),
                note = "Core tight. Full lockout at top."
            ),
            ExerciseLibraryItem(
                id = 132,
                name = "Rotary Torso (Machine)",
                region = BodyRegion.CORE,
                pattern = MovementPattern.CORE_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.OBLIQUES),
                secondaryTargets = listOf(TargetMuscle.ABS),
                note = "Rotate from hips. Controlled twist.",
                manualMechanics = Mechanics.ISOLATION
            ),
            ExerciseLibraryItem(
                id = 133,
                name = "Incline press (Machine)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_HORIZONTAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.CHEST_UPPER),
                secondaryTargets = listOf(TargetMuscle.CHEST_MIDDLE, TargetMuscle.TRICEPS_LONG, TargetMuscle.TRICEPS_LATERAL),
                note = "Back flat on pad. Full ROM.",
                manualMechanics = Mechanics.ISOLATION
            ),
            ExerciseLibraryItem(
                id = 134,
                name = "Hip Adduction",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.OTHER,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.ADDUCTORS),
                secondaryTargets = emptyList(),
                note = "Squeeze thighs together. Controlled tempo.",
                manualMechanics = Mechanics.ISOLATION
            ),
            ExerciseLibraryItem(
                id = 135,
                name = "Hip Abduction",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.OTHER,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.ABDUCTORS),
                secondaryTargets = emptyList(),
                note = "Push knees out. Squeeze glutes.",
                manualMechanics = Mechanics.ISOLATION
            ),
            ExerciseLibraryItem(
                id = 136,
                name = "Romanian Deadlift (Dumbbell)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.HINGE,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.HAMSTRINGS, TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.LOWER_BACK, TargetMuscle.FOREARMS),
                note = "Hinge at hips. Dumbbells close to legs.",
                manualMechanics = Mechanics.COMPOUND
            ),
            ExerciseLibraryItem(
                id = 137,
                name = "Side Plank",
                region = BodyRegion.CORE,
                pattern = MovementPattern.CORE_STABILITY,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.GLUTES, TargetMuscle.OBLIQUES),
                secondaryTargets = listOf(TargetMuscle.LOWER_BACK, TargetMuscle.ABS),
                note = "Stack feet/legs. Don't let hips sag.",
                manualMechanics = Mechanics.ISOLATION
            ),
            ExerciseLibraryItem(
                id = 138,
                name = "Eccentric Heel Drop",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.OTHER,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.CALVES),
                secondaryTargets = emptyList(),
                note = "Slow 3-sec lower. Support on way up.",
                manualMechanics = Mechanics.ISOLATION
            ),
            ExerciseLibraryItem(
                id = 139,
                name = "Chest Press (Machine Wide)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_HORIZONTAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.CHEST_UPPER, TargetMuscle.CHEST_MIDDLE),
                secondaryTargets = listOf(TargetMuscle.TRICEPS_LONG, TargetMuscle.TRICEPS_LATERAL),
                note = "Full stretch. Don't lock elbows.",
                manualMechanics = Mechanics.COMPOUND
            ),
            ExerciseLibraryItem(
                id = 140,
                name = "Chest Press (Machine)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_HORIZONTAL,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.CHEST_MIDDLE),
                secondaryTargets = listOf(TargetMuscle.CHEST_UPPER, TargetMuscle.CHEST_LOWER, TargetMuscle.DELT_FRONT, TargetMuscle.DELT_SIDE, TargetMuscle.TRICEPS_LONG, TargetMuscle.TRICEPS_LATERAL),
                note = "Back flat. Full ROM.",
                manualMechanics = Mechanics.COMPOUND
            ),
            ExerciseLibraryItem(
                id = 141,
                name = "Row (Machine)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PULL_HORIZONTAL,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.LATS, TargetMuscle.TRAPS_MID, TargetMuscle.TRAPS_UPPER, TargetMuscle.DELT_REAR),
                secondaryTargets = listOf(TargetMuscle.LOWER_BACK, TargetMuscle.BICEPS, TargetMuscle.FOREARMS),
                note = "Retract scapula. Squeeze at peak.",
                manualMechanics = Mechanics.COMPOUND
            ),
            ExerciseLibraryItem(
                id = 142,
                name = "Prone Leg Curl",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.ISOLATION_KNEE_FLEXION,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.HAMSTRINGS),
                secondaryTargets = emptyList(),
                note = "Hips down. Squeeze hamstrings.",
                manualMechanics = Mechanics.ISOLATION
            ),
            ExerciseLibraryItem(
                id = 143,
                name = "Side Raises (Dumbbell)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_SHOULDER_ABDUCTION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.DELT_SIDE),
                secondaryTargets = emptyList(),
                note = "Slight bend in elbow. Lead with elbows.",
                manualMechanics = Mechanics.ISOLATION
            ),
            ExerciseLibraryItem(
                id = 144,
                name = "Incline Barbell Press",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_HORIZONTAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.CHEST_UPPER),
                secondaryTargets = listOf(TargetMuscle.CHEST_MIDDLE, TargetMuscle.TRICEPS_LONG, TargetMuscle.TRICEPS_LATERAL),
                note = "30-45° incline. Bar to upper chest.",
                manualMechanics = Mechanics.ISOLATION
            ),
            ExerciseLibraryItem(
                id = 145,
                name = "Glute (Machine)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.OTHER,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.GLUTES),
                secondaryTargets = emptyList(),
                note = "Squeeze glutes at peak. Control negative.",
                manualMechanics = Mechanics.ISOLATION
            ),
            ExerciseLibraryItem(
                id = 146,
                name = "Hack Squat",
                region = BodyRegion.FULL,
                pattern = MovementPattern.SQUAT,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.QUADS, TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.HIPFLEXORS, TargetMuscle.ABS, TargetMuscle.HAMSTRINGS),
                note = "Back flat on pad. Feet shoulder-width.",
                manualMechanics = Mechanics.COMPOUND
            ),
            ExerciseLibraryItem(
                id = 147,
                name = "Cable Straight Leg Raises",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.OTHER,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.HIPFLEXORS),
                secondaryTargets = listOf(TargetMuscle.ABS),
                note = "Stand tall; avoid leaning back. Lift knee to parallel, pause 1s, lower slowly.",
                manualMechanics = Mechanics.ISOLATION
            ),
            ExerciseLibraryItem(
                id = 148,
                name = "Cable Straight Back Kicks",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.OTHER,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.HAMSTRINGS),
                note = "Keep spine neutral; squeeze glute at top. Lead with heel.",
                manualMechanics = Mechanics.ISOLATION
            ),
            ExerciseLibraryItem(
                id = 149,
                name = "Low Row (Machine)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PULL_HORIZONTAL,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.LATS),
                secondaryTargets = listOf(TargetMuscle.TRAPS_MID, TargetMuscle.TRAPS_UPPER, TargetMuscle.DELT_REAR, TargetMuscle.BICEPS),
                note = "Chest supported; pull elbows back. Squeeze lats.",
                manualMechanics = Mechanics.COMPOUND
            ),
        )
    }
}