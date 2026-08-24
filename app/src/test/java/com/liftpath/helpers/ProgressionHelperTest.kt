package com.liftpath.helpers

import com.liftpath.models.Equipment
import com.liftpath.models.ExerciseEntry
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.ExerciseTargetMetric
import com.liftpath.models.ExerciseType
import com.liftpath.models.SetIntent
import com.liftpath.models.TrainingData
import com.liftpath.models.TrainingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Pins the behaviour of [ProgressionHelper.getIntentSuggestion] before the equipment-increment
 * work merges `calculateStrengthProgression` and `calculateBuildProgression` into one function.
 *
 * Those two are ~180 lines that differ in exactly three things: the intent constant, the rep
 * settings they read, and a hardcoded "consolidate" RPE ceiling (9.5 strength / 9.0 build). The
 * ceiling is the one a careless merge collapses, so it is pinned from both sides in
 * [strengthConsolidatesAt95] / [buildConsolidatesAt90].
 *
 * These tests assert *structure* — action, badge, targets — not `displayText`. The display strings
 * are deliberately left loose because a later phase rewrites them through a single formatter;
 * pinning them here would only manufacture churn.
 *
 * Two hazards shape the fixtures:
 *  - `calculateDaysSince` reads the wall clock, so every date is built with [daysAgo]. Normal
 *    progression cases sit at 3 days: under the 14-day time-decay gate and the 30-day
 *    `intentFallbackDays` gate.
 *  - `selectBestSession` short-circuits for a single session, so one-session fixtures skip the
 *    bad-day heuristic entirely and land on the branch under test.
 */
class ProgressionHelperTest {

    private val settings = ProgressionHelper.ProgressionSettings()

    // ── Fixtures ───────────────────────────────────────────────────────────

    private fun daysAgo(n: Int): String =
        SimpleDateFormat("yyyy/MM/dd", Locale.US).format(
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -n) }.time
        )

    /** `explicitIntent` is always set: a null intent with RPE 6.0 trips `isLegacyWarmup()`. */
    private fun set(
        kg: Float,
        reps: Int,
        rpe: Float,
        intent: SetIntent,
        setNumber: Int = 1,
        completed: Boolean? = null
    ) = ExerciseEntry(
        exerciseId = EX_ID,
        exerciseName = "Bench Press",
        setNumber = setNumber,
        kg = kg,
        reps = reps,
        rpe = rpe,
        explicitIntent = intent,
        completed = completed
    )

    /** Default library row: no equipment, so it resolves to [WeightIncrementHelper.FALLBACK]. */
    private fun library(
        equipment: Equipment? = null,
        stepOverride: Float? = null,
        minOverride: Float? = null,
        type: ExerciseType? = null,
        metric: ExerciseTargetMetric? = null
    ) = ExerciseLibraryItem(
        id = EX_ID,
        name = "Bench Press",
        equipment = equipment,
        weightIncrementKgOverride = stepOverride,
        weightMinimumKgOverride = minOverride,
        exerciseType = type,
        targetMetric = metric
    )

    private fun dataOf(date: String, vararg sets: ExerciseEntry) =
        dataOf(library(), date, *sets)

    private fun dataOf(item: ExerciseLibraryItem, date: String, vararg sets: ExerciseEntry) =
        TrainingData(
            exerciseLibrary = mutableListOf(item),
            trainings = mutableListOf(
                TrainingSession(trainingNumber = 1, date = date, exercises = sets.toMutableList())
            )
        )

    private fun suggest(intent: SetIntent, data: TrainingData) =
        ProgressionHelper.getIntentSuggestion(EX_ID, intent, data, settings)

    // ── No-suggestion intents ──────────────────────────────────────────────

    @Test
    fun warmupAndUnknownYieldNoSuggestion() {
        val data = dataOf(daysAgo(3), set(80f, 5, 8.0f, SetIntent.STRENGTH))

        for (intent in listOf(SetIntent.WARMUP, SetIntent.UNKNOWN)) {
            val s = suggest(intent, data)
            assertEquals(ProgressionHelper.WeightAction.NONE, s.weightAction)
            assertEquals("", s.displayText)
            assertNull(s.badge)
            assertNull(s.suggestedWeight)
        }
    }

    // ── First time ─────────────────────────────────────────────────────────

    @Test
    fun noHistoryStartsLight() {
        val empty = TrainingData(
            exerciseLibrary = mutableListOf(ExerciseLibraryItem(id = EX_ID, name = "Bench Press"))
        )
        val s = suggest(SetIntent.STRENGTH, empty)

        assertEquals(ProgressionHelper.WeightAction.START_LIGHT, s.weightAction)
        assertTrue(s.isFirstTime)
        assertEquals("NEW", s.badge)
        assertEquals(settings.strengthMinReps, s.suggestedReps)
        assertEquals(3, s.suggestedSets)
        // No history means no number to offer, and none is invented.
        assertNull(s.suggestedWeight)
        assertNull(s.lastWeight)
    }

    // ── INCREASE: the branch that names no weight today ────────────────────

    @Test
    fun strengthAtMaxRepsBelowRpeThresholdIncreases() {
        // 6 reps == strengthMaxReps, RPE 7.5 < strengthIncreaseRpeThreshold (8.0).
        val data = dataOf(daysAgo(3), set(82.5f, 6, 7.5f, SetIntent.STRENGTH))
        val s = suggest(SetIntent.STRENGTH, data)

        assertEquals(ProgressionHelper.WeightAction.INCREASE, s.weightAction)
        assertEquals("LEVEL UP", s.badge)
        assertEquals(settings.strengthMinReps, s.suggestedReps)
        assertEquals(82.5f, s.lastWeight!!, 0.001f)
        // The gap this whole feature closes: INCREASE now names the next loadable weight rather
        // than saying "go heavier" and leaving the user to pick a number off a generic stepper.
        assertEquals(85f, s.suggestedWeight!!, 0.001f)
        assertTrue(s.displayText.contains("85 kg"))
    }

    @Test
    fun buildAtMaxRepsBelowRpeThresholdIncreases() {
        val data = dataOf(daysAgo(3), set(60f, 12, 7.5f, SetIntent.BUILD))
        val s = suggest(SetIntent.BUILD, data)

        assertEquals(ProgressionHelper.WeightAction.INCREASE, s.weightAction)
        assertEquals("LEVEL UP", s.badge)
        assertEquals(settings.buildMinReps, s.suggestedReps)
        // Single set, so the BUILD average is exactly 60; the fallback ladder steps 2.5.
        assertEquals(62.5f, s.suggestedWeight!!, 0.001f)
    }

    @Test
    fun maxRepsAtHighRpeDoesNotIncrease() {
        // Reps are there but RPE 8.5 is at/above the 8.0 threshold — the AND guard must hold.
        val data = dataOf(daysAgo(3), set(82.5f, 6, 8.5f, SetIntent.STRENGTH))
        val s = suggest(SetIntent.STRENGTH, data)

        assertEquals(ProgressionHelper.WeightAction.MAINTAIN, s.weightAction)
    }

    // ── The 9.5 / 9.0 consolidate split ────────────────────────────────────

    @Test
    fun strengthConsolidatesAt95() {
        val over = suggest(SetIntent.STRENGTH, dataOf(daysAgo(3), set(82.5f, 4, 9.6f, SetIntent.STRENGTH)))
        assertEquals("CONSOLIDATE", over.badge)
        assertEquals(ProgressionHelper.WeightAction.MAINTAIN, over.weightAction)
        // Consolidating holds reps where they were rather than adding one.
        assertEquals(4, over.suggestedReps)

        val under = suggest(SetIntent.STRENGTH, dataOf(daysAgo(3), set(82.5f, 4, 9.4f, SetIntent.STRENGTH)))
        assertEquals("ADD REP", under.badge)
        assertEquals(5, under.suggestedReps)
    }

    @Test
    fun buildConsolidatesAt90() {
        // BUILD's ceiling is 9.0, not 9.5 — an RPE that consolidates here would add a rep in STRENGTH.
        val over = suggest(SetIntent.BUILD, dataOf(daysAgo(3), set(60f, 10, 9.1f, SetIntent.BUILD)))
        assertEquals("CONSOLIDATE", over.badge)
        assertEquals(10, over.suggestedReps)

        val under = suggest(SetIntent.BUILD, dataOf(daysAgo(3), set(60f, 10, 8.9f, SetIntent.BUILD)))
        assertEquals("ADD REP", under.badge)
        assertEquals(11, under.suggestedReps)
    }

    @Test
    fun rpe94ConsolidatesInBuildButNotInStrength() {
        // The single assertion that fails if the merge collapses the two ceilings to one.
        val strength = suggest(SetIntent.STRENGTH, dataOf(daysAgo(3), set(82.5f, 4, 9.4f, SetIntent.STRENGTH)))
        val build = suggest(SetIntent.BUILD, dataOf(daysAgo(3), set(60f, 10, 9.4f, SetIntent.BUILD)))

        assertEquals("ADD REP", strength.badge)
        assertEquals("CONSOLIDATE", build.badge)
    }

    // ── Rep capping ────────────────────────────────────────────────────────

    @Test
    fun addRepStopsAtMaxAndDropsTheBadge() {
        // 6 reps at RPE 8.5: too hard to increase, already at strengthMaxReps, so there is no
        // rep to add. Badge must be null rather than a misleading "ADD REP".
        val s = suggest(SetIntent.STRENGTH, dataOf(daysAgo(3), set(82.5f, 6, 8.5f, SetIntent.STRENGTH)))

        assertEquals(settings.strengthMaxReps, s.suggestedReps)
        assertNull(s.badge)
    }

    @Test
    fun buildAddRepStopsAtMax() {
        val s = suggest(SetIntent.BUILD, dataOf(daysAgo(3), set(60f, 12, 8.5f, SetIntent.BUILD)))

        assertEquals(settings.buildMaxReps, s.suggestedReps)
        assertNull(s.badge)
    }

    // ── Failure → retry ────────────────────────────────────────────────────

    @Test
    fun failedSetRetriesSameWeight() {
        val data = dataOf(daysAgo(3), set(82.5f, 3, 9.0f, SetIntent.STRENGTH, completed = false))
        val s = suggest(SetIntent.STRENGTH, data)

        assertEquals("RETRY", s.badge)
        assertEquals(ProgressionHelper.WeightAction.MAINTAIN, s.weightAction)
        assertEquals(3, s.suggestedReps)
        assertEquals(82.5f, s.lastWeight!!, 0.001f)
    }

    // ── Time decay ─────────────────────────────────────────────────────────

    @Test
    fun longBreakDecaysTheWeight() {
        // 20 days: past the 14-day threshold, so the 0.95 multiplier applies.
        val s = suggest(SetIntent.STRENGTH, dataOf(daysAgo(20), set(100f, 5, 8.0f, SetIntent.STRENGTH)))

        assertEquals(ProgressionHelper.WeightAction.MAINTAIN, s.weightAction)
        assertNull(s.badge)
        assertEquals(settings.strengthMinReps, s.suggestedReps)
        // The decayed figure now reaches suggestedWeight, which is what callers prefill from,
        // while lastWeight stays at what was actually lifted. Previously the decay existed only
        // in the display string, so the kg field was filled with 100 while the hint advised 95.
        assertEquals(95f, s.suggestedWeight!!, 0.001f)
        assertEquals(100f, s.lastWeight!!, 0.001f)
        assertTrue(s.displayText.contains("95 kg"))
    }

    @Test
    fun decayedWeightIsPutBackOnTheLadder() {
        // 72.5 × 0.95 = 68.875, which is not a weight anyone can rack. On a barbell it must
        // resolve to a real rung.
        val data = dataOf(library(Equipment.BARBELL), daysAgo(20), set(72.5f, 5, 8.0f, SetIntent.STRENGTH))
        val s = suggest(SetIntent.STRENGTH, data)

        val rule = WeightIncrementHelper.BUILT_IN[Equipment.BARBELL]!!
        assertEquals(70f, s.suggestedWeight!!, 0.001f)
        assertEquals(s.suggestedWeight!!, WeightIncrementHelper.snap(s.suggestedWeight!!, rule), 0.001f)
    }

    // ── Aggregation shape ──────────────────────────────────────────────────

    @Test
    fun strengthPicksTheBestSetNotTheLast() {
        // Three sets; the middle one has the highest Epley 1RM (85×5 = 99.2 vs 82.5×6 = 99.0).
        val data = dataOf(
            daysAgo(3),
            set(80f, 5, 8.0f, SetIntent.STRENGTH, setNumber = 1),
            set(85f, 5, 8.5f, SetIntent.STRENGTH, setNumber = 2),
            set(75f, 5, 9.0f, SetIntent.STRENGTH, setNumber = 3)
        )
        val s = suggest(SetIntent.STRENGTH, data)

        assertEquals(85f, s.lastWeight!!, 0.001f)
        assertEquals(8.5f, s.lastRpe!!, 0.001f)
    }

    @Test
    fun buildAveragesWeightAcrossSetsAndLeavesItUnrounded() {
        // Plain (not RPE-weighted) mean of 65/70/70 = 68.333…, which today flows straight into the
        // kg field as a raw float. The equipment grid is what will eventually round this.
        val data = dataOf(
            daysAgo(3),
            set(65f, 10, 8.0f, SetIntent.BUILD, setNumber = 1),
            set(70f, 10, 8.0f, SetIntent.BUILD, setNumber = 2),
            set(70f, 10, 8.0f, SetIntent.BUILD, setNumber = 3)
        )
        val s = suggest(SetIntent.BUILD, data)

        assertEquals(68.333f, s.lastWeight!!, 0.01f)
        assertFalse("BUILD's representative weight is a raw average today", s.lastWeight!! % 1f == 0f)
    }

    // ── Cross-intent fallback ──────────────────────────────────────────────

    @Test
    fun staleStrengthHistoryFallsBackToBuildAndMarksItEstimated() {
        // BUILD requested, only STRENGTH logged, and it is older than intentFallbackDays (30).
        val data = dataOf(daysAgo(45), set(100f, 5, 8.0f, SetIntent.STRENGTH))
        val s = suggest(SetIntent.BUILD, data)

        assertTrue(s.isEstimated)
        assertTrue(s.displayText.contains("est."))
        // 1RM 116.67 × build1RMPercent 0.70 = 81.67, now snapped at source onto the 2.5 fallback
        // ladder. It used to reach the kg field as the raw float.
        assertNotNull(s.lastWeight)
        assertEquals(82.5f, s.lastWeight!!, 0.001f)
        // The estimated branch names a number now instead of dropping it from the text.
        assertTrue("estimated text should carry a weight: ${s.displayText}", s.displayText.contains("kg"))
    }

    @Test
    fun freshHistoryDoesNotFallBack() {
        val data = dataOf(daysAgo(3), set(100f, 5, 8.0f, SetIntent.STRENGTH))
        val s = suggest(SetIntent.STRENGTH, data)

        assertFalse(s.isEstimated)
        assertFalse(s.displayText.contains("est."))
    }

    // ── FLUSH ──────────────────────────────────────────────────────────────

    @Test
    fun flushDerivesWeightFromStrength1RM() {
        val data = dataOf(daysAgo(3), set(100f, 5, 8.0f, SetIntent.STRENGTH))
        val s = suggest(SetIntent.FLUSH, data)

        // Epley 1RM = 116.67, × flush1RMPercent 0.5 = 58.33, snapped onto the exercise's ladder
        // (the 2.5 fallback here) rather than the old flat 0.5 grid, which offered weights no
        // stack or rack actually has.
        assertNotNull(s.suggestedWeight)
        assertEquals(57.5f, s.suggestedWeight!!, 0.001f)
        assertEquals(settings.flushTargetReps, s.suggestedReps)
        assertEquals(settings.flushTargetSets, s.suggestedSets)
    }

    @Test
    fun flushUsesTheEquipmentLadderNotAFlatGrid() {
        val data = dataOf(library(Equipment.CABLE), daysAgo(3), set(100f, 5, 8.0f, SetIntent.STRENGTH))
        val s = suggest(SetIntent.FLUSH, data)

        // 58.33 on a 5 kg stack anchored at 5 → 60.
        assertEquals(60f, s.suggestedWeight!!, 0.001f)
    }

    @Test
    fun flushWithNoHistoryStartsLight() {
        val empty = TrainingData(
            exerciseLibrary = mutableListOf(ExerciseLibraryItem(id = EX_ID, name = "Bench Press"))
        )
        val s = suggest(SetIntent.FLUSH, empty)

        assertEquals(ProgressionHelper.WeightAction.START_LIGHT, s.weightAction)
        assertEquals("NEW", s.badge)
        assertNull(s.suggestedWeight)
    }

    // ── Warmup sets are excluded from history ──────────────────────────────

    @Test
    fun warmupSetsDoNotFeedProgression() {
        val data = TrainingData(
            exerciseLibrary = mutableListOf(ExerciseLibraryItem(id = EX_ID, name = "Bench Press")),
            trainings = mutableListOf(
                TrainingSession(
                    trainingNumber = 1,
                    date = daysAgo(3),
                    exercises = mutableListOf(
                        ExerciseEntry(
                            exerciseId = EX_ID, exerciseName = "Bench Press", setNumber = 1,
                            kg = 40f, reps = 10, rpe = 5f,
                            explicitIntent = SetIntent.STRENGTH, isWarmup = true
                        ),
                        set(82.5f, 5, 8.5f, SetIntent.STRENGTH, setNumber = 2)
                    )
                )
            )
        )
        val s = suggest(SetIntent.STRENGTH, data)

        // The 40 kg warmup must not drag the representative weight down.
        assertEquals(82.5f, s.lastWeight!!, 0.001f)
    }

    // ── Equipment awareness: the point of the feature ──────────────────────

    @Test
    fun theSameLiftIncreasesDifferentlyOnDifferentEquipment() {
        // Identical performance — 6 reps at RPE 7.5, top of the range with RPE to spare — logged
        // on three machines. "Go heavier" means a different number on each, which is precisely
        // what a single hardcoded 2.5 kg step could never express.
        fun increaseFor(equipment: Equipment, kg: Float): Float {
            val data = dataOf(library(equipment), daysAgo(3), set(kg, 6, 7.5f, SetIntent.STRENGTH))
            return suggest(SetIntent.STRENGTH, data).suggestedWeight!!
        }

        assertEquals(85f, increaseFor(Equipment.BARBELL, 82.5f), 0.001f)
        // Cable rungs are multiples of 5, so 82.5 sits between 80 and 85.
        assertEquals(85f, increaseFor(Equipment.CABLE, 82.5f), 0.001f)
        // Dumbbells are logged as the pair total, so one rack size up is +4 kg.
        assertEquals(64f, increaseFor(Equipment.DUMBBELL, 60f), 0.001f)
        assertEquals(12f, increaseFor(Equipment.KETTLEBELL, 8f), 0.001f)
    }

    @Test
    fun perExerciseOverrideBeatsTheEquipmentDefault() {
        // A barbell exercise on a bar that only has microplates.
        val item = library(Equipment.BARBELL, stepOverride = 1f)
        val s = suggest(SetIntent.STRENGTH, dataOf(item, daysAgo(3), set(82f, 6, 7.5f, SetIntent.STRENGTH)))

        assertEquals(83f, s.suggestedWeight!!, 0.001f)
    }

    @Test
    fun aStoredTableBeatsTheBuiltInDefault() {
        // A gym whose cable stacks take 2.5 kg add-on pins.
        val table = EquipmentIncrementTable(
            mapOf(Equipment.CABLE.name to WeightIncrementRule(2.5f, 2.5f))
        )
        val data = dataOf(library(Equipment.CABLE), daysAgo(3), set(80f, 6, 7.5f, SetIntent.STRENGTH))
        val s = ProgressionHelper.getIntentSuggestion(EX_ID, SetIntent.STRENGTH, data, settings, table)

        assertEquals(82.5f, s.suggestedWeight!!, 0.001f)
    }

    // ── Suppression: where naming a number would be wrong, not just imprecise ──

    @Test
    fun bandsGetRepsAndRpeButNoWeight() {
        val data = dataOf(library(Equipment.BANDS), daysAgo(3), set(0f, 6, 7.5f, SetIntent.STRENGTH))
        val s = suggest(SetIntent.STRENGTH, data)

        assertEquals(ProgressionHelper.WeightAction.INCREASE, s.weightAction)
        assertNull(s.suggestedWeight)
        assertFalse("bands must not be given a kg: ${s.displayText}", s.displayText.contains("kg"))
        // The reps/RPE half of the advice is still there and still correct.
        assertEquals(settings.strengthMinReps, s.suggestedReps)
        assertEquals(settings.strengthTargetRpe, s.suggestedRpe!!, 0.001f)
    }

    @Test
    fun bodyweightExercisesGetNoWeight() {
        // Progression reads ExerciseEntry.kg, which for a bodyweight set is body weight + added.
        // Until that pipeline separates addedKg out, any kg here would track the user's scale
        // rather than their training, so none is offered.
        val item = library(Equipment.BODYWEIGHT, type = ExerciseType.BODYWEIGHT)
        val s = suggest(SetIntent.STRENGTH, dataOf(item, daysAgo(3), set(85f, 6, 7.5f, SetIntent.STRENGTH)))

        assertNull(s.suggestedWeight)
        assertFalse(s.displayText.contains("kg"))
        assertEquals(settings.strengthMinReps, s.suggestedReps)
    }

    @Test
    fun timedHoldsGetNoWeight() {
        val item = library(Equipment.BODYWEIGHT, metric = ExerciseTargetMetric.TIME)
        val s = suggest(SetIntent.STRENGTH, dataOf(item, daysAgo(3), set(20f, 6, 7.5f, SetIntent.STRENGTH)))

        assertNull(s.suggestedWeight)
        assertFalse(s.displayText.contains("kg"))
    }

    // ── Snap policy ────────────────────────────────────────────────────────

    @Test
    fun aRealLoggedWeightIsNeverSnapped() {
        // 61 kg on a barbell is off the 2.5 ladder, but it is what the user actually lifted —
        // someone running 1 kg microplates must not be told they lifted 60.
        val data = dataOf(library(Equipment.BARBELL), daysAgo(3), set(61f, 4, 8.5f, SetIntent.STRENGTH))
        val s = suggest(SetIntent.STRENGTH, data)

        assertEquals(ProgressionHelper.WeightAction.MAINTAIN, s.weightAction)
        assertEquals(61f, s.suggestedWeight!!, 0.001f)
        assertEquals(61f, s.lastWeight!!, 0.001f)
    }

    @Test
    fun aDerivedBuildAverageIsSnapped() {
        // The mean of 65/70/70 is 68.333…, which used to reach the kg field verbatim.
        val data = dataOf(
            library(Equipment.BARBELL),
            daysAgo(3),
            set(65f, 10, 8.5f, SetIntent.BUILD, setNumber = 1),
            set(70f, 10, 8.5f, SetIntent.BUILD, setNumber = 2),
            set(70f, 10, 8.5f, SetIntent.BUILD, setNumber = 3)
        )
        val s = suggest(SetIntent.BUILD, data)

        assertEquals(67.5f, s.suggestedWeight!!, 0.001f)
        // lastWeight still reports what the history actually averaged out to.
        assertEquals(68.333f, s.lastWeight!!, 0.01f)
    }

    @Test
    fun everySuggestedWeightIsLoadable() {
        // Sweeps the branches that produce a number and asserts each lands on its own ladder.
        val rule = WeightIncrementHelper.BUILT_IN[Equipment.CABLE]!!
        val cases = listOf(
            "increase" to set(80f, 6, 7.5f, SetIntent.STRENGTH),
            "consolidate" to set(80f, 4, 9.6f, SetIntent.STRENGTH),
            "add rep" to set(80f, 4, 8.5f, SetIntent.STRENGTH),
            "retry" to set(80f, 4, 9.0f, SetIntent.STRENGTH, completed = false)
        )
        for ((label, entry) in cases) {
            val s = suggest(SetIntent.STRENGTH, dataOf(library(Equipment.CABLE), daysAgo(3), entry))
            val w = s.suggestedWeight!!
            assertEquals("$label produced an unloadable $w kg", w, WeightIncrementHelper.snap(w, rule), 0.001f)
        }
    }

    private companion object {
        const val EX_ID = 1
    }
}
