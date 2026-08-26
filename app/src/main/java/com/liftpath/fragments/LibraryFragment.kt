package com.liftpath.fragments

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import com.liftpath.R
import com.liftpath.databinding.FragmentLibraryBinding
import com.liftpath.helpers.CircuitStore
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.showWithTransparentWindow
import com.liftpath.adapters.CircuitLibraryAdapter
import com.liftpath.adapters.ExerciseLibraryAdapter
import com.liftpath.models.CircuitTemplate
import com.liftpath.models.ExerciseFamily
import com.liftpath.models.ExerciseLibraryItem
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import com.liftpath.activities.EditCircuitActivity
import com.liftpath.activities.EditExerciseActivity
import com.liftpath.activities.ManageFavoritesActivity
import com.liftpath.activities.SettingsActivity

class LibraryFragment : Fragment() {

    /** The catalogue holds two kinds of object; the chip segment picks which one is listed. */
    private enum class Mode { EXERCISES, CIRCUITS }

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private lateinit var jsonHelper: JsonHelper
    private lateinit var adapter: ExerciseLibraryAdapter
    private lateinit var circuitAdapter: CircuitLibraryAdapter
    private var allExercises: List<ExerciseLibraryItem> = emptyList()
    private var allFamilies: List<ExerciseFamily> = emptyList()
    private var allCircuits: List<CircuitTemplate> = emptyList()
    private val familyNameMap: Map<String, String> get() = allFamilies.associate { it.id to it.name }
    private var searchQuery: String = ""
    private var mode = Mode.EXERCISES

    private val addExerciseLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // An exercise was either created or added from default. We just need to reload the list.
            loadExercises()
        }
    }

    private val manageFavoritesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // Favorites were updated, reload the list
            loadExercises()
        }
    }

    private val editCircuitLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            loadCircuits()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentLibraryBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        jsonHelper = JsonHelper(requireContext())
        setupRecyclerView()
        setupSearchField()
        setupModeSegment()
        loadExercises()
        loadCircuits()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        // Circuits can be edited from the active workout too, so the file may have moved on.
        jsonHelper.invalidateTrainingDataCache()
        loadCircuits()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    private fun setupClickListeners() {
        binding.buttonAddExercise.setOnClickListener {
            // Launch in create mode (no ID passed)
            val intent = Intent(requireContext(), EditExerciseActivity::class.java)
            addExerciseLauncher.launch(intent)
        }

        binding.buttonAddFavourites.setOnClickListener {
            // Launch favorites management screen
            val intent = Intent(requireContext(), ManageFavoritesActivity::class.java)
            manageFavoritesLauncher.launch(intent)
        }

        binding.buttonAddCircuit.setOnClickListener {
            editCircuitLauncher.launch(Intent(requireContext(), EditCircuitActivity::class.java))
        }

        binding.cardSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        // A tab has nowhere to go back to; collapse the header's back button.
        binding.cardBack.visibility = View.GONE
    }

    private fun setupModeSegment() {
        binding.chipGroupLibraryMode.setOnCheckedStateChangeListener { _, checkedIds ->
            mode = if (checkedIds.contains(R.id.chip_mode_circuits)) Mode.CIRCUITS else Mode.EXERCISES
            applyMode()
        }
        applyMode()
    }

    /** One list and one footer button are visible at a time; the search box serves both. */
    private fun applyMode() {
        val circuits = mode == Mode.CIRCUITS
        binding.cardExercises.visibility = if (circuits) View.GONE else View.VISIBLE
        binding.cardCircuits.visibility = if (circuits) View.VISIBLE else View.GONE
        binding.buttonAddCircuit.visibility = if (circuits) View.VISIBLE else View.GONE
        binding.buttonAddExercise.visibility = if (circuits) View.GONE else View.VISIBLE
        binding.buttonAddFavourites.visibility = if (circuits) View.GONE else View.VISIBLE
        binding.editTextSearch.setHint(
            if (circuits) R.string.hint_search_circuits else R.string.hint_search_exercises
        )
        applySearchFilter()
    }

    private fun setupRecyclerView() {
        adapter = ExerciseLibraryAdapter(
            emptyList(),
            onEditClicked = { exercise ->
                // Launch in edit mode (pass the exercise ID)
                val intent = Intent(requireContext(), EditExerciseActivity::class.java).apply {
                    putExtra(EditExerciseActivity.EXTRA_EXERCISE_ID, exercise.id)
                    putExtra(EditExerciseActivity.EXTRA_EXERCISE_NAME, exercise.name)
                }
                addExerciseLauncher.launch(intent)
            }
        )
        binding.recyclerViewExercises.adapter = adapter
        binding.recyclerViewExercises.layoutManager = LinearLayoutManager(requireContext())

        circuitAdapter = CircuitLibraryAdapter(
            emptyList(),
            onEditClicked = { circuit ->
                val intent = Intent(requireContext(), EditCircuitActivity::class.java).apply {
                    putExtra(EditCircuitActivity.EXTRA_CIRCUIT_ID, circuit.id)
                }
                editCircuitLauncher.launch(intent)
            },
            onDeleteClicked = { circuit -> confirmDeleteCircuit(circuit) }
        )
        binding.recyclerViewCircuits.adapter = circuitAdapter
        binding.recyclerViewCircuits.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun confirmDeleteCircuit(circuit: CircuitTemplate) {
        DialogHelper.createBuilder(requireContext())
            .setTitle(getString(R.string.dialog_title_delete_circuit))
            .setMessage(getString(R.string.dialog_message_delete_circuit, circuit.name))
            .setPositiveButton(getString(R.string.button_delete)) { _, _ ->
                val data = jsonHelper.readTrainingData()
                CircuitStore.delete(data, circuit.id)
                jsonHelper.writeTrainingData(data)
                loadCircuits()
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    private fun loadExercises() {
        val trainingData = jsonHelper.readTrainingData()
        allExercises = trainingData.exerciseLibrary.sortedBy { it.name }
        allFamilies = trainingData.exerciseFamilies ?: emptyList()
        adapter.updateFamilies(allFamilies)
        applySearchFilter()
    }

    private fun loadCircuits() {
        if (_binding == null) return
        val trainingData = jsonHelper.readTrainingData()
        allCircuits = CircuitStore.circuits(trainingData).sortedBy { it.name }
        applySearchFilter()
    }

    /** A circuit matches on its own name or on any of its stations' names. */
    private fun filterCircuits(query: String): List<CircuitTemplate> {
        if (query.isEmpty()) return allCircuits
        val nameById = allExercises.associate { it.id to it.name.lowercase() }
        return allCircuits.filter { circuit ->
            circuit.name.lowercase().contains(query) ||
                circuit.items.any { nameById[it.exerciseId]?.contains(query) == true }
        }
    }

    private fun applyCircuitFilter() {
        val filtered = filterCircuits(searchQuery.trim().lowercase())
        circuitAdapter.update(filtered, allExercises)
        binding.textCircuitsEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerViewCircuits.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        binding.textCircuitsEmpty.setText(
            if (allCircuits.isEmpty()) R.string.circuits_empty_state else R.string.circuits_no_matches
        )
    }

    private fun applySearchFilter() {
        if (_binding == null) return
        applyCircuitFilter()
        if (mode == Mode.CIRCUITS) return
        try {
            val filtered = if (searchQuery.isNotEmpty()) {
                val query = searchQuery.trim().lowercase()
                if (query.isNotEmpty()) {
                    allExercises.filter { exercise ->
                        try {
                            exercise.name.lowercase().contains(query)
                                || exercise.aliases?.any { it.lowercase().contains(query) } == true
                                || (exercise.familyId?.let { familyNameMap[it]?.lowercase()?.contains(query) } == true)
                        } catch (e: Exception) {
                            android.util.Log.e("LibraryFragment", "Error filtering exercise: ${exercise.name}", e)
                            false
                        }
                    }
                } else {
                    allExercises
                }
            } else {
                allExercises
            }
            
            adapter.updateExercises(filtered)
        } catch (e: Exception) {
            android.util.Log.e("LibraryFragment", "Error in applySearchFilter", e)
            adapter.updateExercises(allExercises)
        }
    }
    
    private fun setupSearchField() {
        // Search field TextWatcher
        binding.editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                try {
                    searchQuery = s?.toString() ?: ""
                    applySearchFilter()
                } catch (e: Exception) {
                    android.util.Log.e("LibraryFragment", "Error in search filter", e)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        
        // Handle IME action to prevent activity from closing
        binding.editTextSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                // Hide keyboard and keep focus on search field
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(binding.editTextSearch.windowToken, 0)
                binding.editTextSearch.clearFocus()
                true
            } else {
                false
            }
        }
    }
}
