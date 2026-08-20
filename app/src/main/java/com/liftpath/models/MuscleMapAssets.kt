package com.liftpath.models

import com.liftpath.R

/**
 * Maps each [TargetMuscle] to the illustrated body-diagram mask(s) that represent it.
 *
 * Mask artwork is adapted from github.com/MertenD/musclegroup-image-generator
 * (commit 2aaf12e667d02d16f6a335404415b475f7d83fd1), used under its Non-Commercial Source
 * License — LiftPath is a personal, non-commercial project, so this is license-compliant.
 * Revisit if that ever changes. Assets live in `res/drawable-nodpi/muscle_base.png` and
 * `muscle_mask_*.png`.
 *
 * The source repo's regions are coarser than LiftPath's granular enum, so several entries
 * share a mask (noted below) or fall back to the nearest available region since no exact
 * match exists in the source artwork.
 */
object MuscleMapAssets {

    /** TargetMuscle -> one or more mask drawables to paint the same resolved color onto. */
    val maskResIds: Map<TargetMuscle, List<Int>> = mapOf(
        // Chest: repo has only one "chest" region; the upper/mid/lower split collapses.
        TargetMuscle.CHEST_UPPER to listOf(R.drawable.muscle_mask_chest),
        TargetMuscle.CHEST_MIDDLE to listOf(R.drawable.muscle_mask_chest),
        TargetMuscle.CHEST_LOWER to listOf(R.drawable.muscle_mask_chest),
        TargetMuscle.LATS to listOf(R.drawable.muscle_mask_latissimus),
        // Traps: repo has no dedicated traps region; back_upper/neck are the closest visual proxies.
        TargetMuscle.TRAPS_MID to listOf(R.drawable.muscle_mask_back_upper),
        TargetMuscle.TRAPS_UPPER to listOf(R.drawable.muscle_mask_neck),
        TargetMuscle.LOWER_BACK to listOf(R.drawable.muscle_mask_back_lower),
        TargetMuscle.DELT_FRONT to listOf(R.drawable.muscle_mask_shoulders_front),
        // Side delt: repo only has a generic "shoulders" region, no front/side/rear split.
        TargetMuscle.DELT_SIDE to listOf(R.drawable.muscle_mask_shoulders),
        TargetMuscle.DELT_REAR to listOf(R.drawable.muscle_mask_shoulders_back),
        TargetMuscle.BICEPS to listOf(R.drawable.muscle_mask_biceps),
        // Triceps: repo has only one "triceps" region; the long/lateral head split collapses.
        TargetMuscle.TRICEPS_LONG to listOf(R.drawable.muscle_mask_triceps),
        TargetMuscle.TRICEPS_LATERAL to listOf(R.drawable.muscle_mask_triceps),
        TargetMuscle.FOREARMS to listOf(R.drawable.muscle_mask_forearms),
        TargetMuscle.QUADS to listOf(R.drawable.muscle_mask_quadriceps),
        TargetMuscle.HAMSTRINGS to listOf(R.drawable.muscle_mask_hamstring),
        TargetMuscle.GLUTES to listOf(R.drawable.muscle_mask_gluteus),
        TargetMuscle.CALVES to listOf(R.drawable.muscle_mask_calfs),
        // Tibialis: repo has no shin region; calfs is the nearest available lower-leg mask.
        TargetMuscle.TIBIALIS to listOf(R.drawable.muscle_mask_calfs),
        TargetMuscle.ADDUCTORS to listOf(R.drawable.muscle_mask_adductors),
        TargetMuscle.ABDUCTORS to listOf(R.drawable.muscle_mask_abductors),
        // Hip flexors: repo has no dedicated region; upper quadriceps is the nearest visible proxy.
        TargetMuscle.HIPFLEXORS to listOf(R.drawable.muscle_mask_quadriceps),
        // Abs: repo splits core into upper/lower, an improvement over the old single abs blob.
        TargetMuscle.ABS to listOf(R.drawable.muscle_mask_core_upper, R.drawable.muscle_mask_core_lower),
        TargetMuscle.OBLIQUES to listOf(R.drawable.muscle_mask_core_side),
    )
}
