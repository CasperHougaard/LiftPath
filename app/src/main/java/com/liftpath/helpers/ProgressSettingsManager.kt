package com.liftpath.helpers

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

data class ProgressSettings(
    val defaultEstimationPeriodMonths: Int = 3, // Default 2-3 months (using 3 as middle)
    val minimumDataPoints: Int = 4, // Minimum sessions for qualified estimation
    val recentDataWindowDays: Int = 30, // How recent data must be
    val defaultChartType: String = "weight", // weight, volume, one_rm, avg_weight, avg_rpe
    val estimationMethod: String = "linear_regression", // linear_regression, moving_average, exponential_smoothing
    val showWarnings: Boolean = true // Whether to show data quality warnings
)

class ProgressSettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("progress_settings", Context.MODE_PRIVATE)

    private val gson = Gson()

    fun getSettings(): ProgressSettings {
        val json = prefs.getString("settings", null)
        return if (json != null) {
            try {
                gson.fromJson(json, ProgressSettings::class.java)
            } catch (e: Exception) {
                getDefaultSettings()
            }
        } else {
            getDefaultSettings()
        }
    }

    fun saveSettings(settings: ProgressSettings) {
        val json = gson.toJson(settings)
        prefs.edit().putString("settings", json).apply()
    }

    fun resetToDefaults() {
        prefs.edit().remove("settings").apply()
    }

    private fun getDefaultSettings(): ProgressSettings {
        return ProgressSettings()
    }
}

