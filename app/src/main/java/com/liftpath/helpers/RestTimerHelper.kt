package com.liftpath.helpers

import com.liftpath.models.SetIntent
import java.util.Locale
import kotlin.math.max

/**
 * Central place for rest duration after logging a set — matches LogSetActivity behavior.
 */
object RestTimerHelper {

    /** Formats a duration in seconds as `m:ss` (e.g. 45 -> "0:45", 90 -> "1:30"). */
    fun formatDuration(seconds: Int): String {
        val safe = max(0, seconds)
        return String.format(Locale.US, "%d:%02d", safe / 60, safe % 60)
    }

    /**
     * Formats an aggregate hold time (total time under tension) for stat tiles: `m:ss` below an
     * hour, `Xh MMm` above it. [DurationHelper.formatDuration] is `HH:mm:ss` and too heavy here.
     */
    fun formatHoldTotal(seconds: Int): String {
        val safe = max(0, seconds)
        return if (safe < 3600) {
            formatDuration(safe)
        } else {
            String.format(Locale.US, "%dh %02dm", safe / 3600, (safe % 3600) / 60)
        }
    }

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
