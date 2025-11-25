package com.liftpath.activities

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import com.liftpath.R
import com.liftpath.databinding.ActivityProgressionSettingsBinding
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.ProgressionHelper
import com.liftpath.helpers.ProgressionSettingsManager
import com.liftpath.helpers.showWithTransparentWindow
import com.liftpath.models.UserLevel

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

        setupBackgroundAnimation()

        settingsManager = ProgressionSettingsManager(this)

        // Initialize sections as collapsed
        binding.iconExpandCore.rotation = 270f
        binding.iconExpandRestTimer.rotation = 270f
        binding.iconExpandDeload.rotation = 270f
        
        collapseViewImmediate(binding.contentCoreSettings)
        collapseViewImmediate(binding.contentRestTimer)
        collapseViewImmediate(binding.contentDeload)

        setupUserLevelSpinner()
        loadSettings()
        setupListeners()
        setupExpandCollapseListeners()
        
        // Header back button
        binding.buttonBackHeader.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
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
        
        binding.spinnerUserLevel.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                updateRpeSuggestions(userLevels[position])
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }
    
    private fun updateRpeSuggestions(userLevel: UserLevel) {
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
                "The Timer adjusts based on your logged RPE vs these targets."
        
        binding.textRpeSuggestions.text = text
    }
    
    private fun updateTimerCalculationInfo() {
        try {
            val settings = settingsManager.getSettings()
            
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
            // Ignore errors
        }
    }

    private fun loadSettings() {
        val settings = settingsManager.getSettings()

        // 1. Core settings
        val userLevels = UserLevel.values()
        val selectedIndex = userLevels.indexOf(settings.userLevel)
        if (selectedIndex >= 0) {
            binding.spinnerUserLevel.setSelection(selectedIndex)
            updateRpeSuggestions(settings.userLevel)
        }
        
        binding.etLookbackCount.setText(settings.lookbackCount.toString())
        binding.etIncreaseStep.setText(settings.increaseStep.toString())
        binding.etSmallStep.setText(settings.smallStep.toString())

        // 2. Deload settings
        binding.etDeloadThreshold.setText(settings.deloadThreshold.toString())
        binding.etDeloadRPE.setText(settings.deloadRPEThreshold.toString())
        //binding.etDeloadPercent.setText((settings.deloadPercent * 100).toInt().toString()) // If you kept deloadPercent

        // 3. Rest timer settings
        binding.switchRestTimer.isChecked = settings.restTimerEnabled
        binding.etHeavyRest.setText(settings.heavyRestSeconds.toString())
        binding.etLightRest.setText(settings.lightRestSeconds.toString())
        binding.etCustomRest.setText(settings.customRestSeconds.toString())
        
        binding.switchRpeAdjustment.isChecked = settings.rpeAdjustmentEnabled
        binding.etRpeThreshold.setText(settings.rpeHighThreshold.toString())
        binding.etRpeBonus.setText(settings.rpeHighBonusSeconds.toString())
        
        binding.etRpeDeviationThreshold.setText(settings.rpeDeviationThreshold.toString())
        binding.etRpePositiveAdjustment.setText(settings.rpePositiveAdjustmentSeconds.toString())
        binding.etRpeNegativeAdjustment.setText(settings.rpeNegativeAdjustmentSeconds.toString())
        
        // Visibility Toggles
        binding.layoutRestTimerSettings.visibility = if (settings.restTimerEnabled) View.VISIBLE else View.GONE
        binding.layoutRpeAdjustmentSettings.visibility = if (settings.rpeAdjustmentEnabled) View.VISIBLE else View.GONE
        
        updateTimerCalculationInfo()
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            saveSettings()
        }

        binding.btnResetDefaults.setOnClickListener {
            showResetDialog()
        }
        
        binding.switchRestTimer.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutRestTimerSettings.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        
        binding.switchRpeAdjustment.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutRpeAdjustmentSettings.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        
        // Focus listeners for info updates
        val focusListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) updateTimerCalculationInfo()
        }
        
        binding.etRpeDeviationThreshold.onFocusChangeListener = focusListener
        binding.etRpePositiveAdjustment.onFocusChangeListener = focusListener
        binding.etRpeNegativeAdjustment.onFocusChangeListener = focusListener
        binding.etRpeThreshold.onFocusChangeListener = focusListener
        binding.etRpeBonus.onFocusChangeListener = focusListener
    }
    
    private fun setupExpandCollapseListeners() {
        binding.headerCoreSettings.setOnClickListener {
            toggleSection("core", binding.contentCoreSettings, binding.iconExpandCore)
        }
        
        binding.headerRestTimer.setOnClickListener {
            toggleSection("rest_timer", binding.contentRestTimer, binding.iconExpandRestTimer)
        }
        
        binding.headerDeload.setOnClickListener {
            toggleSection("deload", binding.contentDeload, binding.iconExpandDeload)
        }
    }
    
    private fun toggleSection(sectionId: String, contentView: ViewGroup, iconView: View) {
        val isExpanded = expandedSections.contains(sectionId)
        if (isExpanded) {
            collapseView(contentView, iconView)
            expandedSections.remove(sectionId)
        } else {
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
            val selectedUserLevel = UserLevel.values()[binding.spinnerUserLevel.selectedItemPosition]
            
            // Create settings using ONLY the fields that exist in our new Data Class
            val settings = ProgressionHelper.ProgressionSettings(
                userLevel = selectedUserLevel,
                lookbackCount = binding.etLookbackCount.text.toString().toInt(),
                increaseStep = binding.etIncreaseStep.text.toString().toFloat(),
                smallStep = binding.etSmallStep.text.toString().toFloat(),
                
                deloadThreshold = binding.etDeloadThreshold.text.toString().toInt(),
                deloadRPEThreshold = binding.etDeloadRPE.text.toString().toFloat(),
                
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
            settings.heavyRestSeconds < 5 || settings.heavyRestSeconds > 600 -> {
                Toast.makeText(this, getString(R.string.validation_heavy_rest), Toast.LENGTH_LONG).show()
                return false
            }
            settings.lightRestSeconds < 5 || settings.lightRestSeconds > 600 -> {
                Toast.makeText(this, getString(R.string.validation_light_rest), Toast.LENGTH_LONG).show()
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