package com.liftpath.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.liftpath.R
import com.liftpath.databinding.ActivitySelectExercisesForPlanBinding
import com.liftpath.helpers.JsonHelper
import com.liftpath.adapters.SelectExercisesAdapter
import com.liftpath.adapters.SelectListItem
import com.liftpath.models.ExerciseFamily
import com.liftpath.models.ExerciseLibraryItem

class SelectExercisesForPlanActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySelectExercisesForPlanBinding
    private lateinit var jsonHelper: JsonHelper
    private lateinit var adapter: SelectExercisesAdapter
    private var selectedCount = 0
    private var allExercises: List<ExerciseLibraryItem> = emptyList()
    private var allFamilies: List<ExerciseFamily> = emptyList()
    private val familyNameMap: Map<String, String> get() = allFamilies.associate { it.id to it.name }
    private var searchQuery: String = ""
    private var isGroupedByFamily: Boolean = false

    companion object {
        const val EXTRA_SELECTED_EXERCISE_IDS = "extra_selected_exercise_ids"
        const val EXTRA_PRESELECTED_IDS = "extra_preselected_ids"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectExercisesForPlanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jsonHelper = JsonHelper(this)
        
        val preselectedIds = intent.getIntArrayExtra(EXTRA_PRESELECTED_IDS)?.toSet() ?: emptySet()
        selectedCount = preselectedIds.size
        
        setupRecyclerView(preselectedIds)
        setupClickListeners()
        setupSearchField()
        updateSelectedCount()
    }

    private fun setupRecyclerView(preselectedIds: Set<Int>) {
        val trainingData = jsonHelper.readTrainingData()
        allExercises = trainingData.exerciseLibrary.sortedBy { it.name }
        allFamilies = trainingData.exerciseFamilies ?: emptyList()

        adapter = SelectExercisesAdapter(
            exercises = allExercises,
            preselectedIds = preselectedIds,
            onSelectionChanged = { _, isChecked ->
                selectedCount += if (isChecked) 1 else -1
                updateSelectedCount()
            },
            families = allFamilies
        )
        binding.recyclerViewExercises.adapter = adapter
        binding.recyclerViewExercises.layoutManager = LinearLayoutManager(this)
    }
    
    private fun applySearchFilter() {
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
                            android.util.Log.e("SelectExercisesForPlanActivity", "Error filtering exercise: ${exercise.name}", e)
                            false
                        }
                    }
                } else {
                    allExercises
                }
            } else {
                allExercises
            }

            if (isGroupedByFamily && searchQuery.isEmpty()) {
                adapter.updateItems(buildGroupedItems(filtered))
            } else {
                adapter.updateExercises(filtered)
            }
        } catch (e: Exception) {
            android.util.Log.e("SelectExercisesForPlanActivity", "Error in applySearchFilter", e)
            adapter.updateExercises(allExercises)
        }
    }

    private fun buildGroupedItems(exercises: List<ExerciseLibraryItem>): List<SelectListItem> {
        val result = mutableListOf<SelectListItem>()
        val withFamily = exercises.filter { it.familyId != null }
        val withoutFamily = exercises.filter { it.familyId == null }

        val grouped = withFamily.groupBy { it.familyId!! }
        val sortedFamilyIds = grouped.keys.sortedBy { familyNameMap[it] ?: it }

        for (familyId in sortedFamilyIds) {
            val familyExercises = grouped[familyId] ?: continue
            val familyName = familyNameMap[familyId] ?: familyId
            result.add(SelectListItem.SectionHeader(familyName))
            familyExercises.sortedBy { it.name.lowercase() }.forEach { result.add(SelectListItem.ExerciseItem(it)) }
        }

        if (withoutFamily.isNotEmpty()) {
            result.add(SelectListItem.SectionHeader(getString(R.string.label_other_exercises)))
            withoutFamily.sortedBy { it.name.lowercase() }.forEach { result.add(SelectListItem.ExerciseItem(it)) }
        }

        return result
    }

    private fun setupClickListeners() {
        binding.buttonBack.setOnClickListener {
            finish()
        }

        binding.chipGroupByFamily.setOnCheckedChangeListener { _, isChecked ->
            isGroupedByFamily = isChecked
            applySearchFilter()
        }
        
        binding.buttonDone.setOnClickListener {
            val selectedIds = adapter.getSelectedIds()
            if (selectedIds.isEmpty()) {
                Toast.makeText(this, getString(R.string.toast_please_select_exercise), Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent().apply {
                    putIntegerArrayListExtra(EXTRA_SELECTED_EXERCISE_IDS, ArrayList(selectedIds))
                }
                setResult(Activity.RESULT_OK, intent)
                finish()
            }
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
                    android.util.Log.e("SelectExercisesForPlanActivity", "Error in search filter", e)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        
        // Handle IME action to prevent activity from closing
        binding.editTextSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                // Hide keyboard and keep focus on search field
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(binding.editTextSearch.windowToken, 0)
                binding.editTextSearch.clearFocus()
                true
            } else {
                false
            }
        }
    }

    private fun updateSelectedCount() {
        binding.textSelectedCount.text = getString(R.string.label_selected_count, selectedCount)
        binding.buttonDone.isEnabled = selectedCount > 0
    }
}

