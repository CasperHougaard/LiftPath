package com.liftpath.helpers

import android.content.Context

/**
 * Single source of truth for resolving the user's current body weight and for deciding when to
 * prompt for it. Precedence:
 *
 *  1. If Withings (Health Connect) is enabled and has a usable weight:
 *       - a reading newer than the active manual-override baseline clears the override (auto resumes);
 *       - if the latest reading is fresh (< 2 weeks) it is used automatically;
 *       - if it is stale (>= 2 weeks) a manual override is used when active, otherwise the (stale)
 *         Withings reading is still returned (a real measurement beats nothing).
 *  2. Otherwise the manually entered weight is used (may be null if never set).
 *
 * All thresholds are simple epoch-millis deltas, so no calendar/timezone handling is needed.
 */
object BodyWeightHelper {

    /** "Every 2 weeks". */
    const val TWO_WEEKS_MS = 14L * 24 * 60 * 60 * 1000

    /** Body weight is always stored/used rounded to 1 decimal place. */
    fun round1(v: Float): Float = Math.round(v * 10f) / 10f

    enum class BodyWeightSource { WITHINGS, MANUAL, NONE }

    data class ResolvedBodyWeight(val kg: Float?, val source: BodyWeightSource)

    /** What the app-open check should do. */
    enum class BodyWeightPromptType { NONE, MANUAL_RECURRING, WITHINGS_STALE, NEEDS_INITIAL }

    /** True if Withings sync is enabled and Health Connect is available on this device. */
    fun isWithingsEnabled(context: Context): Boolean {
        val enabled = context
            .getSharedPreferences("health_connect_settings", Context.MODE_PRIVATE)
            .getBoolean("use_health_connect_data", false)
        return enabled && WithingsHealthConnectHelper.isAvailable(context)
    }

    /** Latest synced Withings weight and its timestamp, or null if none has a weight value. */
    fun latestWithingsWeight(context: Context): Pair<Float, Long>? {
        val latest = WithingsStorageHelper(context).read().entries
            .filter { it.weightKg != null }
            .maxByOrNull { it.dateMs }
            ?: return null
        return round1(latest.weightKg!!.toFloat()) to latest.dateMs
    }

    /**
     * Resolve the body weight to use right now (no UI). May clear a stale manual override as a
     * side effect when a newer Withings reading has arrived.
     */
    fun resolveBodyWeight(context: Context): ResolvedBodyWeight {
        val manager = BodyWeightSettingsManager(context)
        var settings = manager.getSettings()
        val now = System.currentTimeMillis()
        val withings = if (isWithingsEnabled(context)) latestWithingsWeight(context) else null

        if (withings != null) {
            val (wKg, wDateMs) = withings
            // A reading newer than the override baseline means auto can resume.
            if (settings.manualOverrideActive && wDateMs > settings.overrideBaselineWithingsMs) {
                manager.clearManualOverride()
                settings = manager.getSettings()
            }
            val fresh = (now - wDateMs) < TWO_WEEKS_MS
            if (fresh) return ResolvedBodyWeight(wKg, BodyWeightSource.WITHINGS)
            // Stale Withings:
            return if (settings.manualOverrideActive && settings.manualWeightKg != null) {
                ResolvedBodyWeight(settings.manualWeightKg, BodyWeightSource.MANUAL)
            } else {
                ResolvedBodyWeight(wKg, BodyWeightSource.WITHINGS)
            }
        }

        // No Withings: fall back to manual.
        return if (settings.manualWeightKg != null) {
            ResolvedBodyWeight(settings.manualWeightKg, BodyWeightSource.MANUAL)
        } else {
            ResolvedBodyWeight(null, BodyWeightSource.NONE)
        }
    }

    /** Convenience: the body weight to snapshot onto a logged set (or null if unknown). */
    fun getCurrentBodyweightKg(context: Context): Float? = resolveBodyWeight(context).kg

    /** Decide whether the app-open check should prompt the user (no UI; pure read). */
    fun evaluateBodyWeightPrompt(context: Context): BodyWeightPromptType {
        val settings = BodyWeightSettingsManager(context).getSettings()
        val now = System.currentTimeMillis()
        val withings = if (isWithingsEnabled(context)) latestWithingsWeight(context) else null

        if (withings != null) {
            val (_, wDateMs) = withings
            val stale = (now - wDateMs) >= TWO_WEEKS_MS
            if (!stale) return BodyWeightPromptType.NONE
            // Already chose an override for this stale reading -> don't nag again.
            if (settings.manualOverrideActive && wDateMs <= settings.overrideBaselineWithingsMs) {
                return BodyWeightPromptType.NONE
            }
            return if (now - settings.lastPromptedAt >= TWO_WEEKS_MS) {
                BodyWeightPromptType.WITHINGS_STALE
            } else {
                BodyWeightPromptType.NONE
            }
        }

        // Manual flow. Initial entry is handled by the exercise-add hook, not app-open.
        if (settings.manualWeightKg == null) return BodyWeightPromptType.NONE
        val lastRelevant = maxOf(settings.manualWeightUpdatedAt, settings.lastPromptedAt)
        return if (now - lastRelevant >= TWO_WEEKS_MS) {
            BodyWeightPromptType.MANUAL_RECURRING
        } else {
            BodyWeightPromptType.NONE
        }
    }

    /** Used by the exercise-add hook: prompt for an initial weight only if none is known yet. */
    fun needsInitialBodyweight(context: Context): Boolean {
        val settings = BodyWeightSettingsManager(context).getSettings()
        return getCurrentBodyweightKg(context) == null && !settings.firstBodyweightPromptDone
    }
}
