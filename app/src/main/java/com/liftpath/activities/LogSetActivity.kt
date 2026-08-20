package com.liftpath.activities

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Animatable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Html
import android.text.InputType
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.liftpath.R
import com.liftpath.databinding.ActivityLogSetBinding
import com.liftpath.helpers.BodyWeightHelper
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.ProgressionHelper
import com.liftpath.helpers.ProgressionSettingsManager
import com.liftpath.helpers.RestTimerHelper
import com.liftpath.helpers.showWithTransparentWindow
import com.liftpath.models.ExerciseEntry
import com.liftpath.models.SetIntent
import java.util.Locale
import com.liftpath.helpers.lpColor

class LogSetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogSetBinding
    private lateinit var jsonHelper: JsonHelper
    private var exerciseId: Int = 0
    private var exerciseName: String = ""
    private var setNumber: Int = 1
    private var workoutType: String = "heavy"
    private var isBodyweight: Boolean = false
    private var isTimeBased: Boolean = false

    // Count-up timer state (time-based exercises only)
    private var isTimerRunning = false
    private var currentDurationSeconds = 0
    private val timerHandler = Handler(Looper.getMainLooper())
    private var suppressDurationWatcher = false
    private val timerTick = object : Runnable {
        override fun run() {
            currentDurationSeconds += 1
            renderDuration()
            timerHandler.postDelayed(this, 1000)
        }
    }
    private var loggedBodyweight: Float? = null
    private var previousSetReps: Int? = null
    private var lastLoggedKg: Float? = null
    private var lastLoggedReps: Int? = null
    private var lastLoggedRpe: Float? = null

    private var pendingTimerRpe: Float? = null
    private var pendingTimerIntent: SetIntent? = null
    private var pendingTimerExerciseName: String? = null
    private var shouldFinishAfterTimer = false
    private var originalRpeHint: CharSequence? = null

    // Stepper state
    private val weightStep = 2.5f
    private val rpeStep = 0.5f
    private var isNoteExpanded = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, start the timer
            startRestTimerAfterPermissionCheck()
        } else {
            // Permission denied, finish the activity
            if (shouldFinishAfterTimer) {
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_EXERCISE_ID = "extra_exercise_id"
        const val EXTRA_EXERCISE_NAME = "extra_exercise_name"
        const val EXTRA_SET_NUMBER = "extra_set_number"
        const val EXTRA_LOGGED_SET = "extra_logged_set"
        const val EXTRA_WORKOUT_TYPE = "extra_workout_type"
        const val EXTRA_PREVIOUS_SET_REPS = "extra_previous_set_reps"
        const val EXTRA_LAST_LOGGED_KG = "extra_last_logged_kg"
        const val EXTRA_LAST_LOGGED_REPS = "extra_last_logged_reps"
        const val EXTRA_LAST_LOGGED_RPE = "extra_last_logged_rpe"
        const val EXTRA_INTENT = "extra_intent"
        const val EXTRA_REST_SECONDS_OVERRIDE = "extra_rest_seconds_override"
        const val EXTRA_IS_BODYWEIGHT = "extra_is_bodyweight"
        const val EXTRA_IS_TIME_BASED = "extra_is_time_based"
        const val EXTRA_DURATION_TARGET = "extra_duration_target"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogSetBinding.inflate(layoutInflater)
        setContentView(binding.root)
        jsonHelper = JsonHelper(this)

        exerciseId = intent.getIntExtra(EXTRA_EXERCISE_ID, 0)
        exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: "Exercise"
        setNumber = intent.getIntExtra(EXTRA_SET_NUMBER, 1)
        workoutType = intent.getStringExtra(EXTRA_WORKOUT_TYPE) ?: "heavy"
        isBodyweight = intent.getBooleanExtra(EXTRA_IS_BODYWEIGHT, false)
        isTimeBased = intent.getBooleanExtra(EXTRA_IS_TIME_BASED, false)
        previousSetReps = intent.getIntExtra(EXTRA_PREVIOUS_SET_REPS, -1).takeIf { it > 0 }
        lastLoggedKg = intent.getFloatExtra(EXTRA_LAST_LOGGED_KG, -1f).takeIf { it > 0 }
        lastLoggedReps = intent.getIntExtra(EXTRA_LAST_LOGGED_REPS, -1).takeIf { it > 0 }
        lastLoggedRpe = intent.getFloatExtra(EXTRA_LAST_LOGGED_RPE, -1f).takeIf { it in 6.0f..10.0f }
        // Get intent for title display
        val intentName = intent.getStringExtra(EXTRA_INTENT)
        val displayIntent = try {
            if (intentName != null) SetIntent.valueOf(intentName) else SetIntent.BUILD
        } catch (e: Exception) {
            SetIntent.BUILD
        }
        binding.textLogSetTitle.text = exerciseName
        val illustrationRes = jsonHelper.readTrainingData().exerciseLibrary.find { it.id == exerciseId }?.illustrationRes
        binding.imageLogSetIllustration.setImageResource(illustrationRes ?: R.drawable.ic_dumbbell)
        binding.textSetNumberBadge.text = "SET $setNumber"
        binding.textIntentLabel.text = displayIntent.displayName
        val intentColor = getIntentColor(displayIntent)
        binding.textSetNumberBadge.setTextColor(intentColor)
        binding.textIntentLabel.setTextColor(intentColor)
        binding.viewSuggestionStripe.setBackgroundColor(intentColor)

        setupBackgroundAnimation()

        binding.btnRpeHelp.setOnClickListener {
            showRpeHelpDialog()
        }

        // The two axes are independent: an exercise can be BODYWEIGHT + TIME (e.g. a plank), in
        // which case both the body-weight snapshot UI and the timer are shown.
        if (isTimeBased) setupTimeMode()
        if (isBodyweight) setupBodyweightMode()
        if (!isTimeBased && !isBodyweight) {
            prefillLastSetFallback()
            showWeightSuggestion()
        }
        if (!isTimeBased) prefillRepsFromPreviousSet()

        binding.buttonSaveSet.setOnClickListener {
            saveSet()
        }

        binding.buttonBack.setOnClickListener {
            finish()
        }

        // Store original RPE hint
        originalRpeHint = binding.textInputLayoutRpe.hint

        // Warmup chip (replaces checkbox). Checked goes lpIntentWarmup rather than the ink fill
        // Widget.LP.Chip.Choice would give it — a warmup set is the one thing on this screen with
        // its own semantic colour, and it's worth spending it here so the state is unmistakable.
        binding.chipWarmup.setOnCheckedChangeListener { _, isChecked ->
            updateRpeFieldForWarmup(isChecked)
            applyWarmupChipColors(isChecked)
        }
        applyWarmupChipColors(binding.chipWarmup.isChecked)

        // Initialize RPE field state based on initial warmup chip state
        updateRpeFieldForWarmup(binding.chipWarmup.isChecked)

        setupSteppers()
        setupNoteToggle()
    }

    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is Animatable) {
            drawable.start()
        }
    }

    private fun prefillLastSetFallback() {
        if (binding.editTextKg.text?.isNotBlank() == true) return

        // 1. Use passed last logged values (from last working set)
        if (lastLoggedKg != null && lastLoggedReps != null) {
            binding.editTextKg.setText(lastLoggedKg.toString())
            binding.editTextReps.setText(lastLoggedReps.toString())
            lastLoggedRpe?.let {
                if (binding.editTextRpe.text.isNullOrBlank()) {
                    binding.editTextRpe.setText(it.toString())
                }
            }
            return
        }

        // 2. First working set: try ProgressionHelper suggestion
        val intentName = intent.getStringExtra(EXTRA_INTENT)
        val setIntent = try {
            if (intentName != null) SetIntent.valueOf(intentName) else SetIntent.BUILD
        } catch (e: Exception) {
            SetIntent.BUILD
        }
        if (workoutType != "custom") {
            val trainingData = jsonHelper.readTrainingData()
            val settings = ProgressionSettingsManager(this).getSettings()
            val suggestion = ProgressionHelper.getIntentSuggestion(
                exerciseId = exerciseId,
                intent = setIntent,
                trainingData = trainingData,
                settings = settings
            )
            val weightToUse = suggestion.suggestedWeight ?: suggestion.lastWeight
            val repsToUse = suggestion.suggestedReps ?: suggestion.lastReps
            val rpeToUse = suggestion.suggestedRpe ?: suggestion.lastRpe
            if (weightToUse != null && weightToUse > 0f) {
                binding.editTextKg.setText(weightToUse.toString())
            }
            if (repsToUse != null && repsToUse > 0 && binding.editTextReps.text.isNullOrBlank()) {
                binding.editTextReps.setText(repsToUse.toString())
            }
            if (rpeToUse != null && binding.editTextRpe.text.isNullOrBlank()) {
                binding.editTextRpe.setText(rpeToUse.toString())
            }
            if (weightToUse != null && weightToUse > 0f) return
        }

        // 3. Fall back to last working set from training data
        val lastWorkingSet = getLastWorkingSetFromHistory()
        if (lastWorkingSet != null) {
            binding.editTextKg.setText(lastWorkingSet.kg.toString())
            if (binding.editTextReps.text.isNullOrBlank()) {
                binding.editTextReps.setText(lastWorkingSet.reps.toString())
            }
            lastWorkingSet.rpe?.let {
                if (binding.editTextRpe.text.isNullOrBlank()) {
                    binding.editTextRpe.setText(it.toString())
                }
            }
        }
        // 4. No data: leave blank (nothing to do)
    }

    private fun getLastWorkingSetFromHistory(): ExerciseEntry? {
        val trainingData = jsonHelper.readTrainingData()
        return trainingData.trainings
            .flatMap { it.exercises }
            .filter {
                it.exerciseId == exerciseId &&
                !it.isEffectivelyWarmup()  // Exclude warmups (isWarmup or legacy RPE 6)
            }
            .lastOrNull()
    }

    /**
     * The last logged *timed* set, so a timed exercise never prefills from a stale rep-based entry
     * (which would carry durationSeconds == null and a meaningless reps count).
     */
    private fun getLastTimedSetFromHistory(): ExerciseEntry? {
        val trainingData = jsonHelper.readTrainingData()
        return trainingData.trainings
            .flatMap { it.exercises }
            .filter {
                it.exerciseId == exerciseId &&
                !it.isEffectivelyWarmup() &&
                it.isTimedEntry()
            }
            .lastOrNull()
    }

    private fun prefillRepsFromPreviousSet() {
        if (setNumber <= 1) return
        val reps = previousSetReps ?: return
        binding.editTextReps.setText(reps.toString())
    }

    // --- Time-based (isometric hold) logging ---

    /**
     * Sets up the duration half of the screen. Owns only the metric (reps ⇄ timer); the load half
     * is left to [setupBodyweightMode] when the exercise is also bodyweight, so a bodyweight hold
     * still captures its body-weight snapshot.
     */
    private fun setupTimeMode() {
        // Swap reps stepper for the timer block.
        binding.repsContainer.visibility = View.GONE
        binding.timeContainer.visibility = View.VISIBLE
        binding.cardSuggestionHint.visibility = View.GONE

        // A weighted hold keeps the optional external-load field; a bodyweight hold uses the
        // body-weight snapshot UI instead (configured by setupBodyweightMode).
        if (!isBodyweight) {
            binding.layoutWeightStepperRow.visibility = View.VISIBLE
            binding.textInputLayoutKg.visibility = View.VISIBLE
            binding.textInputLayoutKg.hint = getString(R.string.optional_kg_hint)
        }

        // Prefill: last logged hold for this exercise, else the plan target.
        val last = getLastTimedSetFromHistory()
        val targetSeconds = intent.getIntExtra(EXTRA_DURATION_TARGET, -1).takeIf { it > 0 }
        currentDurationSeconds = last?.durationSeconds ?: targetSeconds ?: 0
        if (!isBodyweight && last != null && last.kg > 0f && binding.editTextKg.text.isNullOrBlank()) {
            binding.editTextKg.setText(formatNum(last.kg))
        }
        last?.rpe?.let {
            if (binding.editTextRpe.text.isNullOrBlank()) binding.editTextRpe.setText(it.toString())
        }

        setupCountUpTimer()
    }

    private fun renderDuration() {
        binding.textTimerDisplay.text = RestTimerHelper.formatDuration(currentDurationSeconds)
        suppressDurationWatcher = true
        binding.editTextDurationSeconds.setText(currentDurationSeconds.toString())
        binding.editTextDurationSeconds.setSelection(
            binding.editTextDurationSeconds.text?.length ?: 0
        )
        suppressDurationWatcher = false
    }

    private fun setupCountUpTimer() {
        renderDuration()
        binding.buttonTimerStartStop.setOnClickListener { toggleTimer() }
        binding.buttonTimerReset.setOnClickListener {
            stopCountUpTimer()
            currentDurationSeconds = 0
            renderDuration()
        }
        binding.editTextDurationSeconds.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (suppressDurationWatcher || isTimerRunning) return
                currentDurationSeconds = s?.toString()?.toIntOrNull() ?: 0
                binding.textTimerDisplay.text = RestTimerHelper.formatDuration(currentDurationSeconds)
            }
        })
    }

    private fun toggleTimer() {
        if (isTimerRunning) stopCountUpTimer() else startCountUpTimer()
    }

    private fun startCountUpTimer() {
        isTimerRunning = true
        binding.buttonTimerStartStop.text = getString(R.string.timer_stop)
        timerHandler.postDelayed(timerTick, 1000)
    }

    private fun stopCountUpTimer() {
        isTimerRunning = false
        binding.buttonTimerStartStop.text = getString(R.string.timer_start)
        timerHandler.removeCallbacks(timerTick)
    }

    // --- Bodyweight set logging ---

    private fun formatNum(v: Float): String =
        if (v == v.toLong().toFloat()) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

    /** Body weight & total load are always shown to 1 decimal. */
    private fun format1(v: Float): String = String.format(Locale.US, "%.1f", v)

    /**
     * Sets up the load half of the screen: body-weight snapshot chip plus the signed Added/Assisted
     * extra. Independent of the metric half, so it composes with [setupTimeMode] for a bodyweight
     * hold.
     */
    private fun setupBodyweightMode() {
        binding.layoutWeightStepperRow.visibility = View.GONE  // hides label + stepper together
        binding.textInputLayoutKg.visibility = View.GONE
        binding.textInputLayoutExtra.visibility = View.VISIBLE
        binding.bodyweightContainer.visibility = View.VISIBLE
        binding.cardSuggestionHint.visibility = View.GONE

        // Resolve the body weight to snapshot onto this set.
        loggedBodyweight = BodyWeightHelper.getCurrentBodyweightKg(this)

        // Default the toggle to "Added".
        if (binding.chipGroupExtraType.checkedChipId == View.NO_ID) {
            binding.chipAdded.isChecked = true
        }

        // Prefill from the last logged set for this exercise, matching the current metric so a
        // bodyweight hold doesn't inherit a rep count and vice versa.
        val last = if (isTimeBased) getLastTimedSetFromHistory() else getLastWorkingSetFromHistory()
        if (last != null) {
            if (loggedBodyweight == null) loggedBodyweight = last.bodyweightKg
            if (!isTimeBased && binding.editTextReps.text.isNullOrBlank()) {
                binding.editTextReps.setText(last.reps.toString())
            }
            when (val added = last.addedKg) {
                null -> {}
                else -> when {
                    added < 0f -> {
                        binding.chipAssisted.isChecked = true
                        binding.editTextExtra.setText(formatNum(-added))
                    }
                    added > 0f -> {
                        binding.chipAdded.isChecked = true
                        binding.editTextExtra.setText(formatNum(added))
                    }
                    else -> binding.chipAdded.isChecked = true
                }
            }
        }

        binding.chipBodyweight.setOnClickListener { showBodyweightOverrideDialog() }
        binding.chipGroupExtraType.setOnCheckedStateChangeListener { _, _ -> updateEffectiveLoad() }
        binding.editTextExtra.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateEffectiveLoad() }
        })

        updateBodyweightChip()
        updateEffectiveLoad()
    }

    private fun updateBodyweightChip() {
        val bw = loggedBodyweight
        binding.chipBodyweight.text = if (bw != null) {
            getString(R.string.bodyweight_chip_label, format1(bw))
        } else {
            getString(R.string.bodyweight_chip_unknown)
        }
    }

    /** Signed extra weight: positive when "Added", negative when "Assisted". */
    private fun currentAddedKg(): Float {
        val extra = binding.editTextExtra.text.toString().trim().toFloatOrNull() ?: 0f
        val sign = if (binding.chipAssisted.isChecked) -1f else 1f
        return sign * extra
    }

    private fun updateEffectiveLoad() {
        val bw = loggedBodyweight
        binding.textEffectiveLoad.text = if (bw == null) {
            getString(R.string.bodyweight_need_value)
        } else {
            getString(R.string.bodyweight_total_label, format1(bw + currentAddedKg()))
        }
    }

    /** Override the body-weight snapshot for this set only (does not change saved settings). */
    private fun showBodyweightOverrideDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.bodyweight_field_hint)
            loggedBodyweight?.let { setText(format1(it)) }
        }
        val density = resources.displayMetrics.density
        val pad = (24 * density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(pad, (8 * density).toInt(), pad, 0)
            addView(input)
        }
        DialogHelper.createBuilder(this)
            .setTitle(R.string.bodyweight_initial_title)
            .setView(container)
            .setPositiveButton(R.string.button_save) { _, _ ->
                val kg = input.text.toString().trim().toFloatOrNull()
                if (kg != null && kg in 20f..400f) {
                    loggedBodyweight = BodyWeightHelper.round1(kg)
                    updateBodyweightChip()
                    updateEffectiveLoad()
                } else {
                    Toast.makeText(this, R.string.bodyweight_invalid, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.button_cancel, null)
            .showWithTransparentWindow()
    }

    private fun showWeightSuggestion() {
        // Get intent from extra
        val intentName = intent.getStringExtra(EXTRA_INTENT)
        val setIntent = try {
            if (intentName != null) SetIntent.valueOf(intentName) else SetIntent.BUILD
        } catch (e: Exception) {
            SetIntent.BUILD
        }
        
        // For custom workouts, don't show progression suggestions
        if (workoutType == "custom") {
            binding.cardSuggestionHint.visibility = View.GONE
            binding.textRpeHint.visibility = View.GONE
            return
        }

        // FLUSH: use dedicated suggestion (2×50% 1RM × 20 reps @ 6-7 RPE)
        if (setIntent == SetIntent.FLUSH) {
            val trainingData = jsonHelper.readTrainingData()
            val userSettings = ProgressionSettingsManager(this).getSettings()
            val suggestion = ProgressionHelper.getIntentSuggestion(
                exerciseId = exerciseId,
                intent = setIntent,
                trainingData = trainingData,
                settings = userSettings
            )
            if (suggestion.displayText.isNotEmpty()) {
                binding.textSuggestionContent.text = suggestion.displayText
                binding.cardSuggestionHint.visibility = View.VISIBLE
            }
            val weightToUse = suggestion.suggestedWeight ?: suggestion.lastWeight
            val repsToUse = suggestion.suggestedReps ?: suggestion.lastReps
            val rpeToUse = suggestion.suggestedRpe ?: suggestion.lastRpe
            if (weightToUse != null && weightToUse > 0f && binding.editTextKg.text.isNullOrBlank()) {
                binding.editTextKg.setText(weightToUse.toString())
            }
            if (repsToUse != null && repsToUse > 0 && binding.editTextReps.text.isNullOrBlank()) {
                binding.editTextReps.setText(repsToUse.toString())
            }
            if (rpeToUse != null && binding.editTextRpe.text.isNullOrBlank()) {
                binding.editTextRpe.setText(rpeToUse.toString())
                updateRpeHint(rpeToUse)
            }
            return
        }
        
        val trainingData = jsonHelper.readTrainingData()
        val settingsManager = ProgressionSettingsManager(this)
        val userSettings = settingsManager.getSettings()

        // Use new intent-based progression API
        val suggestion = ProgressionHelper.getIntentSuggestion(
            exerciseId = exerciseId,
            intent = setIntent,
            trainingData = trainingData,
            settings = userSettings
        )

        // Get target RPE from settings
        val suggestedRpe = ProgressionHelper.getTargetRpe(setIntent, userSettings)

        if (!suggestion.isFirstTime) {
            val suggestedReps = suggestion.suggestedReps

            // Build hint text based on intent suggestion
            val hintText = buildString {
                when (suggestion.weightAction) {
                    ProgressionHelper.WeightAction.INCREASE -> {
                        append("Increase weight, ")
                        if (suggestedReps != null) {
                            append("aim for $suggestedReps reps")
                        }
                    }
                    ProgressionHelper.WeightAction.MAINTAIN -> {
                        if (suggestedReps != null) {
                            append("Aim for $suggestedReps reps")
                        } else {
                            append("Same weight")
                        }
                    }
                    ProgressionHelper.WeightAction.DECREASE -> {
                        append("Reduce weight, ")
                        if (suggestedReps != null) {
                            append("aim for $suggestedReps reps")
                        }
                    }
                    else -> {}
                }
                
                append(" @ RPE ${String.format(Locale.US, "%.1f", suggestedRpe)}")
                
                suggestion.badge?.let {
                    append(" [$it]")
                }
            }

            binding.textSuggestionContent.text = hintText

            // Prefill reps if empty
            if (binding.editTextReps.text.isNullOrBlank() && suggestedReps != null && suggestedReps > 0) {
                binding.editTextReps.setText(suggestedReps.toString())
            }

            // Prefill RPE if empty
            if (binding.editTextRpe.text.isNullOrBlank()) {
                binding.editTextRpe.setText(suggestedRpe.toString())
                updateRpeHint(suggestedRpe)
            }

            binding.cardSuggestionHint.visibility = View.VISIBLE
        } else {
            // First time - show suggested reps and RPE from settings
            val (minReps, _) = ProgressionHelper.getRepRange(setIntent, userSettings)
            
            val hintText = "First time! Start light, aim for $minReps reps @ RPE ${String.format(Locale.US, "%.1f", suggestedRpe)}"
            binding.textSuggestionContent.text = hintText
            binding.cardSuggestionHint.visibility = View.VISIBLE
            
            if (binding.editTextReps.text.isNullOrBlank()) {
                binding.editTextReps.setText(minReps.toString())
            }
            
            if (binding.editTextRpe.text.isNullOrBlank()) {
                binding.editTextRpe.setText(suggestedRpe.toString())
                updateRpeHint(suggestedRpe)
            }
        }
    }

    private fun updateRpeHint(rpe: Float) {
        val rpeDescription = when {
            rpe <= 6.0f -> "Very easy"
            rpe <= 7.0f -> "Easy"
            rpe <= 8.0f -> "Moderate"
            rpe <= 9.0f -> "Hard"
            else -> "Maximal"
        }
        val hintText = "Suggested RPE ${String.format(Locale.US, "%.1f", rpe)}: $rpeDescription"
        binding.textRpeHint.text = hintText
        binding.textRpeHint.visibility = View.VISIBLE
    }

    private fun showRpeHelpDialog() {
        val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(getString(R.string.dialog_message_rpe_scale), Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(getString(R.string.dialog_message_rpe_scale))
        }
        
        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.dialog_title_rpe_scale))
            .setMessage(message)
            .setPositiveButton(getString(R.string.button_got_it), null)
            .showWithTransparentWindow()
    }

    private fun saveSet() {
        // Resolve the target metric: reps (default) or a timed hold (durationSeconds).
        val reps: Int
        val durationSeconds: Int?
        if (isTimeBased) {
            val dur = binding.editTextDurationSeconds.text.toString().toIntOrNull()
            if (dur == null || dur <= 0) {
                Toast.makeText(this, getString(R.string.toast_please_enter_duration), Toast.LENGTH_SHORT).show()
                return
            }
            stopCountUpTimer()
            reps = 0
            durationSeconds = dur
        } else {
            val parsedReps = binding.editTextReps.text.toString().toIntOrNull()
            if (parsedReps == null) {
                Toast.makeText(this, getString(R.string.toast_please_enter_weight_reps), Toast.LENGTH_SHORT).show()
                return
            }
            reps = parsedReps
            durationSeconds = null
        }

        // Resolve the load, independently of the metric above.
        //  - Bodyweight (reps or hold): effective = body-weight snapshot + signed extra.
        //  - Weighted hold: optional external kg (blank => 0, an unloaded hold).
        //  - Weighted reps: kg is required.
        val kg: Float
        val bodyweightKgValue: Float?
        val addedKgValue: Float?
        if (isBodyweight) {
            val bw = loggedBodyweight
            if (bw == null) {
                Toast.makeText(this, getString(R.string.bodyweight_need_value), Toast.LENGTH_SHORT).show()
                return
            }
            val added = BodyWeightHelper.round1(currentAddedKg())
            val roundedBw = BodyWeightHelper.round1(bw)
            val effective = BodyWeightHelper.round1(roundedBw + added)
            if (effective <= 0f) {
                Toast.makeText(this, getString(R.string.bodyweight_invalid), Toast.LENGTH_SHORT).show()
                return
            }
            kg = effective
            bodyweightKgValue = roundedBw
            addedKgValue = added
        } else if (isTimeBased) {
            kg = binding.editTextKg.text.toString().toFloatOrNull() ?: 0f
            bodyweightKgValue = null
            addedKgValue = null
        } else {
            val parsedKg = binding.editTextKg.text.toString().toFloatOrNull()
            if (parsedKg == null) {
                Toast.makeText(this, getString(R.string.toast_please_enter_weight_reps), Toast.LENGTH_SHORT).show()
                return
            }
            kg = parsedKg
            bodyweightKgValue = null
            addedKgValue = null
        }

        val rpeText = binding.editTextRpe.text.toString()
        val rpe = if (rpeText.isNotEmpty()) {
            val value = rpeText.toFloatOrNull()
            if (value != null && value in 6.0f..10.0f) {
                value
            } else {
                Toast.makeText(this, getString(R.string.toast_rpe_range), Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            null
        }

        val note = binding.editTextNote.text.toString()
        val isWarmup = binding.chipWarmup.isChecked

        // Logged sets count as completed (hadFailure = false); no checkbox
        val completed = true

        // Get intent from Intent extra, or default to BUILD
        val intentName = intent.getStringExtra(EXTRA_INTENT)
        val explicitIntent = try {
            if (intentName != null) {
                SetIntent.valueOf(intentName)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

        val newEntry = ExerciseEntry(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            setNumber = setNumber,
            kg = kg,
            reps = reps,
            note = note.takeIf { it.isNotBlank() },
            rating = null,
            workoutType = workoutType,
            rpe = rpe,
            completed = completed,
            isWarmup = isWarmup,
            explicitIntent = explicitIntent,
            bodyweightKg = bodyweightKgValue,
            addedKg = addedKgValue,
            durationSeconds = durationSeconds
        )

        val resultIntent = Intent().apply {
            putExtra(EXTRA_LOGGED_SET, newEntry)
        }
        setResult(Activity.RESULT_OK, resultIntent)

        // Start timer - this will handle permission request if needed
        shouldFinishAfterTimer = true
        val needsPermission = startRestTimer(rpe)
        
        // Only finish if timer was started immediately (no permission request needed)
        // Otherwise, finish will be called in the permission callback
        if (!needsPermission) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerTick)
    }

    private fun startRestTimer(rpe: Float?): Boolean {
        val settings = ProgressionSettingsManager(this).getSettings()

        if (!settings.restTimerEnabled) {
            return false // Timer disabled, no permission needed
        }

        // Check and request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Store the timer parameters to use after permission is granted
                pendingTimerRpe = rpe
                val intentName = intent.getStringExtra(EXTRA_INTENT)
                pendingTimerIntent = try {
                    if (intentName != null) SetIntent.valueOf(intentName) else SetIntent.BUILD
                } catch (e: Exception) {
                    SetIntent.BUILD
                }
                pendingTimerExerciseName = exerciseName
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return true // Permission request needed
            }
        }

        startRestTimerAfterPermissionCheck(rpe)
        return false // No permission request needed
    }

    private fun startRestTimerAfterPermissionCheck(rpe: Float? = null) {
        val settings = ProgressionSettingsManager(this).getSettings()
        
        // Use pending values if available (from permission request), otherwise use current values
        val actualRpe = pendingTimerRpe ?: rpe
        val intentName = intent.getStringExtra(EXTRA_INTENT)
        val actualIntent = pendingTimerIntent ?: try {
            if (intentName != null) SetIntent.valueOf(intentName) else SetIntent.BUILD
        } catch (e: Exception) {
            SetIntent.BUILD
        }
        val actualExerciseName = pendingTimerExerciseName ?: exerciseName
        
        // Clear pending values
        pendingTimerRpe = null
        pendingTimerIntent = null
        pendingTimerExerciseName = null

        val overrideSeconds = intent.getIntExtra(EXTRA_REST_SECONDS_OVERRIDE, -1).takeIf { it > 0 }
        val restSeconds = RestTimerHelper.restSecondsAfterLoggedSet(
            settings,
            actualIntent,
            actualRpe,
            overrideSeconds
        )

        com.liftpath.services.RestTimerService.startTimer(this, restSeconds, actualExerciseName, showDialog = false)
        
        // Finish activity after timer is started
        if (shouldFinishAfterTimer) {
            finish()
        }
    }

    /**
     * Warmup chip fill + label.
     *
     * Set from code rather than a colour state list because the checked colour is
     * `lpIntentWarmup`, not the ink that `Widget.LP.Chip.Choice` uses for every other chip in
     * the app; the label flips to inverse ink to stay legible on it.
     */
    private fun applyWarmupChipColors(isChecked: Boolean) {
        binding.chipWarmup.chipBackgroundColor = ColorStateList.valueOf(
            if (isChecked) this.lpColor(R.attr.lpIntentWarmup) else this.lpColor(R.attr.lpSurface)
        )
        binding.chipWarmup.setTextColor(
            if (isChecked) this.lpColor(R.attr.lpInkInverse) else this.lpColor(R.attr.lpInk)
        )
    }

    // ── Stepper helpers ────────────────────────────────────────────

    private fun setupSteppers() {
        binding.btnWeightMinus.setOnClickListener {
            val v = binding.editTextKg.text.toString().toFloatOrNull() ?: 0f
            setStepperText(binding.editTextKg, formatStepperValue((v - weightStep).coerceAtLeast(0f)))
        }
        binding.btnWeightPlus.setOnClickListener {
            val v = binding.editTextKg.text.toString().toFloatOrNull() ?: 0f
            setStepperText(binding.editTextKg, formatStepperValue(v + weightStep))
        }
        binding.btnRepsMinus.setOnClickListener {
            val v = binding.editTextReps.text.toString().toIntOrNull() ?: 0
            setStepperText(binding.editTextReps, (v - 1).coerceAtLeast(0).toString())
        }
        binding.btnRepsPlus.setOnClickListener {
            val v = binding.editTextReps.text.toString().toIntOrNull() ?: 0
            setStepperText(binding.editTextReps, (v + 1).toString())
        }
        binding.btnRpeMinus.setOnClickListener {
            if (binding.chipWarmup.isChecked) return@setOnClickListener
            val v = binding.editTextRpe.text.toString().toFloatOrNull() ?: 6.0f
            val next = (v - rpeStep).coerceIn(6.0f, 10.0f)
            setStepperText(binding.editTextRpe, String.format(Locale.US, "%.1f", next))
            updateRpeHint(next)
        }
        binding.btnRpePlus.setOnClickListener {
            if (binding.chipWarmup.isChecked) return@setOnClickListener
            val v = binding.editTextRpe.text.toString().toFloatOrNull() ?: 5.5f
            val next = (v + rpeStep).coerceIn(6.0f, 10.0f)
            setStepperText(binding.editTextRpe, String.format(Locale.US, "%.1f", next))
            updateRpeHint(next)
        }
    }

    /**
     * Sets text, moves the cursor to the end so a manual edit afterwards feels natural, and
     * ticks the phone.
     *
     * The haptic is what makes a stepper feel like a physical control rather than a repaint —
     * you can nudge a weight up four notches without looking at the field. CLOCK_TICK is the
     * lightest constant available; CONFIRM would be too much six times in a row.
     */
    private fun setStepperText(field: EditText, value: String) {
        field.setText(value)
        field.setSelection(field.text?.length ?: 0)
        field.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Avoids float edge-case: treats values within 0.001 of a whole number as integers. */
    private fun formatStepperValue(v: Float): String {
        val rounded = Math.round(v)
        return if (kotlin.math.abs(v - rounded) < 0.001f) rounded.toString()
        else String.format(Locale.US, "%.1f", v)
    }

    // ── Note toggle ────────────────────────────────────────────────

    private fun setupNoteToggle() {
        binding.buttonToggleNote.setOnClickListener {
            isNoteExpanded = !isNoteExpanded
            binding.textInputLayoutNote.visibility = if (isNoteExpanded) View.VISIBLE else View.GONE
            binding.buttonToggleNote.setText(if (isNoteExpanded) R.string.btn_hide_note else R.string.btn_add_note)
            if (isNoteExpanded) binding.editTextNote.requestFocus()  // user-initiated only
        }
        // Restore expanded state (e.g. prefilled note) without opening the keyboard
        if (!binding.editTextNote.text.isNullOrBlank()) {
            isNoteExpanded = true
            binding.textInputLayoutNote.visibility = View.VISIBLE
            binding.buttonToggleNote.setText(R.string.btn_hide_note)
        }
    }

    // ── Intent color ───────────────────────────────────────────────

    private fun getIntentColor(intent: SetIntent): Int {
        val attr = when (intent) {
            SetIntent.STRENGTH -> R.attr.lpIntentStrength
            SetIntent.BUILD    -> R.attr.lpIntentBuild
            SetIntent.FLUSH    -> R.attr.lpIntentFlush
            SetIntent.WARMUP   -> R.attr.lpIntentWarmup
            else               -> R.attr.lpInkSecondary
        }
        return lpColor(attr)
    }

    private fun formatTypeLabel(type: String): String {
        return type.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }

    private fun updateRpeFieldForWarmup(isWarmup: Boolean) {
        val greyColor = this.lpColor(R.attr.lpInkSecondary)
        val normalTextColor = this.lpColor(R.attr.lpInk)
        val normalHintColor = this.lpColor(R.attr.lpAccent)

        if (isWarmup) {
            binding.editTextRpe.setTextColor(greyColor)
            binding.editTextRpe.isEnabled = false
            binding.textInputLayoutRpe.hint = "(Warmup)"
            binding.textRpeLabel.setTextColor(greyColor)
            binding.textRpeHint.setTextColor(greyColor)
            binding.editTextRpe.setText("")
            binding.textInputLayoutRpe.hintTextColor = ColorStateList.valueOf(greyColor)
            binding.textInputLayoutRpe.boxStrokeColor = greyColor
        } else {
            binding.editTextRpe.setTextColor(normalTextColor)
            binding.editTextRpe.isEnabled = true
            binding.textInputLayoutRpe.hint = originalRpeHint
            binding.textRpeLabel.setTextColor(normalTextColor)
            binding.textRpeHint.setTextColor(this.lpColor(R.attr.lpInkSecondary))
            binding.textInputLayoutRpe.hintTextColor = ColorStateList.valueOf(normalHintColor)
            binding.textInputLayoutRpe.boxStrokeColor = normalHintColor
        }

        // Keep stepper buttons and text input layout visually in sync with the field
        val alpha = if (isWarmup) 0.38f else 1.0f
        binding.btnRpeMinus.alpha = alpha
        binding.btnRpePlus.alpha = alpha
        binding.btnRpeMinus.isClickable = !isWarmup
        binding.btnRpePlus.isClickable = !isWarmup
        binding.textInputLayoutRpe.alpha = alpha
    }
}