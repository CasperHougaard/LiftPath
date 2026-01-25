package com.liftpath.helpers

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.liftpath.helpers.ProgressionHelper.ProgressionSettings

class ProgressionSettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("progression_settings", Context.MODE_PRIVATE)

    private val gson = Gson()

    fun getSettings(): ProgressionSettings {
        val json = prefs.getString("settings", null)
        return if (json != null) {
            try {
                val settings = gson.fromJson(json, ProgressionSettings::class.java)
                // Migrate old default values (180/90) to new defaults (150/60) if they match
                val migratedSettings = migrateSettings(settings)
                
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
     * Ensures users transition from 3min/1.5min -> 2.5min/1min automatically.
     * Also migrates heavy/light rest times to intent-based rest times.
     */
    private fun migrateSettings(settings: ProgressionSettings): ProgressionSettings {
        val oldHeavyRest = 180
        val oldLightRest = 90
        val newHeavyRest = 150
        val newLightRest = 60
        
        var newHeavyRestSeconds = settings.heavyRestSeconds
        var newLightRestSeconds = settings.lightRestSeconds
        var needsMigration = false
        
        // If heavy rest is at old default, migrate
        if (settings.heavyRestSeconds == oldHeavyRest) {
            newHeavyRestSeconds = newHeavyRest
            needsMigration = true
        }
        
        // If light rest is at old default, migrate
        if (settings.lightRestSeconds == oldLightRest) {
            newLightRestSeconds = newLightRest
            needsMigration = true
        }
        
        // Migrate heavy/light rest times to intent-based rest times if not already set
        var newStrengthRestSeconds = settings.strengthRestSeconds
        var newBuildRestSeconds = settings.buildRestSeconds
        var newFlushRestSeconds = settings.flushRestSeconds
        
        // If intent-based rest times are at defaults, migrate from heavy/light
        if (settings.strengthRestSeconds == 180 && settings.buildRestSeconds == 90 && settings.flushRestSeconds == 45) {
            newStrengthRestSeconds = settings.heavyRestSeconds
            newBuildRestSeconds = settings.lightRestSeconds
            newFlushRestSeconds = 45  // Default for flush
            needsMigration = true
        }
        
        return if (needsMigration) {
            settings.copy(
                heavyRestSeconds = newHeavyRestSeconds,
                lightRestSeconds = newLightRestSeconds,
                strengthRestSeconds = newStrengthRestSeconds,
                buildRestSeconds = newBuildRestSeconds,
                flushRestSeconds = newFlushRestSeconds
            )
        } else {
            settings
        }
    }

    fun saveSettings(settings: ProgressionSettings) {
        val json = gson.toJson(settings)
        prefs.edit().putString("settings", json).apply()
    }

    fun resetToDefaults() {
        prefs.edit().remove("settings").apply()
    }

    private fun getDefaultSettings(): ProgressionSettings {
        return ProgressionSettings()
    }
}