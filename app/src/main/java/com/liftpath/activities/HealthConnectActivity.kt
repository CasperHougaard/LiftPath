package com.liftpath.activities

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.liftpath.R
import com.liftpath.databinding.ActivityHealthConnectBinding
import com.liftpath.helpers.ExternalActivity
import com.liftpath.helpers.FatigueScores
import com.liftpath.helpers.HealthConnectHelper
import com.liftpath.helpers.HealthConnectStorageHelper
import com.liftpath.helpers.StoredHealthConnectActivity
import com.liftpath.helpers.HealthConnectStorage
import com.liftpath.helpers.JsonHelper
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class HealthConnectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHealthConnectBinding
    private lateinit var storageHelper: HealthConnectStorageHelper
    private lateinit var jsonHelper: JsonHelper
    private var syncedActivities: List<ExternalActivity> = emptyList()
    private val dateTimeFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    private val requestPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(HealthConnectHelper.PERMISSIONS)) {
            updatePermissionStatus(true)
            binding.buttonSync.isEnabled = true
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            updatePermissionStatus(false)
            binding.buttonSync.isEnabled = false
            Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHealthConnectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storageHelper = HealthConnectStorageHelper(this)
        jsonHelper = JsonHelper(this)
        
        setupClickListeners()
        loadStoredData()
        checkHealthConnectStatus()
    }

    private fun loadStoredData() {
        val storage = storageHelper.readStoredActivities()
        
        // Load retention days setting
        binding.editRetentionDays.setText(storage.retentionDays.toString())
        
        // Load last sync time
        if (storage.lastSyncTime > 0) {
            binding.textLastSync.text = "Last synced: ${dateTimeFormat.format(Date(storage.lastSyncTime))}"
        }
        
        // Load and display stored activities (including ignored ones for transparency)
        val allStoredActivities = storage.activities.map { it.toExternalActivity() }
        syncedActivities = allStoredActivities
        displayActivitiesWithIgnored(storage.activities)
    }

    private fun displayActivitiesWithIgnored(storedActivities: List<StoredHealthConnectActivity>) {
        binding.layoutActivitiesList.removeAllViews()
        
        if (storedActivities.isEmpty()) {
            binding.textNoActivities.visibility = View.VISIBLE
            binding.textActivitiesCount.text = "No activities synced"
            return
        }

        binding.textNoActivities.visibility = View.GONE
        
        // Count ignored vs active
        val activeCount = storedActivities.count { !it.ignored }
        val ignoredCount = storedActivities.count { it.ignored }
        binding.textActivitiesCount.text = "$activeCount active, $ignoredCount ignored (${storedActivities.size} total)"

        // Sort by start time (newest first)
        val sortedActivities = storedActivities.sortedByDescending { it.startTime }

        sortedActivities.forEach { storedActivity ->
            val activityRow = createActivityRowWithIgnored(storedActivity)
            // Make row clickable to toggle ignored status
            activityRow.setOnClickListener {
                toggleActivityIgnored(storedActivity)
            }
            binding.layoutActivitiesList.addView(activityRow)
        }
    }

    private fun toggleActivityIgnored(storedActivity: StoredHealthConnectActivity) {
        // Toggle ignored status
        val newIgnoredStatus = !storedActivity.ignored
        val newIgnoreReason = if (newIgnoredStatus) {
            if (storedActivity.ignoreReason == null) "Manually excluded" else storedActivity.ignoreReason
        } else {
            null
        }
        
        // Update the activity in storage
        val storage = storageHelper.readStoredActivities()
        val activityIndex = storage.activities.indexOfFirst { it.id == storedActivity.id }
        if (activityIndex >= 0) {
            val updatedActivity = storedActivity.copy(
                ignored = newIgnoredStatus,
                ignoreReason = newIgnoreReason
            )
            storage.activities[activityIndex] = updatedActivity
            storageHelper.writeStoredActivities(storage)
            
            // Refresh display
            displayActivitiesWithIgnored(storage.activities)
            
            // Show feedback
            val message = if (newIgnoredStatus) {
                "Activity marked as ignored"
            } else {
                "Activity included in fatigue calculations"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun createActivityRowWithIgnored(storedActivity: StoredHealthConnectActivity): View {
        val activity = storedActivity.toExternalActivity()
        val rowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }
            setPadding(16, 16, 16, 16)
            // Use card background color for theme consistency
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(ContextCompat.getColor(this@HealthConnectActivity, R.color.fitness_card_background))
                cornerRadius = 12f * resources.displayMetrics.density
            }
            // Add alpha if ignored
            if (storedActivity.ignored) {
                alpha = 0.6f
            }
            // Make clickable with ripple effect
            isClickable = true
            isFocusable = true
            foreground = ContextCompat.getDrawable(this@HealthConnectActivity, android.R.drawable.list_selector_background)
        }

        // Activity type and date
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val activityTypeText = TextView(this).apply {
            var typeName = getActivityTypeName(activity.type)
            if (storedActivity.ignored) {
                typeName = "🚫 $typeName (Ignored)"
            }
            text = typeName
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@HealthConnectActivity, R.color.fitness_text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        headerLayout.addView(activityTypeText)
        
        // Add hint text for clickability
        val clickHintText = TextView(this).apply {
            text = if (storedActivity.ignored) "Tap to include" else "Tap to exclude"
            textSize = 10f
            setTextColor(ContextCompat.getColor(this@HealthConnectActivity, R.color.fitness_text_secondary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(8, 0, 0, 0)
            }
        }
        headerLayout.addView(clickHintText)

        val durationMinutes = ((activity.endTime - activity.startTime) / 60000).toInt()
        val dateText = TextView(this).apply {
            text = "${dateTimeFormat.format(Date(activity.startTime))} (${durationMinutes}min)"
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@HealthConnectActivity, R.color.fitness_text_secondary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        headerLayout.addView(dateText)

        rowLayout.addView(headerLayout)

        // Ignore reason if present
        if (storedActivity.ignored && storedActivity.ignoreReason != null) {
            val ignoreReasonText = TextView(this).apply {
                text = "Reason: ${storedActivity.ignoreReason}"
                textSize = 11f
                setTextColor(ContextCompat.getColor(this@HealthConnectActivity, R.color.fitness_text_secondary))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 4, 0, 0)
                }
            }
            rowLayout.addView(ignoreReasonText)
        }

        // Fatigue impact (only show if not ignored)
        if (!storedActivity.ignored) {
            val fatigueText = TextView(this).apply {
                text = String.format(
                    "Fatigue: Lower %.1f | Upper %.1f | Systemic %.1f",
                    activity.fatigue.lowerFatigue,
                    activity.fatigue.upperFatigue,
                    activity.fatigue.systemicFatigue
                )
                textSize = 11f
                setTextColor(ContextCompat.getColor(this@HealthConnectActivity, R.color.fitness_text_secondary))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 0)
                }
            }
            rowLayout.addView(fatigueText)
        }

        return rowLayout
    }

    private fun setupClickListeners() {
        binding.buttonBack.setOnClickListener {
            finish()
        }

        binding.buttonRequestPermissions.setOnClickListener {
            requestHealthConnectPermissions()
        }

        binding.buttonSync.setOnClickListener {
            syncActivities()
        }

        // Save retention days when changed
        binding.editRetentionDays.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                try {
                    val retentionDays = binding.editRetentionDays.text.toString().toInt().coerceIn(1, 365)
                    val storage = storageHelper.readStoredActivities()
                    storage.retentionDays = retentionDays
                    storageHelper.writeStoredActivities(storage)
                } catch (e: Exception) {
                    // Invalid input, will use default on sync
                }
            }
        }
    }

    private fun checkHealthConnectStatus() {
        val isAvailable = HealthConnectHelper.isAvailable(this)
        
        if (!isAvailable) {
            binding.textStatus.text = "Health Connect is not available on this device"
            binding.textStatus.setTextColor(ContextCompat.getColor(this, R.color.fitness_error_border))
            binding.buttonSync.isEnabled = false
            binding.buttonRequestPermissions.visibility = View.GONE
            return
        }

        binding.textStatus.text = "Health Connect is available"
        binding.textStatus.setTextColor(ContextCompat.getColor(this, R.color.fitness_highlight_border))

        // Check permissions
        val client = HealthConnectHelper.getClient(this)
        if (client != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                val granted = client.permissionController.getGrantedPermissions()
                val hasPermissions = granted.containsAll(HealthConnectHelper.PERMISSIONS)
                
                withContext(Dispatchers.Main) {
                    updatePermissionStatus(hasPermissions)
                    binding.buttonSync.isEnabled = hasPermissions
                    if (!hasPermissions) {
                        binding.buttonRequestPermissions.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun updatePermissionStatus(hasPermissions: Boolean) {
        if (hasPermissions) {
            binding.textPermissionStatus.text = "Permissions: Granted"
            binding.textPermissionStatus.setTextColor(ContextCompat.getColor(this, R.color.fitness_highlight_border))
            binding.buttonRequestPermissions.visibility = View.GONE
        } else {
            binding.textPermissionStatus.text = "Permissions: Not granted"
            binding.textPermissionStatus.setTextColor(ContextCompat.getColor(this, R.color.fitness_error_border))
            binding.buttonRequestPermissions.visibility = View.VISIBLE
        }
    }

    private fun requestHealthConnectPermissions() {
        val client = HealthConnectHelper.getClient(this)
        if (client != null) {
            requestPermissions.launch(HealthConnectHelper.PERMISSIONS)
        } else {
            Toast.makeText(this, "Health Connect is not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun syncActivities() {
        binding.buttonSync.isEnabled = false
        binding.buttonSync.text = "Syncing..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Get retention days from UI
                val retentionDays = try {
                    binding.editRetentionDays.text.toString().toInt().coerceIn(1, 365)
                } catch (e: Exception) {
                    14 // Default
                }

                // Load existing stored activities
                val storage = storageHelper.readStoredActivities()
                storage.retentionDays = retentionDays
                
                val existingActivityIds = storage.activities.map { it.id }.toSet()
                
                // Fetch new activities from Health Connect
                val newActivities = HealthConnectHelper.readRecentExercises(applicationContext)
                
                // Get workouts for overlap detection
                val trainingData = jsonHelper.readTrainingData()
                val workouts = trainingData.trainings
                
                // Process new activities: deduplicate and check overlaps
                val now = System.currentTimeMillis()
                val retentionCutoff = now - (retentionDays * 24 * 60 * 60 * 1000L)
                
                val activitiesToAdd = mutableListOf<StoredHealthConnectActivity>()
                var ignoredCount = 0
                var duplicateCount = 0
                
                for (activity in newActivities) {
                    // Skip if already exists (deduplication)
                    if (activity.id in existingActivityIds) {
                        duplicateCount++
                        continue
                    }
                    
                    // Check for overlap with workouts
                    val hasOverlap = HealthConnectHelper.checkWorkoutOverlap(activity, workouts)
                    
                    val storedActivity = StoredHealthConnectActivity(
                        id = activity.id,
                        startTime = activity.startTime,
                        endTime = activity.endTime,
                        type = activity.type,
                        fatigue = activity.fatigue,
                        syncedAt = now,
                        ignored = hasOverlap,
                        ignoreReason = if (hasOverlap) "Overlaps with registered workout" else null
                    )
                    
                    activitiesToAdd.add(storedActivity)
                    if (hasOverlap) {
                        ignoredCount++
                    }
                }
                
                // Add new activities to storage
                storage.activities.addAll(activitiesToAdd)
                
                // Filter out old activities (older than retention days)
                val filteredActivities = storage.activities.filter { activity ->
                    activity.syncedAt >= retentionCutoff
                }
                storage.activities.clear()
                storage.activities.addAll(filteredActivities)
                
                // Update last sync time
                storage.lastSyncTime = now
                
                // Save to JSON
                storageHelper.writeStoredActivities(storage)
                
                // Display all activities (including ignored) for transparency
                withContext(Dispatchers.Main) {
                    val displayActivities = storage.activities.map { it.toExternalActivity() }
                    syncedActivities = displayActivities
                    displayActivitiesWithIgnored(storage.activities)
                    binding.textLastSync.text = "Last synced: ${dateTimeFormat.format(Date(now))}"
                    binding.buttonSync.isEnabled = true
                    binding.buttonSync.text = "Sync Now"
                    
                    val totalNew = activitiesToAdd.size
                    val message = when {
                        totalNew == 0 && duplicateCount > 0 -> "No new activities (${duplicateCount} duplicates skipped)"
                        totalNew == 0 -> "No activities found in the last 7 days"
                        ignoredCount > 0 -> "Synced $totalNew activities (${ignoredCount} ignored due to workout overlap, ${duplicateCount} duplicates)"
                        else -> "Synced $totalNew activities (${duplicateCount} duplicates skipped)"
                    }
                    Toast.makeText(this@HealthConnectActivity, message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.buttonSync.isEnabled = true
                    binding.buttonSync.text = "Sync Now"
                    Toast.makeText(this@HealthConnectActivity, "Sync failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getActivityTypeName(type: Int): String {
        return when (type) {
            androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
            androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Cycling"
            androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Spinning"
            androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Swimming (Pool)"
            androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Swimming (Open Water)"
            androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hiking"
            androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN -> "Football"
            androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "Soccer"
            androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Weightlifting"
            else -> "Exercise"
        }
    }
}

