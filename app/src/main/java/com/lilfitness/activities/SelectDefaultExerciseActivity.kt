package com.lilfitness.activities

import android.app.Activity
import android.os.Bundle
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.lilfitness.databinding.ActivitySelectDefaultExerciseBinding
import com.lilfitness.helpers.DefaultExercisesHelper // <--- Make sure this Import exists
import com.lilfitness.helpers.JsonHelper
import com.lilfitness.adapters.SelectDefaultExerciseAdapter
import com.lilfitness.models.ExerciseLibraryItem

class SelectDefaultExerciseActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySelectDefaultExerciseBinding
    private lateinit var jsonHelper: JsonHelper
    private val selectedExercises = mutableSetOf<ExerciseLibraryItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectDefaultExerciseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Add Default Exercises"

        jsonHelper = JsonHelper(this)

        setupListView()
        setupClickListeners()
    }

    private fun setupListView() {
        // --- CHANGED HERE ---
        // Old code: val allDefaultExercises = listOf(...)
        // New code: Call the helper to get the 25 smart exercises
        val allDefaultExercises = DefaultExercisesHelper.getPopularDefaults()
        // --------------------

        // Get existing exercises to prevent duplicates
        val existingLibrary = jsonHelper.readTrainingData().exerciseLibrary
        
        // Filter: Only show exercises that match neither ID nor Name of existing ones
        val availableExercises = allDefaultExercises.filter { defaultItem ->
            val idExists = existingLibrary.any { it.id == defaultItem.id }
            val nameExists = existingLibrary.any { it.name.equals(defaultItem.name, ignoreCase = true) }
            !idExists && !nameExists
        }

        val adapter = SelectDefaultExerciseAdapter(this, availableExercises, selectedExercises)
        binding.listViewDefaultExercises.adapter = adapter
        binding.listViewDefaultExercises.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        binding.listViewDefaultExercises.setOnItemClickListener { _, _, position, _ ->
            val exercise = availableExercises[position]
            if (selectedExercises.contains(exercise)) {
                selectedExercises.remove(exercise)
            } else {
                selectedExercises.add(exercise)
            }
            // Notify adapter to update checkboxes
            (binding.listViewDefaultExercises.adapter as SelectDefaultExerciseAdapter).notifyDataSetChanged()
        }
    }

    private fun setupClickListeners() {
        binding.buttonAddSelected.setOnClickListener {
            addSelectedExercises()
        }
    }

    private fun addSelectedExercises() {
        val trainingData = jsonHelper.readTrainingData()
        val existingExercises = trainingData.exerciseLibrary

        // --- CHANGED HERE ---
        // We do NOT generate new IDs (nextId++). 
        // We use the ID coming from the helper (100+) so the logic engine knows what they are.
        selectedExercises.forEach { selected ->
            existingExercises.add(selected)
        }
        // --------------------

        jsonHelper.writeTrainingData(trainingData)
        setResult(Activity.RESULT_OK)
        finish()
    }
}