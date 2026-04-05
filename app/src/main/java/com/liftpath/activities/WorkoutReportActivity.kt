package com.liftpath.activities

import android.app.Activity
import android.graphics.drawable.Animatable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.liftpath.R
import com.liftpath.adapters.ExerciseTrendAdapter
import com.liftpath.databinding.ActivityWorkoutReportBinding
import com.liftpath.helpers.DurationHelper
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.WorkoutComparisonHelper
import com.liftpath.models.TargetMuscle
import com.liftpath.models.TrainingSession

class WorkoutReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorkoutReportBinding
    private lateinit var jsonHelper: JsonHelper
    private lateinit var trainingSession: TrainingSession
    private var isWebViewReady = false

    companion object {
        const val EXTRA_TRAINING_SESSION = "extra_training_session"
        private const val TAG = "WorkoutReportActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkoutReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jsonHelper = JsonHelper(this)

        // Get training session from intent
        val session = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_TRAINING_SESSION, TrainingSession::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_TRAINING_SESSION)
        }

        if (session == null) {
            Log.e(TAG, "No training session provided")
            finish()
            return
        }

        trainingSession = session

        setupBackgroundAnimation()
        setupClickListeners()
        loadReportData()
    }

    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is Animatable) {
            drawable.start()
        }
    }

    private fun setupClickListeners() {
        binding.buttonBack.setOnClickListener {
            finish()
        }

        binding.buttonDone.setOnClickListener {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun loadReportData() {
        try {
            // Load training data
            val trainingData = jsonHelper.readTrainingData()
            val allSessions = trainingData.trainings
            val exerciseLibrary = trainingData.exerciseLibrary

            // Calculate summary
            val summary = WorkoutComparisonHelper.calculateSessionSummary(
                trainingSession,
                allSessions
            )

            // Display header
            binding.textDate.text = trainingSession.date

            // Display summary stats
            binding.textTotalVolume.text = String.format("%,dkg", summary.totalVolume.toInt())
            binding.textTotalSets.text = summary.totalSets.toString()
            binding.textTotalReps.text = summary.totalReps.toString()
            binding.textExercisesCount.text = summary.exerciseCount.toString()
            binding.textPrsCount.text = summary.prCount.toString()

            val durationText = summary.durationSeconds?.let { 
                DurationHelper.formatDuration(it) 
            } ?: "--"
            binding.textDuration.text = durationText

            // Calculate exercise trends
            val exerciseTrends = WorkoutComparisonHelper.calculateExerciseTrends(
                trainingSession,
                allSessions,
                exerciseLibrary
            )

            // Setup exercise trends RecyclerView
            binding.recyclerExerciseTrends.layoutManager = LinearLayoutManager(this)
            binding.recyclerExerciseTrends.adapter = ExerciseTrendAdapter(exerciseTrends)

            // Calculate muscle progress
            val muscleProgress = WorkoutComparisonHelper.calculateMuscleProgress(
                trainingSession,
                allSessions,
                exerciseLibrary
            )

            // Setup muscle map WebView
            setupMuscleMapWebView(muscleProgress)

        } catch (e: Exception) {
            Log.e(TAG, "Error loading report data", e)
        }
    }

    private fun setupMuscleMapWebView(muscleProgress: Map<TargetMuscle, Float?>) {
        binding.webviewMuscleMap.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        binding.webviewMuscleMap.settings.apply {
            javaScriptEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            domStorageEnabled = true
        }

        binding.webviewMuscleMap.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    Log.d(TAG, "${it.message()} -- From line ${it.lineNumber()}")
                }
                return true
            }
        }

        binding.webviewMuscleMap.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isWebViewReady = true
                binding.progressMuscleMap.visibility = View.GONE
                updateMuscleMap(muscleProgress)
            }
        }

        // Try both HTML files - use progress version, fallback to original if needed
        binding.webviewMuscleMap.loadUrl("file:///android_asset/muscle_map.html")
    }

    private fun updateMuscleMap(muscleProgress: Map<TargetMuscle, Float?>) {
        if (!isWebViewReady) return
        if (muscleProgress.isEmpty()) return

        val improvedMuscles = muscleProgress
            .filter { (_, progress) -> progress != null && progress > 0 }
            .keys.toList()
        val otherMuscles = muscleProgress
            .filter { (_, progress) -> progress == null || progress <= 0 }
            .keys.toList()

        val primaryArray = improvedMuscles.joinToString(
            prefix = "[", postfix = "]", separator = ", "
        ) { "'${it.name}'" }
        val secondaryArray = otherMuscles.joinToString(
            prefix = "[", postfix = "]", separator = ", "
        ) { "'${it.name}'" }

        // Call JavaScript setHighlights function (from muscle_map.html)
        val jsCode = """
            (function() {
                try {
                    if (typeof setHighlights === 'function') {
                        setHighlights($primaryArray, $secondaryArray);
                        return 'setHighlights called';
                    } else if (typeof window.setHighlights === 'function') {
                        window.setHighlights($primaryArray, $secondaryArray);
                        return 'window.setHighlights called';
                    } else {
                        console.error('setHighlights function not found!');
                        return 'ERROR: setHighlights not found';
                    }
                } catch (e) {
                    console.error('Error calling setHighlights:', e);
                    return 'ERROR: ' + e.message;
                }
            })();
        """.trimIndent()

        binding.webviewMuscleMap.evaluateJavascript(jsCode) { /* no-op */ }
    }
}
