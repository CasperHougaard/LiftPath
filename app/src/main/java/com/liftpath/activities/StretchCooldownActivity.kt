package com.liftpath.activities

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.liftpath.R
import com.liftpath.databinding.ActivityStretchCooldownBinding
import com.liftpath.helpers.BluetoothBeepHelper
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

    // Pause/resume bookkeeping for the current phase (readiness countdown or the stretch hold itself).
    // CountDownTimer has no native pause, so pausing cancels it and remembers how much of the phase
    // has elapsed; resuming starts a fresh CountDownTimer for just the remaining time.
    private var isPaused = false
    private var isReadinessPhase = false
    private var pendingStretchSeconds = 0
    private var currentPhaseTotalMillis = 0L
    private var currentPhaseElapsedMillis = 0L
    private var currentSegmentTotalMillis = 0L
    private var lastMillisUntilFinished = 0L

    companion object {
        const val EXTRA_TRAINING_SESSION = "extra_training_session"
        const val EXTRA_WORKED_MUSCLES   = "extra_worked_muscles"
        /** When true, the screen runs without a session and finishing returns to the caller. */
        const val EXTRA_STANDALONE       = "extra_standalone"
        private const val READINESS_SECONDS = 5
        private const val STRETCH_DONE_CHANNEL_ID = "StretchDoneChannel"
        private const val STRETCH_DONE_NOTIFICATION_ID = 1101
        private const val STRETCH_DONE_AUTO_DISMISS_MS = 2500L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStretchCooldownBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep the screen from sleeping for the whole stretch flow; the user isn't touching
        // the phone while holding a stretch, so the OS would otherwise dim/lock mid-timer.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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
        binding.buttonPause.setOnClickListener { togglePause() }
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
     *  since that means the left side was already completed.
     *
     *  The tile fill already animates with elapsed time (see [updateActiveTileFill]), so when the
     *  tiles are showing, the standalone timer progress bar would just be a second bar tracking the
     *  same thing. Hide it then and let the tile pair be the single time-progress indicator. */
    private fun showSideTiles(side: Side?) {
        if (side == null) {
            binding.layoutSideTiles.visibility = View.GONE
            binding.progressTimerFg.visibility = View.VISIBLE
            return
        }
        binding.layoutSideTiles.visibility = View.VISIBLE
        binding.progressTimerFg.visibility = View.GONE

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
                binding.buttonPause.visibility = View.GONE
            }
            StretchState.COUNTDOWN -> {
                binding.textReadyHint.visibility = View.GONE
                binding.layoutTimer.visibility = View.VISIBLE
                binding.buttonPause.visibility = View.VISIBLE
                binding.textGetReadyLabel.visibility = View.VISIBLE
                binding.textButtonNext.text = getString(R.string.stretch_button_start)
            }
            StretchState.STRETCHING -> {
                binding.textReadyHint.visibility = View.GONE
                binding.layoutTimer.visibility = View.VISIBLE
                binding.buttonPause.visibility = View.VISIBLE
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
        applyState(StretchState.COUNTDOWN)
        binding.progressTimerFg.max = 100
        isReadinessPhase = true
        pendingStretchSeconds = stretchSeconds
        beginPhase(READINESS_SECONDS * 1000L)
    }

    private fun startActualCountdown(totalSeconds: Int) {
        applyState(StretchState.STRETCHING)
        binding.progressTimerFg.max = 100
        isReadinessPhase = false
        beginPhase(totalSeconds * 1000L)
    }

    /** Starts a fresh phase (readiness or the stretch hold) from the beginning. */
    private fun beginPhase(totalMillis: Long) {
        currentPhaseTotalMillis = totalMillis
        currentPhaseElapsedMillis = 0L
        isPaused = false
        updatePauseButtonIcon()
        runSegment(totalMillis)
    }

    /** Runs a CountDownTimer for [remainingMillis] of the current phase. Called both when a phase
     *  starts (remainingMillis == full phase) and when resuming from a pause (remainingMillis ==
     *  whatever was left). [currentPhaseElapsedMillis] anchors elapsed/progress math across segments
     *  so the displayed progress doesn't jump when pausing and resuming. */
    private fun runSegment(remainingMillis: Long) {
        countDownTimer?.cancel()
        currentSegmentTotalMillis = remainingMillis
        lastMillisUntilFinished = remainingMillis

        countDownTimer = object : CountDownTimer(remainingMillis, 50L) {
            override fun onTick(millisUntilFinished: Long) {
                lastMillisUntilFinished = millisUntilFinished
                val elapsedTotal = currentPhaseElapsedMillis + (currentSegmentTotalMillis - millisUntilFinished)
                val remainingTotal = (currentPhaseTotalMillis - elapsedTotal).coerceAtLeast(0L)
                val elapsedFraction = elapsedTotal.toFloat() / currentPhaseTotalMillis

                binding.textTimer.text = (remainingTotal / 1000L + 1).toString()
                binding.progressTimerFg.progress = if (isReadinessPhase) {
                    (elapsedFraction * 100).toInt()
                } else {
                    ((1f - elapsedFraction) * 100).toInt()
                }
                updateActiveTileFill(elapsedFraction)
            }
            override fun onFinish() {
                if (isReadinessPhase) {
                    startActualCountdown(pendingStretchSeconds)
                } else {
                    binding.textTimer.text = "0"
                    binding.progressTimerFg.progress = 0
                    updateActiveTileFill(1f)
                    notifyStretchDone()
                    advanceOrFinish()
                }
            }
        }.start()
    }

    private fun togglePause() {
        if (stretchState == StretchState.WAITING) return
        if (isPaused) resumeTimer() else pauseTimer()
    }

    private fun pauseTimer() {
        if (isPaused) return
        countDownTimer?.cancel()
        countDownTimer = null
        currentPhaseElapsedMillis += currentSegmentTotalMillis - lastMillisUntilFinished
        isPaused = true
        updatePauseButtonIcon()
    }

    private fun resumeTimer() {
        if (!isPaused) return
        isPaused = false
        updatePauseButtonIcon()
        val remaining = (currentPhaseTotalMillis - currentPhaseElapsedMillis).coerceAtLeast(0L)
        runSegment(remaining)
    }

    private fun updatePauseButtonIcon() {
        binding.imagePauseIcon.setImageResource(if (isPaused) R.drawable.ic_play else R.drawable.ic_pause)
        binding.buttonPause.contentDescription = getString(
            if (isPaused) R.string.stretch_resume_content_description else R.string.stretch_pause_content_description
        )
    }

    /** Buzzes the phone once and posts a transient notification so a paired watch that
     *  mirrors phone notifications buzzes too, signalling this stretch is complete. */
    private fun notifyStretchDone() {
        vibrateOnce()

        // Beep over Bluetooth (if that's the active audio route) without ducking music
        BluetoothBeepHelper.playIfBluetoothActive(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                STRETCH_DONE_CHANNEL_ID,
                getString(R.string.notification_channel_stretch_done),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_description_stretch_done)
                enableVibration(false) // Phone vibration already handled by vibrateOnce()
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, STRETCH_DONE_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_stretch_done_title))
            .setContentText(getString(R.string.notification_stretch_done_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .build()

        try {
            val notificationManager = NotificationManagerCompat.from(this)
            if (notificationManager.areNotificationsEnabled()) {
                notificationManager.notify(STRETCH_DONE_NOTIFICATION_ID, notification)
                Handler(mainLooper).postDelayed(
                    { notificationManager.cancel(STRETCH_DONE_NOTIFICATION_ID) },
                    STRETCH_DONE_AUTO_DISMISS_MS
                )
            }
        } catch (e: SecurityException) {
            android.util.Log.w("StretchCooldown", "Cannot show notification: permission denied", e)
        }
    }

    private fun vibrateOnce() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(300)
        }
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
