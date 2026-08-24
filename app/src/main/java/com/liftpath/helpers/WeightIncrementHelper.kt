package com.liftpath.helpers

import com.liftpath.models.Equipment
import com.liftpath.models.ExerciseLibraryItem
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A ladder of loadable weights: rungs sit at [minimumKg] + k × [incrementKg].
 *
 * Anchoring at the minimum rather than at zero is what makes the model match real equipment — a
 * barbell loads 20 / 22.5 / 25, not 20 / 21.25 / 22.5, and kettlebells run 8 / 12 / 16.
 *
 * An [incrementKg] of zero is a deliberate sentinel meaning "this equipment has no kg ladder"
 * (bands). Every function here is the identity in that case, which is also what keeps the old
 * unguarded divide-by-zero from coming back.
 */
data class WeightIncrementRule(
    val incrementKg: Float = 0f,
    val minimumKg: Float = 0f
) {
    val hasLadder: Boolean get() = incrementKg > 0f
}

/**
 * The user's global per-equipment overrides, persisted by [WeightIncrementSettingsManager].
 *
 * [rules] is nullable, and keyed by `Equipment.name` rather than by the enum, on purpose. Gson
 * builds objects through `Unsafe` without running constructors, so a *field* absent from the
 * stored JSON arrives as the JVM zero value rather than the Kotlin default — `null` for a
 * reference, which would NPE through a non-null `Map` declaration. Declaring it nullable and
 * never dereferencing it directly makes an old or partial file resolve cleanly instead.
 *
 * Absent *entries* carry no such hazard, so a half-filled table simply falls through to
 * [WeightIncrementHelper.BUILT_IN] for everything it does not mention.
 */
data class EquipmentIncrementTable(
    val rules: Map<String, WeightIncrementRule>? = null
) {
    fun ruleFor(equipment: Equipment): WeightIncrementRule? = rules?.get(equipment.name)
}

/**
 * Answers "what is the next weight I can actually load?" for a given exercise.
 *
 * The progression system used to duck this question: `WeightAction.INCREASE` carried no kg at all
 * and left the user to pick one off a hardcoded 2.5 kg stepper. 2.5 is right for a barbell, wrong
 * on a 5 kg cable stack, and unreachable on a dumbbell rack — so without a per-equipment answer
 * the app could only ever say "go heavier" and hope.
 */
object WeightIncrementHelper {

    /**
     * Used for [Equipment.OTHER] and for an exercise with no equipment set.
     *
     * Deliberately identical to today's hardcoded stepper, so this whole feature is a no-op for
     * anyone who never opens the new settings and for every unclassified user-created exercise.
     */
    val FALLBACK = WeightIncrementRule(2.5f, 0f)

    val BUILT_IN: Map<Equipment, WeightIncrementRule> = mapOf(
        // Smallest plate pair any gym owns is 1.25 kg, so the smallest honest jump is 2.5.
        Equipment.BARBELL to WeightIncrementRule(2.5f, 20.0f),
        Equipment.EZ_BAR to WeightIncrementRule(2.5f, 10.0f),
        Equipment.SMITH_MACHINE to WeightIncrementRule(2.5f, 15.0f),
        // Logged as the pair total, so one rack size up is +2 kg per hand = +4 kg logged.
        Equipment.DUMBBELL to WeightIncrementRule(4.0f, 4.0f),
        Equipment.KETTLEBELL to WeightIncrementRule(4.0f, 8.0f),
        Equipment.CABLE to WeightIncrementRule(5.0f, 5.0f),
        Equipment.MACHINE to WeightIncrementRule(5.0f, 5.0f),
        // The progressible part is belt/assistance load, not the body. Not yet reachable:
        // bodyweight suggestions are suppressed until progression reads `addedKg`.
        Equipment.BODYWEIGHT to WeightIncrementRule(1.25f, 0.0f),
        // No ladder. Reps and RPE are the only honest progression on a band.
        Equipment.BANDS to WeightIncrementRule(0f, 0f),
        Equipment.OTHER to WeightIncrementRule(2.5f, 0.0f)
    )

    /**
     * Resolves the ladder for [item], newest override winning.
     *
     * Resolution is field-by-field rather than all-or-nothing: an exercise that overrides only the
     * step still inherits its equipment's minimum, which is almost always what was meant.
     */
    fun resolve(item: ExerciseLibraryItem?, table: EquipmentIncrementTable?): WeightIncrementRule {
        val base = item?.equipment?.let { table?.ruleFor(it) ?: BUILT_IN[it] } ?: FALLBACK
        return WeightIncrementRule(
            incrementKg = item?.weightIncrementKgOverride ?: base.incrementKg,
            minimumKg = item?.weightMinimumKgOverride ?: base.minimumKg
        )
    }

    /** Nearest loadable weight to [kg]. Never returns below the rule's minimum. */
    fun snap(kg: Float, rule: WeightIncrementRule): Float {
        if (!rule.hasLadder) return kg
        val rungs = ((kg - rule.minimumKg) / rule.incrementKg).roundToInt()
        return max(rule.minimumKg + rungs * rule.incrementKg, rule.minimumKg)
    }

    /**
     * The first rung *strictly above* [kg].
     *
     * Strictness is what makes this monotonic for off-grid input: 61 kg on a barbell goes to 62.5,
     * not back down to 60. An input at or below the minimum resolves to the minimum itself, so a
     * first increase with no history lands on the empty bar.
     */
    fun nextUp(kg: Float, rule: WeightIncrementRule): Float {
        if (!rule.hasLadder) return kg
        if (kg < rule.minimumKg) return rule.minimumKg
        val rungs = floor((kg - rule.minimumKg) / rule.incrementKg).toInt() + 1
        return max(rule.minimumKg + rungs * rule.incrementKg, rule.minimumKg)
    }

    /** The first rung strictly below [kg], floored at the rule's minimum. */
    fun prevDown(kg: Float, rule: WeightIncrementRule): Float {
        if (!rule.hasLadder) return kg
        if (kg <= rule.minimumKg) return rule.minimumKg
        val rungs = ceilDiv(kg - rule.minimumKg, rule.incrementKg) - 1
        return max(rule.minimumKg + rungs * rule.incrementKg, rule.minimumKg)
    }

    /** Trims a trailing `.0` so a whole number reads as "85", not "85.0". */
    fun format(kg: Float): String =
        if (kg % 1f == 0f) kg.toInt().toString() else String.format(Locale.US, "%.1f", kg)

    /** `ceil(a / b)` for positive [b], without the Float→Double→Float round trip. */
    private fun ceilDiv(a: Float, b: Float): Int {
        val exact = a / b
        val floored = floor(exact)
        // Tolerate binary-float drift so an on-grid value is not treated as fractionally above.
        return if (exact - floored < 1e-4f) floored.toInt() else floored.toInt() + 1
    }
}
