package com.liftpath.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.liftpath.R
import com.liftpath.databinding.ActivitySettingsBinding
import com.liftpath.databinding.ViewThemeDropdownItemBinding
import com.liftpath.helpers.AppearanceManager
import com.liftpath.helpers.AppearanceMode
import com.liftpath.helpers.LiftPathTheme
import com.liftpath.helpers.SwatchPreview
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.liftpath.helpers.AiExportHelper
import com.liftpath.helpers.BackupManager
import com.liftpath.helpers.BackupScheduler
import com.liftpath.helpers.BackupSettingsManager
import com.liftpath.helpers.BodyWeightHelper
import com.liftpath.helpers.BodyWeightSettingsManager
import com.liftpath.helpers.CatalogMergePrefsManager
import com.liftpath.helpers.DefaultExercisesHelper
import com.liftpath.helpers.DialogHelper
import com.liftpath.helpers.DriveAuthHelper
import com.liftpath.helpers.DriveBackupHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.LocalFolderBackupHelper
import com.liftpath.helpers.TrainingDataTransfer
import com.liftpath.helpers.TriPathConnection
import com.liftpath.helpers.TriPathContract
import com.liftpath.helpers.TriPathStorageHelper
import com.liftpath.helpers.TriPathSyncHelper
import com.liftpath.helpers.WithingsHealthConnectHelper
import com.liftpath.helpers.WithingsStorageHelper
import com.liftpath.helpers.showWithTransparentWindow
import com.liftpath.models.BackupBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var jsonHelper: JsonHelper

    /** Lazy so it resolves after [jsonHelper] is assigned in onCreate; the SAF launchers below
     *  are field initializers but their lambdas only run once the user picks a document. */
    private val transfer by lazy { TrainingDataTransfer(this, jsonHelper) }
    private lateinit var bodyWeightSettingsManager: BodyWeightSettingsManager
    private lateinit var backupSettings: BackupSettingsManager
    private lateinit var folderBackupHelper: LocalFolderBackupHelper

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
            transfer.exportTrainingData(it)
                .onSuccess { showToast(getString(R.string.toast_backup_exported)) }
                .onFailure { showToast(getString(R.string.toast_export_failed, it.localizedMessage ?: "")) }
        }
    }

    private val exportLibraryLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            transfer.exportExerciseLibrary(it)
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

    private val exportAiLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        uri?.let {
            val markdown = AiExportHelper.buildMarkdown(this, jsonHelper.readTrainingData())
            transfer.exportAiMarkdown(it, markdown)
                .onSuccess { showToast(getString(R.string.toast_ai_export_exported)) }
                .onFailure { e ->
                    showToast(getString(R.string.toast_ai_export_failed, e.localizedMessage ?: ""))
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
            transfer.importTrainingData(it)
                .onSuccess { showToast(getString(R.string.toast_backup_imported)) }
                .onFailure { showToast(getString(R.string.toast_import_failed, it.localizedMessage ?: "")) }
        }
    }

    /** Folder picker for the backup destination. Pick a Google Drive folder here to sync. */
    private val pickBackupFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri ?: return@registerForActivityResult
        folderBackupHelper.saveFolder(uri)
            .onSuccess {
                showToast(getString(R.string.backup_toast_folder_saved))
                refreshBackupUi()
                // Write one immediately: a destination the user has never seen a file land in
                // is indistinguishable from a broken one.
                runFolderBackup()
            }
            .onFailure {
                showToast(
                    getString(R.string.backup_toast_folder_failed, it.localizedMessage ?: "")
                )
            }
    }

    /** Restore from an arbitrary backup file (e.g. one pulled off an old phone by hand). */
    private val restoreFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        // Off the main thread: the picked file may live behind a cloud DocumentsProvider,
        // in which case opening the stream does network I/O.
        loadAndConfirm { folderBackupHelper.readBundle(uri) }
    }

    /**
     * Drive consent screen. Google returns the granted scope here; the pending action is
     * re-run afterwards because the token only exists once consent completes.
     */
    private val driveConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        when (val outcome = DriveAuthHelper.tokenFromConsentResult(this, result.data)) {
            is DriveAuthHelper.AuthOutcome.Token -> {
                backupSettings.driveEnabled = true
                backupSettings.lastDriveError = null
                showToast(getString(R.string.backup_toast_drive_connected))
                refreshBackupUi()
                pendingDriveAction?.let { action ->
                    pendingDriveAction = null
                    action(outcome.accessToken)
                } ?: runDriveBackup(outcome.accessToken)
            }
            is DriveAuthHelper.AuthOutcome.NeedsConsent -> {
                pendingDriveAction = null
                showToast(getString(R.string.backup_toast_drive_failed, "consent not completed"))
            }
            is DriveAuthHelper.AuthOutcome.Failure -> {
                pendingDriveAction = null
                showToast(
                    getString(
                        R.string.backup_toast_drive_failed,
                        outcome.error.localizedMessage ?: ""
                    )
                )
            }
        }
    }

    /** What to do once a Drive token arrives — back up, or list backups for restore. */
    private var pendingDriveAction: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jsonHelper = JsonHelper(this)
        bodyWeightSettingsManager = BodyWeightSettingsManager(this)
        backupSettings = BackupSettingsManager(this)
        folderBackupHelper = LocalFolderBackupHelper(this)

        setupAppearanceModeToggle()
        setupPalettePickers()
        setupClickListeners()
        setupBackupControls()
        loadBodyWeightField()
    }

    /** The System/Light/Dark chip toggle. Forces `AppCompatDelegate`'s night mode, which is
     *  what makes every palette's values-night colours resolve consistently app-wide. */
    private fun setupAppearanceModeToggle() {
        when (AppearanceManager.mode(this)) {
            AppearanceMode.SYSTEM -> binding.chipModeSystem.isChecked = true
            AppearanceMode.LIGHT -> binding.chipModeLight.isChecked = true
            AppearanceMode.DARK -> binding.chipModeDark.isChecked = true
        }

        binding.chipGroupAppearanceMode.setOnCheckedStateChangeListener { _, _ ->
            val mode = when {
                binding.chipModeLight.isChecked -> AppearanceMode.LIGHT
                binding.chipModeDark.isChecked -> AppearanceMode.DARK
                else -> AppearanceMode.SYSTEM
            }
            if (mode == AppearanceManager.mode(this)) return@setOnCheckedStateChangeListener
            binding.chipGroupAppearanceMode.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            AppearanceManager.setMode(this, mode)
            AppCompatDelegate.setDefaultNightMode(mode.toNightMode())
            recreate()
        }
    }

    /**
     * The "Light palette" / "Dark palette" dropdowns, populated from [LiftPathTheme.values]
     * — data-driven rather than hand-written rows so the enum stays the single source of
     * truth. The two are independent: picking a palette for one slot never touches the other.
     */
    private fun setupPalettePickers() {
        val themes = LiftPathTheme.values().toList()

        binding.spinnerLightTheme.adapter = ThemeSpinnerAdapter(this, themes) { it.previewLight }
        binding.spinnerLightTheme.setSelection(themes.indexOf(AppearanceManager.lightTheme(this)))
        binding.spinnerLightTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val theme = themes[position]
                if (theme == AppearanceManager.lightTheme(this@SettingsActivity)) return
                parent.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                AppearanceManager.setLightTheme(this@SettingsActivity, theme)
                recreate()
            }
            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }

        binding.spinnerDarkTheme.adapter = ThemeSpinnerAdapter(this, themes) { it.previewDark }
        binding.spinnerDarkTheme.setSelection(themes.indexOf(AppearanceManager.darkTheme(this)))
        binding.spinnerDarkTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val theme = themes[position]
                if (theme == AppearanceManager.darkTheme(this@SettingsActivity)) return
                parent.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                AppearanceManager.setDarkTheme(this@SettingsActivity, theme)
                recreate()
            }
            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
    }

    /**
     * Backs both palette Spinners. Shows a compact swatch trio (canvas/ink/accent) plus the
     * palette name for every row, using [previewFor] to pick the light or dark swatch set —
     * the one place a palette's colours are referenced directly, since a dropdown must show
     * every palette's look at once and `?attr/` only ever resolves the *active* theme.
     */
    private class ThemeSpinnerAdapter(
        private val context: Context,
        private val themes: List<LiftPathTheme>,
        private val previewFor: (LiftPathTheme) -> SwatchPreview
    ) : BaseAdapter() {
        private val inflater = LayoutInflater.from(context)

        override fun getCount() = themes.size
        override fun getItem(position: Int) = themes[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            bindRow(position, convertView, parent)

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
            bindRow(position, convertView, parent)

        private fun bindRow(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView?.let(ViewThemeDropdownItemBinding::bind)
                ?: ViewThemeDropdownItemBinding.inflate(inflater, parent, false)

            val theme = themes[position]
            row.textThemeLabel.setText(theme.labelRes)

            val preview = previewFor(theme)
            row.swatchCanvas.imageTintList = ContextCompat.getColorStateList(context, preview.canvas)
            row.swatchInk.imageTintList = ContextCompat.getColorStateList(context, preview.ink)
            row.swatchAccent.imageTintList = ContextCompat.getColorStateList(context, preview.accent)

            return row.root
        }
    }

    override fun onResume() {
        super.onResume()
        // A backup may have completed in the background while this screen was open.
        refreshBackupUi()
        updateTriPathStatus()
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

        binding.buttonAiExport.setOnClickListener {
            if (jsonHelper.readTrainingData().trainings.isEmpty()) {
                showToast(getString(R.string.toast_ai_export_no_sessions))
            } else {
                exportAiLauncher.launch(defaultAiExportFileName())
            }
        }

        binding.buttonSaveBodyWeight.setOnClickListener {
            saveBodyWeightField()
        }

        binding.buttonProgressionSettings.setOnClickListener {
            val intent = Intent(this, com.liftpath.activities.ProgressionSettingsActivity::class.java)
            startActivity(intent)
        }

        binding.buttonProgressSettings.setOnClickListener {
            startActivity(Intent(this, com.liftpath.activities.ProgressSettingsActivity::class.java))
        }

        binding.buttonReadiness.setOnClickListener {
            startActivity(Intent(this, com.liftpath.activities.ReadinessDashboardActivity::class.java))
        }

        binding.buttonWithingsData.setOnClickListener {
            showWithingsInfoDialog()
        }

        binding.buttonTripath.setOnClickListener {
            showTriPathDialog()
        }

        // Header back button
        binding.buttonBackHeader.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    // ---------------------------------------------------------------- backup

    private fun setupBackupControls() {
        binding.switchAutoBackup.isChecked = backupSettings.autoBackupEnabled
        binding.switchAutoBackup.setOnCheckedChangeListener { _, checked ->
            backupSettings.autoBackupEnabled = checked
            if (checked) {
                BackupScheduler.ensurePeriodicBackup(this)
            } else {
                BackupScheduler.cancelAll(this)
            }
            refreshBackupUi()
        }

        binding.buttonBackupFolder.setOnClickListener { onFolderCardClicked() }
        binding.buttonBackupDrive.setOnClickListener { onDriveCardClicked() }
        binding.buttonBackupRestore.setOnClickListener { onRestoreClicked() }
        binding.buttonBackupNow.setOnClickListener { onBackupNowClicked() }

        refreshBackupUi()
    }

    private fun refreshBackupUi() {
        val folderLabel = backupSettings.folderLabel
        binding.textBackupFolderStatus.text = when {
            folderLabel == null -> getString(R.string.backup_folder_not_set)
            backupSettings.lastFolderError != null ->
                getString(R.string.backup_toast_folder_failed, backupSettings.lastFolderError)
            else -> getString(R.string.backup_folder_set, folderLabel)
        }

        binding.textBackupDriveStatus.text = when {
            !backupSettings.driveEnabled -> getString(R.string.backup_drive_not_connected)
            backupSettings.lastDriveError != null -> backupSettings.lastDriveError
            else -> getString(
                R.string.backup_drive_connected,
                BackupManager.formatTimestamp(backupSettings.lastDriveBackupMs)
            )
        }

        val lastBackupMs = maxOf(
            backupSettings.lastFolderBackupMs,
            backupSettings.lastDriveBackupMs
        )
        binding.textBackupStatus.text = when {
            !backupSettings.hasAnyDestination() -> getString(R.string.backup_status_none)
            lastBackupMs <= 0L -> getString(R.string.backup_status_pending, "never")
            backupSettings.hasPendingChanges() -> getString(
                R.string.backup_status_pending,
                BackupManager.formatTimestamp(lastBackupMs)
            )
            else -> getString(
                R.string.backup_status_ok,
                BackupManager.formatTimestamp(lastBackupMs)
            )
        }
    }

    private fun onBackupNowClicked() {
        if (!backupSettings.hasAnyDestination()) {
            showToast(getString(R.string.backup_toast_no_destination))
            return
        }
        showToast(getString(R.string.backup_toast_started))

        // Run the folder write inline so the result is immediate and visible; Drive goes
        // through the worker so a flaky connection retries instead of failing in the user's face.
        if (backupSettings.folderUri != null) runFolderBackup()
        if (backupSettings.driveEnabled) BackupScheduler.backupNow(this)
    }

    private fun runFolderBackup() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { folderBackupHelper.backupNow() }
            result
                .onSuccess { showToast("Saved $it") }
                .onFailure {
                    showToast(
                        getString(R.string.backup_toast_folder_failed, it.localizedMessage ?: "")
                    )
                }
            refreshBackupUi()
        }
    }

    private fun onFolderCardClicked() {
        if (backupSettings.folderUri == null) {
            pickBackupFolderLauncher.launch(null)
            return
        }
        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.backup_folder_title))
            .setMessage(
                getString(R.string.backup_folder_set, backupSettings.folderLabel ?: "") +
                    "\n\nLast backup: " +
                    BackupManager.formatTimestamp(backupSettings.lastFolderBackupMs)
            )
            .setPositiveButton("Change folder") { _, _ -> pickBackupFolderLauncher.launch(null) }
            .setNeutralButton("Back up now") { _, _ -> runFolderBackup() }
            .setNegativeButton("Stop using") { _, _ ->
                backupSettings.clearFolder()
                refreshBackupUi()
            }
            .showWithTransparentWindow()
    }

    private fun onDriveCardClicked() {
        if (!backupSettings.driveEnabled) {
            withDriveToken { token ->
                backupSettings.driveEnabled = true
                backupSettings.lastDriveError = null
                showToast(getString(R.string.backup_toast_drive_connected))
                runDriveBackup(token)
            }
            return
        }
        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.backup_drive_title))
            .setMessage(
                "Backups are stored in a \"LiftPath Backups\" folder in your Google Drive, " +
                    "one file per day.\n\nLast sync: " +
                    BackupManager.formatTimestamp(backupSettings.lastDriveBackupMs)
            )
            .setPositiveButton("Sync now") { _, _ -> withDriveToken { runDriveBackup(it) } }
            .setNegativeButton("Disconnect") { _, _ ->
                DriveAuthHelper.disconnect(this)
                showToast(getString(R.string.backup_toast_drive_disconnected))
                refreshBackupUi()
            }
            .setNeutralButton(getString(R.string.button_cancel), null)
            .showWithTransparentWindow()
    }

    /**
     * Runs [action] with a valid Drive access token, routing through the consent screen when
     * the scope hasn't been granted yet. [pendingDriveAction] carries the intent across that
     * round trip so the user's original tap still happens after they approve.
     */
    private fun withDriveToken(action: (String) -> Unit) {
        lifecycleScope.launch {
            when (val outcome = DriveAuthHelper.authorize(this@SettingsActivity)) {
                is DriveAuthHelper.AuthOutcome.Token -> action(outcome.accessToken)
                is DriveAuthHelper.AuthOutcome.NeedsConsent -> {
                    pendingDriveAction = action
                    driveConsentLauncher.launch(
                        IntentSenderRequest.Builder(outcome.pendingIntent).build()
                    )
                }
                is DriveAuthHelper.AuthOutcome.Failure -> showToast(
                    getString(
                        R.string.backup_toast_drive_failed,
                        outcome.error.localizedMessage ?: ""
                    )
                )
            }
        }
    }

    private fun runDriveBackup(token: String) {
        lifecycleScope.launch {
            showToast(getString(R.string.backup_toast_started))
            withContext(Dispatchers.IO) { DriveBackupHelper(this@SettingsActivity).backupNow(token) }
                .onSuccess { showToast("Synced to Drive: $it") }
                .onFailure {
                    showToast(
                        getString(R.string.backup_toast_drive_failed, it.localizedMessage ?: "")
                    )
                }
            refreshBackupUi()
        }
    }

    // --------------------------------------------------------------- restore

    private fun onRestoreClicked() {
        val sources = mutableListOf<Pair<String, () -> Unit>>()
        if (backupSettings.folderUri != null) {
            sources.add("From backup folder" to { showFolderBackupPicker() })
        }
        if (backupSettings.driveEnabled) {
            sources.add("From Google Drive" to { withDriveToken { showDriveBackupPicker(it) } })
        }
        sources.add("From a file…" to { restoreFileLauncher.launch(arrayOf("application/json")) })

        if (sources.size == 1) {
            sources.first().second()
            return
        }
        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.backup_restore_title))
            .setItems(sources.map { it.first }.toTypedArray()) { _, index ->
                sources[index].second()
            }
            .showWithTransparentWindow()
    }

    private fun showFolderBackupPicker() {
        lifecycleScope.launch {
            val backups = withContext(Dispatchers.IO) { folderBackupHelper.listBackups() }
            if (backups.isEmpty()) {
                showToast(getString(R.string.backup_toast_no_backups))
                return@launch
            }
            val labels = backups.map {
                "${BackupManager.formatTimestamp(it.lastModifiedMs)}  ·  ${it.sizeBytes / 1024} kB"
            }
            DialogHelper.createBuilder(this@SettingsActivity)
                .setTitle(getString(R.string.backup_restore_title))
                .setItems(labels.toTypedArray()) { _, index ->
                    loadAndConfirm { folderBackupHelper.readBundle(backups[index].uri) }
                }
                .showWithTransparentWindow()
        }
    }

    private fun showDriveBackupPicker(token: String) {
        lifecycleScope.launch {
            val helper = DriveBackupHelper(this@SettingsActivity)
            val backups = withContext(Dispatchers.IO) { helper.listBackups(token) }.getOrElse {
                showToast(getString(R.string.backup_toast_drive_failed, it.localizedMessage ?: ""))
                return@launch
            }
            if (backups.isEmpty()) {
                showToast(getString(R.string.backup_toast_no_backups))
                return@launch
            }
            val labels = backups.map {
                "${it.name.removePrefix("liftpath_backup_").removeSuffix(".json")}" +
                    "  ·  ${it.sizeBytes / 1024} kB"
            }
            DialogHelper.createBuilder(this@SettingsActivity)
                .setTitle(getString(R.string.backup_restore_title))
                .setItems(labels.toTypedArray()) { _, index ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { helper.downloadBundle(token, backups[index].id) }
                            .onSuccess { confirmAndRestore(it) }
                            .onFailure { e ->
                                showToast(
                                    getString(
                                        R.string.backup_toast_restore_failed,
                                        e.localizedMessage ?: ""
                                    )
                                )
                            }
                    }
                }
                .showWithTransparentWindow()
        }
    }

    private fun loadAndConfirm(load: () -> Result<BackupBundle>) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { load() }
                .onSuccess { confirmAndRestore(it) }
                .onFailure {
                    showToast(
                        getString(R.string.backup_toast_restore_failed, it.localizedMessage ?: "")
                    )
                }
        }
    }

    /** Restore is destructive, so it always goes through an explicit confirmation. */
    private fun confirmAndRestore(bundle: BackupBundle) {
        DialogHelper.createBuilder(this)
            .setTitle(getString(R.string.backup_dialog_restore_title))
            .setMessage(
                getString(R.string.backup_dialog_restore_message, BackupManager.describe(bundle))
            )
            .setNegativeButton(getString(R.string.button_cancel), null)
            .setPositiveButton(getString(R.string.backup_dialog_restore_confirm)) { _, _ ->
                performRestore(bundle)
            }
            .showWithTransparentWindow()
    }

    private fun performRestore(bundle: BackupBundle) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                BackupManager.restore(this@SettingsActivity, bundle)
            }
            result
                .onSuccess {
                    showToast(getString(R.string.backup_toast_restored))
                    restartApp()
                }
                .onFailure {
                    showToast(
                        getString(R.string.backup_toast_restore_failed, it.localizedMessage ?: "")
                    )
                }
        }
    }

    /**
     * Every screen holds its own [JsonHelper] with an in-memory cache of the old data, and
     * there is no central invalidation hook. Restarting the process is the only way to
     * guarantee nothing stale gets written back over what was just restored.
     */
    private fun restartApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            finishAffinity()
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(launchIntent)
        finishAffinity()
        exitProcess(0)
    }

    private fun formatKg(kg: Float): String = String.format(Locale.US, "%.1f", kg)

    private fun loadBodyWeightField() {
        bodyWeightSettingsManager.getSettings().manualWeightKg?.let {
            binding.editTextBodyWeight.setText(formatKg(it))
        }
        refreshBodyWeightSource()
    }

    private fun refreshBodyWeightSource() {
        val resolved = BodyWeightHelper.resolveBodyWeight(this)
        binding.textBodyWeightSource.text = when (resolved.source) {
            BodyWeightHelper.BodyWeightSource.WITHINGS ->
                getString(R.string.bodyweight_source_withings, formatKg(resolved.kg ?: 0f))
            BodyWeightHelper.BodyWeightSource.MANUAL ->
                getString(R.string.bodyweight_source_manual, formatKg(resolved.kg ?: 0f))
            BodyWeightHelper.BodyWeightSource.NONE ->
                getString(R.string.bodyweight_source_none)
        }
    }

    private fun saveBodyWeightField() {
        val kg = binding.editTextBodyWeight.text.toString().trim().toFloatOrNull()
        if (kg == null || kg < 20f || kg > 400f) {
            showToast(getString(R.string.bodyweight_invalid))
            return
        }
        bodyWeightSettingsManager.setManualWeight(kg)
        showToast(getString(R.string.toast_bodyweight_saved))
        refreshBodyWeightSource()
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

    /**
     * The status line under the TriPath row. Refreshed on resume so uninstalling TriPath, or
     * connecting it, is reflected without reopening Settings.
     */
    private fun updateTriPathStatus() {
        binding.textTripathStatus.text = when {
            !TriPathConnection.isInstalled(this) -> getString(R.string.tripath_not_installed).lineSequence().first()
            !TriPathConnection.isEnabled(this) -> getString(R.string.tripath_disabled).lineSequence().first()
            TriPathConnection.lastSyncTime(this) == 0L -> getString(R.string.tripath_never_synced)
            else -> getString(
                R.string.tripath_last_synced,
                SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                    .format(Date(TriPathConnection.lastSyncTime(this)))
            )
        }
    }

    /**
     * Connect / disconnect and manual sync for the TriPath integration.
     *
     * Connecting only flips a local switch — TriPath grants access by package, so there is no
     * consent flow to run. The handshake it triggers is what tells us whether the other side
     * actually answers.
     */
    private fun showTriPathDialog() {
        if (!TriPathConnection.isInstalled(this)) {
            DialogHelper.createBuilder(this)
                .setTitle(getString(R.string.tripath_settings_title))
                .setMessage(getString(R.string.tripath_not_installed))
                .setPositiveButton(getString(R.string.button_cancel), null)
                .showWithTransparentWindow()
            return
        }

        val enabled = TriPathConnection.isEnabled(this)
        if (!enabled) {
            DialogHelper.createBuilder(this)
                .setTitle(getString(R.string.tripath_settings_title))
                .setMessage(getString(R.string.tripath_disabled))
                .setNegativeButton(getString(R.string.button_cancel), null)
                .setPositiveButton(getString(R.string.tripath_connect)) { _, _ ->
                    TriPathConnection.setEnabled(this, true)
                    syncTriPathNow()
                }
                .showWithTransparentWindow()
            return
        }

        lifecycleScope.launch {
            val handshake = withContext(Dispatchers.IO) { TriPathConnection.handshake(applicationContext) }
            val storage = withContext(Dispatchers.IO) { TriPathStorageHelper(applicationContext).read() }

            val message = buildString {
                if (handshake == null) {
                    append(getString(R.string.tripath_sync_failed))
                } else {
                    append(getString(R.string.tripath_connected, handshake.appVersionName ?: "?"))
                    if (!handshake.versionMatches) {
                        append("\n\n")
                        append(
                            getString(
                                R.string.tripath_version_mismatch,
                                handshake.contractVersion,
                                TriPathContract.CONTRACT_VERSION
                            )
                        )
                    }
                    append("\n\nWorkouts in TriPath: ${handshake.workoutCount}")
                    handshake.latestWorkoutDate?.let { append("\nLatest workout: $it") }
                }
                append("\n\nCached here: ${storage.days.size} days, ${storage.workouts.size} sessions")
                append("\n")
                append(
                    if (storage.lastSyncTime == 0L) {
                        getString(R.string.tripath_never_synced)
                    } else {
                        getString(
                            R.string.tripath_last_synced,
                            SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                                .format(Date(storage.lastSyncTime))
                        )
                    }
                )
            }

            DialogHelper.createBuilder(this@SettingsActivity)
                .setTitle(getString(R.string.tripath_settings_title))
                .setMessage(message)
                .setNeutralButton(getString(R.string.tripath_disconnect)) { _, _ ->
                    TriPathConnection.setEnabled(this@SettingsActivity, false)
                    updateTriPathStatus()
                }
                .setNegativeButton(getString(R.string.button_cancel), null)
                .setPositiveButton(getString(R.string.tripath_sync_now)) { _, _ -> syncTriPathNow() }
                .showWithTransparentWindow()
        }
    }

    private fun syncTriPathNow() {
        lifecycleScope.launch {
            showToast(getString(R.string.tripath_syncing))
            TriPathSyncHelper.autoSync(applicationContext).fold(
                onSuccess = { count -> showToast(getString(R.string.tripath_sync_success, count)) },
                onFailure = { showToast(getString(R.string.tripath_sync_failed)) }
            )
            updateTriPathStatus()
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

    private fun defaultAiExportFileName(): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "liftpath_ai_export_${formatter.format(Date())}.md"
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}