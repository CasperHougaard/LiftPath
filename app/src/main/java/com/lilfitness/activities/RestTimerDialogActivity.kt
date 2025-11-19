package com.lilfitness.activities

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
import com.lilfitness.R
import com.lilfitness.databinding.DialogRestTimerBinding
import com.lilfitness.services.RestTimerService

class RestTimerDialogActivity : AppCompatActivity() {

    private lateinit var binding: DialogRestTimerBinding
    private var exerciseName: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private var pollingRunnable: Runnable? = null
    
    private val timerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.lilfitness.REST_TIMER_TICK" -> {
                    val remaining = intent.getIntExtra("remaining", 0)
                    Log.d("RestTimer", "Received TICK broadcast: $remaining seconds")
                    updateTimerDisplay(remaining)
                }
                "com.lilfitness.REST_TIMER_COMPLETE" -> {
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
        val remainingSeconds = com.lilfitness.services.RestTimerService.getRemainingSeconds(this)
        updateTimerDisplay(remainingSeconds)
        
        setupButtons()
        
        // Register receiver for timer updates
        val filter = IntentFilter().apply {
            addAction("com.lilfitness.REST_TIMER_TICK")
            addAction("com.lilfitness.REST_TIMER_COMPLETE")
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
                if (com.lilfitness.services.RestTimerService.isTimerRunning(this@RestTimerDialogActivity)) {
                    val remaining = com.lilfitness.services.RestTimerService.getRemainingSeconds(this@RestTimerDialogActivity)
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
            val intent = Intent(this, com.lilfitness.services.RestTimerService::class.java).apply {
                action = RestTimerService.ACTION_ADD_TIME
            }
            startService(intent)
        }
        
        binding.btnRemove15s.setOnClickListener {
            val intent = Intent(this, com.lilfitness.services.RestTimerService::class.java).apply {
                action = RestTimerService.ACTION_REMOVE_TIME
            }
            startService(intent)
        }
        
        binding.btnSkipRest.setOnClickListener {
            com.lilfitness.services.RestTimerService.stopTimer(this)
            finish()
        }
        
        binding.btnDismiss.setOnClickListener {
            finish()
        }
    }

    private fun updateTimerDisplay(seconds: Int) {
        val minutes = seconds / 60
        val secs = seconds % 60
        binding.tvTimerDisplay.text = String.format("%d:%02d", minutes, secs)
        
        // Change color based on time remaining
        val color = when {
            seconds > 60 -> getColor(R.color.fitness_accent)
            seconds > 30 -> getColor(android.R.color.holo_orange_dark)
            else -> getColor(android.R.color.holo_red_dark)
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

