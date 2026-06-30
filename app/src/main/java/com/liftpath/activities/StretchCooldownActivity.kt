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
import com.liftpath.models.TargetMuscle
import com.liftpath.models.TrainingSession

class StretchCooldownActivity : AppCompatActivity() {

    private enum class StretchState { WAITING, COUNTDOWN, STRETCHING }

    private lateinit var binding: ActivityStretchCooldownBinding
    private lateinit var session: TrainingSession
    private var stretches = emptyList<com.liftpath.models.StretchItem>()
    private var currentIndex = 0
    private var countDownTimer: CountDownTimer? = null
    private var stretchState = StretchState.WAITING

    companion object {
        const val EXTRA_TRAINING_SESSION = "extra_training_session"
        const val EXTRA_WORKED_MUSCLES   = "extra_worked_muscles"
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

        val s = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_TRAINING_SESSION, TrainingSession::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_TRAINING_SESSION)
        }
        if (s == null) { goToReport(null); return }
        session = s

        val muscleNames = intent.getStringArrayListExtra(EXTRA_WORKED_MUSCLES) ?: arrayListOf()
        val workedMuscles = muscleNames
            .mapNotNull { name -> runCatching { TargetMuscle.valueOf(name) }.getOrNull() }
            .toSet()

        stretches = DefaultStretchesHelper.getStretchesFor(workedMuscles)
        if (stretches.isEmpty()) { goToReport(session); return }

        binding.buttonSkipAll.setOnClickListener { goToReport(session) }
        binding.buttonSkip.setOnClickListener { advanceOrFinish() }
        binding.buttonNext.setOnClickListener {
            when (stretchState) {
                StretchState.WAITING   -> startActualCountdown(stretches[currentIndex].durationSeconds)
                StretchState.COUNTDOWN -> startActualCountdown(stretches[currentIndex].durationSeconds)
                StretchState.STRETCHING -> advanceOrFinish()
            }
        }

        showStretch(currentIndex)
    }

    private fun showStretch(index: Int) {
        val stretch = stretches[index]
        val total = stretches.size

        binding.textStretchName.text = stretch.name
        binding.textTargetMuscle.text = stretch.targetMuscles.joinToString(" · ") { it.displayName }
        binding.textProgressLabel.text = getString(R.string.stretch_progress_label, index + 1, total)

        binding.progressBar.max = total
        binding.progressBar.progress = index + 1

        if (index == 0) {
            applyState(StretchState.WAITING)
            binding.textButtonNext.text = getString(R.string.stretch_button_ready)
        } else {
            startReadinessCountdown(stretch.durationSeconds)
        }
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
                val total = stretches.size
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
                binding.progressTimerFg.progress =
                    (elapsed.toFloat() / (READINESS_SECONDS * 1000L) * 100).toInt()
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

        countDownTimer = object : CountDownTimer(totalSeconds * 1000L, 50L) {
            override fun onTick(millisUntilFinished: Long) {
                binding.textTimer.text = (millisUntilFinished / 1000L + 1).toString()
                binding.progressTimerFg.progress =
                    (millisUntilFinished.toFloat() / (totalSeconds * 1000L) * 100).toInt()
            }
            override fun onFinish() {
                binding.textTimer.text = "0"
                binding.progressTimerFg.progress = 0
                advanceOrFinish()
            }
        }.start()
    }

    private fun advanceOrFinish() {
        countDownTimer?.cancel()
        if (currentIndex < stretches.size - 1) {
            currentIndex++
            showStretch(currentIndex)
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
        goToReport(session)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
