package com.liftpath.activities

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.liftpath.R
import com.liftpath.adapters.CircuitStationAdapter
import com.liftpath.components.CircuitRoundLogBottomSheet
import com.liftpath.databinding.ActivityCircuitRunnerBinding
import com.liftpath.helpers.BodyWeightHelper
import com.liftpath.helpers.CircuitStore
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.RestTimerHelper
import com.liftpath.helpers.showWithTransparentWindow
import com.liftpath.models.CircuitInstance
import com.liftpath.models.CircuitItem
import com.liftpath.models.ExerciseEntry
import com.liftpath.models.ExerciseLibraryItem

/**
 * Runs one circuit: an up-counting clock, the station list, and round-by-round logging.
 *
 * The round count is never enforced here — [instance.suggestedRounds] is display only, and
 * "Start round" / "Finish circuit" are always both available. See the file header comment on
 * activity_circuit_runner.xml for the reasoning.
 */
class CircuitRunnerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCircuitRunnerBinding
    private lateinit var stationAdapter: CircuitStationAdapter
    private lateinit var library: List<ExerciseLibraryItem>
    private var bodyweightKg: Float? = null
    private var workoutType: String? = null

    private lateinit var instance: CircuitInstance
    private val sessionEntries = mutableListOf<ExerciseEntry>()

    private enum class Mode { IDLE, RUNNING, RESTING }
    private var mode = Mode.IDLE

    private var roundElapsedSeconds = 0
    private var roundStartMillis = 0L
    private var roundPaused = false

    private var restRemainingSeconds = 0

    private var stationRunningItemId: String? = null
    private val stationBaseSeconds = mutableMapOf<String, Int>()
    private var stationRunningStartMillis: Long = 0L

    private val tickHandler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            onTick()
            tickHandler.postDelayed(this, 1000L)
        }
    }

    companion object {
        const val EXTRA_CIRCUIT_INSTANCE = "extra_circuit_instance"
        const val EXTRA_CIRCUIT_ENTRIES = "extra_circuit_entries"
        const val EXTRA_WORKOUT_TYPE = "extra_workout_type"
        const val RESULT_CIRCUIT_INSTANCE = "result_circuit_instance"
        const val RESULT_CIRCUIT_ENTRIES = "result_circuit_entries"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityCircuitRunnerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.layoutHeader.setPadding(
                binding.layoutHeader.paddingLeft,
                insets.top + resources.getDimensionPixelSize(R.dimen.lp_gutter),
                binding.layoutHeader.paddingRight,
                binding.layoutHeader.paddingBottom
            )
            val actionsParams = binding.layoutActions.layoutParams as ViewGroup.MarginLayoutParams
            actionsParams.bottomMargin = insets.bottom
            binding.layoutActions.layoutParams = actionsParams
            windowInsets
        }

        val loadedInstance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_CIRCUIT_INSTANCE, CircuitInstance::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_CIRCUIT_INSTANCE)
        }
        if (loadedInstance == null) {
            finish()
            return
        }
        instance = loadedInstance

        val loadedEntries = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_CIRCUIT_ENTRIES, ExerciseEntry::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_CIRCUIT_ENTRIES)
        }
        sessionEntries.addAll(loadedEntries ?: emptyList())
        workoutType = intent.getStringExtra(EXTRA_WORKOUT_TYPE)

        val jsonHelper = JsonHelper(this)
        library = jsonHelper.readTrainingData().exerciseLibrary
        bodyweightKg = BodyWeightHelper.getCurrentBodyweightKg(this)

        binding.textCircuitName.text = instance.name
        stationAdapter = CircuitStationAdapter(instance.items, library) { item -> toggleStationTimer(item) }
        binding.recyclerViewStations.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewStations.adapter = stationAdapter
        refreshStationList()

        binding.buttonBack.setOnClickListener { finishWithResult(markFinished = false) }
        binding.buttonFinish.setOnClickListener { confirmFinish() }
        binding.buttonPrimary.setOnClickListener { onPrimaryClicked() }
        binding.buttonLogRound.setOnClickListener { logPendingRound() }
        binding.buttonPause.setOnClickListener { togglePause() }
        binding.buttonRestSkip.setOnClickListener { endRest() }
        binding.buttonRestPlus.setOnClickListener {
            restRemainingSeconds += 15
            updateRestUi()
        }

        onBackPressedDispatcher.addCallback(this) { finishWithResult(markFinished = false) }

        updateRoundLabel()
        updateActionButtons()
    }

    override fun onResume() {
        super.onResume()
        tickHandler.post(tickRunnable)
    }

    override fun onPause() {
        super.onPause()
        tickHandler.removeCallbacks(tickRunnable)
    }

    private fun refreshStationList() {
        val circuitEntries = sessionEntries.filter { it.groupId == instance.instanceId }
        stationAdapter.update(instance.items, library, circuitEntries)
    }

    private fun currentRound(): Int = instance.completedRounds + 1

    private fun updateRoundLabel() {
        val suggested = instance.suggestedRounds
        binding.textRoundLabel.text = if (suggested != null) {
            getString(R.string.circuit_round_of, currentRound(), suggested)
        } else {
            getString(R.string.circuit_round_number, currentRound())
        }
    }

    private fun updateActionButtons() {
        binding.buttonLogRound.visibility =
            if (instance.pendingRounds.isNotEmpty() && mode == Mode.IDLE) View.VISIBLE else View.GONE
        binding.buttonPrimary.text = when (mode) {
            Mode.RUNNING -> getString(R.string.circuit_done_round)
            else -> getString(R.string.circuit_start_round)
        }
        binding.buttonPause.visibility = if (mode == Mode.RUNNING) View.VISIBLE else View.GONE
    }

    private fun onPrimaryClicked() {
        when (mode) {
            Mode.IDLE -> startRound()
            Mode.RESTING -> {
                endRest()
                startRound()
            }
            Mode.RUNNING -> stopRound()
        }
    }

    private fun startRound() {
        mode = Mode.RUNNING
        roundElapsedSeconds = 0
        roundPaused = false
        roundStartMillis = System.currentTimeMillis()
        binding.layoutRest.visibility = View.GONE
        binding.textClockCaption.text = getString(R.string.circuit_clock_elapsed)
        binding.textClock.text = RestTimerHelper.formatDuration(0)
        stationAdapter.resetStationTimers()
        stationBaseSeconds.clear()
        stationRunningItemId = null
        updateActionButtons()
    }

    private fun stopRound() {
        mode = Mode.IDLE
        finalizeStationTimerIfRunning()
        val elapsed = roundElapsedSeconds
        val round = currentRound()
        val prefill = CircuitStore.prefillForRound(instance, round, sessionEntries, library)
        CircuitRoundLogBottomSheet.newInstance(
            items = instance.items,
            library = library,
            round = round,
            prefill = prefill,
            bodyweightKg = bodyweightKg,
            onSaved = { inputs -> completeRound(round, elapsed, inputs, logged = true) },
            onLater = { completeRound(round, elapsed, prefill, logged = false) }
        ).show(supportFragmentManager, "CircuitRoundLog")
        updateActionButtons()
    }

    private fun completeRound(
        round: Int,
        elapsedSeconds: Int,
        inputs: List<CircuitStore.StationInput>,
        logged: Boolean
    ) {
        if (logged) {
            val entries = CircuitStore.entriesForRound(instance, round, inputs, library, bodyweightKg, workoutType)
            sessionEntries.removeAll { it.groupId == instance.instanceId && it.setNumber == round }
            sessionEntries.addAll(entries)
        }
        instance = instance.copy(
            completedRounds = maxOf(instance.completedRounds, round),
            roundWorkSeconds = instance.roundWorkSeconds + elapsedSeconds,
            loggedRounds = if (logged) (instance.loggedRounds + round).distinct() else instance.loggedRounds
        )
        refreshStationList()
        updateRoundLabel()
        if (instance.restBetweenRoundsSeconds > 0) {
            startRest()
        } else {
            mode = Mode.IDLE
            binding.textClock.text = getString(R.string.circuit_clock_zero)
            binding.textClockCaption.text = getString(R.string.circuit_clock_ready)
        }
        updateActionButtons()
    }

    private fun logPendingRound() {
        val round = instance.pendingRounds.lastOrNull() ?: return
        val prefill = CircuitStore.prefillForRound(instance, round, sessionEntries, library)
        CircuitRoundLogBottomSheet.newInstance(
            items = instance.items,
            library = library,
            round = round,
            prefill = prefill,
            bodyweightKg = bodyweightKg,
            onSaved = { inputs ->
                val entries = CircuitStore.entriesForRound(instance, round, inputs, library, bodyweightKg, workoutType)
                sessionEntries.removeAll { it.groupId == instance.instanceId && it.setNumber == round }
                sessionEntries.addAll(entries)
                instance = instance.copy(loggedRounds = (instance.loggedRounds + round).distinct())
                refreshStationList()
                updateActionButtons()
            },
            onLater = {}
        ).show(supportFragmentManager, "CircuitRoundLogPending")
    }

    private fun startRest() {
        mode = Mode.RESTING
        restRemainingSeconds = instance.restBetweenRoundsSeconds
        binding.layoutRest.visibility = View.VISIBLE
        updateRestUi()
    }

    private fun endRest() {
        mode = Mode.IDLE
        binding.layoutRest.visibility = View.GONE
        binding.textClock.text = getString(R.string.circuit_clock_zero)
        binding.textClockCaption.text = getString(R.string.circuit_clock_ready)
        updateActionButtons()
    }

    private fun updateRestUi() {
        binding.textRestCountdown.text = RestTimerHelper.formatDuration(restRemainingSeconds)
    }

    private fun togglePause() {
        roundPaused = !roundPaused
        if (!roundPaused) {
            roundStartMillis = System.currentTimeMillis()
        } else {
            roundElapsedSeconds += ((System.currentTimeMillis() - roundStartMillis) / 1000).toInt()
        }
        binding.imagePauseIcon.setImageResource(if (roundPaused) R.drawable.ic_play else R.drawable.ic_pause)
    }

    private fun toggleStationTimer(item: CircuitItem) {
        if (stationRunningItemId == item.id) {
            finalizeStationTimerIfRunning()
        } else {
            finalizeStationTimerIfRunning()
            stationRunningItemId = item.id
            stationRunningStartMillis = System.currentTimeMillis()
        }
        pushStationTimerUpdate()
    }

    private fun finalizeStationTimerIfRunning() {
        val runningId = stationRunningItemId ?: return
        val elapsed = ((System.currentTimeMillis() - stationRunningStartMillis) / 1000).toInt()
        stationBaseSeconds[runningId] = (stationBaseSeconds[runningId] ?: 0) + elapsed
        stationRunningItemId = null
    }

    private fun pushStationTimerUpdate() {
        val liveSeconds = stationBaseSeconds.toMutableMap()
        stationRunningItemId?.let { id ->
            val elapsed = ((System.currentTimeMillis() - stationRunningStartMillis) / 1000).toInt()
            liveSeconds[id] = (stationBaseSeconds[id] ?: 0) + elapsed
        }
        stationAdapter.updateStationTimer(stationRunningItemId, liveSeconds)
    }

    private fun onTick() {
        if (mode == Mode.RUNNING && !roundPaused) {
            val elapsed = roundElapsedSeconds + ((System.currentTimeMillis() - roundStartMillis) / 1000).toInt()
            binding.textClock.text = RestTimerHelper.formatDuration(elapsed)
        }
        if (stationRunningItemId != null) {
            pushStationTimerUpdate()
        }
        if (mode == Mode.RESTING) {
            restRemainingSeconds -= 1
            if (restRemainingSeconds <= 0) {
                endRest()
            } else {
                updateRestUi()
            }
        }
    }

    private fun confirmFinish() {
        DialogHelper.createBuilder(this)
            .setTitle(R.string.circuit_finish_confirm_title)
            .setMessage(getString(R.string.circuit_finish_confirm_message, instance.completedRounds))
            .setPositiveButton(R.string.circuit_finish) { _, _ -> finishWithResult(markFinished = true) }
            .setNegativeButton(R.string.button_cancel, null)
            .showWithTransparentWindow()
    }

    private fun finishWithResult(markFinished: Boolean) {
        finalizeStationTimerIfRunning()
        val finalInstance = if (markFinished) instance.copy(isFinished = true) else instance
        val resultIntent = Intent().apply {
            putExtra(RESULT_CIRCUIT_INSTANCE, finalInstance)
            putParcelableArrayListExtra(RESULT_CIRCUIT_ENTRIES, ArrayList(sessionEntries))
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
