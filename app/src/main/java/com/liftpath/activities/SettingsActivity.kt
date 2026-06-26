package com.liftpath.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import com.liftpath.R
import com.liftpath.databinding.ActivitySettingsBinding
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.liftpath.helpers.CatalogMergePrefsManager
import com.liftpath.helpers.DefaultExercisesHelper
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.WithingsHealthConnectHelper
import com.liftpath.helpers.WithingsStorageHelper
import com.liftpath.helpers.showWithTransparentWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var jsonHelper: JsonHelper

    private val requestWithingsPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(WithingsHealthConnectHelper.PERMISSIONS)) {
            showToast("Withings permissions granted")
            // Trigger an immediate sync now that permissions are available
            lifecycleScope.launch {
                WithingsHealthConnectHelper.autoSync(applicationContext)
                withContext(Dispatchers.Main) {
                    showWithingsInfoDialog()
                }
            }
        } else {
            showToast("Some permissions were denied")
        }
    }

    private val exportDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            jsonHelper.exportTrainingData(it)
                .onSuccess { showToast(getString(R.string.toast_backup_exported)) }
                .onFailure { showToast(getString(R.string.toast_export_failed, it.localizedMessage ?: "")) }
        }
    }

    private val exportLibraryLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            jsonHelper.exportExerciseLibrary(it)
                .onSuccess { showToast(getString(R.string.toast_library_exported)) }
                .onFailure {
                    showToast(
                        getString(
                            R.string.toast_library_export_failed,
                            it.localizedMessage ?: ""
                        )
                    )
                }
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

        setupSettingsTabs()
        setupClickListeners()
    }

    private fun setupSettingsTabs() {
        binding.tabLayoutSettings.addTab(
            binding.tabLayoutSettings.newTab().setText(getString(R.string.settings_tab_general))
        )
        binding.tabLayoutSettings.addTab(
            binding.tabLayoutSettings.newTab().setText(getString(R.string.settings_tab_advanced))
        )
        binding.tabLayoutSettings.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        binding.panelGeneral.visibility = View.VISIBLE
                        binding.panelAdvanced.visibility = View.GONE
                    }
                    1 -> {
                        binding.panelGeneral.visibility = View.GONE
                        binding.panelAdvanced.visibility = View.VISIBLE
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}

            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
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

        binding.buttonResetLibrary.setOnClickListener {
            showResetLibraryConfirmationDialog()
        }

        binding.buttonExportData.setOnClickListener {
            exportDocumentLauncher.launch(defaultBackupFileName())
        }

        binding.buttonImportData.setOnClickListener {
            importDocumentLauncher.launch(arrayOf("application/json"))
        }

        binding.buttonExportLibrary.setOnClickListener {
            exportLibraryLauncher.launch(defaultExerciseLibraryFileName())
        }

        binding.buttonProgressionSettings.setOnClickListener {
            val intent = Intent(this, com.liftpath.activities.ProgressionSettingsActivity::class.java)
            startActivity(intent)
        }

        binding.buttonWithingsData.setOnClickListener {
            showWithingsInfoDialog()
        }

        // Header back button
        binding.buttonBackHeader.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun showWithingsInfoDialog() {
        if (!WithingsHealthConnectHelper.isAvailable(this)) {
            DialogHelper.createBuilder(this)
                .setTitle("Withings Body Scan")
                .setMessage(
                    "Health Connect is not available on this device.\n\n" +
                    "Install Health Connect from the Play Store to enable Withings body scan syncing."
                )
                .setPositiveButton(getString(R.string.button_cancel), null)
                .showWithTransparentWindow()
            return
        }

        lifecycleScope.launch {
            val hasPermissions = withContext(Dispatchers.IO) {
                try {
                    val client = androidx.health.connect.client.HealthConnectClient.getOrCreate(applicationContext)
                    val granted = client.permissionController.getGrantedPermissions()
                    granted.containsAll(WithingsHealthConnectHelper.PERMISSIONS)
                } catch (e: Exception) {
                    false
                }
            }

            val storage = WithingsStorageHelper(this@SettingsActivity).read()
            val entries = storage.entries

            val message = if (!hasPermissions) {
                "Withings body scan data requires Health Connect permissions.\n\n" +
                "The following data will be read from Health Connect:\n" +
                "• Weight\n" +
                "• Body Fat %\n" +
                "• Lean Body Mass\n" +
                "• Bone Mass\n" +
                "• Body Water Mass\n" +
                "• Basal Metabolic Rate (BMR)\n\n" +
                "Only data from the Withings Health Mate app will be used. " +
                "No data is shared externally."
            } else if (entries.isEmpty()) {
                "Permissions: Granted ✓\n\n" +
                "No Withings body scan data has been synced yet.\n\n" +
                "Make sure the Withings Health Mate app is installed and syncing to Health Connect. " +
                "Data will sync automatically on next app launch."
            } else {
                val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                val newest = dateFormat.format(Date(entries.first().dateMs))
                val oldest = dateFormat.format(Date(entries.last().dateMs))
                val syncTime = if (storage.lastSyncTime > 0) {
                    dateFormat.format(Date(storage.lastSyncTime))
                } else "Never"

                val metrics = mutableListOf<String>()
                if (entries.any { it.weightKg != null })       metrics.add("Weight")
                if (entries.any { it.bodyFatPct != null })     metrics.add("Body Fat %")
                if (entries.any { it.leanBodyMassKg != null }) metrics.add("Lean Mass")
                if (entries.any { it.boneMassKg != null })     metrics.add("Bone Mass")
                if (entries.any { it.bodyWaterMassKg != null })metrics.add("Body Water")
                if (entries.any { it.bmrKcal != null })        metrics.add("BMR")

                "Permissions: Granted ✓\n" +
                "Status: Connected (${entries.size} scans)\n" +
                "Date range: $oldest → $newest\n" +
                "Last sync: $syncTime\n" +
                "Metrics available: ${metrics.joinToString(", ")}"
            }

            val builder = DialogHelper.createBuilder(this@SettingsActivity)
                .setTitle("Withings Body Scan")
                .setMessage(message)
                .setNegativeButton(getString(R.string.button_cancel), null)

            if (!hasPermissions) {
                builder.setPositiveButton("Grant Permissions") { _, _ ->
                    requestWithingsPermissions.launch(WithingsHealthConnectHelper.PERMISSIONS)
                }
            } else {
                builder.setPositiveButton("Sync Now") { _, _ ->
                    lifecycleScope.launch {
                        showToast("Syncing Withings data...")
                        val result = WithingsHealthConnectHelper.autoSync(applicationContext)
                        withContext(Dispatchers.Main) {
                            result.fold(
                                onSuccess = { count -> showToast("Synced $count new entries") },
                                onFailure = { showToast("Sync failed: ${it.message}") }
                            )
                        }
                    }
                }
            }

            builder.showWithTransparentWindow()
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
        CatalogMergePrefsManager(this).resetForLibraryReset()
        showToast(getString(R.string.toast_data_reset))
    }

    private fun showResetLibraryConfirmationDialog() {
        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.dialog_title_reset_library))
            .setMessage(getString(R.string.dialog_message_reset_library))
            .setNegativeButton(getString(R.string.button_cancel), null)
            .setPositiveButton(getString(R.string.button_reset)) { _, _ ->
                resetExerciseLibraryOnly()
            }
            .showWithTransparentWindow()
    }

    private fun resetExerciseLibraryOnly() {
        val data = jsonHelper.readTrainingData()
        data.exerciseLibrary.clear()
        data.exerciseLibrary.addAll(DefaultExercisesHelper.getPopularDefaults())
        jsonHelper.writeTrainingData(data)
        CatalogMergePrefsManager(this).resetForLibraryReset()
        showToast(getString(R.string.toast_library_reset))
    }

    private fun defaultBackupFileName(): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "training_backup_${formatter.format(Date())}.json"
    }

    private fun defaultExerciseLibraryFileName(): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "liftpath_exercises_${formatter.format(Date())}.json"
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}