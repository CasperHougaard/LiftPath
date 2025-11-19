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
import com.lilfitness.activities.MainActivity
import com.lilfitness.activities.RestTimerDialogActivity

class RestTimerService : Service() {

    private var countDownTimer: CountDownTimer? = null
    private var isTimerRunning = false
    private var remainingSeconds = 0
    
    companion object {
        const val CHANNEL_ID = "RestTimerChannel"
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_ADD_TIME = "ACTION_ADD_TIME"
        const val ACTION_REMOVE_TIME = "ACTION_REMOVE_TIME"
        
        const val EXTRA_DURATION_SECONDS = "EXTRA_DURATION_SECONDS"
        const val EXTRA_EXERCISE_NAME = "EXTRA_EXERCISE_NAME"
        
        private const val PREFS_NAME = "RestTimerPrefs"
        private const val KEY_REMAINING = "remaining_seconds"
        private const val KEY_IS_RUNNING = "is_running"
        
        fun startTimer(context: Context, durationSeconds: Int, exerciseName: String) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
                putExtra(EXTRA_EXERCISE_NAME, exerciseName)
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
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val duration = intent.getIntExtra(EXTRA_DURATION_SECONDS, 180)
                val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: "Exercise"
                startCountdown(duration, exerciseName)
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
        }
        return START_STICKY
    }

    private fun startCountdown(durationSeconds: Int, exerciseName: String) {
        stopCountdown()
        
        currentExerciseName = exerciseName
        remainingSeconds = durationSeconds
        isTimerRunning = true
        saveState()
        
        // Show timer dialog FIRST so receiver is registered
        com.lilfitness.activities.RestTimerDialogActivity.show(this, exerciseName)
        
        // Small delay to ensure dialog's receiver is registered
        android.os.Handler(mainLooper).postDelayed({
            countDownTimer = object : CountDownTimer((durationSeconds * 1000L), 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    remainingSeconds = (millisUntilFinished / 1000).toInt()
                    saveState()
                    // Don't update notification every second - too spammy for watches
                    
                    // Broadcast update for UI only
                    sendBroadcast(Intent("com.lilfitness.REST_TIMER_TICK").apply {
                        putExtra("remaining", remainingSeconds)
                    })
                }

                override fun onFinish() {
                    remainingSeconds = 0
                    isTimerRunning = false
                    saveState()
                    
                    // Vibrate phone
                    vibratePhone()
                    
                    // Broadcast completion
                    sendBroadcast(Intent("com.lilfitness.REST_TIMER_COMPLETE"))
                    
                    stopSelf()
                }
            }.start()
            
            // Send initial update immediately after starting
            sendBroadcast(Intent("com.lilfitness.REST_TIMER_TICK").apply {
                putExtra("remaining", remainingSeconds)
            })
        }, 300)
        
        startForeground(NOTIFICATION_ID, createNotification(remainingSeconds, exerciseName, false))
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
                    // Don't update notification every second - too spammy for watches
                    
                    // Broadcast update for UI only
                    sendBroadcast(Intent("com.lilfitness.REST_TIMER_TICK").apply {
                        putExtra("remaining", remainingSeconds)
                    })
                }

                override fun onFinish() {
                    remainingSeconds = 0
                    isTimerRunning = false
                    saveState()
                    
                    // Vibrate phone
                    vibratePhone()
                    
                    // Don't show completion notification, just broadcast
                    // Broadcast completion
                    sendBroadcast(Intent("com.lilfitness.REST_TIMER_COMPLETE"))
                    
                    stopSelf()
                }
            }.start()
            
            updateNotification(newRemaining, currentExerciseName, false)
        }
    }

    private fun saveState() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putInt(KEY_REMAINING, remainingSeconds)
            putBoolean(KEY_IS_RUNNING, isTimerRunning)
            apply()
        }
    }

    private fun updateNotification(seconds: Int, exerciseName: String, isComplete: Boolean) {
        val notification = createNotification(seconds, exerciseName, isComplete)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(seconds: Int, exerciseName: String, isComplete: Boolean): Notification {
        createNotificationChannel()
        
        val minutes = seconds / 60
        val secs = seconds % 60
        val timeText = String.format("%d:%02d", minutes, secs)
        
        val contentText = if (isComplete) {
            "✅ Rest complete! Ready for next set"
        } else {
            "⏱️ $timeText remaining"
        }
        
        // Intent to open app
        val openIntent = Intent(this, com.lilfitness.activities.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Add time action
        val addTimeIntent = Intent(this, RestTimerService::class.java).apply {
            action = ACTION_ADD_TIME
        }
        val addTimePendingIntent = PendingIntent.getService(
            this, 1, addTimeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Remove time action
        val removeTimeIntent = Intent(this, RestTimerService::class.java).apply {
            action = ACTION_REMOVE_TIME
        }
        val removeTimePendingIntent = PendingIntent.getService(
            this, 2, removeTimeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Stop action
        val stopIntent = Intent(this, RestTimerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 3, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rest Timer - $exerciseName")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(!isComplete)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_media_play, "+15s", addTimePendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "-15s", removeTimePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setProgress(0, 0, false)
            .build()
    }

    private fun showCompletionNotification(exerciseName: String) {
        val notification = createNotification(0, exerciseName, true)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        
        // Auto-dismiss after 5 seconds
        android.os.Handler(mainLooper).postDelayed({
            notificationManager.cancel(NOTIFICATION_ID)
        }, 5000)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rest Timer",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows rest timer countdown between sets"
                setSound(null, null)  // Silent by default
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
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

