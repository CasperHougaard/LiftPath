package com.lilfitness.helpers

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

class ProgressionSettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("progression_settings", Context.MODE_PRIVATE)

    private val gson = Gson()

    fun getSettings(): ProgressionHelper.ProgressionSettings {
        val json = prefs.getString("settings", null)
        return if (json != null) {
            try {
                val settings = gson.fromJson(json, ProgressionHelper.ProgressionSettings::class.java)
                // Migrate old default values to new defaults if they match
                val migratedSettings = migrateSettings(settings)
                // Save migrated settings if they were changed
                if (migratedSettings != settings) {
                    saveSettings(migratedSettings)
                }
                migratedSettings
            } catch (e: Exception) {
                getDefaultSettings()
            }
        } else {
            getDefaultSettings()
        }
    }
    
    /**
     * Migrates settings from old default values to new default values.
     * This ensures users with saved old defaults get updated to new defaults.
     */
    private fun migrateSettings(settings: ProgressionHelper.ProgressionSettings): ProgressionHelper.ProgressionSettings {
        // Check if rest timer values are at old defaults and migrate to new defaults
        val oldHeavyRest = 180
        val oldLightRest = 90
        val newHeavyRest = 150
        val newLightRest = 60
        
        var needsMigration = false
        var newHeavyRestSeconds = settings.heavyRestSeconds
        var newLightRestSeconds = settings.lightRestSeconds
        
        // If heavy rest is at old default, migrate to new default
        if (settings.heavyRestSeconds == oldHeavyRest) {
            newHeavyRestSeconds = newHeavyRest
            needsMigration = true
        }
        
        // If light rest is at old default, migrate to new default
        if (settings.lightRestSeconds == oldLightRest) {
            newLightRestSeconds = newLightRest
            needsMigration = true
        }
        
        // Return migrated settings if changes were made, otherwise return original
        return if (needsMigration) {
            settings.copy(
                heavyRestSeconds = newHeavyRestSeconds,
                lightRestSeconds = newLightRestSeconds
            )
        } else {
            settings
        }
    }

    fun saveSettings(settings: ProgressionHelper.ProgressionSettings) {
        val json = gson.toJson(settings)
        prefs.edit().putString("settings", json).apply()
    }

    fun resetToDefaults() {
        prefs.edit().remove("settings").apply()
    }

    private fun getDefaultSettings(): ProgressionHelper.ProgressionSettings {
        return ProgressionHelper.ProgressionSettings()
    }
}

