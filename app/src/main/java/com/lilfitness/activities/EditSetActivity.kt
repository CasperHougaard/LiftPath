package com.lilfitness.activities

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lilfitness.databinding.ActivityEditSetBinding
import com.lilfitness.models.ExerciseEntry

class EditSetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditSetBinding
    private lateinit var exerciseEntry: ExerciseEntry

    companion object {
        const val EXTRA_EXERCISE_ENTRY = "extra_exercise_entry"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditSetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        exerciseEntry = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_EXERCISE_ENTRY, ExerciseEntry::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_EXERCISE_ENTRY)
        } ?: return

        binding.editTextKg.setText(exerciseEntry.kg.toString() ?: "")
        binding.editTextReps.setText(exerciseEntry.reps.toString() ?: "")

        binding.buttonSave.setOnClickListener {
            val updatedKg = binding.editTextKg.text.toString().toFloatOrNull()
            val updatedReps = binding.editTextReps.text.toString().toIntOrNull()

            if (updatedKg != null && updatedReps != null) {
                val updatedEntry = exerciseEntry.copy(kg = updatedKg, reps = updatedReps)

                val resultIntent = Intent().apply {
                    putExtra(EXTRA_EXERCISE_ENTRY, updatedEntry)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }
    }
}