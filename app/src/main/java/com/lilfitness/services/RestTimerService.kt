package com.lilfitness.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lilfitness.R
import com.lilfitness.activities.MainActivity
import com.lilfitness.activities.RestTimerDialogActivity

class RestTimerService : Service() {

    private var countDownTimer: CountDownTimer? = null
    private var isTimerRunning = false
    private var remainingSeconds = 0
    
    companion object {
        const val CHANNEL_ID = "RestTimerChannel"
        const val NOTIFICATION_ID = 1001
        const val COMPLETION_NOTIFICATION_ID = 1002
        const val COMPLETION_CHANNEL_ID = "RestTimerCompletionChannel"
        
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_ADD_TIME = "ACTION_ADD_TIME"
        const val ACTION_REMOVE_TIME = "ACTION_REMOVE_TIME"
        const val ACTION_SET_TIME = "ACTION_SET_TIME"
        
        const val EXTRA_DURATION_SECONDS = "EXTRA_DURATION_SECONDS"
        const val EXTRA_EXERCISE_NAME = "EXTRA_EXERCISE_NAME"
        const val EXTRA_SHOW_DIALOG = "EXTRA_SHOW_DIALOG"
        
        private const val PREFS_NAME = "RestTimerPrefs"
        private const val KEY_REMAINING = "remaining_seconds"
        private const val KEY_IS_RUNNING = "is_running"
        
        fun startTimer(context: Context, durationSeconds: Int, exerciseName: String, showDialog: Boolean = true) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
                putExtra(EXTRA_EXERCISE_NAME, exerciseName)
                putExtra(EXTRA_SHOW_DIALOG, showDialog)
            }
            context.startService(intent)
        }
        
        fun stopTimer(context: Context) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
        
        fun isTimerRunning(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_IS_RUNNING, false)
        }
        
        fun getRemainingSeconds(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_REMAINING, 0)
        }
        
        fun setTimerTime(context: Context, durationSeconds: Int) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_SET_TIME
                putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
            }
            context.startService(intent)
        }
        
        fun addTime(context: Context, seconds: Int = 15) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_ADD_TIME
            }
            context.startService(intent)
        }
        
        fun removeTime(context: Context, seconds: Int = 15) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_REMOVE_TIME
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val duration = intent.getIntExtra(EXTRA_DURATION_SECONDS, 180)
                val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: "Exercise"
                val showDialog = intent.getBooleanExtra(EXTRA_SHOW_DIALOG, true)
                startCountdown(duration, exerciseName, showDialog)
            }
            ACTION_STOP -> {
                stopCountdown()
            }
            ACTION_ADD_TIME -> {
                adjustTime(15)
            }
            ACTION_REMOVE_TIME -> {
                adjustTime(-15)
            }
            ACTION_SET_TIME -> {
                val duration = intent.getIntExtra(EXTRA_DURATION_SECONDS, 0)
                setTimerTime(duration)
            }
        }
        return START_STICKY
    }

    private var shouldShowDialog = true
    
    private fun startCountdown(durationSeconds: Int, exerciseName: String, showDialog: Boolean = true) {
        stopCountdown()
        
        currentExerciseName = exerciseName
        remainingSeconds = durationSeconds
        isTimerRunning = true
        shouldShowDialog = showDialog
        saveState()
        
        // Show timer dialog only if requested
        if (showDialog) {
            com.lilfitness.activities.RestTimerDialogActivity.show(this, exerciseName)
        }
        
        // Small delay to ensure dialog's receiver is registered (if shown)
        val delay = if (showDialog) 300L else 0L
        android.os.Handler(mainLooper).postDelayed({
            countDownTimer = object : CountDownTimer((durationSeconds * 1000L), 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    remainingSeconds = (millisUntilFinished / 1000).toInt()
                    saveState()
                    
                    // Broadcast update for UI (timer counts live in the app)
                    sendBroadcast(Intent("com.lilfitness.REST_TIMER_TICK").apply {
                        setPackage(applicationContext.packageName)
                        putExtra("remaining", remainingSeconds)
                    })
                }

                override fun onFinish() {
                    remainingSeconds = 0
                    isTimerRunning = false
                    saveState()
                    
                    // Vibrate phone
                    vibratePhone()
                    
                    // Show completion notification that auto-dismisses after 5 seconds
                    showCompletionNotification()
                    
                    // Broadcast completion (activity will handle UI updates)
                    sendBroadcast(Intent("com.lilfitness.REST_TIMER_COMPLETE").apply {
                        setPackage(applicationContext.packageName)
                    })
                    
                    // Stop foreground service and self
                    stopForeground(true)
                    stopSelf()
                }
            }.start()
            
            // Send initial update immediately after starting
            sendBroadcast(Intent("com.lilfitness.REST_TIMER_TICK").apply {
                setPackage(applicationContext.packageName)
                putExtra("remaining", remainingSeconds)
            })
        }, delay)
        
        // Start foreground service with minimal notification (required for background execution)
        startForeground(NOTIFICATION_ID, createMinimalNotification())
    }

    private fun stopCountdown() {
        countDownTimer?.cancel()
        countDownTimer = null
        isTimerRunning = false
        remainingSeconds = 0
        saveState()
        stopForeground(true)
    }

    private var currentExerciseName: String = "Exercise"
    
    private fun adjustTime(seconds: Int) {
        if (isTimerRunning && countDownTimer != null) {
            val newRemaining = (remainingSeconds + seconds).coerceAtLeast(0)
            // Restart timer with adjusted time, don't show dialog again
            countDownTimer?.cancel()
            
            remainingSeconds = newRemaining
            saveState()
            
            countDownTimer = object : CountDownTimer((newRemaining * 1000L), 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    remainingSeconds = (millisUntilFinished / 1000).toInt()
                    saveState()
                    
                    // Broadcast update for UI (timer counts live in the app)
                    sendBroadcast(Intent("com.lilfitness.REST_TIMER_TICK").apply {
                        setPackage(applicationContext.packageName)
                        putExtra("remaining", remainingSeconds)
                    })
                }

                override fun onFinish() {
                    remainingSeconds = 0
                    isTimerRunning = false
                    saveState()
                    
                    // Vibrate phone
                    vibratePhone()
                    
                    // Broadcast completion (activity will handle UI updates)
                    sendBroadcast(Intent("com.lilfitness.REST_TIMER_COMPLETE").apply {
                        setPackage(applicationContext.packageName)
                    })
                    
                    // Stop foreground service and self
                    stopForeground(true)
                    stopSelf()
                }
            }.start()
            
            // Update foreground notification silently (required for foreground service)
            startForeground(NOTIFICATION_ID, createMinimalNotification())
        }
    }
    
    private fun setTimerTime(durationSeconds: Int) {
        // Pause timer if running
        val wasRunning = isTimerRunning
        if (wasRunning && countDownTimer != null) {
            countDownTimer?.cancel()
            countDownTimer = null
        }
        
        // Set new time
        remainingSeconds = durationSeconds.coerceAtLeast(0)
        isTimerRunning = false // Reset to idle state
        saveState()
        
        // Broadcast update to UI
        sendBroadcast(Intent("com.lilfitness.REST_TIMER_TICK").apply {
            setPackage(applicationContext.packageName)
            putExtra("remaining", remainingSeconds)
        })
        
        // Stop foreground service if it was running
        if (wasRunning) {
            stopForeground(true)
        }
    }

    private fun saveState() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putInt(KEY_REMAINING, remainingSeconds)
            putBoolean(KEY_IS_RUNNING, isTimerRunning)
            apply()
        }
    }

    // Minimal notification required for foreground service (Android requirement)
    // This runs silently in background - no visible notification shown
    private fun createMinimalNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create channel with lowest importance (won't show to user)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_rest_timer),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description_background)
                setShowBadge(false)
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        
        // Create minimal notification (required for foreground service)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_rest_timer_title))
            .setContentText(getString(R.string.notification_rest_timer_running))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun showCompletionNotification() {
        // Create notification channel for completion notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                COMPLETION_CHANNEL_ID,
                getString(R.string.notification_channel_timer_completion),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_description_completion)
                enableVibration(false) // Already vibrating separately
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        
        // Create intent to open the app when notification is tapped
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        } else {
            @Suppress("DEPRECATION")
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
        
        // Create notification
        val notification = NotificationCompat.Builder(this, COMPLETION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_timer_completion_title))
            .setContentText(getString(R.string.notification_timer_completion_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .build()
        
        // Show notification
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(COMPLETION_NOTIFICATION_ID, notification)
        
        // Auto-dismiss after 10 seconds
        android.os.Handler(mainLooper).postDelayed({
            notificationManager.cancel(COMPLETION_NOTIFICATION_ID)
        }, 10000)
    }
    
    private fun vibratePhone() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Vibrate pattern: 500ms on, 200ms off, 500ms on (strong finish)
            val pattern = longArrayOf(0, 500, 200, 500)
            val amplitudes = intArrayOf(0, 255, 0, 255)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCountdown()
    }
}

