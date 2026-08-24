package com.liftpath.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.liftpath.R
import com.liftpath.adapters.EditCircuitItemAdapter
import com.liftpath.databinding.ActivityEditCircuitBinding
import com.liftpath.helpers.CircuitStore
import com.liftpath.helpers.JsonHelper
import com.liftpath.models.CircuitItem
import com.liftpath.models.CircuitTemplate

/**
 * Authors a reusable circuit: name, an optional round suggestion, rest between rounds, and the
 * ordered stations.
 *
 * The round field is intentionally allowed to stay empty. A circuit's round count belongs to the
 * session, not the definition — [CircuitTemplate.suggestedRounds] is a hint the runner shows and
 * never enforces.
 */
class EditCircuitActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditCircuitBinding
    private lateinit var jsonHelper: JsonHelper
    private lateinit var adapter: EditCircuitItemAdapter

    private val items: MutableList<CircuitItem> = mutableListOf()
    private var circuitId: String? = null
    private var existing: CircuitTemplate? = null

    companion object {
        const val EXTRA_CIRCUIT_ID = "extra_circuit_id"
        private const val DEFAULT_REST_SECONDS = 90
    }

    private val selectExercisesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val selected = result.data
            ?.getIntegerArrayListExtra(SelectExercisesForPlanActivity.EXTRA_SELECTED_EXERCISE_IDS)
            ?: return@registerForActivityResult
        addStations(selected)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditCircuitBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jsonHelper = JsonHelper(this)
        circuitId = intent.getStringExtra(EXTRA_CIRCUIT_ID)

        setupRecyclerView()
        setupClickListeners()
        loadIfEditing()
    }

    private fun setupRecyclerView() {
        adapter = EditCircuitItemAdapter(items, onRemoveClicked = { position ->
            adapter.removeAt(position)
        })
        adapter.library = jsonHelper.readTrainingData().exerciseLibrary
        binding.recyclerViewCircuitItems.adapter = adapter
        binding.recyclerViewCircuitItems.layoutManager = LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonSaveCircuit.setOnClickListener { save() }
        binding.buttonAddStations.setOnClickListener {
            // No preselection: a circuit may legitimately use the same exercise twice (e.g. a
            // hold at both ends of the round), so the picker must not treat "already in" as "done".
            selectExercisesLauncher.launch(
                Intent(this, SelectExercisesForPlanActivity::class.java)
            )
        }
    }

    private fun loadIfEditing() {
        val id = circuitId
        if (id == null) {
            binding.editTextCircuitRest.setText(DEFAULT_REST_SECONDS.toString())
            return
        }

        val circuit = CircuitStore.find(jsonHelper.readTrainingData(), id)
        if (circuit == null) {
            // Deleted from under us (e.g. from the active workout). Fall back to create mode.
            circuitId = null
            binding.editTextCircuitRest.setText(DEFAULT_REST_SECONDS.toString())
            return
        }

        existing = circuit
        binding.textHeaderTitle.setText(R.string.title_edit_circuit)
        binding.editTextCircuitName.setText(circuit.name)
        binding.editTextCircuitRounds.setText(circuit.suggestedRounds?.toString() ?: "")
        binding.editTextCircuitRest.setText(circuit.restBetweenRoundsSeconds.toString())
        binding.editTextCircuitNotes.setText(circuit.notes ?: "")

        items.clear()
        items.addAll(circuit.items)
        adapter.notifyDataSetChanged()
    }

    /** Seeds a new station with the exercise's own defaults: a hold time for timed ones. */
    private fun addStations(exerciseIds: List<Int>) {
        val library = jsonHelper.readTrainingData().exerciseLibrary
        adapter.library = library
        val newItems = exerciseIds.mapNotNull { id ->
            val exercise = library.find { it.id == id } ?: return@mapNotNull null
            if (exercise.isTimeBased) {
                CircuitItem(exerciseId = id, targetDurationSeconds = DEFAULT_HOLD_SECONDS)
            } else {
                CircuitItem(exerciseId = id, targetReps = DEFAULT_REPS)
            }
        }
        adapter.addAll(newItems)
    }

    private fun save() {
        val name = binding.editTextCircuitName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_circuit_name_required), Toast.LENGTH_SHORT).show()
            return
        }
        val stations = adapter.snapshot()
        if (stations.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_circuit_needs_exercise), Toast.LENGTH_SHORT).show()
            return
        }

        // Blank rounds is a real answer — "decide as you go" — so it stays null rather than
        // falling back to a number the runner would then display as a target.
        val rounds = binding.editTextCircuitRounds.text?.toString()?.trim()
            ?.toIntOrNull()?.takeIf { it > 0 }
        val rest = binding.editTextCircuitRest.text?.toString()?.trim()
            ?.toIntOrNull()?.coerceAtLeast(0) ?: DEFAULT_REST_SECONDS
        val notes = binding.editTextCircuitNotes.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        val data = jsonHelper.readTrainingData()
        val template = (existing ?: CircuitTemplate(name = name, createdDate = CircuitStore.today()))
            .copy(
                name = name,
                suggestedRounds = rounds,
                restBetweenRoundsSeconds = rest,
                items = stations,
                notes = notes
            )
        CircuitStore.upsert(data, template)
        jsonHelper.writeTrainingData(data)

        setResult(Activity.RESULT_OK)
        finish()
    }
}

private const val DEFAULT_REPS = "12"
private const val DEFAULT_HOLD_SECONDS = 45
