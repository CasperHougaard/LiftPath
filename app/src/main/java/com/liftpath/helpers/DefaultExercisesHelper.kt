package com.liftpath.helpers

import com.liftpath.models.BodyRegion
import com.liftpath.models.Equipment
import com.liftpath.models.ExerciseAngle
import com.liftpath.models.ExerciseFamily
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.ExerciseTargetMetric
import com.liftpath.models.ExerciseType
import com.liftpath.models.Laterality
import com.liftpath.models.Mechanics
import com.liftpath.models.MovementPattern
import com.liftpath.models.TargetMuscle
import com.liftpath.models.Tier

object DefaultExercisesHelper {

    const val CATALOG_VERSION = 4

    /**
     * Default-catalog exercise IDs that target TIME rather than reps (isometric holds). Used by the
     * one-time backfill migration so existing installs (whose persisted library predates the
     * targetMetric field) auto-flag these as timed. Only applied when the item's metric is still null.
     */
    val DEFAULT_TIMED_EXERCISE_IDS: Set<Int> = setOf(121, 137)

    // --- Exercise Family Catalog (40 families, 84 exercises) ---

    val DEFAULT_FAMILIES: List<ExerciseFamily> = listOf(
        ExerciseFamily("chest_press",       "Chest Press",               MovementPattern.PUSH_HORIZONTAL,              BodyRegion.UPPER, listOf(TargetMuscle.CHEST_MIDDLE, TargetMuscle.DELT_FRONT, TargetMuscle.TRICEPS_LATERAL)),
        ExerciseFamily("incline_press",     "Incline Press",             MovementPattern.PUSH_HORIZONTAL,              BodyRegion.UPPER, listOf(TargetMuscle.CHEST_UPPER, TargetMuscle.DELT_FRONT, TargetMuscle.TRICEPS_LATERAL)),
        ExerciseFamily("decline_press",     "Decline Press",             MovementPattern.PUSH_HORIZONTAL,              BodyRegion.UPPER, listOf(TargetMuscle.CHEST_LOWER, TargetMuscle.TRICEPS_LATERAL)),
        ExerciseFamily("chest_fly",         "Chest Fly",                 MovementPattern.ISOLATION_SHOULDER_FLEXION,   BodyRegion.UPPER, listOf(TargetMuscle.CHEST_MIDDLE, TargetMuscle.CHEST_UPPER)),
        ExerciseFamily("dips",              "Dips",                      MovementPattern.PUSH_VERTICAL,                BodyRegion.UPPER, listOf(TargetMuscle.CHEST_LOWER, TargetMuscle.TRICEPS_LATERAL)),
        ExerciseFamily("overhead_press",    "Overhead Press",            MovementPattern.PUSH_VERTICAL,                BodyRegion.UPPER, listOf(TargetMuscle.DELT_FRONT, TargetMuscle.DELT_SIDE, TargetMuscle.TRICEPS_LATERAL)),
        ExerciseFamily("pull_up",           "Pull-Up / Chin-Up",         MovementPattern.PULL_VERTICAL,                BodyRegion.UPPER, listOf(TargetMuscle.LATS, TargetMuscle.BICEPS)),
        ExerciseFamily("lat_pulldown",      "Lat Pulldown",              MovementPattern.PULL_VERTICAL,                BodyRegion.UPPER, listOf(TargetMuscle.LATS, TargetMuscle.TRAPS_MID, TargetMuscle.BICEPS)),
        ExerciseFamily("row_horizontal",    "Horizontal Row",            MovementPattern.PULL_HORIZONTAL,              BodyRegion.UPPER, listOf(TargetMuscle.LATS, TargetMuscle.TRAPS_MID)),
        ExerciseFamily("face_pull",         "Face Pull",                 MovementPattern.ISOLATION_SHOULDER_EXTENSION, BodyRegion.UPPER, listOf(TargetMuscle.DELT_REAR, TargetMuscle.TRAPS_MID)),
        ExerciseFamily("lateral_raise",     "Lateral Raise",             MovementPattern.ISOLATION_SHOULDER_ABDUCTION, BodyRegion.UPPER, listOf(TargetMuscle.DELT_SIDE)),
        ExerciseFamily("reverse_fly",       "Reverse Fly",               MovementPattern.ISOLATION_SHOULDER_EXTENSION, BodyRegion.UPPER, listOf(TargetMuscle.DELT_REAR, TargetMuscle.TRAPS_MID)),
        ExerciseFamily("shrug",             "Shrug",                     MovementPattern.PULL_VERTICAL,                BodyRegion.UPPER, listOf(TargetMuscle.TRAPS_UPPER)),
        ExerciseFamily("squat",             "Squat",                     MovementPattern.SQUAT,                        BodyRegion.LOWER, listOf(TargetMuscle.QUADS, TargetMuscle.GLUTES, TargetMuscle.ADDUCTORS)),
        ExerciseFamily("leg_press",         "Leg Press",                 MovementPattern.SQUAT,                        BodyRegion.LOWER, listOf(TargetMuscle.QUADS, TargetMuscle.GLUTES)),
        ExerciseFamily("split_squat",       "Split Squat / Lunge",       MovementPattern.LUNGE,                        BodyRegion.LOWER, listOf(TargetMuscle.QUADS, TargetMuscle.GLUTES)),
        ExerciseFamily("deadlift",          "Deadlift",                  MovementPattern.HINGE,                        BodyRegion.LOWER, listOf(TargetMuscle.HAMSTRINGS, TargetMuscle.GLUTES, TargetMuscle.LOWER_BACK)),
        ExerciseFamily("rdl",               "Romanian Deadlift",         MovementPattern.HINGE,                        BodyRegion.LOWER, listOf(TargetMuscle.HAMSTRINGS, TargetMuscle.GLUTES)),
        ExerciseFamily("hip_thrust",        "Hip Thrust / Glute Bridge", MovementPattern.HINGE,                        BodyRegion.LOWER, listOf(TargetMuscle.GLUTES, TargetMuscle.HAMSTRINGS)),
        ExerciseFamily("leg_curl",          "Leg Curl",                  MovementPattern.ISOLATION_KNEE_FLEXION,       BodyRegion.LOWER, listOf(TargetMuscle.HAMSTRINGS)),
        ExerciseFamily("leg_extension",     "Leg Extension",             MovementPattern.ISOLATION_KNEE_EXTENSION,     BodyRegion.LOWER, listOf(TargetMuscle.QUADS)),
        ExerciseFamily("calf_raise",        "Calf Raise",                MovementPattern.ISOLATION_PLANTAR_FLEXION,    BodyRegion.LOWER, listOf(TargetMuscle.CALVES)),
        ExerciseFamily("biceps_curl",       "Biceps Curl",               MovementPattern.ISOLATION_ELBOW_FLEXION,      BodyRegion.UPPER, listOf(TargetMuscle.BICEPS)),
        ExerciseFamily("triceps_extension", "Triceps Extension",         MovementPattern.ISOLATION_ELBOW_EXTENSION,    BodyRegion.UPPER, listOf(TargetMuscle.TRICEPS_LONG, TargetMuscle.TRICEPS_LATERAL)),
        ExerciseFamily("ab_crunch",         "Ab Crunch",                 MovementPattern.CORE_FLEXION,                 BodyRegion.CORE,  listOf(TargetMuscle.ABS)),
        ExerciseFamily("plank",             "Plank",                     MovementPattern.CORE_STABILITY,               BodyRegion.CORE,  listOf(TargetMuscle.ABS, TargetMuscle.OBLIQUES)),
        ExerciseFamily("cable_woodchopper", "Cable Woodchopper",         MovementPattern.CORE_STABILITY,               BodyRegion.CORE,  listOf(TargetMuscle.OBLIQUES, TargetMuscle.ABS)),
        ExerciseFamily("rotary_torso",      "Rotary Torso",              MovementPattern.CORE_FLEXION,                 BodyRegion.CORE,  listOf(TargetMuscle.OBLIQUES)),
        ExerciseFamily("hip_adduction",     "Hip Adduction",             MovementPattern.OTHER,                        BodyRegion.LOWER, listOf(TargetMuscle.ADDUCTORS)),
        ExerciseFamily("hip_abduction",     "Hip Abduction",             MovementPattern.OTHER,                        BodyRegion.LOWER, listOf(TargetMuscle.ABDUCTORS)),
        ExerciseFamily("glute_machine",     "Glute Machine",             MovementPattern.OTHER,                        BodyRegion.LOWER, listOf(TargetMuscle.GLUTES)),
        ExerciseFamily("farmers_walk",      "Farmer's Walk",             MovementPattern.CARRY,                        BodyRegion.FULL,  listOf(TargetMuscle.FOREARMS, TargetMuscle.TRAPS_UPPER)),
        ExerciseFamily("cable_leg_raises",  "Cable Leg Raises",          MovementPattern.OTHER,                        BodyRegion.LOWER, listOf(TargetMuscle.HIPFLEXORS, TargetMuscle.ABS)),
        ExerciseFamily("front_raise",       "Front Raise",               MovementPattern.ISOLATION_SHOULDER_FLEXION,   BodyRegion.UPPER, listOf(TargetMuscle.DELT_FRONT)),
        ExerciseFamily("upright_row",       "Upright Row",               MovementPattern.PULL_VERTICAL,                BodyRegion.UPPER, listOf(TargetMuscle.TRAPS_UPPER, TargetMuscle.DELT_SIDE, TargetMuscle.DELT_FRONT)),
        ExerciseFamily("back_extension",    "Back Extension",            MovementPattern.HINGE,                        BodyRegion.LOWER, listOf(TargetMuscle.LOWER_BACK, TargetMuscle.GLUTES, TargetMuscle.HAMSTRINGS)),
        ExerciseFamily("good_morning",      "Good Morning",              MovementPattern.HINGE,                        BodyRegion.LOWER, listOf(TargetMuscle.LOWER_BACK, TargetMuscle.HAMSTRINGS, TargetMuscle.GLUTES)),
        ExerciseFamily("ab_wheel",          "Ab Wheel Rollout",          MovementPattern.CORE_STABILITY,               BodyRegion.CORE,  listOf(TargetMuscle.ABS, TargetMuscle.OBLIQUES)),
        ExerciseFamily("kettlebell_swing",  "Kettlebell Swing",          MovementPattern.HINGE,                        BodyRegion.FULL,  listOf(TargetMuscle.GLUTES, TargetMuscle.HAMSTRINGS)),
        ExerciseFamily("close_grip_bench",  "Close-Grip Press",          MovementPattern.PUSH_HORIZONTAL,              BodyRegion.UPPER, listOf(TargetMuscle.TRICEPS_LONG, TargetMuscle.TRICEPS_LATERAL, TargetMuscle.CHEST_MIDDLE)),
    )

    fun getDefaultFamilies(): List<ExerciseFamily> = DEFAULT_FAMILIES

    fun getPrimaryTargets(exerciseId: Int): List<TargetMuscle>? {
        val familyId = DEFAULT_EXERCISE_META_MAP[exerciseId] ?: return null
        return DEFAULT_FAMILIES.find { it.id == familyId }?.primaryTargets
    }

    // --- Per-exercise metadata: familyId, equipment, angle, laterality ---

    private data class DefaultExerciseMeta(
        val familyId: String,
        val equipment: Equipment,
        val angle: ExerciseAngle? = null,
        val laterality: Laterality? = null
    )

    val DEFAULT_EXERCISE_META_MAP: Map<Int, String> = mapOf(
        1   to "deadlift",
        2   to "squat",
        4   to "biceps_curl",
        5   to "triceps_extension",
        7   to "chest_press",
        8   to "split_squat",
        9   to "calf_raise",
        10  to "decline_press",
        11  to "incline_press",
        12  to "row_horizontal",
        13  to "triceps_extension",
        14  to "overhead_press",
        15  to "dips",
        16  to "ab_crunch",
        17  to "chest_press",
        18  to "leg_curl",
        100 to "overhead_press",
        101 to "pull_up",
        102 to "pull_up",
        103 to "rdl",
        104 to "leg_press",
        105 to "split_squat",
        106 to "lat_pulldown",
        107 to "row_horizontal",
        108 to "face_pull",
        109 to "lateral_raise",
        110 to "leg_extension",
        111 to "hip_thrust",
        112 to "triceps_extension",
        113 to "biceps_curl",
        114 to "squat",
        115 to "split_squat",
        116 to "chest_fly",
        117 to "chest_fly",
        118 to "overhead_press",
        119 to "reverse_fly",
        120 to "ab_crunch",
        121 to "plank",
        122 to "cable_woodchopper",
        123 to "deadlift",
        124 to "shrug",
        125 to "farmers_walk",
        126 to "hip_thrust",
        127 to "squat",
        128 to "row_horizontal",
        129 to "chest_fly",
        130 to "biceps_curl",
        131 to "chest_press",
        132 to "rotary_torso",
        133 to "incline_press",
        134 to "hip_adduction",
        135 to "hip_abduction",
        136 to "rdl",
        137 to "plank",
        138 to "calf_raise",
        139 to "chest_press",
        140 to "chest_press",
        141 to "row_horizontal",
        142 to "leg_curl",
        143 to "lateral_raise",
        144 to "incline_press",
        145 to "glute_machine",
        146 to "squat",
        147 to "cable_leg_raises",
        148 to "glute_machine",
        149 to "row_horizontal",
        150 to "front_raise",
        151 to "front_raise",
        152 to "upright_row",
        153 to "back_extension",
        154 to "back_extension",
        155 to "good_morning",
        156 to "ab_wheel",
        157 to "kettlebell_swing",
        158 to "close_grip_bench",
        159 to "calf_raise",
        160 to "leg_curl",
        161 to "split_squat",
        162 to "overhead_press",
        163 to "deadlift",
        164 to "rdl",
        165 to "ab_crunch",
        166 to "rotary_torso",
        167 to "hip_thrust"
    )

    private val DEFAULT_EXERCISE_FULL_META: Map<Int, DefaultExerciseMeta> = mapOf(
        1   to DefaultExerciseMeta("deadlift",          Equipment.BARBELL,    null,                  Laterality.BILATERAL),
        2   to DefaultExerciseMeta("squat",             Equipment.BARBELL,    null,                  Laterality.BILATERAL),
        4   to DefaultExerciseMeta("biceps_curl",       Equipment.DUMBBELL,   null,                  Laterality.UNILATERAL),
        5   to DefaultExerciseMeta("triceps_extension", Equipment.CABLE,      null,                  Laterality.BILATERAL),
        7   to DefaultExerciseMeta("chest_press",       Equipment.BARBELL,    ExerciseAngle.FLAT,    Laterality.BILATERAL),
        8   to DefaultExerciseMeta("split_squat",       Equipment.BARBELL,    null,                  Laterality.UNILATERAL),
        9   to DefaultExerciseMeta("calf_raise",        Equipment.MACHINE,    null,                  Laterality.BILATERAL),
        10  to DefaultExerciseMeta("decline_press",     Equipment.BARBELL,    ExerciseAngle.DECLINE, Laterality.BILATERAL),
        11  to DefaultExerciseMeta("incline_press",     Equipment.DUMBBELL,   ExerciseAngle.INCLINE, Laterality.BILATERAL),
        12  to DefaultExerciseMeta("row_horizontal",    Equipment.CABLE,      null,                  Laterality.BILATERAL),
        13  to DefaultExerciseMeta("triceps_extension", Equipment.CABLE,      null,                  Laterality.UNILATERAL),
        14  to DefaultExerciseMeta("overhead_press",    Equipment.MACHINE,    null,                  Laterality.BILATERAL),
        15  to DefaultExerciseMeta("dips",              Equipment.BODYWEIGHT, null,                  Laterality.BILATERAL),
        16  to DefaultExerciseMeta("ab_crunch",         Equipment.MACHINE,    null,                  Laterality.BILATERAL),
        17  to DefaultExerciseMeta("chest_press",       Equipment.BARBELL,    ExerciseAngle.FLAT,    Laterality.BILATERAL),
        18  to DefaultExerciseMeta("leg_curl",          Equipment.MACHINE,    null,                  Laterality.BILATERAL),
        100 to DefaultExerciseMeta("overhead_press",    Equipment.BARBELL,    null,                  Laterality.BILATERAL),
        101 to DefaultExerciseMeta("pull_up",           Equipment.BODYWEIGHT, null,                  Laterality.BILATERAL),
        102 to DefaultExerciseMeta("pull_up",           Equipment.BODYWEIGHT, null,                  Laterality.BILATERAL),
        103 to DefaultExerciseMeta("rdl",               Equipment.BARBELL,    null,                  Laterality.BILATERAL),
        104 to DefaultExerciseMeta("leg_press",         Equipment.MACHINE,    null,                  Laterality.BILATERAL),
        105 to DefaultExerciseMeta("split_squat",       Equipment.DUMBBELL,   null,                  Laterality.UNILATERAL),
        106 to DefaultExerciseMeta("lat_pulldown",      Equipment.CABLE,      null,                  Laterality.BILATERAL),
        107 to DefaultExerciseMeta("row_horizontal",    Equipment.DUMBBELL,   null,                  Laterality.UNILATERAL),
        108 to DefaultExerciseMeta("face_pull",         Equipment.CABLE,      null,                  Laterality.BILATERAL),
        109 to DefaultExerciseMeta("lateral_raise",     Equipment.DUMBBELL,   null,                  Laterality.BILATERAL),
        110 to DefaultExerciseMeta("leg_extension",     Equipment.MACHINE,    null,                  Laterality.BILATERAL),
        111 to DefaultExerciseMeta("hip_thrust",        Equipment.BARBELL,    null,                  Laterality.BILATERAL),
        112 to DefaultExerciseMeta("triceps_extension", Equipment.EZ_BAR,     ExerciseAngle.FLAT,    Laterality.BILATERAL),
        113 to DefaultExerciseMeta("biceps_curl",       Equipment.DUMBBELL,   null,                  Laterality.BILATERAL),
        114 to DefaultExerciseMeta("squat",             Equipment.BARBELL,    null,                  Laterality.BILATERAL),
        115 to DefaultExerciseMeta("split_squat",       Equipment.BODYWEIGHT, null,                  Laterality.UNILATERAL),
        116 to DefaultExerciseMeta("chest_fly",         Equipment.DUMBBELL,   ExerciseAngle.INCLINE, Laterality.BILATERAL),
        117 to DefaultExerciseMeta("chest_fly",         Equipment.MACHINE,    null,                  Laterality.BILATERAL),
        118 to DefaultExerciseMeta("overhead_press",    Equipment.DUMBBELL,   null,                  Laterality.BILATERAL),
        119 to DefaultExerciseMeta("reverse_fly",       Equipment.DUMBBELL,   null,                  Laterality.BILATERAL),
        120 to DefaultExerciseMeta("ab_crunch",         Equipment.BODYWEIGHT, null,                  Laterality.BILATERAL),
        121 to DefaultExerciseMeta("plank",             Equipment.BODYWEIGHT, null,                  Laterality.BILATERAL),
        122 to DefaultExerciseMeta("cable_woodchopper", Equipment.CABLE,      null,                  Laterality.UNILATERAL),
        123 to DefaultExerciseMeta("deadlift",          Equipment.BARBELL,    null,                  Laterality.BILATERAL),
        124 to DefaultExerciseMeta("shrug",             Equipment.BARBELL,    null,                  Laterality.BILATERAL),
        125 to DefaultExerciseMeta("farmers_walk",      Equipment.OTHER,      null,                  Laterality.BILATERAL),
        126 to DefaultExerciseMeta("hip_thrust",        Equipment.BARBELL,    null,                  Laterality.BILATERAL),
        127 to DefaultExerciseMeta("squat",             Equipment.KETTLEBELL, null,                  Laterality.BILATERAL),
        128 to DefaultExerciseMeta("row_horizontal",    Equipment.BARBELL,    null,                  Laterality.BILATERAL),
        129 to DefaultExerciseMeta("chest_fly",         Equipment.CABLE,      null,                  Laterality.BILATERAL),
        130 to DefaultExerciseMeta("biceps_curl",       Equipment.EZ_BAR,     null,                  Laterality.BILATERAL),
        131 to DefaultExerciseMeta("chest_press",       Equipment.BODYWEIGHT, ExerciseAngle.FLAT,    Laterality.BILATERAL),
        132 to DefaultExerciseMeta("rotary_torso",      Equipment.MACHINE,    null,                  Laterality.BILATERAL),
        133 to DefaultExerciseMeta("incline_press",     Equipment.MACHINE,    ExerciseAngle.INCLINE, Laterality.BILATERAL),
        134 to DefaultExerciseMeta("hip_adduction",     Equipment.MACHINE,    null,                  Laterality.BILATERAL),
        135 to DefaultExerciseMeta("hip_abduction",     Equipment.MACHINE,    null,                  Laterality.BILATERAL),
        136 to DefaultExerciseMeta("rdl",               Equipment.DUMBBELL,   null,                  Laterality.BILATERAL),
        137 to DefaultExerciseMeta("plank",             Equipment.BODYWEIGHT, null,                  Laterality.UNILATERAL),
        138 to DefaultExerciseMeta("calf_raise",        Equipment.BODYWEIGHT, null,                  Laterality.BILATERAL),
        139 to DefaultExerciseMeta("chest_press",       Equipment.MACHINE,    null,                  Laterality.BILATERAL),
        140 to DefaultExerciseMeta("chest_press",       Equipment.MACHINE,    null,                  Laterality.BILATERAL),
        141 to DefaultExerciseMeta("row_horizontal",    Equipment.MACHINE,    null,                  Laterality.BILATERAL),
        142 to DefaultExerciseMeta("leg_curl",          Equipment.MACHINE,    null,                  Laterality.BILATERAL),
        143 to DefaultExerciseMeta("lateral_raise",     Equipment.CABLE,      null,                  Laterality.UNILATERAL),
        144 to DefaultExerciseMeta("incline_press",     Equipment.BARBELL,    ExerciseAngle.INCLINE, Laterality.BILATERAL),
        145 to DefaultExerciseMeta("glute_machine",     Equipment.MACHINE,    null,                  Laterality.UNILATERAL),
        146 to DefaultExerciseMeta("squat",             Equipment.MACHINE,    null,                  Laterality.BILATERAL),
        147 to DefaultExerciseMeta("cable_leg_raises",  Equipment.CABLE,      null,                  Laterality.UNILATERAL),
        148 to DefaultExerciseMeta("glute_machine",     Equipment.CABLE,      null,                  Laterality.UNILATERAL),
        149 to DefaultExerciseMeta("row_horizontal",    Equipment.MACHINE,    null,                  Laterality.BILATERAL),
        150 to DefaultExerciseMeta("front_raise",       Equipment.DUMBBELL,   null,                  Laterality.BILATERAL),
        151 to DefaultExerciseMeta("front_raise",       Equipment.CABLE,      null,                  Laterality.BILATERAL),
        152 to DefaultExerciseMeta("upright_row",       Equipment.BARBELL,    null,                  Laterality.BILATERAL),
        153 to DefaultExerciseMeta("back_extension",    Equipment.MACHINE,    null,                  Laterality.BILATERAL),
        154 to DefaultExerciseMeta("back_extension",    Equipment.BODYWEIGHT, null,                  Laterality.BILATERAL),
        155 to DefaultExerciseMeta("good_morning",      Equipment.BARBELL,    null,                  Laterality.BILATERAL),
        156 to DefaultExerciseMeta("ab_wheel",          Equipment.OTHER,      null,                  Laterality.BILATERAL),
        157 to DefaultExerciseMeta("kettlebell_swing",  Equipment.KETTLEBELL, null,                  Laterality.BILATERAL),
        158 to DefaultExerciseMeta("close_grip_bench",  Equipment.BARBELL,    ExerciseAngle.FLAT,    Laterality.BILATERAL),
        159 to DefaultExerciseMeta("calf_raise",        Equipment.MACHINE,    null,                  Laterality.BILATERAL),
        160 to DefaultExerciseMeta("leg_curl",          Equipment.BODYWEIGHT, null,                  Laterality.BILATERAL),
        161 to DefaultExerciseMeta("split_squat",       Equipment.DUMBBELL,   null,                  Laterality.UNILATERAL),
        162 to DefaultExerciseMeta("overhead_press",    Equipment.DUMBBELL,   null,                  Laterality.BILATERAL),
        163 to DefaultExerciseMeta("deadlift",          Equipment.OTHER,      null,                  Laterality.BILATERAL),
        164 to DefaultExerciseMeta("rdl",               Equipment.DUMBBELL,   null,                  Laterality.UNILATERAL),
        165 to DefaultExerciseMeta("ab_crunch",         Equipment.CABLE,      null,                  Laterality.BILATERAL),
        166 to DefaultExerciseMeta("rotary_torso",      Equipment.BODYWEIGHT, null,                  Laterality.BILATERAL),
        167 to DefaultExerciseMeta("hip_thrust",        Equipment.BODYWEIGHT, null,                  Laterality.BILATERAL)
    )

    fun getPopularDefaults(): List<ExerciseLibraryItem> {
        return rawDefaults().map { exercise ->
            val meta = DEFAULT_EXERCISE_FULL_META[exercise.id] ?: return@map exercise
            exercise.copy(
                familyId = meta.familyId,
                equipment = meta.equipment,
                angle = meta.angle,
                laterality = meta.laterality
            )
        }
    }

    private fun rawDefaults(): List<ExerciseLibraryItem> {
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
                name = "Biceps Curl (Dumbbell)",
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
                note = "Slight forward lean for chest. Control depth.",
                exerciseType = ExerciseType.BODYWEIGHT
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
                note = "Chin over bar. Full hang at bottom.",
                exerciseType = ExerciseType.BODYWEIGHT
            ),
            ExerciseLibraryItem(
                id = 102,
                name = "Chin Up",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PULL_VERTICAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.LATS, TargetMuscle.BICEPS),
                secondaryTargets = listOf(TargetMuscle.FOREARMS),
                note = "Palms face you. Squeeze at top.",
                exerciseType = ExerciseType.BODYWEIGHT
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
                pattern = MovementPattern.ISOLATION_SHOULDER_FLEXION,
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
                note = "Control swing. Exhale as legs rise.",
                exerciseType = ExerciseType.BODYWEIGHT
            ),
            ExerciseLibraryItem(
                id = 121,
                name = "Plank",
                region = BodyRegion.CORE,
                pattern = MovementPattern.CORE_STABILITY,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.ABS, TargetMuscle.OBLIQUES),
                secondaryTargets = emptyList(),
                note = "Neutral spine. Squeeze glutes.",
                exerciseType = ExerciseType.BODYWEIGHT,
                targetMetric = ExerciseTargetMetric.TIME
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
                note = "Core tight. Full lockout at top.",
                exerciseType = ExerciseType.BODYWEIGHT
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
                name = "Incline Press (Machine)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_HORIZONTAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.CHEST_UPPER),
                secondaryTargets = listOf(TargetMuscle.CHEST_MIDDLE, TargetMuscle.DELT_FRONT, TargetMuscle.TRICEPS_LATERAL),
                note = "Back flat on pad. Full ROM.",
                manualMechanics = Mechanics.COMPOUND
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
                manualMechanics = Mechanics.ISOLATION,
                exerciseType = ExerciseType.BODYWEIGHT,
                targetMetric = ExerciseTargetMetric.TIME
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
                name = "Cable Lateral Raise",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_SHOULDER_ABDUCTION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.DELT_SIDE),
                secondaryTargets = emptyList(),
                note = "Cable keeps constant tension. Slight bend in elbow. Lead with elbows.",
                manualMechanics = Mechanics.ISOLATION
            ),
            ExerciseLibraryItem(
                id = 144,
                name = "Incline Barbell Press",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_HORIZONTAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.CHEST_UPPER),
                secondaryTargets = listOf(TargetMuscle.CHEST_MIDDLE, TargetMuscle.DELT_FRONT, TargetMuscle.TRICEPS_LATERAL),
                note = "30-45° incline. Bar to upper chest.",
                manualMechanics = Mechanics.COMPOUND
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
                region = BodyRegion.LOWER,
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
            ExerciseLibraryItem(
                id = 150,
                name = "Front Raise (Dumbbell)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_SHOULDER_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.DELT_FRONT),
                secondaryTargets = emptyList(),
                note = "Raise to shoulder height. Slight bend in elbow."
            ),
            ExerciseLibraryItem(
                id = 151,
                name = "Front Raise (Cable)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.ISOLATION_SHOULDER_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.DELT_FRONT),
                secondaryTargets = emptyList(),
                note = "Cable keeps constant tension. Control the descent."
            ),
            ExerciseLibraryItem(
                id = 152,
                name = "Upright Row (Barbell)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PULL_VERTICAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.TRAPS_UPPER, TargetMuscle.DELT_SIDE),
                secondaryTargets = listOf(TargetMuscle.DELT_FRONT, TargetMuscle.BICEPS),
                note = "Elbows lead, pull to chin level. Narrow grip for delts, wider for traps."
            ),
            ExerciseLibraryItem(
                id = 153,
                name = "Back Extension (Machine)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.HINGE,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.LOWER_BACK),
                secondaryTargets = listOf(TargetMuscle.GLUTES, TargetMuscle.HAMSTRINGS),
                note = "Round forward at hips. Extend through lower back. Controlled.",
                manualMechanics = Mechanics.ISOLATION
            ),
            ExerciseLibraryItem(
                id = 154,
                name = "Hyperextension (45°)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.HINGE,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.LOWER_BACK, TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.HAMSTRINGS),
                note = "Hinge at hips, not waist. Squeeze glutes at top.",
                manualMechanics = Mechanics.ISOLATION,
                exerciseType = ExerciseType.BODYWEIGHT
            ),
            ExerciseLibraryItem(
                id = 155,
                name = "Good Morning (Barbell)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.HINGE,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.LOWER_BACK, TargetMuscle.HAMSTRINGS),
                secondaryTargets = listOf(TargetMuscle.GLUTES),
                note = "Bar on upper back. Hinge forward to parallel. Neutral spine.",
                manualMechanics = Mechanics.COMPOUND
            ),
            ExerciseLibraryItem(
                id = 156,
                name = "Ab Wheel Rollout",
                region = BodyRegion.CORE,
                pattern = MovementPattern.CORE_STABILITY,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.ABS, TargetMuscle.OBLIQUES),
                secondaryTargets = listOf(TargetMuscle.LOWER_BACK, TargetMuscle.LATS),
                note = "Brace hard. Roll to full extension if stable. No hip sag.",
                exerciseType = ExerciseType.BODYWEIGHT
            ),
            ExerciseLibraryItem(
                id = 157,
                name = "Kettlebell Swing",
                region = BodyRegion.FULL,
                pattern = MovementPattern.HINGE,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.GLUTES, TargetMuscle.HAMSTRINGS),
                secondaryTargets = listOf(TargetMuscle.LOWER_BACK, TargetMuscle.TRAPS_UPPER, TargetMuscle.ABS),
                note = "Hip hinge, not squat. Snap hips to drive. Bell floats to chest height.",
                manualMechanics = Mechanics.COMPOUND
            ),
            ExerciseLibraryItem(
                id = 158,
                name = "Close-Grip Bench Press",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_HORIZONTAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.TRICEPS_LONG, TargetMuscle.TRICEPS_LATERAL),
                secondaryTargets = listOf(TargetMuscle.CHEST_MIDDLE, TargetMuscle.DELT_FRONT),
                note = "Hands shoulder-width. Elbows tucked. Bar to lower chest.",
                manualMechanics = Mechanics.COMPOUND
            ),
            ExerciseLibraryItem(
                id = 159,
                name = "Seated Calf Raise (Machine)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.ISOLATION_PLANTAR_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.CALVES),
                secondaryTargets = emptyList(),
                note = "Seated isolates soleus. Full stretch at bottom.",
                manualMechanics = Mechanics.ISOLATION
            ),
            ExerciseLibraryItem(
                id = 160,
                name = "Nordic Curl",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.ISOLATION_KNEE_FLEXION,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.HAMSTRINGS),
                secondaryTargets = emptyList(),
                note = "Anchor feet. Lower under control. Pull back with hamstrings.",
                manualMechanics = Mechanics.ISOLATION,
                exerciseType = ExerciseType.BODYWEIGHT
            ),
            ExerciseLibraryItem(
                id = 161,
                name = "Step-Up (Dumbbell)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.LUNGE,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.QUADS, TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.CALVES),
                note = "Drive through the heel of the working leg. Full extension at top."
            ),
            ExerciseLibraryItem(
                id = 162,
                name = "Arnold Press (Dumbbell)",
                region = BodyRegion.UPPER,
                pattern = MovementPattern.PUSH_VERTICAL,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.DELT_FRONT, TargetMuscle.DELT_SIDE),
                secondaryTargets = listOf(TargetMuscle.TRICEPS_LATERAL),
                note = "Start palms in, rotate out as you press. Full ROM."
            ),
            ExerciseLibraryItem(
                id = 163,
                name = "Trap Bar Deadlift",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.HINGE,
                tier = Tier.TIER_1,
                primaryTargets = listOf(TargetMuscle.HAMSTRINGS, TargetMuscle.GLUTES, TargetMuscle.QUADS),
                secondaryTargets = listOf(TargetMuscle.LOWER_BACK, TargetMuscle.TRAPS_UPPER, TargetMuscle.FOREARMS),
                note = "Neutral handles reduce lower-back moment. Sit into it slightly more than conventional.",
                manualMechanics = Mechanics.COMPOUND
            ),
            ExerciseLibraryItem(
                id = 164,
                name = "Single Leg RDL (Dumbbell)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.HINGE,
                tier = Tier.TIER_2,
                primaryTargets = listOf(TargetMuscle.HAMSTRINGS, TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.LOWER_BACK),
                note = "Hip-width stance. Hinge and reach dumbbell toward floor. Control balance.",
                manualMechanics = Mechanics.COMPOUND
            ),
            ExerciseLibraryItem(
                id = 165,
                name = "Cable Crunch",
                region = BodyRegion.CORE,
                pattern = MovementPattern.CORE_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.ABS),
                secondaryTargets = emptyList(),
                note = "Kneel, hands by ears. Curl ribs to pelvis. Don't pull with arms.",
                manualMechanics = Mechanics.ISOLATION
            ),
            ExerciseLibraryItem(
                id = 166,
                name = "Russian Twist",
                region = BodyRegion.CORE,
                pattern = MovementPattern.CORE_FLEXION,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.OBLIQUES),
                secondaryTargets = listOf(TargetMuscle.ABS),
                note = "Lean back ~45°. Rotate shoulders, not just arms. Control tempo.",
                manualMechanics = Mechanics.ISOLATION,
                exerciseType = ExerciseType.BODYWEIGHT
            ),
            ExerciseLibraryItem(
                id = 167,
                name = "Glute Bridge (Bodyweight)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.HINGE,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.HAMSTRINGS),
                note = "Drive through heels. Squeeze glutes at top. Progress to single leg.",
                manualMechanics = Mechanics.ISOLATION,
                exerciseType = ExerciseType.BODYWEIGHT
            ),
        )
    }
}
