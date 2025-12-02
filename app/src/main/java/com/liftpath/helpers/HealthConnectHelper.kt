package com.liftpath.helpers

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit
import com.liftpath.models.TrainingSession
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object HealthConnectHelper {

    // 1. Define Permissions we need
    val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class) // Optional
    )

    // 2. Check if available
    fun isAvailable(context: Context): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    // 3. Get the Client
    fun getClient(context: Context): HealthConnectClient? {
        return if (isAvailable(context)) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    /**
     * READS workouts from the last 7 days (to match your fatigue window).
     */
    suspend fun readRecentExercises(context: Context): List<ExternalActivity> {
        val client = getClient(context) ?: return emptyList()

        // Verify permissions first (Logic handled in UI, but good safety check)
        val granted = client.permissionController.getGrantedPermissions()
        if (!granted.containsAll(PERMISSIONS)) return emptyList()

        val endTime = Instant.now()
        val startTime = endTime.minus(7, ChronoUnit.DAYS)

        val request = ReadRecordsRequest(
            recordType = ExerciseSessionRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
        )

        val response = client.readRecords(request)

        // Map Health Connect records to YOUR App's format
        return response.records.map { record ->
            mapRecordToFatigueActivity(record)
        }
    }

    /**
     * Maps Google's generic types to YOUR specific Fatigue Impact
     */
    private fun mapRecordToFatigueActivity(record: ExerciseSessionRecord): ExternalActivity {
        val durationMinutes = ChronoUnit.MINUTES.between(record.startTime, record.endTime).toFloat()
        
        // Default Moderate Fatigue
        var fatigueImpact = FatigueScores(0f, 0f, 0f)

        when (record.exerciseType) {
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN,
            ExerciseSessionRecord.EXERCISE_TYPE_SOCCER,
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> {
                // High Lower Body Fatigue
                // Example formula: 1.5 fatigue per minute of running
                val score = durationMinutes * 1.5f 
                fatigueImpact = FatigueScores(lowerFatigue = score, upperFatigue = 0f, systemicFatigue = score * 0.8f)
            }

            ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> {
                // Moderate Lower Body Fatigue
                val score = durationMinutes * 1.0f
                fatigueImpact = FatigueScores(lowerFatigue = score, upperFatigue = 0f, systemicFatigue = score * 0.6f)
            }

            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> {
                // High Upper Body & Systemic
                val score = durationMinutes * 1.2f
                fatigueImpact = FatigueScores(lowerFatigue = score * 0.2f, upperFatigue = score, systemicFatigue = score)
            }
            
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> {
                // Warning: This might duplicate what you log manually in the app!
                // You might want to ignore this or check if it matches a log in your DB.
                fatigueImpact = FatigueScores(0f, 0f, 0f) 
            }

            else -> {
                // Walking / Yoga / Other
                val score = durationMinutes * 0.5f
                fatigueImpact = FatigueScores(lowerFatigue = score * 0.5f, upperFatigue = 0f, systemicFatigue = score * 0.5f)
            }
        }

        return ExternalActivity(
            id = record.metadata.id,
            startTime = record.startTime.toEpochMilli(),
            endTime = record.endTime.toEpochMilli(),
            type = record.exerciseType,
            fatigue = fatigueImpact
        )
    }

    /**
     * Gets stored activities from JSON (non-ignored only).
     * Used by fatigue calculation when Health Connect toggle is enabled.
     */
    fun getStoredActivities(context: Context): List<ExternalActivity> {
        val storageHelper = HealthConnectStorageHelper(context)
        val storage = storageHelper.readStoredActivities()
        
        // Filter out ignored activities and return as ExternalActivity
        return storage.activities
            .filter { !it.ignored }
            .map { it.toExternalActivity() }
    }

    /**
     * Checks if a Health Connect activity overlaps with any workout.
     * Overlap criteria:
     * - Same day
     * - Start time within ±20 minutes OR activity time range overlaps with workout time range
     * - Duration within ±20 minutes
     */
    fun checkWorkoutOverlap(
        activity: ExternalActivity,
        workouts: List<TrainingSession>
    ): Boolean {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val activityDate = dateFormat.format(Date(activity.startTime))
        
        // Filter workouts on the same day
        val sameDayWorkouts = workouts.filter { workout ->
            workout.date == activityDate
        }
        
        if (sameDayWorkouts.isEmpty()) {
            return false
        }
        
        // Activity times
        val activityStartTime = activity.startTime
        val activityEndTime = activity.endTime
        val activityDurationMs = activityEndTime - activityStartTime
        
        // Check each workout on the same day
        for (workout in sameDayWorkouts) {
            try {
                // Parse workout date to get start of day
                val workoutDate = dateFormat.parse(workout.date) ?: continue
                
                // Get workout duration (default to 1 hour if not specified)
                val workoutDurationMs = (workout.durationSeconds?.times(1000) ?: 3600_000L)
                
                // Get activity time of day (hour and minute)
                val activityCalendar = Calendar.getInstance().apply {
                    timeInMillis = activityStartTime
                }
                val activityHour = activityCalendar.get(Calendar.HOUR_OF_DAY)
                val activityMinute = activityCalendar.get(Calendar.MINUTE)
                
                // Try workout start time at the same hour as activity, or nearby hours
                // Check activity hour ± 2 hours to account for timing differences
                val hoursToCheck = (activityHour - 2..activityHour + 2).filter { it in 0..23 }
                
                for (hour in hoursToCheck) {
                    val calendar = Calendar.getInstance().apply {
                        time = workoutDate
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, activityMinute) // Use same minute as activity
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val workoutStartTime = calendar.timeInMillis
                    val workoutEndTime = workoutStartTime + workoutDurationMs
                    
                    // Check if activity time range overlaps with workout time range
                    val timeOverlaps = (activityStartTime <= workoutEndTime && activityEndTime >= workoutStartTime)
                    
                    // Check start time difference (±20 minutes)
                    val startTimeDiff = kotlin.math.abs(activityStartTime - workoutStartTime)
                    val startTimeClose = startTimeDiff <= 20 * 60 * 1000L
                    
                    // Check duration difference (±20 minutes)
                    val durationDiff = kotlin.math.abs(activityDurationMs - workoutDurationMs)
                    val durationClose = durationDiff <= 20 * 60 * 1000L
                    
                    // If time ranges overlap AND duration is close, it's a match
                    // OR if start time and duration are both close, it's a match
                    if ((timeOverlaps && durationClose) || (startTimeClose && durationClose)) {
                        return true
                    }
                }
                
                // Also check if activity is within the workout day and duration is similar
                // This catches cases where timing is off but it's clearly the same workout
                val dayStart = Calendar.getInstance().apply {
                    time = workoutDate
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                
                val dayEnd = dayStart + (24 * 60 * 60 * 1000L)
                
                // If activity is on the same day and duration is very close (±10 minutes), consider it a match
                if (activityStartTime >= dayStart && activityStartTime < dayEnd) {
                    val durationDiff = kotlin.math.abs(activityDurationMs - workoutDurationMs)
                    if (durationDiff <= 10 * 60 * 1000L) { // Within 10 minutes
                        return true
                    }
                }
            } catch (e: Exception) {
                // Skip workouts with invalid dates
                continue
            }
        }
        
        return false
    }

    /**
     * Auto-syncs Health Connect activities in the background.
     * This function performs the same sync logic as the manual sync but silently.
     * It checks permissions, fetches activities, deduplicates, checks overlaps, and stores them.
     * 
     * @param context Application context
     * @param retentionDays Number of days to retain activities (default: 14)
     * @return Result indicating success or failure (no UI feedback)
     */
    suspend fun autoSyncActivities(
        context: Context,
        retentionDays: Int = 14
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // Check if Health Connect is available
            if (!isAvailable(context)) {
                return@withContext Result.failure(Exception("Health Connect is not available"))
            }

            // Check permissions
            val client = getClient(context) ?: return@withContext Result.failure(Exception("Health Connect client unavailable"))
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.containsAll(PERMISSIONS)) {
                return@withContext Result.failure(Exception("Health Connect permissions not granted"))
            }

            // Load existing stored activities
            val storageHelper = HealthConnectStorageHelper(context)
            val storage = storageHelper.readStoredActivities()
            storage.retentionDays = retentionDays

            val existingActivityIds = storage.activities.map { it.id }.toSet()

            // Fetch new activities from Health Connect
            val newActivities = readRecentExercises(context)

            // Get workouts for overlap detection
            val jsonHelper = JsonHelper(context)
            val trainingData = jsonHelper.readTrainingData()
            val workouts = trainingData.trainings

            // Process new activities: deduplicate and check overlaps
            val now = System.currentTimeMillis()
            val retentionCutoff = now - (retentionDays * 24 * 60 * 60 * 1000L)

            val activitiesToAdd = mutableListOf<StoredHealthConnectActivity>()

            for (activity in newActivities) {
                // Skip if already exists (deduplication)
                if (activity.id in existingActivityIds) {
                    continue
                }

                // Check for overlap with workouts
                val hasOverlap = checkWorkoutOverlap(activity, workouts)

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

            Result.success(activitiesToAdd.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// Simple data class to hold the converted data
data class ExternalActivity(
    val id: String,
    val startTime: Long,
    val endTime: Long,
    val type: Int,
    val fatigue: FatigueScores
)

