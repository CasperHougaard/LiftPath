package com.lilfitness.activities

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lilfitness.R
import com.lilfitness.databinding.ActivityProgressionSettingsBinding
import android.widget.ArrayAdapter
import android.widget.Spinner
import com.lilfitness.helpers.DialogHelper
import com.lilfitness.helpers.ProgressionHelper
import com.lilfitness.helpers.ProgressionSettingsManager
import com.lilfitness.helpers.showWithTransparentWindow
import com.lilfitness.models.UserLevel

class ProgressionSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProgressionSettingsBinding
    private lateinit var settingsManager: ProgressionSettingsManager
    
    // Track expanded state for each section
    private val expandedSections = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgressionSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Progression Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Setup background animation
        setupBackgroundAnimation()

        settingsManager = ProgressionSettingsManager(this)

        // Initialize all sections as collapsed by default
        // Initialize icon rotations to match collapsed state (270 degrees = collapsed)
        binding.iconExpandCore.rotation = 270f
        binding.iconExpandRestTimer.rotation = 270f
        binding.iconExpandDeload.rotation = 270f
        binding.iconExpandPlateau.rotation = 270f
        
        // Set content views to collapsed initially
        collapseViewImmediate(binding.contentCoreSettings)
        collapseViewImmediate(binding.contentRestTimer)
        collapseViewImmediate(binding.contentDeload)
        collapseViewImmediate(binding.contentPlateau)

        setupUserLevelSpinner()
        loadSettings()
        setupListeners()
        setupExpandCollapseListeners()
    }
    
    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is android.graphics.drawable.Animatable) {
            drawable.start()
        }
    }
    
    private fun setupUserLevelSpinner() {
        val userLevels = UserLevel.values()
        val displayNames = userLevels.map { it.displayName }.toTypedArray()
        
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            displayNames
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerUserLevel.adapter = adapter
        
        // Update RPE suggestions when user level changes
        binding.spinnerUserLevel.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                updateRpeSuggestions(userLevels[position])
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }
    
    private fun updateRpeSuggestions(userLevel: UserLevel) {
        val settings = settingsManager.getSettings()
        
        val heavyRpe = when (userLevel) {
            UserLevel.NOVICE -> "8.0"
            UserLevel.INTERMEDIATE -> "8.5"
        }
        val lightRpe = when (userLevel) {
            UserLevel.NOVICE -> "7.0"
            UserLevel.INTERMEDIATE -> "7.5"
        }
        
        val levelName = when (userLevel) {
            UserLevel.NOVICE -> "Novice"
            UserLevel.INTERMEDIATE -> "Intermediate"
        }
        
        val text = "Suggested RPE values (for $levelName):\n" +
                "• Heavy workouts: RPE $heavyRpe\n" +
                "• Light workouts: RPE $lightRpe\n\n" +
                "These are auto-filled when logging sets. Timer adjusts based on your logged RPE vs suggested."
        
        binding.textRpeSuggestions.text = text
        
        // Update timer calculation example
        updateTimerCalculationInfo()
    }
    
    private fun updateTimerCalculationInfo() {
        try {
            val settings = settingsManager.getSettings()
            
            // Get current values from fields (or use settings if fields are empty/invalid)
            val highThreshold = binding.etRpeThreshold.text.toString().toFloatOrNull() ?: settings.rpeHighThreshold
            val highBonus = binding.etRpeBonus.text.toString().toIntOrNull() ?: settings.rpeHighBonusSeconds
            val deviationThreshold = binding.etRpeDeviationThreshold.text.toString().toFloatOrNull() ?: settings.rpeDeviationThreshold
            val positiveAdjustment = binding.etRpePositiveAdjustment.text.toString().toIntOrNull() ?: settings.rpePositiveAdjustmentSeconds
            val negativeAdjustment = binding.etRpeNegativeAdjustment.text.toString().toIntOrNull() ?: settings.rpeNegativeAdjustmentSeconds
            
            val calculationText = "1. Start with base rest time (Heavy/Light/Custom)\n" +
                    "2. If RPE ≥ ${highThreshold}: +${highBonus}s\n" +
                    "3. If logged RPE ≥ suggested+${deviationThreshold}: +${positiveAdjustment}s\n" +
                    "4. If logged RPE ≤ suggested-${deviationThreshold}: -${negativeAdjustment}s"
            
            binding.textTimerCalculation.text = calculationText
        } catch (e: Exception) {
            // Ignore errors, keep default text
        }
    }

    private fun loadSettings() {
        val settings = settingsManager.getSettings()

        // Core settings
        // Set user level spinner selection
        val userLevels = UserLevel.values()
        val selectedIndex = userLevels.indexOf(settings.userLevel)
        if (selectedIndex >= 0) {
            binding.spinnerUserLevel.setSelection(selectedIndex)
            // Update RPE suggestions based on loaded level
            updateRpeSuggestions(settings.userLevel)
        }
        
        binding.etLookbackCount.setText(settings.lookbackCount.toString())
        binding.etIncreaseStep.setText(settings.increaseStep.toString())
        binding.etSmallStep.setText(settings.smallStep.toString())

        // Deload settings
        binding.etDeloadThreshold.setText(settings.deloadThreshold.toString())
        binding.etDeloadRPE.setText(settings.deloadRPEThreshold.toString())
        binding.etDeloadPercent.setText((settings.deloadPercent * 100).toInt().toString())

        // Plateau settings
        binding.etPlateauSessions.setText(settings.plateauSessionCount.toString())
        binding.etPlateauRPE.setText(settings.plateauRPEMax.toString())
        binding.etPlateauBoost.setText(settings.plateauBoost.toString())

        // Recommended sets and reps
        binding.etHeavySets.setText(settings.heavySets.toString())
        binding.etHeavyReps.setText(settings.heavyReps.toString())
        binding.etLightSets.setText(settings.lightSets.toString())
        binding.etLightReps.setText(settings.lightReps.toString())
        
        // Rest timer settings
        binding.switchRestTimer.isChecked = settings.restTimerEnabled
        binding.etHeavyRest.setText(settings.heavyRestSeconds.toString())
        binding.etLightRest.setText(settings.lightRestSeconds.toString())
        binding.etCustomRest.setText(settings.customRestSeconds.toString())
        binding.switchRpeAdjustment.isChecked = settings.rpeAdjustmentEnabled
        binding.etRpeThreshold.setText(settings.rpeHighThreshold.toString())
        binding.etRpeBonus.setText(settings.rpeHighBonusSeconds.toString())
        
        // RPE deviation adjustment settings
        binding.etRpeDeviationThreshold.setText(settings.rpeDeviationThreshold.toString())
        binding.etRpePositiveAdjustment.setText(settings.rpePositiveAdjustmentSeconds.toString())
        binding.etRpeNegativeAdjustment.setText(settings.rpeNegativeAdjustmentSeconds.toString())
        
        // Show/hide rest timer settings based on toggle
        binding.layoutRestTimerSettings.visibility = if (settings.restTimerEnabled) android.view.View.VISIBLE else android.view.View.GONE
        binding.layoutRpeAdjustmentSettings.visibility = if (settings.rpeAdjustmentEnabled) android.view.View.VISIBLE else android.view.View.GONE
        
        // Update info card with loaded settings
        updateTimerCalculationInfo()
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            saveSettings()
        }

        binding.btnResetDefaults.setOnClickListener {
            showResetDialog()
        }
        
        // Toggle rest timer settings visibility
        binding.switchRestTimer.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutRestTimerSettings.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }
        
        // Toggle RPE adjustment settings visibility
        binding.switchRpeAdjustment.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutRpeAdjustmentSettings.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }
        
        // Update info card when RPE deviation settings change
        binding.etRpeDeviationThreshold.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                updateTimerCalculationInfo()
            }
        }
        binding.etRpePositiveAdjustment.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                updateTimerCalculationInfo()
            }
        }
        binding.etRpeNegativeAdjustment.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                updateTimerCalculationInfo()
            }
        }
        binding.etRpeThreshold.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                updateTimerCalculationInfo()
            }
        }
        binding.etRpeBonus.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                updateTimerCalculationInfo()
            }
        }
    }
    
    private fun setupExpandCollapseListeners() {
        // Core Settings
        binding.headerCoreSettings.setOnClickListener {
            toggleSection("core", binding.contentCoreSettings, binding.iconExpandCore)
        }
        
        // Rest Timer
        binding.headerRestTimer.setOnClickListener {
            toggleSection("rest_timer", binding.contentRestTimer, binding.iconExpandRestTimer)
        }
        
        // Deload
        binding.headerDeload.setOnClickListener {
            toggleSection("deload", binding.contentDeload, binding.iconExpandDeload)
        }
        
        // Plateau
        binding.headerPlateau.setOnClickListener {
            toggleSection("plateau", binding.contentPlateau, binding.iconExpandPlateau)
        }
    }
    
    private fun toggleSection(sectionId: String, contentView: ViewGroup, iconView: View) {
        val isExpanded = expandedSections.contains(sectionId)
        
        if (isExpanded) {
            // Collapse
            collapseView(contentView, iconView)
            expandedSections.remove(sectionId)
        } else {
            // Expand
            expandView(contentView, iconView)
            expandedSections.add(sectionId)
        }
    }
    
    private fun expandView(view: ViewGroup, iconView: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val targetHeight = view.measuredHeight
        
        view.layoutParams.height = 0
        view.visibility = View.VISIBLE
        
        val animator = ValueAnimator.ofInt(0, targetHeight)
        animator.interpolator = DecelerateInterpolator()
        animator.duration = 300
        animator.addUpdateListener { animation ->
            view.layoutParams.height = animation.animatedValue as Int
            view.requestLayout()
        }
        animator.doOnEnd {
            view.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        animator.start()
        
        // Rotate icon
        ObjectAnimator.ofFloat(iconView, "rotation", 270f, 90f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
        }.start()
    }
    
    private fun collapseView(view: ViewGroup, iconView: View) {
        val initialHeight = view.height
        
        val animator = ValueAnimator.ofInt(initialHeight, 0)
        animator.interpolator = DecelerateInterpolator()
        animator.duration = 300
        animator.addUpdateListener { animation ->
            view.layoutParams.height = animation.animatedValue as Int
            view.requestLayout()
        }
        animator.doOnEnd {
            view.visibility = View.GONE
            view.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        animator.start()
        
        // Rotate icon
        ObjectAnimator.ofFloat(iconView, "rotation", 90f, 270f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
        }.start()
    }
    
    private fun collapseViewImmediate(view: ViewGroup) {
        view.visibility = View.GONE
        view.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    private fun saveSettings() {
        try {
            // Get selected user level from spinner
            val selectedUserLevel = UserLevel.values()[binding.spinnerUserLevel.selectedItemPosition]
            
            val settings = ProgressionHelper.ProgressionSettings(
                userLevel = selectedUserLevel,
                lookbackCount = binding.etLookbackCount.text.toString().toInt(),
                increaseStep = binding.etIncreaseStep.text.toString().toFloat(),
                smallStep = binding.etSmallStep.text.toString().toFloat(),
                
                deloadThreshold = binding.etDeloadThreshold.text.toString().toInt(),
                deloadRPEThreshold = binding.etDeloadRPE.text.toString().toFloat(),
                deloadPercent = binding.etDeloadPercent.text.toString().toFloat() / 100f,
                
                plateauSessionCount = binding.etPlateauSessions.text.toString().toInt(),
                plateauRPEMax = binding.etPlateauRPE.text.toString().toFloat(),
                plateauBoost = binding.etPlateauBoost.text.toString().toFloat(),
                
                heavySets = binding.etHeavySets.text.toString().toInt(),
                heavyReps = binding.etHeavyReps.text.toString().toInt(),
                lightSets = binding.etLightSets.text.toString().toInt(),
                lightReps = binding.etLightReps.text.toString().toInt(),
                
                restTimerEnabled = binding.switchRestTimer.isChecked,
                heavyRestSeconds = binding.etHeavyRest.text.toString().toInt(),
                lightRestSeconds = binding.etLightRest.text.toString().toInt(),
                customRestSeconds = binding.etCustomRest.text.toString().toInt(),
                rpeAdjustmentEnabled = binding.switchRpeAdjustment.isChecked,
                rpeHighThreshold = binding.etRpeThreshold.text.toString().toFloat(),
                rpeHighBonusSeconds = binding.etRpeBonus.text.toString().toInt(),
                rpeDeviationThreshold = binding.etRpeDeviationThreshold.text.toString().toFloat(),
                rpePositiveAdjustmentSeconds = binding.etRpePositiveAdjustment.text.toString().toInt(),
                rpeNegativeAdjustmentSeconds = binding.etRpeNegativeAdjustment.text.toString().toInt()
            )

            // Validate settings
            if (!validateSettings(settings)) {
                return
            }

            settingsManager.saveSettings(settings)
            Toast.makeText(this, getString(R.string.toast_settings_saved), Toast.LENGTH_SHORT).show()
            finish()

        } catch (e: NumberFormatException) {
            Toast.makeText(this, getString(R.string.toast_invalid_input), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_error_saving_settings, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun validateSettings(settings: ProgressionHelper.ProgressionSettings): Boolean {
        when {
            settings.lookbackCount < 1 || settings.lookbackCount > 10 -> {
                Toast.makeText(this, getString(R.string.validation_lookback_sessions), Toast.LENGTH_LONG).show()
                return false
            }
            settings.increaseStep < 0 || settings.increaseStep > 10 -> {
                Toast.makeText(this, getString(R.string.validation_increase_step), Toast.LENGTH_LONG).show()
                return false
            }
            settings.smallStep < 0 || settings.smallStep > 5 -> {
                Toast.makeText(this, getString(R.string.validation_small_step), Toast.LENGTH_LONG).show()
                return false
            }
            settings.deloadThreshold < 1 || settings.deloadThreshold > 10 -> {
                Toast.makeText(this, getString(R.string.validation_deload_threshold), Toast.LENGTH_LONG).show()
                return false
            }
            settings.deloadRPEThreshold < 6.0f || settings.deloadRPEThreshold > 10.0f -> {
                Toast.makeText(this, getString(R.string.validation_deload_rpe), Toast.LENGTH_LONG).show()
                return false
            }
            settings.deloadPercent < 0.5f || settings.deloadPercent > 1.0f -> {
                Toast.makeText(this, getString(R.string.validation_deload_percent), Toast.LENGTH_LONG).show()
                return false
            }
            settings.plateauSessionCount < 2 || settings.plateauSessionCount > 10 -> {
                Toast.makeText(this, getString(R.string.validation_plateau_sessions), Toast.LENGTH_LONG).show()
                return false
            }
            settings.plateauRPEMax < 6.0f || settings.plateauRPEMax > 10.0f -> {
                Toast.makeText(this, getString(R.string.validation_plateau_rpe), Toast.LENGTH_LONG).show()
                return false
            }
            settings.plateauBoost < 1.0f || settings.plateauBoost > 3.0f -> {
                Toast.makeText(this, getString(R.string.validation_plateau_boost), Toast.LENGTH_LONG).show()
                return false
            }
            settings.heavySets < 1 || settings.heavySets > 10 -> {
                Toast.makeText(this, getString(R.string.validation_heavy_sets), Toast.LENGTH_LONG).show()
                return false
            }
            settings.heavyReps < 1 || settings.heavyReps > 50 -> {
                Toast.makeText(this, getString(R.string.validation_heavy_reps), Toast.LENGTH_LONG).show()
                return false
            }
            settings.lightSets < 1 || settings.lightSets > 10 -> {
                Toast.makeText(this, getString(R.string.validation_light_sets), Toast.LENGTH_LONG).show()
                return false
            }
            settings.lightReps < 1 || settings.lightReps > 50 -> {
                Toast.makeText(this, getString(R.string.validation_light_reps), Toast.LENGTH_LONG).show()
                return false
            }
            settings.heavyRestSeconds < 5 || settings.heavyRestSeconds > 600 -> {
                Toast.makeText(this, getString(R.string.validation_heavy_rest), Toast.LENGTH_LONG).show()
                return false
            }
            settings.lightRestSeconds < 5 || settings.lightRestSeconds > 600 -> {
                Toast.makeText(this, getString(R.string.validation_light_rest), Toast.LENGTH_LONG).show()
                return false
            }
            settings.customRestSeconds < 5 || settings.customRestSeconds > 600 -> {
                Toast.makeText(this, getString(R.string.validation_custom_rest), Toast.LENGTH_LONG).show()
                return false
            }
            settings.rpeHighThreshold < 6.0f || settings.rpeHighThreshold > 10.0f -> {
                Toast.makeText(this, getString(R.string.validation_rpe_threshold), Toast.LENGTH_LONG).show()
                return false
            }
            settings.rpeHighBonusSeconds < 0 || settings.rpeHighBonusSeconds > 300 -> {
                Toast.makeText(this, getString(R.string.validation_rpe_bonus_rest), Toast.LENGTH_LONG).show()
                return false
            }
            settings.rpeDeviationThreshold < 0.1f || settings.rpeDeviationThreshold > 5.0f -> {
                Toast.makeText(this, "RPE deviation threshold must be between 0.1 and 5.0", Toast.LENGTH_LONG).show()
                return false
            }
            settings.rpePositiveAdjustmentSeconds < 0 || settings.rpePositiveAdjustmentSeconds > 300 -> {
                Toast.makeText(this, "Positive adjustment must be between 0 and 300 seconds", Toast.LENGTH_LONG).show()
                return false
            }
            settings.rpeNegativeAdjustmentSeconds < 0 || settings.rpeNegativeAdjustmentSeconds > 300 -> {
                Toast.makeText(this, "Negative adjustment must be between 0 and 300 seconds", Toast.LENGTH_LONG).show()
                return false
            }
        }
        return true
    }

    private fun showResetDialog() {
        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.dialog_title_reset_to_defaults))
            .setMessage(getString(R.string.dialog_message_reset_to_defaults))
            .setPositiveButton(getString(R.string.button_reset)) { _, _ ->
                settingsManager.resetToDefaults()
                loadSettings()
                Toast.makeText(this, getString(R.string.toast_settings_reset), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

