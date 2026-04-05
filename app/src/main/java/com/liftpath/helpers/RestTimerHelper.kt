package com.liftpath.helpers

import com.liftpath.models.SetIntent
import kotlin.math.max

/**
 * Central place for rest duration after logging a set — matches LogSetActivity behavior.
 */
object RestTimerHelper {

    fun restSecondsAfterLoggedSet(
        settings: ProgressionHelper.ProgressionSettings,
        setIntent: SetIntent,
        rpe: Float?,
        restSecondsOverride: Int?
    ): Int {
        val override = restSecondsOverride?.takeIf { it > 0 }
        return if (override != null) {
            override
        } else {
            var base = when (setIntent) {
                SetIntent.STRENGTH -> settings.strengthRestSeconds
                SetIntent.BUILD -> settings.buildRestSeconds
                SetIntent.FLUSH -> settings.flushRestSeconds
                SetIntent.WARMUP -> settings.flushRestSeconds
                else -> settings.buildRestSeconds
            }
            if (settings.rpeAdjustmentEnabled && rpe != null) {
                if (rpe >= settings.rpeHighThreshold) {
                    base += settings.rpeHighBonusSeconds
                }
                val suggestedRpe = ProgressionHelper.getTargetRpe(setIntent, settings)
                val rpeDifference = rpe - suggestedRpe
                if (rpeDifference >= settings.rpeDeviationThreshold) {
                    base += settings.rpePositiveAdjustmentSeconds
                } else if (rpeDifference <= -settings.rpeDeviationThreshold) {
                    base = max(0, base - settings.rpeNegativeAdjustmentSeconds)
                }
            }
            base
        }
    }
}
