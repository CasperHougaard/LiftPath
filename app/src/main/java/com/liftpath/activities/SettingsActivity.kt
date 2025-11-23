package com.liftpath.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.liftpath.R
import com.liftpath.databinding.ActivitySettingsBinding
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.showWithTransparentWindow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var jsonHelper: JsonHelper

    private val exportDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            jsonHelper.exportTrainingData(it)
                .onSuccess { showToast(getString(R.string.toast_backup_exported)) }
                .onFailure { showToast(getString(R.string.toast_export_failed, it.localizedMessage ?: "")) }
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
                .onSuccess { showToast(getString(R.string.toast_backup_imported)) }
                .onFailure { showToast(getString(R.string.toast_import_failed, it.localizedMessage ?: "")) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup background animation
        setupBackgroundAnimation()

        jsonHelper = JsonHelper(this)

        setupClickListeners()
    }
    
    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is android.graphics.drawable.Animatable) {
            drawable.start()
        }
    }

    private fun setupClickListeners() {
        binding.buttonResetData.setOnClickListener {
            showResetDataConfirmationDialog()
        }

        binding.buttonExportData.setOnClickListener {
            exportDocumentLauncher.launch(defaultBackupFileName())
        }

        binding.buttonImportData.setOnClickListener {
            importDocumentLauncher.launch(arrayOf("application/json"))
        }

        binding.buttonProgressionSettings.setOnClickListener {
            val intent = Intent(this, com.liftpath.activities.ProgressionSettingsActivity::class.java)
            startActivity(intent)
        }

        // Header back button
        binding.buttonBackHeader.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun showResetDataConfirmationDialog() {
        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.dialog_title_reset_data))
            .setMessage(getString(R.string.dialog_message_reset_data))
            .setNegativeButton(getString(R.string.button_cancel), null)
            .setPositiveButton(getString(R.string.button_reset)) { _, _ ->
                resetData()
            }
            .showWithTransparentWindow()
    }

    private fun resetData() {
        jsonHelper.resetTrainingData()
        showToast(getString(R.string.toast_data_reset))
    }

    private fun defaultBackupFileName(): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "training_backup_${formatter.format(Date())}.json"
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}