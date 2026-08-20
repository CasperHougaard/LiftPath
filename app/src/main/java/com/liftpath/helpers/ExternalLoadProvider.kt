package com.liftpath.helpers

import android.content.Context

/**
 * The one place that answers "what non-lifting load is on the body right now?".
 *
 * LiftPath can hear about the same cardio session twice — once from Health Connect directly, once
 * via TriPath, which imported it from Health Connect too. Both carry the same
 * `ExerciseSessionRecord.metadata.id`, so the duplicate is exact and this class resolves it in
 * favour of TriPath's version, which knows the session's intensity rather than just its length.
 *
 * Every consumer goes through here rather than calling [HealthConnectHelper.getStoredActivities]
 * directly, so the merge rule exists once.
 */
object ExternalLoadProvider {

    private const val HEALTH_CONNECT_PREFS = "health_connect_settings"
    private const val HEALTH_CONNECT_ENABLED_KEY = "use_health_connect_data"

    fun isHealthConnectEnabled(context: Context): Boolean =
        context.getSharedPreferences(HEALTH_CONNECT_PREFS, Context.MODE_PRIVATE)
            .getBoolean(HEALTH_CONNECT_ENABLED_KEY, false)

    /**
     * Merged external load for the fatigue timeline. Returns an empty list when neither source is
     * switched on, which is the pre-integration behaviour.
     */
    fun getExternalActivities(context: Context): List<ExternalActivity> {
        val triPathActivities = if (TriPathConnection.isActive(context)) {
            TriPathStorageHelper(context).read().workouts
                .mapNotNull { TriPathFatigueMapper.toExternalActivity(it) }
        } else {
            emptyList()
        }

        val healthConnectActivities = if (isHealthConnectEnabled(context)) {
            try {
                HealthConnectHelper.getStoredActivities(context)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        if (triPathActivities.isEmpty()) return healthConnectActivities

        // Same Health Connect record id on both sides means the same session. TriPath's row wins:
        // it carries TSS and heart-rate zones, where LiftPath's own copy only has a duration.
        val triPathIds = triPathActivities.map { it.id }.toSet()
        return triPathActivities + healthConnectActivities.filter { it.id !in triPathIds }
    }

    /**
     * Recovery and form modifiers from TriPath, or [TriPathFatigueMapper.NEUTRAL] when it is not
     * connected — in which case readiness maths is bit-for-bit what it was before.
     */
    fun triPathModifier(context: Context): TriPathFatigueMapper.Modifier {
        if (!TriPathConnection.isActive(context)) return TriPathFatigueMapper.NEUTRAL
        return TriPathFatigueMapper.modifierFrom(TriPathStorageHelper(context).read().days)
    }

    /**
     * Readiness config with TriPath's recovery and threshold modifiers folded in. The single call
     * every screen should use in place of `ReadinessConfig.fromSettings(settings)`.
     */
    fun readinessConfig(
        context: Context,
        settings: ReadinessSettingsManager.ReadinessSettings
    ): ReadinessConfig = ReadinessConfig.fromSettings(settings, triPathModifier(context))
}
