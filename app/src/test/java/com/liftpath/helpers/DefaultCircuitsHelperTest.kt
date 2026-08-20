package com.liftpath.helpers

import com.liftpath.models.TrainingData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCircuitsHelperTest {

    @Test
    fun seedIfNeeded_resolvesAllSixStarterCircuitsAgainstFullCatalog() {
        val data = TrainingData()
        data.exerciseLibrary.addAll(DefaultExercisesHelper.getPopularDefaults())

        DefaultCircuitsHelper.seedIfNeeded(data)

        val circuits = CircuitStore.circuits(data)
        assertEquals(6, circuits.size)
        assertTrue(circuits.all { it.items.isNotEmpty() })
        assertTrue(circuits.all { it.defaultKey != null })
    }

    @Test
    fun seedIfNeeded_isIdempotent() {
        val data = TrainingData()
        data.exerciseLibrary.addAll(DefaultExercisesHelper.getPopularDefaults())

        DefaultCircuitsHelper.seedIfNeeded(data)
        val firstPassCount = CircuitStore.circuits(data).size
        DefaultCircuitsHelper.seedIfNeeded(data)

        assertEquals(firstPassCount, CircuitStore.circuits(data).size)
    }

    @Test
    fun seedIfNeeded_skipsCircuitWhenMostOfItsExercisesAreMissing() {
        val data = TrainingData()
        // Only one Lower Lactate station exists in this library — it should not seed half-empty.
        data.exerciseLibrary.addAll(
            DefaultExercisesHelper.getPopularDefaults().filter { it.name == "Hip Thrust (Barbell)" }
        )

        DefaultCircuitsHelper.seedIfNeeded(data)

        assertTrue(CircuitStore.circuits(data).none { it.defaultKey == "lower_lactate" })
    }

    @Test
    fun seedIfNeeded_resolvesByNameEvenWhenIdsDiffer() {
        val data = TrainingData()
        // Same exercises as the catalog, but re-ided the way CatalogMergeHelper.applyMerge would
        // (maxId + 1, not the catalog id) — seeding must still match them up by name.
        data.exerciseLibrary.addAll(
            DefaultExercisesHelper.getPopularDefaults()
                .filter { it.name in setOf("Push Up", "Dumbbell Row", "Lateral Raise (Dumbbell)", "Biceps Curl (Dumbbell)") }
                .mapIndexed { index, item -> item.copy(id = 9000 + index) }
        )

        DefaultCircuitsHelper.seedIfNeeded(data)

        val upperPump = CircuitStore.circuits(data).firstOrNull { it.defaultKey == "upper_pump" }
        assertTrue(upperPump != null)
        assertEquals(4, upperPump!!.items.size)
        assertTrue(upperPump.items.all { it.exerciseId >= 9000 })
    }
}
