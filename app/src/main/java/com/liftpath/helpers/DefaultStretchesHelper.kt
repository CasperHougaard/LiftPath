package com.liftpath.helpers

import com.liftpath.models.StretchItem
import com.liftpath.models.TargetMuscle

object DefaultStretchesHelper {

    val ALL_STRETCHES: List<StretchItem> = listOf(
        // CHEST
        StretchItem("Doorway Chest Stretch",          listOf(TargetMuscle.CHEST_UPPER, TargetMuscle.CHEST_MIDDLE, TargetMuscle.DELT_FRONT), 30),
        StretchItem("Low Doorway Stretch",            listOf(TargetMuscle.CHEST_LOWER, TargetMuscle.CHEST_MIDDLE),                           30),

        // BACK
        StretchItem("Child's Pose",                   listOf(TargetMuscle.LATS, TargetMuscle.LOWER_BACK),                                   40),
        StretchItem("Lat Prayer Stretch",             listOf(TargetMuscle.LATS),                                                             30),
        StretchItem("Cat-Cow Stretch",                listOf(TargetMuscle.LOWER_BACK, TargetMuscle.ABS),                                    40),
        StretchItem("Mid-Back Thoracic Rotation",     listOf(TargetMuscle.TRAPS_MID),                                                       30),
        StretchItem("Upper Trap Neck Tilt",           listOf(TargetMuscle.TRAPS_UPPER),                                                     30),

        // SHOULDERS
        StretchItem("Cross-Body Shoulder Stretch",    listOf(TargetMuscle.DELT_REAR, TargetMuscle.TRAPS_MID),                               30),
        StretchItem("Overhead Shoulder Stretch",      listOf(TargetMuscle.DELT_SIDE, TargetMuscle.DELT_FRONT),                              30),

        // ARMS
        StretchItem("Overhead Triceps Stretch",       listOf(TargetMuscle.TRICEPS_LONG, TargetMuscle.TRICEPS_LATERAL),                      30),
        StretchItem("Biceps Wall Stretch",            listOf(TargetMuscle.BICEPS),                                                          30),
        StretchItem("Wrist Flexor Stretch",           listOf(TargetMuscle.FOREARMS),                                                        25),

        // LEGS — QUADS / HIP FLEXORS / ADDUCTORS
        StretchItem("Kneeling Hip Flexor Stretch",    listOf(TargetMuscle.HIPFLEXORS, TargetMuscle.QUADS),                                  40),
        StretchItem("Lunging Hip Flexor Stretch",     listOf(TargetMuscle.HIPFLEXORS, TargetMuscle.ADDUCTORS),                              40),
        StretchItem("Standing Quad Stretch",          listOf(TargetMuscle.QUADS),                                                           30),
        StretchItem("Seated Butterfly Stretch",       listOf(TargetMuscle.ADDUCTORS),                                                       35),

        // LEGS — HAMSTRINGS / GLUTES / ABDUCTORS
        StretchItem("Pigeon Pose",                    listOf(TargetMuscle.GLUTES, TargetMuscle.HIPFLEXORS),                                 45),
        StretchItem("Figure-Four Stretch",            listOf(TargetMuscle.GLUTES, TargetMuscle.ABDUCTORS),                                  40),
        StretchItem("Supine Hamstring Stretch",       listOf(TargetMuscle.HAMSTRINGS, TargetMuscle.CALVES),                                 35),
        StretchItem("Standing Hamstring Stretch",     listOf(TargetMuscle.HAMSTRINGS),                                                      35),

        // CALVES / TIBIALIS
        StretchItem("Standing Calf Stretch",          listOf(TargetMuscle.CALVES),                                                          30),
        StretchItem("Tibialis Anterior Stretch",      listOf(TargetMuscle.TIBIALIS),                                                        25),

        // CORE
        StretchItem("Cobra Stretch",                  listOf(TargetMuscle.ABS, TargetMuscle.LOWER_BACK),                                   30),
        StretchItem("Side Bend Stretch",              listOf(TargetMuscle.OBLIQUES, TargetMuscle.LATS),                                     30),
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
