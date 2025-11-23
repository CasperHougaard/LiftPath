package com.liftpath.activities

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.liftpath.databinding.ActivityEditSetBinding
import com.liftpath.models.ExerciseEntry

class EditSetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditSetBinding
    private lateinit var exerciseEntry: ExerciseEntry
    private var isEditMode = false

    companion object {
        const val EXTRA_EXERCISE_ENTRY = "extra_exercise_entry"
        const val EXTRA_IS_EDIT_MODE = "extra_is_edit_mode"
        const val RESULT_DELETE = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditSetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Start background animation
        val bgAnimation = binding.imageBgAnimation.drawable as? AnimatedVectorDrawable
        bgAnimation?.start()

        exerciseEntry = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_EXERCISE_ENTRY, ExerciseEntry::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_EXERCISE_ENTRY)
        } ?: return

        isEditMode = intent.getBooleanExtra(EXTRA_IS_EDIT_MODE, false)

        // Update title and subtitle based on mode
        if (isEditMode) {
            binding.textTitle.text = "Edit Set"
            binding.textSubtitle.text = "Update set details"
            binding.cardDelete.visibility = View.VISIBLE
        } else {
            binding.textTitle.text = "Add Set"
            binding.textSubtitle.text = "Enter set details"
            binding.cardDelete.visibility = View.GONE
        }

        // Populate fields with existing data
        binding.editTextKg.setText(exerciseEntry.kg.toString())
        binding.editTextReps.setText(exerciseEntry.reps.toString())
        
        // Populate RPE if available
        exerciseEntry.rpe?.let {
            binding.editTextRpe.setText(it.toString())
        }
        
        // Populate notes if available
        exerciseEntry.note?.let {
            binding.editTextNotes.setText(it)
        }

        // Back button
        binding.buttonBack.setOnClickListener {
            finish()
        }

        // Cancel button
        binding.buttonCancel.setOnClickListener {
            finish()
        }

        // Save button
        binding.buttonSave.setOnClickListener {
            saveSet()
        }

        // Delete button
        binding.cardDelete.setOnClickListener {
            showDeleteConfirmation()
        }
    }

    private fun saveSet() {
        val updatedKg = binding.editTextKg.text.toString().toFloatOrNull()
        val updatedReps = binding.editTextReps.text.toString().toIntOrNull()
        val updatedRpe = binding.editTextRpe.text.toString().toFloatOrNull()
        val updatedNotes = binding.editTextNotes.text.toString().trim().ifEmpty { null }

        // Validate required fields
        if (updatedKg == null) {
            Toast.makeText(this, "Please enter a valid weight", Toast.LENGTH_SHORT).show()
            binding.editTextKg.requestFocus()
            return
        }

        if (updatedReps == null) {
            Toast.makeText(this, "Please enter valid repetitions", Toast.LENGTH_SHORT).show()
            binding.editTextReps.requestFocus()
            return
        }

        // Validate RPE if provided (should be between 1 and 10)
        if (updatedRpe != null && (updatedRpe < 1 || updatedRpe > 10)) {
            Toast.makeText(this, "RPE must be between 1 and 10", Toast.LENGTH_SHORT).show()
            binding.editTextRpe.requestFocus()
            return
        }

        // Create updated entry
        val updatedEntry = exerciseEntry.copy(
            kg = updatedKg,
            reps = updatedReps,
            rpe = updatedRpe,
            note = updatedNotes
        )

        val resultIntent = Intent().apply {
            putExtra(EXTRA_EXERCISE_ENTRY, updatedEntry)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Delete Set")
            .setMessage("Are you sure you want to delete this set?")
            .setPositiveButton("Delete") { _, _ ->
                setResult(RESULT_DELETE)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}