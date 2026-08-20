package com.liftpath.helpers

import android.content.Context
import com.liftpath.R
import com.liftpath.models.ActiveRoutineType
import com.liftpath.models.CircuitTemplate
import com.liftpath.models.ExerciseLibraryItem
import com.liftpath.models.PlanExerciseSelectionType
import com.liftpath.models.PlanExerciseSlot
import com.liftpath.models.PlanSet
import com.liftpath.models.PlanSlotType
import com.liftpath.models.TrainingData
import com.liftpath.models.WorkoutPlan
import java.util.Locale

/**
 * Reads a plan rotation without starting it.
 *
 * Two callers need the same answers: the workout-mode sheet (which offers "continue rotation")
 * and the Workout tab (whose hero and "up next" card describe the same session before you commit
 * to it). This used to be a private method inside SelectWorkoutModeBottomSheet; a second copy
 * would have been a second place to get the modulo wrong.
 */
object PlanRotationHelper {

    /** One row of a plan preview. [target] is null when the slot carries no targets. */
    data class SlotPreview(
        val label: String,
        val target: String?,
        val isSpecial: Boolean
    )

    /** What the Plan tab resolves the user's active routine to. */
    sealed class ActiveRoutine {
        data class SinglePlan(val plan: WorkoutPlan) : ActiveRoutine()
        data class Rotation(val planSet: PlanSet, val nextPlan: WorkoutPlan) : ActiveRoutine()
    }

    /**
     * The id after [lastCompletedPlanId] in [planIds], wrapping at the end. A rotation that has
     * never been run (or whose last-completed id isn't in the list) starts at its first plan.
     * The one place this modulo gets computed — [nextPlan] and `PlanSetListAdapter` both call it.
     */
    fun nextPlanId(planIds: List<String>, lastCompletedPlanId: String?): String? {
        if (planIds.isEmpty()) return null
        val lastIndex = planIds.indexOf(lastCompletedPlanId)
        val nextIndex = if (lastIndex == -1) 0 else (lastIndex + 1) % planIds.size
        return planIds.getOrNull(nextIndex)
    }

    /**
     * The plan a rotation would serve next: the one after [PlanSetProgress.lastCompletedPlanId],
     * wrapping at the end. A rotation that has never been run starts at its first plan.
     */
    fun nextPlan(data: TrainingData, planSet: PlanSet): WorkoutPlan? {
        val progress = data.planSetProgress.find { it.planSetId == planSet.id }
        val nextPlanId = nextPlanId(planSet.planIds, progress?.lastCompletedPlanId) ?: return null
        return data.workoutPlans.find { it.id == nextPlanId }
    }

    /**
     * The rotation the user is actually in the middle of — most recently advanced — paired with
     * the plan it would serve next. Null when no rotation has been run, or when the one that was
     * has since lost its plans. This is the pre-active-routine heuristic, kept as the fallback
     * [resolveActiveRoutine] uses when nothing has ever been explicitly chosen.
     */
    fun activeRotation(data: TrainingData): Pair<PlanSet, WorkoutPlan>? {
        val latestProgress = data.planSetProgress
            .filter { it.lastCompletedAt != null }
            .maxByOrNull { it.lastCompletedAt ?: 0L }
            ?: return null
        val planSet = data.planSets.find { it.id == latestProgress.planSetId } ?: return null
        val next = nextPlan(data, planSet) ?: return null
        return planSet to next
    }

    /**
     * The routine the Plan tab says is active. Prefers the explicit choice
     * ([TrainingData.activeRoutineType]); falls back to [activeRotation]'s completion-based
     * guess only when nothing has ever been chosen, or the chosen target no longer exists
     * (deleted plan/rotation) — a dangling pointer should behave like no choice at all, not
     * like an error.
     */
    fun resolveActiveRoutine(data: TrainingData): ActiveRoutine? {
        fun fallback(): ActiveRoutine? =
            activeRotation(data)?.let { (planSet, next) -> ActiveRoutine.Rotation(planSet, next) }

        return when (data.activeRoutineType) {
            ActiveRoutineType.SINGLE_PLAN ->
                data.workoutPlans.find { it.id == data.activePlanId }
                    ?.let { ActiveRoutine.SinglePlan(it) }
                    ?: fallback()
            ActiveRoutineType.ROTATION ->
                data.planSets.find { it.id == data.activePlanSetId }
                    ?.let { planSet -> nextPlan(data, planSet)?.let { ActiveRoutine.Rotation(planSet, it) } }
                    ?: fallback()
            null -> fallback()
        }
    }

    /**
     * How many *exercises* [plan] resolves to. Warmup, cooldown and circuit slots are excluded: the
     * callers label this "N exercises", and neither a warm-up block nor a circuit is one.
     */
    fun exerciseCount(plan: WorkoutPlan, library: List<ExerciseLibraryItem>): Int =
        previewSlotsInternal(plan, library).count { (slot, _) -> !slot.isSpecialElement && !slot.isCircuit }

    /**
     * The rows [plan] would build, resolved the same way ActiveTrainingActivity.applyPlan
     * resolves them — V2 `exerciseConfigs` with the legacy `exerciseIds` fallback, family slots
     * through [FamilySlotResolver], warmup/cooldown/circuit kept in place. Deliberately mirrors
     * that method: a preview that disagrees with the workout it previews is worse than no preview.
     */
    fun previewSlots(
        context: Context,
        plan: WorkoutPlan,
        library: List<ExerciseLibraryItem>,
        circuits: List<CircuitTemplate> = emptyList()
    ): List<SlotPreview> = previewSlotsInternal(plan, library).map { (slot, exercise) ->
        when (slot.slotType) {
            PlanSlotType.WARMUP -> SlotPreview(
                label = context.getString(R.string.label_warmup_element),
                target = formatMinutes(slot.durationSeconds ?: DEFAULT_SPECIAL_SECONDS),
                isSpecial = true
            )
            PlanSlotType.COOLDOWN -> SlotPreview(
                label = context.getString(R.string.label_cooldown_element),
                target = formatMinutes(slot.durationSeconds ?: DEFAULT_SPECIAL_SECONDS),
                isSpecial = true
            )
            PlanSlotType.CIRCUIT -> {
                val template = circuits.find { it.id == slot.circuitId }
                val rounds = slot.setsTarget ?: template?.suggestedRounds
                SlotPreview(
                    label = template?.name ?: context.getString(R.string.tile_circuit),
                    target = if (rounds != null) {
                        context.getString(R.string.circuit_preview_rounds, rounds)
                    } else {
                        context.getString(R.string.circuit_preview_open)
                    },
                    isSpecial = true
                )
            }
            else -> SlotPreview(
                label = exercise?.name ?: "",
                target = formatTarget(slot),
                isSpecial = false
            )
        }
    }

    /**
     * Slots paired with their resolved exercise. Unresolvable exercise slots are dropped, which
     * is what applyPlan does too (`return@forEach` on a missing id) — so the count matches.
     * Circuit slots carry no [ExerciseLibraryItem] and are always kept; [previewSlots] resolves
     * the template separately.
     */
    private fun previewSlotsInternal(
        plan: WorkoutPlan,
        library: List<ExerciseLibraryItem>
    ): List<Pair<PlanExerciseSlot, ExerciseLibraryItem?>> {
        val configs = plan.exerciseConfigs?.takeIf { it.isNotEmpty() }
            ?: plan.exerciseIds.map { id ->
                PlanExerciseSlot(
                    exerciseId = id,
                    selectionType = PlanExerciseSelectionType.SPECIFIC_VARIANT
                )
            }

        return configs.mapNotNull { slot ->
            if (slot.isSpecialElement) return@mapNotNull slot to null
            if (slot.isCircuit) return@mapNotNull slot to null

            val exercise = when (slot.effectiveSelectionType) {
                PlanExerciseSelectionType.SPECIFIC_VARIANT ->
                    slot.exerciseId?.let { id -> library.find { it.id == id } }
                PlanExerciseSelectionType.FAMILY_SLOT ->
                    FamilySlotResolver.resolve(slot.familyId, slot.movementPattern, library)
            } ?: return@mapNotNull null

            slot to exercise
        }
    }

    /** "4×6 @ 8", "3×45s", "4 sets" — whatever the slot actually specifies. */
    private fun formatTarget(slot: PlanExerciseSlot): String? {
        val sets = slot.setsTarget
        val volume = when {
            slot.durationSeconds != null -> "${slot.durationSeconds}s"
            !slot.repsTarget.isNullOrBlank() -> slot.repsTarget
            else -> null
        }
        val core = when {
            sets != null && volume != null -> "$sets×$volume"
            sets != null -> "$sets×"
            volume != null -> volume
            else -> null
        } ?: return null

        val rpe = slot.rpeTarget ?: return core
        return "$core @ ${SetFormatter.trimNum(rpe)}"
    }

    private fun formatMinutes(seconds: Int): String =
        String.format(Locale.US, "%d min", (seconds / 60).coerceAtLeast(1))

    /** Matches applyPlan's fallback for a special element with no stored duration. */
    private const val DEFAULT_SPECIAL_SECONDS = 300
}
