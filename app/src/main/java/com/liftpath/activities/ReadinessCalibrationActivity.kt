package com.liftpath.activities

import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.liftpath.databinding.ActivityReadinessCalibrationBinding
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.ReadinessSettingsManager
import com.liftpath.helpers.showWithTransparentWindow

class ReadinessCalibrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReadinessCalibrationBinding
    private lateinit var settingsManager: ReadinessSettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReadinessCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = ReadinessSettingsManager(this)

        setupBackgroundAnimation()
        setupSpinner()
        loadSettings()
        setupListeners()
    }

    override fun onPause() {
        super.onPause()
        // Auto-save settings when leaving the activity
        saveSettings()
    }

    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is Animatable) {
            drawable.start()
        }
    }

    private fun setupSpinner() {
        val experiences = ReadinessSettingsManager.TrainingExperience.values()
        val displayNames = experiences.map { it.displayName }.toTypedArray()

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            displayNames
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTrainingExperience.adapter = adapter
    }

    private fun loadSettings() {
        val settings = settingsManager.getSettings()

        // Recovery Speed (0 = Slow/0.8, 1 = Normal/1.0, 2 = Fast/1.2)
        val recoverySpeedIndex = when (settings.recoverySpeedMultiplier) {
            0.8f -> 0
            1.2f -> 2
            else -> 1 // Default to Normal
        }
        binding.seekbarRecoverySpeed.progress = recoverySpeedIndex
        updateRecoverySpeedLabel(recoverySpeedIndex)

        // Default RPE (1-10, stored as 1.0-10.0, default 7.0)
        val rpeProgress = (settings.defaultRPE - 1f).toInt().coerceIn(0, 9)
        binding.seekbarDefaultRpe.progress = rpeProgress
        updateDefaultRpeLabel(rpeProgress)

        // Training Experience
        val experiences = ReadinessSettingsManager.TrainingExperience.values()
        val selectedIndex = experiences.indexOf(settings.trainingExperience)
        if (selectedIndex >= 0) {
            binding.spinnerTrainingExperience.setSelection(selectedIndex)
        }

        // Toggles
        binding.switchStrictRunBlocking.isChecked = settings.strictRunBlocking
        binding.switchIgnoreWeekends.isChecked = settings.ignoreFatigueOnWeekends
    }

    private fun setupListeners() {
        binding.buttonBack.setOnClickListener {
            finish()
        }

        binding.seekbarRecoverySpeed.setOnSeekBarChangeListener(object :
            android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateRecoverySpeedLabel(progress)
                    saveSettings()
                }
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        binding.seekbarDefaultRpe.setOnSeekBarChangeListener(object :
            android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateDefaultRpeLabel(progress)
                    saveSettings()
                }
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        binding.spinnerTrainingExperience.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                saveSettings()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.switchStrictRunBlocking.setOnCheckedChangeListener { _, _ ->
            saveSettings()
        }

        binding.switchIgnoreWeekends.setOnCheckedChangeListener { _, _ ->
            saveSettings()
        }

        binding.buttonReset.setOnClickListener {
            showResetDialog()
        }
    }

    private fun updateRecoverySpeedLabel(progress: Int) {
        val label = when (progress) {
            0 -> "Slow (Cautious)"
            2 -> "Fast (Athlete)"
            else -> "Normal"
        }
        binding.textRecoverySpeedValue.text = label
    }

    private fun updateDefaultRpeLabel(progress: Int) {
        val rpe = (progress + 1).toFloat()
        binding.textDefaultRpeValue.text = String.format("%.1f", rpe)
    }

    private fun saveSettings() {
        val recoverySpeedMultiplier = when (binding.seekbarRecoverySpeed.progress) {
            0 -> 0.8f
            2 -> 1.2f
            else -> 1.0f
        }

        val defaultRPE = (binding.seekbarDefaultRpe.progress + 1).toFloat()

        val experiences = ReadinessSettingsManager.TrainingExperience.values()
        val selectedExperience = experiences[binding.spinnerTrainingExperience.selectedItemPosition]

        val settings = ReadinessSettingsManager.ReadinessSettings(
            recoverySpeedMultiplier = recoverySpeedMultiplier,
            defaultRPE = defaultRPE,
            trainingExperience = selectedExperience,
            strictRunBlocking = binding.switchStrictRunBlocking.isChecked,
            ignoreFatigueOnWeekends = binding.switchIgnoreWeekends.isChecked
        )

        settingsManager.saveSettings(settings)
    }

    private fun showResetDialog() {
        DialogHelper.createBuilder(this)
            .setTitle("Reset to Recommended")
            .setMessage("This will restore all readiness calibration settings to their recommended defaults. Continue?")
            .setPositiveButton("Reset") { _, _ ->
                resetToDefaults()
            }
            .setNegativeButton("Cancel", null)
            .showWithTransparentWindow()
    }

    private fun resetToDefaults() {
        settingsManager.resetToDefaults()
        loadSettings()
        Toast.makeText(this, "Settings reset to recommended defaults", Toast.LENGTH_SHORT).show()
    }
}

