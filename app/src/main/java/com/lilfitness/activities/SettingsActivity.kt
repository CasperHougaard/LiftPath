package com.lilfitness.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.lilfitness.R
import com.lilfitness.helpers.JsonHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    private lateinit var jsonHelper: JsonHelper

    private val exportDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            jsonHelper.exportTrainingData(it)
                .onSuccess { showToast("Backup exported") }
                .onFailure { showToast("Export failed: ${it.localizedMessage}") }
        }
    }

    private val importDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                try {
                    contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (ignored: SecurityException) {
                    // Some providers don't allow persistable permissions; ignore.
                }
            }
            jsonHelper.importTrainingData(it)
                .onSuccess { showToast("Backup imported") }
                .onFailure { showToast("Import failed: ${it.localizedMessage}") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        jsonHelper = JsonHelper(this)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        findViewById<Button>(R.id.button_reset_data).setOnClickListener {
            showResetDataConfirmationDialog()
        }

        findViewById<Button>(R.id.button_export_data).setOnClickListener {
            exportDocumentLauncher.launch(defaultBackupFileName())
        }

        findViewById<Button>(R.id.button_import_data).setOnClickListener {
            importDocumentLauncher.launch(arrayOf("application/json"))
        }

        findViewById<Button>(R.id.button_progression_settings).setOnClickListener {
            val intent = Intent(this, com.lilfitness.activities.ProgressionSettingsActivity::class.java)
            startActivity(intent)
        }

        findViewById<Button>(R.id.button_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun showResetDataConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset Data")
            .setMessage("Are you sure you want to reset all your data? This action cannot be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reset") { _, _ ->
                resetData()
            }
            .show()
    }

    private fun resetData() {
        jsonHelper.resetTrainingData()
        showToast("Data reset")
    }

    private fun defaultBackupFileName(): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "training_backup_${formatter.format(Date())}.json"
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}