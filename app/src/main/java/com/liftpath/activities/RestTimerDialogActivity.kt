package com.liftpath.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.liftpath.R
import com.liftpath.databinding.DialogRestTimerBinding
import com.liftpath.services.RestTimerService
import com.liftpath.helpers.lpColor
import java.util.Locale

class RestTimerDialogActivity : AppCompatActivity() {

    private lateinit var binding: DialogRestTimerBinding
    private var exerciseName: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private var pollingRunnable: Runnable? = null
    
    private val timerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.liftpath.REST_TIMER_TICK" -> {
                    val remaining = intent.getIntExtra("remaining", 0)
                    Log.d("RestTimer", "Received TICK broadcast: $remaining seconds")
                    updateTimerDisplay(remaining)
                }
                "com.liftpath.REST_TIMER_COMPLETE" -> {
                    Log.d("RestTimer", "Received COMPLETE broadcast")
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make dialog show on lock screen
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        
        binding = DialogRestTimerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        exerciseName = intent.getStringExtra("EXERCISE_NAME") ?: "Exercise"
        binding.tvExerciseName.text = exerciseName
        
        // Get initial timer value
        val remainingSeconds = com.liftpath.services.RestTimerService.getRemainingSeconds(this)
        updateTimerDisplay(remainingSeconds)
        
        setupButtons()
        
        // Register receiver for timer updates
        val filter = IntentFilter().apply {
            addAction("com.liftpath.REST_TIMER_TICK")
            addAction("com.liftpath.REST_TIMER_COMPLETE")
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(timerReceiver, filter)
        }
        
        Log.d("RestTimer", "Dialog created, receiver registered")
        
        // Start polling for timer updates as backup (in case broadcasts are missed)
        startPolling()
    }
    
    private fun startPolling() {
        pollingRunnable = object : Runnable {
            override fun run() {
                if (com.liftpath.services.RestTimerService.isTimerRunning(this@RestTimerDialogActivity)) {
                    val remaining = com.liftpath.services.RestTimerService.getRemainingSeconds(this@RestTimerDialogActivity)
                    updateTimerDisplay(remaining)
                    handler.postDelayed(this, 250) // Poll 4 times per second for smooth updates
                } else {
                    // Timer stopped, close dialog
                    finish()
                }
            }
        }
        handler.post(pollingRunnable!!)
    }
    
    private fun stopPolling() {
        pollingRunnable?.let {
            handler.removeCallbacks(it)
        }
    }

    private fun setupButtons() {
        binding.btnAdd15s.setOnClickListener {
            val intent = Intent(this, com.liftpath.services.RestTimerService::class.java).apply {
                action = RestTimerService.ACTION_ADD_TIME
            }
            startService(intent)
        }
        
        binding.btnRemove15s.setOnClickListener {
            val intent = Intent(this, com.liftpath.services.RestTimerService::class.java).apply {
                action = RestTimerService.ACTION_REMOVE_TIME
            }
            startService(intent)
        }
        
        binding.btnSkipRest.setOnClickListener {
            com.liftpath.services.RestTimerService.stopTimer(this)
            finish()
        }
        
        binding.btnDismiss.setOnClickListener {
            finish()
        }
    }

    private fun updateTimerDisplay(seconds: Int) {
        val minutes = seconds / 60
        val secs = seconds % 60
        binding.tvTimerDisplay.text = String.format(Locale.getDefault(), "%d:%02d", minutes, secs)

        // Urgency runs quiet -> accent -> negative. All three are theme attributes, so the
        // gradient stays coherent in every palette; the holo_* colours this replaced were
        // Android 4 stock orange and red, and read as somebody else's app.
        val color = when {
            seconds > 60 -> lpColor(R.attr.lpInk)
            seconds > 30 -> lpColor(R.attr.lpAccent)
            else -> lpColor(R.attr.lpNegative)
        }
        binding.tvTimerDisplay.setTextColor(color)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPolling()
        try {
            unregisterReceiver(timerReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
    }
    
    companion object {
        fun show(context: Context, exerciseName: String) {
            val intent = Intent(context, RestTimerDialogActivity::class.java).apply {
                putExtra("EXERCISE_NAME", exerciseName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}

