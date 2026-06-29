package com.liftpath.helpers

import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.MovementPattern

object FamilySlotResolver {

    /**
     * Resolve a family slot to a concrete exercise.
     *
     * Priority:
     *   1. Exercises whose familyId matches, sorted by id ascending (most fundamental variant first).
     *   2. Exercises whose movement pattern matches, same sort.
     *   3. null if nothing found.
     */
    fun resolve(
        familyId: String?,
        movementPattern: MovementPattern?,
        library: List<ExerciseLibraryItem>
    ): ExerciseLibraryItem? {
        if (familyId != null) {
            val candidates = library.filter { it.familyId == familyId }
            if (candidates.isNotEmpty()) return candidates.minByOrNull { it.id }
        }
        if (movementPattern != null) {
            return library.filter { it.pattern == movementPattern }.minByOrNull { it.id }
        }
        return null
    }

    /**
     * Return all library exercises sorted so that compatible ones come first.
     * Used to populate the "Change Exercise" picker so the user sees matching
     * exercises at the top without being restricted to them.
     *
     * Order: exact familyId match → same movementPattern (different family) → everything else.
     */
    fun sortedCandidates(
        familyId: String?,
        movementPattern: MovementPattern?,
        library: List<ExerciseLibraryItem>
    ): List<ExerciseLibraryItem> {
        val primary = if (familyId != null)
            library.filter { it.familyId == familyId }
        else
            emptyList()
        val primaryIds = primary.map { it.id }.toSet()

        val secondary = if (movementPattern != null)
            library.filter { it.pattern == movementPattern && it.id !in primaryIds }
        else
            emptyList()
        val secondaryIds = secondary.map { it.id }.toSet()

        val rest = library.filter { it.id !in primaryIds && it.id !in secondaryIds }
        return primary + secondary + rest
    }
}
