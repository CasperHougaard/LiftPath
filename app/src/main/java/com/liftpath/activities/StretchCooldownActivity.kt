package com.liftpath.activities

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.liftpath.R
import com.liftpath.databinding.ActivityStretchCooldownBinding
import com.liftpath.helpers.DefaultStretchesHelper
import com.liftpath.models.Laterality
import com.liftpath.models.StretchItem
import com.liftpath.models.TargetMuscle
import com.liftpath.models.TrainingSession

class StretchCooldownActivity : AppCompatActivity() {

    private enum class StretchState { WAITING, COUNTDOWN, STRETCHING }
    private enum class Side { LEFT, RIGHT }
    private data class StretchStep(val stretch: StretchItem, val side: Side?)

    private lateinit var binding: ActivityStretchCooldownBinding
    private var session: TrainingSession? = null
    private var isStandalone = false
    private var steps = emptyList<StretchStep>()
    private var currentIndex = 0
    private var countDownTimer: CountDownTimer? = null
    private var stretchState = StretchState.WAITING

    companion object {
        const val EXTRA_TRAINING_SESSION = "extra_training_session"
        const val EXTRA_WORKED_MUSCLES   = "extra_worked_muscles"
        /** When true, the screen runs without a session and finishing returns to the caller. */
        const val EXTRA_STANDALONE       = "extra_standalone"
        private const val READINESS_SECONDS = 5
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStretchCooldownBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.layoutHeader.setPadding(
                binding.layoutHeader.paddingLeft,
                insets.top + resources.getDimensionPixelSize(R.dimen.stretch_header_padding_top),
                binding.layoutHeader.paddingRight,
                binding.layoutHeader.paddingBottom
            )
            val btnParams = binding.layoutButtons.layoutParams
                    as android.view.ViewGroup.MarginLayoutParams
            btnParams.bottomMargin = insets.bottom +
                    resources.getDimensionPixelSize(R.dimen.stretch_button_bottom_margin)
            binding.layoutButtons.layoutParams = btnParams
            windowInsets
        }

        (binding.imageBgAnimation.drawable as? Animatable)?.start()

        isStandalone = intent.getBooleanExtra(EXTRA_STANDALONE, false)

        val s = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_TRAINING_SESSION, TrainingSession::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_TRAINING_SESSION)
        }
        if (s == null && !isStandalone) { goToReport(null); return }
        session = s

        val muscleNames = intent.getStringArrayListExtra(EXTRA_WORKED_MUSCLES) ?: arrayListOf()
        val workedMuscles = muscleNames
            .mapNotNull { name -> runCatching { TargetMuscle.valueOf(name) }.getOrNull() }
            .toSet()

        val stretches = DefaultStretchesHelper.getStretchesFor(workedMuscles)
        steps = stretches.flatMap { stretch ->
            if (stretch.laterality == Laterality.UNILATERAL)
                listOf(StretchStep(stretch, Side.LEFT), StretchStep(stretch, Side.RIGHT))
            else
                listOf(StretchStep(stretch, null))
        }
        if (steps.isEmpty()) { finishFlow(); return }

        binding.buttonSkipAll.setOnClickListener { finishFlow() }
        binding.buttonSkip.setOnClickListener { advanceOrFinish() }
        binding.buttonNext.setOnClickListener {
            when (stretchState) {
                StretchState.WAITING   -> startActualCountdown(steps[currentIndex].stretch.durationSeconds)
                StretchState.COUNTDOWN -> startActualCountdown(steps[currentIndex].stretch.durationSeconds)
                StretchState.STRETCHING -> advanceOrFinish()
            }
        }

        showStretch(currentIndex)
    }

    private fun showStretch(index: Int) {
        val step = steps[index]
        val stretch = step.stretch
        val total = steps.size

        binding.textStretchName.text = stretch.name
        binding.textTargetMuscle.text = stretch.targetMuscles.joinToString(" · ") { it.displayName }
        binding.imageStretchIllustration.setImageResource(stretch.illustrationRes)
        binding.textProgressLabel.text = getString(R.string.stretch_progress_label, index + 1, total)

        binding.progressBar.max = total
        binding.progressBar.progress = index + 1

        showSideTiles(step.side)

        if (index == 0) {
            applyState(StretchState.WAITING)
            binding.textButtonNext.text = getString(R.string.stretch_button_ready)
        } else {
            startReadinessCountdown(stretch.durationSeconds)
        }
    }

    /** Shows the Left/Right tile pair for unilateral stretches; hides it otherwise.
     *  The non-active side's tile is marked "done" once it's the second (right) step,
     *  since that means the left side was already completed. */
    private fun showSideTiles(side: Side?) {
        if (side == null) {
            binding.layoutSideTiles.visibility = View.GONE
            return
        }
        binding.layoutSideTiles.visibility = View.VISIBLE

        val leftDone = side == Side.RIGHT
        styleSideTile(binding.tileSideLeft, binding.fillSideLeft, active = side == Side.LEFT, done = leftDone)
        styleSideTile(binding.tileSideRight, binding.fillSideRight, active = side == Side.RIGHT, done = false)
    }

    private fun styleSideTile(tile: View, fill: View, active: Boolean, done: Boolean) {
        tile.setBackgroundResource(
            if (active || done) R.drawable.bg_tile_side_active else R.drawable.bg_tile_side_pending
        )
        fill.scaleX = if (done) 1f else 0f
    }

    /** Updates the active step's tile fill to track elapsed time; a no-op for bilateral steps. */
    private fun updateActiveTileFill(elapsedFraction: Float) {
        val side = steps.getOrNull(currentIndex)?.side ?: return
        val fill = if (side == Side.LEFT) binding.fillSideLeft else binding.fillSideRight
        fill.scaleX = elapsedFraction.coerceIn(0f, 1f)
    }

    private fun applyState(state: StretchState) {
        stretchState = state
        when (state) {
            StretchState.WAITING -> {
                binding.textReadyHint.visibility = View.VISIBLE
                binding.layoutTimer.visibility = View.GONE
            }
            StretchState.COUNTDOWN -> {
                binding.textReadyHint.visibility = View.GONE
                binding.layoutTimer.visibility = View.VISIBLE
                binding.textGetReadyLabel.visibility = View.VISIBLE
                binding.textButtonNext.text = getString(R.string.stretch_button_start)
            }
            StretchState.STRETCHING -> {
                binding.textReadyHint.visibility = View.GONE
                binding.layoutTimer.visibility = View.VISIBLE
                binding.textGetReadyLabel.visibility = View.GONE
                val total = steps.size
                binding.textButtonNext.text = if (currentIndex == total - 1)
                    getString(R.string.stretch_button_finish)
                else
                    getString(R.string.stretch_button_next)
            }
        }
    }

    private fun startReadinessCountdown(stretchSeconds: Int) {
        countDownTimer?.cancel()
        applyState(StretchState.COUNTDOWN)
        binding.progressTimerFg.max = 100
        binding.progressTimerFg.progress = 0
        binding.textTimer.text = READINESS_SECONDS.toString()

        countDownTimer = object : CountDownTimer(READINESS_SECONDS * 1000L, 50L) {
            override fun onTick(millisUntilFinished: Long) {
                binding.textTimer.text = (millisUntilFinished / 1000L + 1).toString()
                val elapsed = READINESS_SECONDS * 1000L - millisUntilFinished
                val fraction = elapsed.toFloat() / (READINESS_SECONDS * 1000L)
                binding.progressTimerFg.progress = (fraction * 100).toInt()
                updateActiveTileFill(fraction)
            }
            override fun onFinish() {
                startActualCountdown(stretchSeconds)
            }
        }.start()
    }

    private fun startActualCountdown(totalSeconds: Int) {
        countDownTimer?.cancel()
        applyState(StretchState.STRETCHING)
        binding.progressTimerFg.max = 100
        binding.progressTimerFg.progress = 100
        binding.textTimer.text = totalSeconds.toString()
        updateActiveTileFill(0f)

        countDownTimer = object : CountDownTimer(totalSeconds * 1000L, 50L) {
            override fun onTick(millisUntilFinished: Long) {
                binding.textTimer.text = (millisUntilFinished / 1000L + 1).toString()
                val elapsed = totalSeconds * 1000L - millisUntilFinished
                binding.progressTimerFg.progress =
                    (millisUntilFinished.toFloat() / (totalSeconds * 1000L) * 100).toInt()
                updateActiveTileFill(elapsed.toFloat() / (totalSeconds * 1000L))
            }
            override fun onFinish() {
                binding.textTimer.text = "0"
                binding.progressTimerFg.progress = 0
                updateActiveTileFill(1f)
                advanceOrFinish()
            }
        }.start()
    }

    private fun advanceOrFinish() {
        countDownTimer?.cancel()
        if (currentIndex < steps.size - 1) {
            currentIndex++
            showStretch(currentIndex)
        } else {
            finishFlow()
        }
    }

    /** Exits the screen: standalone sessions return to the caller, post-workout goes to the report. */
    private fun finishFlow() {
        if (isStandalone) {
            countDownTimer?.cancel()
            setResult(Activity.RESULT_OK)
            finish()
        } else {
            goToReport(session)
        }
    }

    private fun goToReport(trainingSession: TrainingSession?) {
        countDownTimer?.cancel()
        val intent = Intent(this, WorkoutReportActivity::class.java)
        if (trainingSession != null) {
            intent.putExtra(WorkoutReportActivity.EXTRA_TRAINING_SESSION, trainingSession)
        }
        startActivity(intent)
        setResult(Activity.RESULT_OK)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishFlow()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
