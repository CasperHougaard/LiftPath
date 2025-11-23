package com.liftpath.activities

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
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
        adapter.updateExercises(trainingData.exerciseLibrary)
    }
}
