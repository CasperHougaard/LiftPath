package com.lilfitness.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.lilfitness.databinding.ActivitySelectExercisesForPlanBinding
import com.lilfitness.helpers.JsonHelper
import com.lilfitness.adapters.SelectExercisesAdapter

class SelectExercisesForPlanActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySelectExercisesForPlanBinding
    private lateinit var jsonHelper: JsonHelper
    private lateinit var adapter: SelectExercisesAdapter
    private var selectedCount = 0

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
        updateSelectedCount()
    }

    private fun setupRecyclerView(preselectedIds: Set<Int>) {
        val trainingData = jsonHelper.readTrainingData()
        val exercises = trainingData.exerciseLibrary.sortedBy { it.name }
        
        adapter = SelectExercisesAdapter(
            exercises = exercises,
            preselectedIds = preselectedIds,
            onSelectionChanged = { _, isChecked ->
                selectedCount += if (isChecked) 1 else -1
                updateSelectedCount()
            }
        )
        binding.recyclerViewExercises.adapter = adapter
        binding.recyclerViewExercises.layoutManager = LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
        binding.buttonDone.setOnClickListener {
            val selectedIds = adapter.getSelectedIds()
            if (selectedIds.isEmpty()) {
                Toast.makeText(this, "Please select at least one exercise", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent().apply {
                    putIntegerArrayListExtra(EXTRA_SELECTED_EXERCISE_IDS, ArrayList(selectedIds))
                }
                setResult(Activity.RESULT_OK, intent)
                finish()
            }
        }
    }

    private fun updateSelectedCount() {
        binding.textSelectedCount.text = "$selectedCount selected"
        binding.buttonDone.isEnabled = selectedCount > 0
    }
}

