package com.lilfitness.activities

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import com.lilfitness.databinding.ActivityProgressionSettingsBinding
import com.lilfitness.helpers.ProgressionHelper
import com.lilfitness.helpers.ProgressionSettingsManager

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

    private fun loadSettings() {
        val settings = settingsManager.getSettings()

        // Core settings
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
        
        // Show/hide rest timer settings based on toggle
        binding.layoutRestTimerSettings.visibility = if (settings.restTimerEnabled) android.view.View.VISIBLE else android.view.View.GONE
        binding.layoutRpeAdjustmentSettings.visibility = if (settings.rpeAdjustmentEnabled) android.view.View.VISIBLE else android.view.View.GONE
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
            val settings = ProgressionHelper.ProgressionSettings(
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
                rpeHighBonusSeconds = binding.etRpeBonus.text.toString().toInt()
            )

            // Validate settings
            if (!validateSettings(settings)) {
                return
            }

            settingsManager.saveSettings(settings)
            Toast.makeText(this, "Settings saved successfully! ✓", Toast.LENGTH_SHORT).show()
            finish()

        } catch (e: NumberFormatException) {
            Toast.makeText(this, "Invalid input. Please check all fields.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun validateSettings(settings: ProgressionHelper.ProgressionSettings): Boolean {
        when {
            settings.lookbackCount < 1 || settings.lookbackCount > 10 -> {
                Toast.makeText(this, "Lookback sessions must be between 1 and 10", Toast.LENGTH_LONG).show()
                return false
            }
            settings.increaseStep < 0 || settings.increaseStep > 10 -> {
                Toast.makeText(this, "Increase step must be between 0 and 10kg", Toast.LENGTH_LONG).show()
                return false
            }
            settings.smallStep < 0 || settings.smallStep > 5 -> {
                Toast.makeText(this, "Small step must be between 0 and 5kg", Toast.LENGTH_LONG).show()
                return false
            }
            settings.deloadThreshold < 1 || settings.deloadThreshold > 10 -> {
                Toast.makeText(this, "Deload threshold must be between 1 and 10", Toast.LENGTH_LONG).show()
                return false
            }
            settings.deloadRPEThreshold < 6.0f || settings.deloadRPEThreshold > 10.0f -> {
                Toast.makeText(this, "Deload RPE must be between 6.0 and 10.0", Toast.LENGTH_LONG).show()
                return false
            }
            settings.deloadPercent < 0.5f || settings.deloadPercent > 1.0f -> {
                Toast.makeText(this, "Deload % must be between 50 and 100", Toast.LENGTH_LONG).show()
                return false
            }
            settings.plateauSessionCount < 2 || settings.plateauSessionCount > 10 -> {
                Toast.makeText(this, "Plateau sessions must be between 2 and 10", Toast.LENGTH_LONG).show()
                return false
            }
            settings.plateauRPEMax < 6.0f || settings.plateauRPEMax > 10.0f -> {
                Toast.makeText(this, "Plateau RPE must be between 6.0 and 10.0", Toast.LENGTH_LONG).show()
                return false
            }
            settings.plateauBoost < 1.0f || settings.plateauBoost > 3.0f -> {
                Toast.makeText(this, "Plateau boost must be between 1.0 and 3.0", Toast.LENGTH_LONG).show()
                return false
            }
            settings.heavySets < 1 || settings.heavySets > 10 -> {
                Toast.makeText(this, "Heavy sets must be between 1 and 10", Toast.LENGTH_LONG).show()
                return false
            }
            settings.heavyReps < 1 || settings.heavyReps > 50 -> {
                Toast.makeText(this, "Heavy reps must be between 1 and 50", Toast.LENGTH_LONG).show()
                return false
            }
            settings.lightSets < 1 || settings.lightSets > 10 -> {
                Toast.makeText(this, "Light sets must be between 1 and 10", Toast.LENGTH_LONG).show()
                return false
            }
            settings.lightReps < 1 || settings.lightReps > 50 -> {
                Toast.makeText(this, "Light reps must be between 1 and 50", Toast.LENGTH_LONG).show()
                return false
            }
            settings.heavyRestSeconds < 30 || settings.heavyRestSeconds > 600 -> {
                Toast.makeText(this, "Heavy rest must be between 30 and 600 seconds", Toast.LENGTH_LONG).show()
                return false
            }
            settings.lightRestSeconds < 30 || settings.lightRestSeconds > 600 -> {
                Toast.makeText(this, "Light rest must be between 30 and 600 seconds", Toast.LENGTH_LONG).show()
                return false
            }
            settings.customRestSeconds < 30 || settings.customRestSeconds > 600 -> {
                Toast.makeText(this, "Custom rest must be between 30 and 600 seconds", Toast.LENGTH_LONG).show()
                return false
            }
            settings.rpeHighThreshold < 6.0f || settings.rpeHighThreshold > 10.0f -> {
                Toast.makeText(this, "RPE threshold must be between 6.0 and 10.0", Toast.LENGTH_LONG).show()
                return false
            }
            settings.rpeHighBonusSeconds < 0 || settings.rpeHighBonusSeconds > 300 -> {
                Toast.makeText(this, "RPE bonus rest must be between 0 and 300 seconds", Toast.LENGTH_LONG).show()
                return false
            }
        }
        return true
    }

    private fun showResetDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reset to Defaults")
            .setMessage("This will reset all progression settings to default values. Continue?")
            .setPositiveButton("Reset") { _, _ ->
                settingsManager.resetToDefaults()
                loadSettings()
                Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

