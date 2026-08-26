package com.liftpath.helpers

import android.content.Context
import androidx.annotation.StringRes
import com.liftpath.R
import kotlin.math.roundToInt

/**
 * How much of a cool-down to run after a workout.
 *
 * [AUTO] is the default and reproduces the behaviour that existed before the setup screen:
 * a set-cover of the muscles the session actually worked. The other three exist because that
 * verdict is not always the one the athlete wants — a heavy leg day may deserve everything,
 * a rushed lunch session nothing at all.
 */
enum class StretchScope(
    /** Stable string written to prefs. Never renumber or reuse — ordinals are deliberately
     *  not persisted so that reordering this enum cannot silently change a user's choice. */
    val prefValue: String,
    @StringRes val labelRes: Int,
    @StringRes val captionRes: Int
) {
    AUTO("auto", R.string.stretch_scope_auto, R.string.stretch_scope_auto_caption),
    FULL("full", R.string.stretch_scope_full, R.string.stretch_scope_full_caption),
    SPECIFIC("specific", R.string.stretch_scope_specific, R.string.stretch_scope_specific_caption),
    NONE("none", R.string.stretch_scope_none, R.string.stretch_scope_none_caption);

    companion object {
        val DEFAULT = AUTO

        /** Unknown or absent values fall back to the default rather than throwing, so a
         *  restored-from-newer-build pref can never crash the post-workout screen — the one
         *  place a crash would cost the user a just-finished session. */
        fun fromPrefValue(value: String?): StretchScope =
            values().firstOrNull { it.prefValue == value } ?: DEFAULT
    }
}

/**
 * Reads and writes the cool-down preferences chosen on
 * [com.liftpath.activities.StretchSetupActivity]: the scope, the body areas that scope
 * [StretchScope.SPECIFIC] uses, and a multiplier on how long each stretch is held.
 *
 * The multiplier scales each stretch's *own* authored duration rather than replacing it, so a
 * 45s Pigeon Pose stays proportionally longer than a 25s wrist stretch at every setting.
 *
 * NOTE for the backup contract (see CLAUDE.md): [PREFS_NAME] is registered in
 * `BackupManager.BACKED_UP_PREFS`. If that entry is removed, these choices are silently lost
 * on a phone swap.
 */
object StretchSettingsManager {

    /** Must stay in sync with `BackupManager.BACKED_UP_PREFS`. */
    const val PREFS_NAME = "stretch_settings"

    private const val KEY_SCOPE = "scope"
    private const val KEY_HOLD_SCALE = "hold_scale"
    private const val KEY_SPECIFIC_AREAS = "specific_areas"

    /** The multipliers offered on the setup screen, in display order. */
    val HOLD_SCALES = listOf(0.5f, 1f, 1.5f, 2f)
    const val DEFAULT_HOLD_SCALE = 1f

    /** A floor rather than a proportion: 0.5× of the shortest stretch is 13s, but this stops a
     *  future shorter stretch from scaling down to a hold nobody can get into position for. */
    private const val MIN_HOLD_SECONDS = 5

    fun scope(context: Context): StretchScope =
        StretchScope.fromPrefValue(prefs(context).getString(KEY_SCOPE, null))

    fun setScope(context: Context, scope: StretchScope) {
        prefs(context).edit().putString(KEY_SCOPE, scope.prefValue).apply()
    }

    fun holdScale(context: Context): Float {
        val stored = prefs(context).getFloat(KEY_HOLD_SCALE, DEFAULT_HOLD_SCALE)
        // A value not on the offered list means a hand-edited or older-build pref; snap back
        // rather than honouring a multiplier no chip can represent.
        return if (stored in HOLD_SCALES) stored else DEFAULT_HOLD_SCALE
    }

    fun setHoldScale(context: Context, scale: Float) {
        prefs(context).edit().putFloat(KEY_HOLD_SCALE, scale).apply()
    }

    /** Keys of [DefaultStretchesHelper.STRETCH_AREAS]. Empty means "nothing remembered yet",
     *  which the setup screen resolves against the muscles the session actually worked. */
    fun specificAreas(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SPECIFIC_AREAS, null).orEmpty()

    fun setSpecificAreas(context: Context, areas: Set<String>) {
        // A defensive copy: SharedPreferences does not copy the set it is handed, and the
        // caller's is built from live chip state.
        prefs(context).edit().putStringSet(KEY_SPECIFIC_AREAS, areas.toSet()).apply()
    }

    /**
     * A stretch's authored duration at the chosen multiplier. Pure and Context-free so the
     * rounding and the floor are unit-testable — the test source set has no Robolectric.
     */
    fun scaledHold(durationSeconds: Int, scale: Float): Int =
        (durationSeconds * scale).roundToInt().coerceAtLeast(MIN_HOLD_SECONDS)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
