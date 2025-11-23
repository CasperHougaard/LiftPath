package com.liftpath.activities

import android.graphics.drawable.Animatable
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.liftpath.R
import com.liftpath.databinding.ActivityProgressSettingsBinding
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.ProgressSettingsManager
import com.liftpath.helpers.showWithTransparentWindow

class ProgressSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProgressSettingsBinding
    private lateinit var settingsManager: ProgressSettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgressSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Progress Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupBackgroundAnimation()
        settingsManager = ProgressSettingsManager(this)

        setupSpinners()
        loadSettings()
        setupListeners()
    }

    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is Animatable) {
            drawable.start()
        }
    }

    private fun setupSpinners() {
        // No spinners needed anymore (confidence level removed)
    }

    private fun loadSettings() {
        val settings = settingsManager.getSettings()

        binding.etEstimationPeriodMonths.setText(settings.defaultEstimationPeriodMonths.toString())
        binding.etMinimumDataPoints.setText(settings.minimumDataPoints.toString())
        binding.etRecentDataWindowDays.setText(settings.recentDataWindowDays.toString())
        binding.switchShowWarnings.isChecked = settings.showWarnings
    }

    private fun setupListeners() {
        binding.buttonBack.setOnClickListener {
            onBackPressed()
        }

        binding.buttonSave.setOnClickListener {
            saveSettings()
        }

        binding.buttonReset.setOnClickListener {
            DialogHelper.createBuilder(this)
                .setTitle("Reset to Defaults")
                .setMessage("Are you sure you want to reset all progress settings to default values?")
                .setPositiveButton("Reset") { _, _ ->
                    settingsManager.resetToDefaults()
                    loadSettings()
                    Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .showWithTransparentWindow()
        }
    }

    private fun saveSettings() {
        try {
            val estimationPeriod = binding.etEstimationPeriodMonths.text.toString().toIntOrNull() ?: 3
            val minDataPoints = binding.etMinimumDataPoints.text.toString().toIntOrNull() ?: 4
            val recentDataWindow = binding.etRecentDataWindowDays.text.toString().toIntOrNull() ?: 30

            val settings = com.liftpath.helpers.ProgressSettings(
                defaultEstimationPeriodMonths = estimationPeriod.coerceIn(1, 12),
                minimumDataPoints = minDataPoints.coerceIn(2, 20),
                recentDataWindowDays = recentDataWindow.coerceIn(7, 365),
                defaultChartType = settingsManager.getSettings().defaultChartType, // Keep existing value
                estimationMethod = settingsManager.getSettings().estimationMethod, // Keep existing value
                showWarnings = binding.switchShowWarnings.isChecked
            )

            settingsManager.saveSettings(settings)
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

