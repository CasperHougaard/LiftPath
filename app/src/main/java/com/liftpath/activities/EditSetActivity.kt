package com.liftpath.activities

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.liftpath.R
import com.liftpath.databinding.ActivityEditSetBinding
import com.liftpath.models.ExerciseEntry
import java.util.Locale

class EditSetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditSetBinding
    private lateinit var exerciseEntry: ExerciseEntry
    private var isEditMode = false
    private var isBodyweight = false
    private var isTimeBased = false

    companion object {
        const val EXTRA_EXERCISE_ENTRY = "extra_exercise_entry"
        const val EXTRA_IS_EDIT_MODE = "extra_is_edit_mode"
        const val RESULT_DELETE = 2
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

        isEditMode = intent.getBooleanExtra(EXTRA_IS_EDIT_MODE, false)

        // Update title and subtitle based on mode
        if (isEditMode) {
            binding.textTitle.setText(R.string.title_edit_set)
            binding.textSubtitle.setText(R.string.subtitle_update_set_details)
            binding.cardDelete.visibility = View.VISIBLE
        } else {
            binding.textTitle.setText(R.string.title_add_set)
            binding.textSubtitle.setText(R.string.subtitle_enter_set_details)
            binding.cardDelete.visibility = View.GONE
        }

        // Detect mode from the entry itself (entry-driven, so legacy weighted sets stay weighted
        // even if the exercise is later reclassified).
        isBodyweight = exerciseEntry.isBodyweightEntry()
        isTimeBased = exerciseEntry.isTimedEntry()

        // Populate fields with existing data. The two axes are independent — a bodyweight hold is
        // both timed and bodyweight, so both halves of the form are set up.
        if (isTimeBased) setupTimeMode()
        if (isBodyweight) setupBodyweightMode()
        if (!isTimeBased && !isBodyweight) {
            binding.editTextKg.setText(exerciseEntry.kg.toString())
        }
        if (isTimeBased) {
            binding.editTextDurationSeconds.setText((exerciseEntry.durationSeconds ?: 0).toString())
        } else {
            binding.editTextReps.setText(exerciseEntry.reps.toString())
        }

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

    // --- Bodyweight set editing ---

    private fun formatNum(v: Float): String =
        if (v == v.toLong().toFloat()) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

    /** Body weight & total load are always shown to 1 decimal. */
    private fun format1(v: Float): String = String.format(Locale.US, "%.1f", v)

    private fun round1(v: Float): Float = Math.round(v * 10f) / 10f

    /** Metric half only: duration instead of reps. The load half is owned by the caller. */
    private fun setupTimeMode() {
        binding.repsContainer.visibility = View.GONE
        binding.timeContainer.visibility = View.VISIBLE
        // A weighted hold keeps the optional external-load field; a bodyweight hold uses the
        // body-weight fields instead (set up by setupBodyweightMode).
        if (!isBodyweight) {
            binding.weightedWeightContainer.visibility = View.VISIBLE
            if (exerciseEntry.kg > 0f) {
                binding.editTextKg.setText(formatNum(exerciseEntry.kg))
            }
        }
    }

    /** Load half only: body-weight snapshot plus the signed Added/Assisted extra. */
    private fun setupBodyweightMode() {
        binding.weightedWeightContainer.visibility = View.GONE
        binding.bodyweightContainer.visibility = View.VISIBLE

        exerciseEntry.bodyweightKg?.let { binding.editTextBodyweight.setText(format1(it)) }

        when (val added = exerciseEntry.addedKg) {
            null -> binding.chipAdded.isChecked = true
            else -> when {
                added < 0f -> {
                    binding.chipAssisted.isChecked = true
                    binding.editTextExtra.setText(formatNum(-added))
                }
                added > 0f -> {
                    binding.chipAdded.isChecked = true
                    binding.editTextExtra.setText(formatNum(added))
                }
                else -> binding.chipAdded.isChecked = true
            }
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateEffectiveLoad() }
        }
        binding.editTextBodyweight.addTextChangedListener(watcher)
        binding.editTextExtra.addTextChangedListener(watcher)
        binding.chipGroupExtraType.setOnCheckedStateChangeListener { _, _ -> updateEffectiveLoad() }

        updateEffectiveLoad()
    }

    /** Signed extra weight: positive when "Added", negative when "Assisted". */
    private fun currentAddedKg(): Float {
        val extra = binding.editTextExtra.text.toString().trim().toFloatOrNull() ?: 0f
        return if (binding.chipAssisted.isChecked) -extra else extra
    }

    private fun updateEffectiveLoad() {
        val bw = binding.editTextBodyweight.text.toString().trim().toFloatOrNull()
        binding.textEffectiveLoad.text = if (bw == null) {
            getString(R.string.bodyweight_need_value)
        } else {
            getString(R.string.bodyweight_total_label, format1(bw + currentAddedKg()))
        }
    }

    private fun saveSet() {
        val updatedRpe = binding.editTextRpe.text.toString().toFloatOrNull()
        val updatedNotes = binding.editTextNotes.text.toString().trim().ifEmpty { null }

        // Resolve the target metric: reps (default) or a timed hold (durationSeconds).
        val updatedReps: Int
        val updatedDurationSeconds: Int?
        if (isTimeBased) {
            val dur = binding.editTextDurationSeconds.text.toString().toIntOrNull()
            if (dur == null || dur <= 0) {
                Toast.makeText(this, getString(R.string.toast_please_enter_duration), Toast.LENGTH_SHORT).show()
                binding.editTextDurationSeconds.requestFocus()
                return
            }
            updatedReps = 0
            updatedDurationSeconds = dur
        } else {
            val parsedReps = binding.editTextReps.text.toString().toIntOrNull()
            if (parsedReps == null) {
                Toast.makeText(this, "Please enter valid repetitions", Toast.LENGTH_SHORT).show()
                binding.editTextReps.requestFocus()
                return
            }
            updatedReps = parsedReps
            updatedDurationSeconds = null
        }

        // Validate RPE if provided (should be between 1 and 10)
        if (updatedRpe != null && (updatedRpe < 1 || updatedRpe > 10)) {
            Toast.makeText(this, "RPE must be between 1 and 10", Toast.LENGTH_SHORT).show()
            binding.editTextRpe.requestFocus()
            return
        }

        // Resolve the load, independently of the metric above: bodyweight (reps or hold) →
        // snapshot + signed extra; weighted hold → optional kg; weighted reps → kg required.
        val updatedKg: Float
        val updatedBodyweight: Float?
        val updatedAdded: Float?
        if (isBodyweight) {
            val bw = binding.editTextBodyweight.text.toString().trim().toFloatOrNull()
            if (bw == null || bw < 20f || bw > 400f) {
                Toast.makeText(this, getString(R.string.bodyweight_invalid), Toast.LENGTH_SHORT).show()
                binding.editTextBodyweight.requestFocus()
                return
            }
            val added = round1(currentAddedKg())
            val roundedBw = round1(bw)
            val effective = round1(roundedBw + added)
            if (effective <= 0f) {
                Toast.makeText(this, getString(R.string.bodyweight_invalid), Toast.LENGTH_SHORT).show()
                return
            }
            updatedKg = effective
            updatedBodyweight = roundedBw
            updatedAdded = added
        } else if (isTimeBased) {
            // External load on a hold is optional; blank means an unloaded hold.
            updatedKg = binding.editTextKg.text.toString().toFloatOrNull() ?: 0f
            updatedBodyweight = null
            updatedAdded = null
        } else {
            val parsedKg = binding.editTextKg.text.toString().toFloatOrNull()
            if (parsedKg == null) {
                Toast.makeText(this, "Please enter a valid weight", Toast.LENGTH_SHORT).show()
                binding.editTextKg.requestFocus()
                return
            }
            updatedKg = parsedKg
            updatedBodyweight = null
            updatedAdded = null
        }

        // Create updated entry
        val updatedEntry = exerciseEntry.copy(
            kg = updatedKg,
            reps = updatedReps,
            rpe = updatedRpe,
            note = updatedNotes,
            bodyweightKg = updatedBodyweight,
            addedKg = updatedAdded,
            durationSeconds = updatedDurationSeconds
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