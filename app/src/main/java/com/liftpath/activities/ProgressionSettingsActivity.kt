package com.liftpath.activities

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import com.liftpath.R
import com.liftpath.databinding.ActivityProgressionSettingsBinding
import android.widget.EditText
import com.liftpath.databinding.ItemEquipmentIncrementBinding
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.EquipmentIncrementTable
import com.liftpath.helpers.ProgressionHelper
import com.liftpath.helpers.ProgressionSettingsManager
import com.liftpath.helpers.WeightIncrementHelper
import com.liftpath.helpers.WeightIncrementRule
import com.liftpath.helpers.WeightIncrementSettingsManager
import com.liftpath.helpers.showWithTransparentWindow
import com.liftpath.models.Equipment

class ProgressionSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProgressionSettingsBinding
    private lateinit var settingsManager: ProgressionSettingsManager
    private lateinit var incrementManager: WeightIncrementSettingsManager

    /** One (step, min) field pair per Equipment value, in enum order. */
    private val equipmentRows = LinkedHashMap<Equipment, Pair<EditText, EditText>>()

    // Track expanded state for each section
    private val expandedSections = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgressionSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // No action bar to title: Theme.LiftPath.Base is NoActionBar and this activity never
        // calls setSupportActionBar, so the header in the layout is the only title. It reads
        // @string/title_progression_settings and wires its own back control below.

        settingsManager = ProgressionSettingsManager(this)
        incrementManager = WeightIncrementSettingsManager(this)

        // Build the equipment rows *before* the first collapse: collapseViewImmediate/expandView
        // work off the measured height, so a section populated afterwards animates open to zero.
        buildEquipmentRows()

        // Initialize sections as collapsed
        binding.iconExpandCore.rotation = 270f
        binding.iconExpandRestTimer.rotation = 270f
        binding.iconExpandEquipment.rotation = 270f
        binding.iconExpandDeload.rotation = 270f

        collapseViewImmediate(binding.contentCoreSettings)
        collapseViewImmediate(binding.contentRestTimer)
        collapseViewImmediate(binding.contentEquipmentIncrements)
        collapseViewImmediate(binding.contentDeload)

        loadSettings()
        setupListeners()
        setupExpandCollapseListeners()
        
        // Header back button
        binding.buttonBackHeader.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun updateTimerCalculationInfo() {
        try {
            val settings = settingsManager.getSettings()
            
            val highThreshold = binding.etRpeThreshold.text.toString().toFloatOrNull() ?: settings.rpeHighThreshold
            val highBonus = binding.etRpeBonus.text.toString().toIntOrNull() ?: settings.rpeHighBonusSeconds
            val deviationThreshold = binding.etRpeDeviationThreshold.text.toString().toFloatOrNull() ?: settings.rpeDeviationThreshold
            val positiveAdjustment = binding.etRpePositiveAdjustment.text.toString().toIntOrNull() ?: settings.rpePositiveAdjustmentSeconds
            val negativeAdjustment = binding.etRpeNegativeAdjustment.text.toString().toIntOrNull() ?: settings.rpeNegativeAdjustmentSeconds
            
            val calculationText = "1. Start with base rest time (Strength/Build/Flush)\n" +
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
        loadEquipmentIncrements()

        // 1. Intent Progression settings (NEW)
        // STRENGTH
        binding.etStrengthMinReps.setText(settings.strengthMinReps.toString())
        binding.etStrengthMaxReps.setText(settings.strengthMaxReps.toString())
        binding.etStrengthTargetRpe.setText(settings.strengthTargetRpe.toString())
        binding.etStrengthIncreaseThreshold.setText(settings.strengthIncreaseRpeThreshold.toString())
        
        // BUILD
        binding.etBuildMinReps.setText(settings.buildMinReps.toString())
        binding.etBuildMaxReps.setText(settings.buildMaxReps.toString())
        binding.etBuildTargetRpe.setText(settings.buildTargetRpe.toString())
        binding.etBuildIncreaseThreshold.setText(settings.buildIncreaseRpeThreshold.toString())
        
        // Cross-intent fallback
        binding.etIntentFallbackDays.setText(settings.intentFallbackDays.toString())

        // 2. Deload settings
        binding.etDeloadThreshold.setText(settings.deloadThreshold.toString())
        binding.etDeloadRPE.setText(settings.deloadRPEThreshold.toString())

        // 3. Rest timer settings
        binding.switchRestTimer.isChecked = settings.restTimerEnabled
        binding.etStrengthRest.setText(settings.strengthRestSeconds.toString())
        binding.etBuildRest.setText(settings.buildRestSeconds.toString())
        binding.etFlushRest.setText(settings.flushRestSeconds.toString())
        binding.etSupersetTransition.setText(settings.supersetTransitionSeconds.toString())
        binding.etSupersetRestBonus.setText(settings.supersetRestBonusSeconds.toString())
        
        binding.switchRpeAdjustment.isChecked = settings.rpeAdjustmentEnabled
        binding.etRpeThreshold.setText(settings.rpeHighThreshold.toString())
        binding.etRpeBonus.setText(settings.rpeHighBonusSeconds.toString())
        
        binding.etRpeDeviationThreshold.setText(settings.rpeDeviationThreshold.toString())
        binding.etRpePositiveAdjustment.setText(settings.rpePositiveAdjustmentSeconds.toString())
        binding.etRpeNegativeAdjustment.setText(settings.rpeNegativeAdjustmentSeconds.toString())
        
        binding.switchNotificationLiveCountdown.isChecked = settings.notificationLiveCountdown
        binding.switchNotificationAutoDismiss.isChecked = settings.notificationAutoDismissEnabled
        binding.etNotificationAutoDismiss.setText(settings.notificationAutoDismissSeconds.toString())
        
        // Visibility Toggles
        binding.layoutRestTimerSettings.visibility = if (settings.restTimerEnabled) View.VISIBLE else View.GONE
        binding.layoutRpeAdjustmentSettings.visibility = if (settings.rpeAdjustmentEnabled) View.VISIBLE else View.GONE
        binding.layoutNotificationAutoDismissSettings.visibility = if (settings.notificationAutoDismissEnabled) View.VISIBLE else View.GONE
        
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
        
        binding.switchNotificationAutoDismiss.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutNotificationAutoDismissSettings.visibility = if (isChecked) View.VISIBLE else View.GONE
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
        
        binding.headerEquipmentIncrements.setOnClickListener {
            toggleSection("equipment", binding.contentEquipmentIncrements, binding.iconExpandEquipment)
        }

        binding.headerDeload.setOnClickListener {
            toggleSection("deload", binding.contentDeload, binding.iconExpandDeload)
        }
    }

    // ── Equipment weight ladders ───────────────────────────────────────────

    /** Inflates one row per [Equipment] value under the (already-present) column header row. */
    private fun buildEquipmentRows() {
        equipmentRows.clear()
        for (equipment in Equipment.values()) {
            val row = ItemEquipmentIncrementBinding.inflate(
                layoutInflater, binding.contentEquipmentIncrements, true
            )
            row.textEquipmentName.text = equipment.displayName
            equipmentRows[equipment] = row.etStep to row.etMin
        }
    }

    /**
     * Fills every row with its effective value — stored override if there is one, built-in
     * otherwise. Deliberately not a blank-means-inherit tri-state: that belongs on the
     * per-exercise override in Edit Exercise, where "inherit" has something to inherit *from*.
     */
    private fun loadEquipmentIncrements() {
        val table = incrementManager.getTable()
        for ((equipment, fields) in equipmentRows) {
            val rule = table.ruleFor(equipment)
                ?: WeightIncrementHelper.BUILT_IN[equipment]
                ?: WeightIncrementHelper.FALLBACK
            fields.first.setText(WeightIncrementHelper.format(rule.incrementKg))
            fields.second.setText(WeightIncrementHelper.format(rule.minimumKg))
        }
    }

    /**
     * Reads the table back, or null if anything is out of range (the caller aborts the save).
     *
     * Stored separately from [ProgressionSettings] on purpose: `saveSettings` rebuilds that
     * object from only the fields this screen binds, so anything living there would be wiped by
     * an unrelated save.
     */
    private fun collectEquipmentIncrements(): EquipmentIncrementTable? {
        val rules = LinkedHashMap<String, WeightIncrementRule>()
        for ((equipment, fields) in equipmentRows) {
            // A blank field means zero, not "leave it alone" — every row is always populated.
            val step = fields.first.text.toString().trim().toFloatOrNull() ?: 0f
            val min = fields.second.text.toString().trim().toFloatOrNull() ?: 0f
            // 0 is a legal step: it is the "no weight ladder" sentinel that bands use.
            if (step < 0f || step > 25f) {
                Toast.makeText(
                    this,
                    "${equipment.displayName}: ${getString(R.string.validation_equipment_step)}",
                    Toast.LENGTH_LONG
                ).show()
                return null
            }
            if (min < 0f || min > 100f) {
                Toast.makeText(
                    this,
                    "${equipment.displayName}: ${getString(R.string.validation_equipment_min)}",
                    Toast.LENGTH_LONG
                ).show()
                return null
            }
            rules[equipment.name] = WeightIncrementRule(step, min)
        }
        return EquipmentIncrementTable(rules)
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
            // Copy over the stored settings rather than constructing a fresh object: this screen
            // binds ~25 of the ~50 fields, and a fresh ProgressionSettings() would silently reset
            // every unbound one (flushWeightIncrementKg, timeDecay*, strength1RMPercent, the flush
            // targets) to its default on every save.
            @Suppress("DEPRECATION")
            val settings = settingsManager.getSettings().copy(
                // Intent Progression Settings (NEW)
                strengthMinReps = binding.etStrengthMinReps.text.toString().toInt(),
                strengthMaxReps = binding.etStrengthMaxReps.text.toString().toInt(),
                strengthTargetRpe = binding.etStrengthTargetRpe.text.toString().toFloat(),
                strengthIncreaseRpeThreshold = binding.etStrengthIncreaseThreshold.text.toString().toFloat(),
                
                buildMinReps = binding.etBuildMinReps.text.toString().toInt(),
                buildMaxReps = binding.etBuildMaxReps.text.toString().toInt(),
                buildTargetRpe = binding.etBuildTargetRpe.text.toString().toFloat(),
                buildIncreaseRpeThreshold = binding.etBuildIncreaseThreshold.text.toString().toFloat(),
                
                // Cross-intent fallback
                intentFallbackDays = binding.etIntentFallbackDays.text.toString().toInt(),
                
                // Deload settings
                deloadThreshold = binding.etDeloadThreshold.text.toString().toInt(),
                deloadRPEThreshold = binding.etDeloadRPE.text.toString().toFloat(),
                
                // Rest timer settings
                restTimerEnabled = binding.switchRestTimer.isChecked,
                strengthRestSeconds = binding.etStrengthRest.text.toString().toInt(),
                buildRestSeconds = binding.etBuildRest.text.toString().toInt(),
                flushRestSeconds = binding.etFlushRest.text.toString().toInt(),
                supersetTransitionSeconds = binding.etSupersetTransition.text.toString().toInt(),
                supersetRestBonusSeconds = binding.etSupersetRestBonus.text.toString().toInt(),
                
                // RPE adjustment settings
                rpeAdjustmentEnabled = binding.switchRpeAdjustment.isChecked,
                rpeHighThreshold = binding.etRpeThreshold.text.toString().toFloat(),
                rpeHighBonusSeconds = binding.etRpeBonus.text.toString().toInt(),
                rpeDeviationThreshold = binding.etRpeDeviationThreshold.text.toString().toFloat(),
                rpePositiveAdjustmentSeconds = binding.etRpePositiveAdjustment.text.toString().toInt(),
                rpeNegativeAdjustmentSeconds = binding.etRpeNegativeAdjustment.text.toString().toInt(),
                
                // Notification settings
                notificationLiveCountdown = binding.switchNotificationLiveCountdown.isChecked,
                notificationAutoDismissEnabled = binding.switchNotificationAutoDismiss.isChecked,
                notificationAutoDismissSeconds = binding.etNotificationAutoDismiss.text.toString().toInt()
            )

            if (!validateSettings(settings)) {
                return
            }

            // Collected before anything is written so a bad increment aborts the whole save
            // rather than leaving the two prefs files disagreeing.
            val incrementTable = collectEquipmentIncrements() ?: return

            settingsManager.saveSettings(settings)
            incrementManager.saveTable(incrementTable)
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
            // Intent progression validation
            settings.strengthMinReps < 1 || settings.strengthMinReps > 20 -> {
                Toast.makeText(this, "Strength min reps must be between 1 and 20", Toast.LENGTH_LONG).show()
                return false
            }
            settings.strengthMaxReps < settings.strengthMinReps || settings.strengthMaxReps > 30 -> {
                Toast.makeText(this, "Strength max reps must be >= min reps and <= 30", Toast.LENGTH_LONG).show()
                return false
            }
            settings.strengthTargetRpe < 5f || settings.strengthTargetRpe > 10f -> {
                Toast.makeText(this, "Strength target RPE must be between 5 and 10", Toast.LENGTH_LONG).show()
                return false
            }
            settings.strengthIncreaseRpeThreshold < 5f || settings.strengthIncreaseRpeThreshold > 10f -> {
                Toast.makeText(this, "Strength increase threshold must be between 5 and 10", Toast.LENGTH_LONG).show()
                return false
            }
            settings.buildMinReps < 1 || settings.buildMinReps > 30 -> {
                Toast.makeText(this, "Build min reps must be between 1 and 30", Toast.LENGTH_LONG).show()
                return false
            }
            settings.buildMaxReps < settings.buildMinReps || settings.buildMaxReps > 50 -> {
                Toast.makeText(this, "Build max reps must be >= min reps and <= 50", Toast.LENGTH_LONG).show()
                return false
            }
            settings.buildTargetRpe < 5f || settings.buildTargetRpe > 10f -> {
                Toast.makeText(this, "Build target RPE must be between 5 and 10", Toast.LENGTH_LONG).show()
                return false
            }
            settings.buildIncreaseRpeThreshold < 5f || settings.buildIncreaseRpeThreshold > 10f -> {
                Toast.makeText(this, "Build increase threshold must be between 5 and 10", Toast.LENGTH_LONG).show()
                return false
            }
            settings.intentFallbackDays < 7 || settings.intentFallbackDays > 90 -> {
                Toast.makeText(this, "Intent fallback days must be between 7 and 90", Toast.LENGTH_LONG).show()
                return false
            }
            // Rest timer validation
            settings.strengthRestSeconds < 5 || settings.strengthRestSeconds > 600 -> {
                Toast.makeText(this, "Strength rest must be between 5 and 600 seconds", Toast.LENGTH_LONG).show()
                return false
            }
            settings.buildRestSeconds < 5 || settings.buildRestSeconds > 600 -> {
                Toast.makeText(this, "Build rest must be between 5 and 600 seconds", Toast.LENGTH_LONG).show()
                return false
            }
            settings.flushRestSeconds < 5 || settings.flushRestSeconds > 600 -> {
                Toast.makeText(this, "Flush rest must be between 5 and 600 seconds", Toast.LENGTH_LONG).show()
                return false
            }
            settings.supersetTransitionSeconds < 5 || settings.supersetTransitionSeconds > 300 -> {
                Toast.makeText(this, "SuperSet transition must be between 5 and 300 seconds", Toast.LENGTH_LONG).show()
                return false
            }
            settings.supersetRestBonusSeconds < 0 || settings.supersetRestBonusSeconds > 300 -> {
                Toast.makeText(this, "SuperSet rest bonus must be between 0 and 300 seconds", Toast.LENGTH_LONG).show()
                return false
            }
            settings.notificationAutoDismissEnabled && (settings.notificationAutoDismissSeconds < 1 || settings.notificationAutoDismissSeconds > 60) -> {
                Toast.makeText(this, getString(R.string.validation_notification_auto_dismiss), Toast.LENGTH_LONG).show()
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
                incrementManager.resetToDefaults()
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