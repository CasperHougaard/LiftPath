package com.liftpath.activities

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.liftpath.R
import com.liftpath.databinding.ActivityStretchSetupBinding
import com.liftpath.helpers.DefaultStretchesHelper
import com.liftpath.helpers.StretchScope
import com.liftpath.helpers.StretchSettingsManager
import com.liftpath.helpers.applyChoiceChipStyle
import com.liftpath.models.StretchItem
import com.liftpath.models.TargetMuscle
import com.liftpath.models.TrainingSession
import kotlin.math.ceil

/**
 * The page that greets the athlete the moment a workout is completed, ahead of
 * [StretchCooldownActivity].
 *
 * It exists because the cool-down used to be decided entirely for the user: the muscles the
 * session worked chose the stretches, each stretch's authored duration chose the hold, and
 * declining meant tapping "Skip All" from *inside* a flow you did not want. All three are
 * choices now — but [StretchScope.AUTO] is the default and reproduces the old behaviour exactly,
 * so a user who taps Continue without reading anything gets what they got before.
 *
 * The choices are remembered ([StretchSettingsManager]), so this becomes a one-tap page for
 * anyone who has settled on an answer.
 */
class StretchSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStretchSetupBinding
    private var session: TrainingSession? = null
    private var workedMuscles: Set<TargetMuscle> = emptySet()

    private var scope = StretchScope.DEFAULT
    private var holdScale = StretchSettingsManager.DEFAULT_HOLD_SCALE
    private val areaChips = mutableListOf<Chip>()

    companion object {
        const val EXTRA_TRAINING_SESSION = "extra_training_session"
        const val EXTRA_WORKED_MUSCLES   = "extra_worked_muscles"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStretchSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_TRAINING_SESSION, TrainingSession::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_TRAINING_SESSION)
        }

        workedMuscles = intent.getStringArrayListExtra(EXTRA_WORKED_MUSCLES)
            .orEmpty()
            .mapNotNull { name -> runCatching { TargetMuscle.valueOf(name) }.getOrNull() }
            .toSet()

        scope = StretchSettingsManager.scope(this)
        holdScale = StretchSettingsManager.holdScale(this)

        setupScopeChips()
        setupAreaChips()
        setupHoldChips()

        binding.buttonContinue.setOnClickListener { onContinue() }

        refresh()
    }

    // ---------------------------------------------------------------- setup

    private fun setupScopeChips() {
        binding.chipGroupScope.check(chipIdFor(scope))
        binding.chipGroupScope.setOnCheckedStateChangeListener { _, checkedIds ->
            scope = checkedIds.firstOrNull()?.let { scopeFor(it) } ?: return@setOnCheckedStateChangeListener
            refresh()
        }
    }

    private fun chipIdFor(scope: StretchScope): Int = when (scope) {
        StretchScope.AUTO     -> R.id.chip_scope_auto
        StretchScope.FULL     -> R.id.chip_scope_full
        StretchScope.SPECIFIC -> R.id.chip_scope_specific
        StretchScope.NONE     -> R.id.chip_scope_none
    }

    private fun scopeFor(chipId: Int): StretchScope? = when (chipId) {
        R.id.chip_scope_auto     -> StretchScope.AUTO
        R.id.chip_scope_full     -> StretchScope.FULL
        R.id.chip_scope_specific -> StretchScope.SPECIFIC
        R.id.chip_scope_none     -> StretchScope.NONE
        else -> null
    }

    /**
     * Built from [DefaultStretchesHelper.STRETCH_AREAS] rather than declared in XML so a
     * seventh area can never appear here without its chip.
     *
     * Pre-checked to the areas this session actually trained — the common case for "Specific"
     * is trimming or extending today's list, not building one from nothing. Only when the
     * session hit no area at all (an empty or warm-up-only workout) does the remembered
     * selection stand in.
     */
    private fun setupAreaChips() {
        val worked = DefaultStretchesHelper.STRETCH_AREAS
            .filterValues { muscles -> muscles.any { it in workedMuscles } }
            .keys
        val preChecked = worked.ifEmpty { StretchSettingsManager.specificAreas(this) }

        DefaultStretchesHelper.STRETCH_AREAS.keys.forEach { area ->
            val chip = Chip(this).apply {
                // A ChipGroup tracks selection by child id, and a chip built in code has none.
                id = View.generateViewId()
                text = area
                isCheckable = true
                isChecked = area in preChecked
                applyChoiceChipStyle()
                setOnCheckedChangeListener { _, _ -> refresh() }
            }
            areaChips.add(chip)
            binding.chipGroupAreas.addView(chip)
        }
    }

    /** The multiplier chips. Each carries its scale as its tag, so the group's checked id maps
     *  straight back to a value without a parallel lookup table. */
    private fun setupHoldChips() {
        val chips = StretchSettingsManager.HOLD_SCALES.map { scale ->
            Chip(this).apply {
                id = View.generateViewId()
                text = getString(R.string.stretch_setup_hold_scale, formatScale(scale))
                isCheckable = true
                tag = scale
                applyChoiceChipStyle()
            }
        }
        chips.forEach { binding.chipGroupHold.addView(it) }

        // Checked after the chips are in the group — a chip checked while still detached is not
        // registered by the group, which would leave singleSelection with nothing to uncheck.
        val selected = chips.firstOrNull { it.tag == holdScale } ?: chips.first()
        binding.chipGroupHold.check(selected.id)
        holdScale = selected.tag as Float

        binding.chipGroupHold.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it) }
                ?: return@setOnCheckedStateChangeListener
            holdScale = chip.tag as Float
            refresh()
        }
    }

    /** "1", not "1.0" — a multiplier reads as a whole number wherever it is one. */
    private fun formatScale(scale: Float): String =
        if (scale % 1f == 0f) scale.toInt().toString() else scale.toString()

    // ---------------------------------------------------------------- state

    private fun refresh() {
        binding.textScopeCaption.setText(scope.captionRes)
        binding.chipGroupAreas.visibility =
            if (scope == StretchScope.SPECIFIC) View.VISIBLE else View.GONE

        val stretches = resolveStretches()
        // Hold time has nothing to act on when there are no stretches, so the whole card goes
        // rather than sitting there inert.
        binding.cardHold.visibility = if (stretches.isEmpty()) View.GONE else View.VISIBLE

        binding.textSummary.text = summaryFor(stretches)
        binding.textHoldCaption.text = holdCaptionFor(stretches)
    }

    private fun resolveStretches(): List<StretchItem> = when (scope) {
        StretchScope.AUTO     -> DefaultStretchesHelper.getStretchesFor(workedMuscles)
        // Deliberately not getStretchesFor(every muscle): that path is a set-cover and would
        // drop the stretches whose muscles an earlier one already covers. "Full body" means
        // the whole catalogue.
        StretchScope.FULL     -> DefaultStretchesHelper.ALL_STRETCHES
        StretchScope.SPECIFIC -> DefaultStretchesHelper.getStretchesFor(selectedAreaMuscles())
        StretchScope.NONE     -> emptyList()
    }

    private fun selectedAreas(): Set<String> =
        areaChips.filter { it.isChecked }.map { it.text.toString() }.toSet()

    private fun selectedAreaMuscles(): Set<TargetMuscle> =
        selectedAreas().flatMap { DefaultStretchesHelper.STRETCH_AREAS[it].orEmpty() }.toSet()

    /**
     * Counts *steps*, not stretches: a unilateral stretch is two holds, and the cool-down's own
     * "Stretch 3 of 12" label counts them that way. A summary that counted stretches instead
     * would promise 24 and then show 42.
     */
    private fun summaryFor(stretches: List<StretchItem>): String {
        if (stretches.isEmpty()) return getString(R.string.stretch_setup_summary_empty)

        val steps = stretches.sumOf { DefaultStretchesHelper.holdCount(it) }
        val holdTotal = stretches.sumOf { DefaultStretchesHelper.holdCount(it) * scaledHold(it) }
        // Every step but the first is preceded by a get-ready countdown; the first waits on a
        // tap instead, so it contributes nothing predictable.
        val seconds = holdTotal + (steps - 1).coerceAtLeast(0) * StretchCooldownActivity.READINESS_SECONDS
        val minutes = ceil(seconds / 60.0).toInt().coerceAtLeast(1)

        return if (steps == 1) {
            getString(R.string.stretch_setup_summary_one, minutes)
        } else {
            getString(R.string.stretch_setup_summary, steps, minutes)
        }
    }

    /** The hold range the current multiplier produces, so the chips show their effect in seconds. */
    private fun holdCaptionFor(stretches: List<StretchItem>): String {
        if (stretches.isEmpty()) return ""
        val holds = stretches.map { scaledHold(it) }
        val min = holds.min()
        val max = holds.max()
        return if (min == max) {
            getString(R.string.stretch_setup_hold_caption_single, min)
        } else {
            getString(R.string.stretch_setup_hold_caption, min, max)
        }
    }

    private fun scaledHold(stretch: StretchItem): Int =
        StretchSettingsManager.scaledHold(stretch.durationSeconds, holdScale)

    // ---------------------------------------------------------------- exit

    private fun onContinue() {
        StretchSettingsManager.setScope(this, scope)
        StretchSettingsManager.setHoldScale(this, holdScale)
        // Only meaningful for SPECIFIC, and persisting otherwise would overwrite the user's
        // remembered areas with whatever this session happened to pre-check.
        if (scope == StretchScope.SPECIFIC) {
            StretchSettingsManager.setSpecificAreas(this, selectedAreas())
        }

        if (resolveStretches().isEmpty()) goToReport() else goToCooldown()
    }

    private fun goToCooldown() {
        val muscles = when (scope) {
            StretchScope.SPECIFIC -> selectedAreaMuscles()
            else -> workedMuscles
        }
        startActivity(Intent(this, StretchCooldownActivity::class.java).apply {
            session?.let { putExtra(StretchCooldownActivity.EXTRA_TRAINING_SESSION, it) }
            putExtra(StretchCooldownActivity.EXTRA_ALL_STRETCHES, scope == StretchScope.FULL)
            putStringArrayListExtra(
                StretchCooldownActivity.EXTRA_WORKED_MUSCLES,
                ArrayList(muscles.map { it.name })
            )
        })
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun goToReport() {
        startActivity(Intent(this, WorkoutReportActivity::class.java).apply {
            session?.let { putExtra(WorkoutReportActivity.EXTRA_TRAINING_SESSION, it) }
        })
        setResult(Activity.RESULT_OK)
        finish()
    }

    /** ActiveTrainingActivity has already finished by the time this screen is up, so backing
     *  out must still land on the report — otherwise the session is saved but never shown. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goToReport()
    }
}
