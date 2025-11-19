package com.lilfitness.activities

import android.app.Activity
import android.os.Bundle
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.lilfitness.databinding.ActivitySelectDefaultExerciseBinding
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
        val allDefaultExercises = listOf(
            ExerciseLibraryItem(id = 1, name = "Deadlift"),
            ExerciseLibraryItem(id = 2, name = "Squat"),
            ExerciseLibraryItem(id = 3, name = "Bench Press"),
            ExerciseLibraryItem(id = 4, name = "Biceps Curl"),
            ExerciseLibraryItem(id = 5, name = "Triceps Pushdown")
        )

        val currentExerciseNames = jsonHelper.readTrainingData().exerciseLibrary.map { it.name }
        val availableExercises = allDefaultExercises.filter { it.name !in currentExerciseNames }

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
        val maxId = existingExercises.maxOfOrNull { it.id } ?: 0

        var nextId = maxId + 1
        selectedExercises.forEach { selected ->
            if (existingExercises.none { it.name.equals(selected.name, ignoreCase = true) }) {
                existingExercises.add(selected.copy(id = nextId++))
            }
        }

        jsonHelper.writeTrainingData(trainingData)
        setResult(Activity.RESULT_OK)
        finish()
    }
}