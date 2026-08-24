package com.liftpath.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.liftpath.R
import com.liftpath.databinding.ActivityManageFavoritesBinding
import com.liftpath.helpers.JsonHelper
import com.liftpath.adapters.SelectExercisesAdapter
import com.liftpath.models.ExerciseLibraryItem

class ManageFavoritesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageFavoritesBinding
    private lateinit var jsonHelper: JsonHelper
    private lateinit var adapter: SelectExercisesAdapter
    private var selectedCount = 0
    private var allExercises: List<ExerciseLibraryItem> = emptyList()
    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageFavoritesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jsonHelper = JsonHelper(this)
        
        // Get current favorite IDs
        val trainingData = jsonHelper.readTrainingData()
        val favoriteIds = trainingData.exerciseLibrary.filter { it.isFavorite }.map { it.id }.toSet()
        selectedCount = favoriteIds.size
        
        setupRecyclerView(favoriteIds)
        setupClickListeners()
        setupSearchField()
        updateSelectedCount()
    }

    private fun setupRecyclerView(preselectedIds: Set<Int>) {
        val trainingData = jsonHelper.readTrainingData()
        allExercises = trainingData.exerciseLibrary.sortedBy { it.name }
        
        adapter = SelectExercisesAdapter(
            exercises = allExercises,
            preselectedIds = preselectedIds,
            onSelectionChanged = { _, isChecked ->
                selectedCount += if (isChecked) 1 else -1
                updateSelectedCount()
            }
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
                        } catch (e: Exception) {
                            android.util.Log.e("ManageFavoritesActivity", "Error filtering exercise: ${exercise.name}", e)
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
            android.util.Log.e("ManageFavoritesActivity", "Error in applySearchFilter", e)
            adapter.updateExercises(allExercises)
        }
    }

    private fun setupClickListeners() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
        
        binding.buttonCancel.setOnClickListener {
            finish()
        }
        
        binding.buttonSave.setOnClickListener {
            saveFavorites()
        }
    }
    
    private fun saveFavorites() {
        val selectedIds = adapter.getSelectedIds().toSet()
        val trainingData = jsonHelper.readTrainingData()
        
        // Update favorite status for all exercises
        trainingData.exerciseLibrary.forEachIndexed { index, exercise ->
            val isFavorite = exercise.id in selectedIds
            if (exercise.isFavorite != isFavorite) {
                trainingData.exerciseLibrary[index] = exercise.copy(isFavorite = isFavorite)
            }
        }
        
        jsonHelper.writeTrainingData(trainingData)
        
        val intent = Intent().apply {
            // No extra data needed, just indicate success
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
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
                    android.util.Log.e("ManageFavoritesActivity", "Error in search filter", e)
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
        binding.textSelectedCount.text = "$selectedCount selected"
    }
}
