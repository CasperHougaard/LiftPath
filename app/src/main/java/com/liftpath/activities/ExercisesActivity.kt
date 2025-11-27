package com.liftpath.activities

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.liftpath.databinding.ActivityExercisesBinding
import com.liftpath.helpers.JsonHelper
import com.liftpath.adapters.ExerciseLibraryAdapter
import com.liftpath.models.ExerciseLibraryItem

class ExercisesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExercisesBinding
    private lateinit var jsonHelper: JsonHelper
    private lateinit var adapter: ExerciseLibraryAdapter
    private var allExercises: List<ExerciseLibraryItem> = emptyList()
    private var searchQuery: String = ""

    private val addExerciseLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // An exercise was either created or added from default. We just need to reload the list.
            loadExercises()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExercisesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jsonHelper = JsonHelper(this)

        setupBackgroundAnimation()
        setupRecyclerView()
        setupSearchField()
        loadExercises()
        setupClickListeners()
    }

    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is Animatable) {
            drawable.start()
        }
    }

    private fun setupClickListeners() {
        binding.buttonAddExercise.setOnClickListener {
            // Launch in create mode (no ID passed)
            val intent = Intent(this, com.liftpath.activities.EditExerciseActivity::class.java)
            addExerciseLauncher.launch(intent)
        }

        binding.buttonBack.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = ExerciseLibraryAdapter(
            emptyList(),
            onEditClicked = { exercise ->
                // Launch in edit mode (pass the exercise ID)
                val intent = Intent(this, com.liftpath.activities.EditExerciseActivity::class.java).apply {
                    putExtra(com.liftpath.activities.EditExerciseActivity.EXTRA_EXERCISE_ID, exercise.id)
                    putExtra(com.liftpath.activities.EditExerciseActivity.EXTRA_EXERCISE_NAME, exercise.name)
                }
                addExerciseLauncher.launch(intent)
            }
        )
        binding.recyclerViewExercises.adapter = adapter
        binding.recyclerViewExercises.layoutManager = LinearLayoutManager(this)
    }

    private fun loadExercises() {
        val trainingData = jsonHelper.readTrainingData()
        allExercises = trainingData.exerciseLibrary.sortedBy { it.name }
        applySearchFilter()
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
                            android.util.Log.e("ExercisesActivity", "Error filtering exercise: ${exercise.name}", e)
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
            android.util.Log.e("ExercisesActivity", "Error in applySearchFilter", e)
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
                    android.util.Log.e("ExercisesActivity", "Error in search filter", e)
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
}
