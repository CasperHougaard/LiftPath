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
                gson.fromJson(json, ProgressionHelper.ProgressionSettings::class.java)
            } catch (e: Exception) {
                getDefaultSettings()
            }
        } else {
            getDefaultSettings()
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

