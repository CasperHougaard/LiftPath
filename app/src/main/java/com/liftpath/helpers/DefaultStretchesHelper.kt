package com.liftpath.helpers

import com.liftpath.R
import com.liftpath.models.Laterality
import com.liftpath.models.StretchItem
import com.liftpath.models.TargetMuscle

object DefaultStretchesHelper {

    val ALL_STRETCHES: List<StretchItem> = listOf(
        // CHEST
        StretchItem("Doorway Chest Stretch",          listOf(TargetMuscle.CHEST_UPPER, TargetMuscle.CHEST_MIDDLE, TargetMuscle.DELT_FRONT), 30, R.drawable.stretch_doorway_chest,          Laterality.BILATERAL),
        StretchItem("Low Doorway Stretch",            listOf(TargetMuscle.CHEST_LOWER, TargetMuscle.CHEST_MIDDLE),                           30, R.drawable.stretch_low_doorway,            Laterality.BILATERAL),

        // BACK
        StretchItem("Child's Pose",                   listOf(TargetMuscle.LATS, TargetMuscle.LOWER_BACK),                                   40, R.drawable.stretch_childs_pose,            Laterality.BILATERAL),
        StretchItem("Lat Prayer Stretch",             listOf(TargetMuscle.LATS),                                                             30, R.drawable.stretch_lat_prayer,             Laterality.UNILATERAL),
        StretchItem("Cat-Cow Stretch",                listOf(TargetMuscle.LOWER_BACK, TargetMuscle.ABS),                                    40, R.drawable.stretch_cat_cow,                Laterality.BILATERAL),
        StretchItem("Mid-Back Thoracic Rotation",     listOf(TargetMuscle.TRAPS_MID),                                                       30, R.drawable.stretch_thoracic_rotation,      Laterality.UNILATERAL),
        StretchItem("Upper Trap Neck Tilt",           listOf(TargetMuscle.TRAPS_UPPER),                                                     30, R.drawable.stretch_upper_trap_neck_tilt,   Laterality.UNILATERAL),

        // SHOULDERS
        StretchItem("Cross-Body Shoulder Stretch",    listOf(TargetMuscle.DELT_REAR, TargetMuscle.TRAPS_MID),                               30, R.drawable.stretch_cross_body_shoulder,    Laterality.UNILATERAL),
        StretchItem("Overhead Shoulder Stretch",      listOf(TargetMuscle.DELT_SIDE, TargetMuscle.DELT_FRONT),                              30, R.drawable.stretch_overhead_shoulder,      Laterality.BILATERAL),

        // ARMS
        StretchItem("Overhead Triceps Stretch",       listOf(TargetMuscle.TRICEPS_LONG, TargetMuscle.TRICEPS_LATERAL),                      30, R.drawable.stretch_overhead_triceps,       Laterality.UNILATERAL),
        StretchItem("Biceps Wall Stretch",            listOf(TargetMuscle.BICEPS),                                                          30, R.drawable.stretch_biceps_wall,            Laterality.UNILATERAL),
        StretchItem("Wrist Flexor Stretch",           listOf(TargetMuscle.FOREARMS),                                                        25, R.drawable.stretch_wrist_flexor,           Laterality.UNILATERAL),

        // LEGS — QUADS / HIP FLEXORS / ADDUCTORS
        StretchItem("Kneeling Hip Flexor Stretch",    listOf(TargetMuscle.HIPFLEXORS, TargetMuscle.QUADS),                                  40, R.drawable.stretch_kneeling_hip_flexor,    Laterality.UNILATERAL),
        StretchItem("Lunging Hip Flexor Stretch",     listOf(TargetMuscle.HIPFLEXORS, TargetMuscle.ADDUCTORS),                              40, R.drawable.stretch_lunging_hip_flexor,     Laterality.UNILATERAL),
        StretchItem("Standing Quad Stretch",          listOf(TargetMuscle.QUADS),                                                           30, R.drawable.stretch_standing_quad,          Laterality.UNILATERAL),
        StretchItem("Seated Butterfly Stretch",       listOf(TargetMuscle.ADDUCTORS),                                                       35, R.drawable.stretch_seated_butterfly,       Laterality.BILATERAL),

        // LEGS — HAMSTRINGS / GLUTES / ABDUCTORS
        StretchItem("Pigeon Pose",                    listOf(TargetMuscle.GLUTES, TargetMuscle.HIPFLEXORS),                                 45, R.drawable.stretch_pigeon_pose,            Laterality.UNILATERAL),
        StretchItem("Figure-Four Stretch",            listOf(TargetMuscle.GLUTES, TargetMuscle.ABDUCTORS),                                  40, R.drawable.stretch_figure_four,            Laterality.UNILATERAL),
        StretchItem("Supine Hamstring Stretch",       listOf(TargetMuscle.HAMSTRINGS, TargetMuscle.CALVES),                                 35, R.drawable.stretch_supine_hamstring,       Laterality.UNILATERAL),
        StretchItem("Standing Hamstring Stretch",     listOf(TargetMuscle.HAMSTRINGS),                                                      35, R.drawable.stretch_standing_hamstring,     Laterality.UNILATERAL),

        // CALVES / TIBIALIS
        StretchItem("Standing Calf Stretch",          listOf(TargetMuscle.CALVES),                                                          30, R.drawable.stretch_standing_calf,          Laterality.UNILATERAL),
        StretchItem("Tibialis Anterior Stretch",      listOf(TargetMuscle.TIBIALIS),                                                        25, R.drawable.stretch_tibialis_anterior,      Laterality.UNILATERAL),

        // CORE
        StretchItem("Cobra Stretch",                  listOf(TargetMuscle.ABS, TargetMuscle.LOWER_BACK),                                   30, R.drawable.stretch_cobra,                   Laterality.BILATERAL),
        StretchItem("Side Bend Stretch",              listOf(TargetMuscle.OBLIQUES, TargetMuscle.LATS),                                     30, R.drawable.stretch_side_bend,               Laterality.UNILATERAL),
    )

    /**
     * Body areas selectable in the standalone stretch picker. Unlike the analytics
     * groupings, "Legs" includes the full lower body so every stretch in
     * [ALL_STRETCHES] is reachable.
     */
    val STRETCH_AREAS: Map<String, List<TargetMuscle>> = linkedMapOf(
        "Chest"     to listOf(TargetMuscle.CHEST_UPPER, TargetMuscle.CHEST_MIDDLE, TargetMuscle.CHEST_LOWER),
        "Back"      to listOf(TargetMuscle.LATS, TargetMuscle.TRAPS_MID, TargetMuscle.TRAPS_UPPER, TargetMuscle.LOWER_BACK),
        "Shoulders" to listOf(TargetMuscle.DELT_FRONT, TargetMuscle.DELT_SIDE, TargetMuscle.DELT_REAR),
        "Arms"      to listOf(TargetMuscle.BICEPS, TargetMuscle.TRICEPS_LONG, TargetMuscle.TRICEPS_LATERAL, TargetMuscle.FOREARMS),
        "Legs"      to listOf(TargetMuscle.QUADS, TargetMuscle.HAMSTRINGS, TargetMuscle.GLUTES, TargetMuscle.CALVES,
                              TargetMuscle.TIBIALIS, TargetMuscle.ADDUCTORS, TargetMuscle.ABDUCTORS, TargetMuscle.HIPFLEXORS),
        "Core"      to listOf(TargetMuscle.ABS, TargetMuscle.OBLIQUES)
    )

    /**
     * Returns deduplicated stretches covering the given worked muscles.
     * Uses greedy set-cover: a stretch is included only if it covers at least one
     * muscle not yet addressed by an earlier stretch in the list.
     */
    fun getStretchesFor(workedMuscles: Set<TargetMuscle>): List<StretchItem> {
        if (workedMuscles.isEmpty()) return emptyList()

        val coveredMuscles = mutableSetOf<TargetMuscle>()
        val result = mutableListOf<StretchItem>()

        for (stretch in ALL_STRETCHES) {
            val relevant = stretch.targetMuscles.filter { it in workedMuscles }
            if (relevant.isEmpty()) continue
            val newlyCovered = relevant.filter { it !in coveredMuscles }
            if (newlyCovered.isEmpty()) continue
            coveredMuscles.addAll(newlyCovered)
            result.add(stretch)
        }

        return result
    }
}
