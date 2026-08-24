package com.liftpath.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.liftpath.R
import com.liftpath.databinding.ActivityProgressSettingsBinding
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.ProgressSettings
import com.liftpath.helpers.ProgressSettingsManager
import com.liftpath.helpers.showWithTransparentWindow

/**
 * 1RM estimation settings, reached from Settings > Training.
 *
 * ProgressExercisesFragment reads these on every chart build, so they are live even though
 * this screen had no entry point for a while — see the layout's header comment.
 *
 * No action bar to title: Theme.LiftPath.Base is NoActionBar and this activity never calls
 * setSupportActionBar, so the header in the layout is the only title.
 */
class ProgressSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProgressSettingsBinding
    private lateinit var settingsManager: ProgressSettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgressSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = ProgressSettingsManager(this)

        loadSettings()
        setupListeners()
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
            onBackPressedDispatcher.onBackPressed()
        }

        binding.buttonSave.setOnClickListener { saveSettings() }

        binding.buttonReset.setOnClickListener {
            DialogHelper.createBuilder(this)
                .setTitle(R.string.dialog_title_reset_to_defaults)
                .setMessage(R.string.progress_settings_reset_message)
                .setPositiveButton(R.string.btn_reset_defaults) { _, _ ->
                    settingsManager.resetToDefaults()
                    loadSettings()
                    toast(R.string.progress_settings_saved)
                }
                .setNegativeButton(R.string.button_cancel, null)
                .showWithTransparentWindow()
        }
    }

    private fun saveSettings() {
        // Existing values are the fallback for a blank or unparseable field, so a stray edit
        // cannot silently reset a setting to the data-class default.
        val current = settingsManager.getSettings()

        settingsManager.saveSettings(
            ProgressSettings(
                defaultEstimationPeriodMonths = binding.etEstimationPeriodMonths.intOr(
                    current.defaultEstimationPeriodMonths
                ).coerceIn(1, 12),
                minimumDataPoints = binding.etMinimumDataPoints.intOr(
                    current.minimumDataPoints
                ).coerceIn(2, 20),
                recentDataWindowDays = binding.etRecentDataWindowDays.intOr(
                    current.recentDataWindowDays
                ).coerceIn(7, 365),
                defaultChartType = current.defaultChartType,
                estimationMethod = current.estimationMethod,
                showWarnings = binding.switchShowWarnings.isChecked
            )
        )
        toast(R.string.progress_settings_saved)
        finish()
    }

    private fun android.widget.EditText.intOr(fallback: Int): Int =
        text.toString().trim().toIntOrNull() ?: fallback

    private fun toast(resId: Int) =
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}
