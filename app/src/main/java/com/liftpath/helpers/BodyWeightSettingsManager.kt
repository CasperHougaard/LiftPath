package com.liftpath.helpers

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

/**
 * User body-weight settings for bodyweight exercises.
 *
 * - [manualWeightKg] / [manualWeightUpdatedAt]: a manually entered body weight (kg) and when it
 *   was last set. Used when Withings is unavailable, or as an override while Withings is stale.
 * - [lastPromptedAt]: last time ANY body-weight prompt was shown; throttles the recurring prompt.
 * - [manualOverrideActive] / [overrideBaselineWithingsMs]: when the latest Withings weighing was
 *   older than two weeks the user may opt to use a manual value "until the next automatic weight".
 *   The override auto-clears once a Withings reading newer than [overrideBaselineWithingsMs] arrives.
 * - [firstBodyweightPromptDone]: the one-time "enter your body weight" prompt on first use of a
 *   bodyweight exercise has been handled.
 */
data class BodyWeightSettings(
    val manualWeightKg: Float? = null,
    val manualWeightUpdatedAt: Long = 0L,
    val lastPromptedAt: Long = 0L,
    val manualOverrideActive: Boolean = false,
    val overrideBaselineWithingsMs: Long = 0L,
    val firstBodyweightPromptDone: Boolean = false
)

class BodyWeightSettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bodyweight_settings", Context.MODE_PRIVATE)

    private val gson = Gson()

    fun getSettings(): BodyWeightSettings {
        val json = prefs.getString("settings", null)
        return if (json != null) {
            try {
                gson.fromJson(json, BodyWeightSettings::class.java)
            } catch (e: Exception) {
                BodyWeightSettings()
            }
        } else {
            BodyWeightSettings()
        }
    }

    fun saveSettings(settings: BodyWeightSettings) {
        prefs.edit().putString("settings", gson.toJson(settings)).apply()
    }

    fun resetToDefaults() {
        prefs.edit().remove("settings").apply()
    }

    private fun round1(v: Float): Float = Math.round(v * 10f) / 10f

    // --- Mutators (read-modify-write so callers don't rebuild the whole object) ---

    /** Plain manual set/update from Settings or a manual prompt. Clears any Withings override. */
    fun setManualWeight(kg: Float) {
        val now = System.currentTimeMillis()
        saveSettings(
            getSettings().copy(
                manualWeightKg = round1(kg),
                manualWeightUpdatedAt = now,
                lastPromptedAt = now,
                manualOverrideActive = false,
                overrideBaselineWithingsMs = 0L
            )
        )
    }

    /**
     * Use a manual value while Withings is stale, until a Withings reading newer than
     * [baselineWithingsMs] arrives (the date of the latest Withings weight at this moment).
     */
    fun setManualOverride(kg: Float, baselineWithingsMs: Long) {
        val now = System.currentTimeMillis()
        saveSettings(
            getSettings().copy(
                manualWeightKg = round1(kg),
                manualWeightUpdatedAt = now,
                lastPromptedAt = now,
                manualOverrideActive = true,
                overrideBaselineWithingsMs = baselineWithingsMs
            )
        )
    }

    /** A newer Withings weight arrived; stop using the manual override and resume auto. */
    fun clearManualOverride() {
        val s = getSettings()
        if (!s.manualOverrideActive) return
        saveSettings(s.copy(manualOverrideActive = false, overrideBaselineWithingsMs = 0L))
    }

    /** Record that a prompt was shown (or dismissed) so we don't re-prompt within the window. */
    fun markPrompted() {
        saveSettings(getSettings().copy(lastPromptedAt = System.currentTimeMillis()))
    }

    fun markFirstBodyweightPromptDone() {
        saveSettings(getSettings().copy(firstBodyweightPromptDone = true))
    }
}
