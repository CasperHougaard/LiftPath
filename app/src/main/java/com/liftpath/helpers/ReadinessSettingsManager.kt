package com.liftpath.helpers

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

/**
 * Manages persistence of Readiness Calibration settings.
 */
class ReadinessSettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("readiness_settings", Context.MODE_PRIVATE)

    private val gson = Gson()

    /**
     * Readiness calibration settings data class.
     */
    data class ReadinessSettings(
        val recoverySpeedMultiplier: Float = 1.0f, // 0.8 = Slow, 1.0 = Normal, 1.2 = Fast
        val defaultRPE: Float = 7.0f, // Default RPE when missing
        val trainingExperience: TrainingExperience = TrainingExperience.INTERMEDIATE,
        val strictRunBlocking: Boolean = true, // Strict blocking for running
        val ignoreFatigueOnWeekends: Boolean = false // Weekend warrior mode
    ) {
        /**
         * Gets the high threshold based on training experience.
         */
        fun getHighThreshold(): Float {
            return when (trainingExperience) {
                TrainingExperience.NOVICE -> 40f
                TrainingExperience.INTERMEDIATE -> 50f
                TrainingExperience.ADVANCED -> 60f
            }
        }
    }

    enum class TrainingExperience(val displayName: String) {
        NOVICE("Novice"),
        INTERMEDIATE("Intermediate"),
        ADVANCED("Advanced")
    }

    fun getSettings(): ReadinessSettings {
        val json = prefs.getString("settings", null)
        return if (json != null) {
            try {
                gson.fromJson(json, ReadinessSettings::class.java)
            } catch (e: Exception) {
                getDefaultSettings()
            }
        } else {
            getDefaultSettings()
        }
    }

    fun saveSettings(settings: ReadinessSettings) {
        val json = gson.toJson(settings)
        prefs.edit().putString("settings", json).apply()
    }

    fun resetToDefaults() {
        prefs.edit().remove("settings").apply()
    }

    private fun getDefaultSettings(): ReadinessSettings {
        return ReadinessSettings()
    }
}

