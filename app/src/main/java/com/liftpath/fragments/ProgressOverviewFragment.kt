package com.liftpath.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.liftpath.R
import com.liftpath.databinding.FragmentProgressOverviewBinding
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.OneRMEstimationHelper
import com.liftpath.helpers.ProgressAnalysisHelper
import com.liftpath.models.SetIntent
import com.liftpath.models.TargetMuscle
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ProgressOverviewFragment : Fragment() {

    private var _binding: FragmentProgressOverviewBinding? = null
    private val binding get() = _binding!!
    private lateinit var jsonHelper: JsonHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProgressOverviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        jsonHelper = JsonHelper(requireContext())
        loadOverviewData()
    }

    override fun onResume() {
        super.onResume()
        loadOverviewData()
    }

    private fun loadOverviewData() {
        val trainingData = jsonHelper.readTrainingData()
        val sessions = trainingData.trainings
        val exerciseLibrary = trainingData.exerciseLibrary

        // Weekly Summary
        val weeklySummary = ProgressAnalysisHelper.getWeeklySummary(sessions)
        binding.textWeeklySessions.text = weeklySummary.sessionCount.toString()
        binding.textWeeklyVolume.text = String.format(Locale.US, "%,.0f", weeklySummary.totalVolume)
        binding.textWeeklyPrs.text = weeklySummary.prCount.toString()

        // Recent PRs
        val recentPRs = ProgressAnalysisHelper.getRecentPRs(sessions, exerciseLibrary, 30)
        if (recentPRs.isEmpty()) {
            binding.scrollRecentPrs.visibility = View.GONE
            binding.textNoPrs.visibility = View.VISIBLE
        } else {
            binding.scrollRecentPrs.visibility = View.VISIBLE
            binding.textNoPrs.visibility = View.GONE
            populatePRCards(recentPRs)
        }

        // Intent Distribution
        val intentDistribution = ProgressAnalysisHelper.getIntentDistribution(sessions, 30)
        setupIntentChart(intentDistribution)

        // Coach's Analysis
        val coachSummary = generateCoachSummary(sessions)
        binding.textCoachSummary.text = coachSummary

        // Muscle Map
        setupMuscleMap(sessions, exerciseLibrary)
    }

    private fun populatePRCards(prs: List<ProgressAnalysisHelper.PRRecord>) {
        binding.layoutPrCards.removeAllViews()
        
        prs.take(5).forEach { pr ->
            val cardView = CardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.pr_card_width),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = resources.getDimensionPixelSize(R.dimen.card_margin)
                }
                radius = resources.getDimension(R.dimen.card_corner_radius)
                cardElevation = resources.getDimension(R.dimen.card_elevation)
                setCardBackgroundColor(resources.getColor(R.color.fitness_card_background, null))
            }

            val contentLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 24, 32, 24)
            }

            // Exercise name
            val exerciseName = TextView(requireContext()).apply {
                text = pr.exerciseName
                setTextColor(resources.getColor(R.color.fitness_text_primary, null))
                textSize = 14f
                maxLines = 1
            }

            // PR value — format depends on type
            val prValue = TextView(requireContext()).apply {
                text = when (pr.prType) {
                    ProgressAnalysisHelper.PRType.VOLUME ->
                        String.format(Locale.US, "%,d kg", pr.value.toInt())
                    else ->
                        String.format(Locale.US, "%.1f kg", pr.value)
                }
                setTextColor(resources.getColor(R.color.fitness_primary, null))
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            // PR type badge — shows what kind of record it is, not the training intent
            val intentBadge = TextView(requireContext()).apply {
                text = when (pr.prType) {
                    ProgressAnalysisHelper.PRType.WEIGHT  -> getString(R.string.weight_pr_label).uppercase()
                    ProgressAnalysisHelper.PRType.ONE_RM  -> getString(R.string.one_rm_pr_label).uppercase()
                    ProgressAnalysisHelper.PRType.VOLUME  -> getString(R.string.volume_pr_label).uppercase()
                    else -> pr.prType.name
                }
                textSize = 10f
                setTextColor(resources.getColor(R.color.white, null))
                setPadding(16, 4, 16, 4)
                setBackgroundResource(R.drawable.badge_rounded_background)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    when (pr.prType) {
                        ProgressAnalysisHelper.PRType.WEIGHT -> resources.getColor(R.color.intent_strength, null)
                        ProgressAnalysisHelper.PRType.ONE_RM -> resources.getColor(R.color.fitness_accent, null)
                        ProgressAnalysisHelper.PRType.VOLUME -> resources.getColor(R.color.intent_build, null)
                        else -> resources.getColor(R.color.fitness_primary, null)
                    }
                )
            }

            contentLayout.addView(exerciseName)
            contentLayout.addView(prValue)
            contentLayout.addView(intentBadge)
            cardView.addView(contentLayout)
            binding.layoutPrCards.addView(cardView)
        }
    }

    private fun setupIntentChart(distribution: Map<SetIntent, Float>) {
        if (distribution.isEmpty()) {
            binding.chartIntentDistribution.visibility = View.GONE
            return
        }

        binding.chartIntentDistribution.visibility = View.VISIBLE

        val entries = mutableListOf<PieEntry>()
        val colors = mutableListOf<Int>()

        distribution.forEach { (intent, percentage) ->
            if (percentage > 0) {
                entries.add(PieEntry(percentage, intent.displayName))
                colors.add(when (intent) {
                    SetIntent.STRENGTH -> Color.parseColor("#DC2626")
                    SetIntent.BUILD -> Color.parseColor("#F59E0B")
                    SetIntent.FLUSH -> Color.parseColor("#10B981")
                    else -> Color.parseColor("#6B7280")
                })
            }
        }

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            setDrawValues(true)
            valueTextSize = 12f
            valueTextColor = Color.WHITE
            sliceSpace = 2f
        }

        val data = PieData(dataSet)
        binding.chartIntentDistribution.apply {
            this.data = data
            description.isEnabled = false
            legend.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 50f
            transparentCircleRadius = 55f
            setHoleColor(Color.TRANSPARENT)
            setDrawEntryLabels(false)
            animateY(800)
            invalidate()
        }
    }

    private fun generateCoachSummary(sessions: List<com.liftpath.models.TrainingSession>): String {
        if (sessions.isEmpty()) {
            return getString(R.string.progress_no_sessions)
        }

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        val cutoffDate = calendar.time
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

        val recentSessions = sessions.filter { session ->
            try {
                val date = dateFormat.parse(session.date)
                date != null && date.after(cutoffDate)
            } catch (e: Exception) {
                false
            }
        }

        if (recentSessions.isEmpty()) {
            return "No training data in the last 30 days. Time to get back in the gym!"
        }

        val sessionCount = recentSessions.size
        val avgSessionsPerWeek = sessionCount / 4.0

        // Calculate total volume trend
        val sortedSessions = recentSessions.sortedBy { it.date }
        val halfwayPoint = sortedSessions.size / 2
        val firstHalf = sortedSessions.take(halfwayPoint)
        val secondHalf = sortedSessions.drop(halfwayPoint)

        val firstHalfVolume = firstHalf.sumOf { session ->
            session.exercises.filterNot { it.isWarmup }.sumOf { (it.kg * it.reps).toDouble() }
        }
        val secondHalfVolume = secondHalf.sumOf { session ->
            session.exercises.filterNot { it.isWarmup }.sumOf { (it.kg * it.reps).toDouble() }
        }

        val volumeTrend = if (firstHalfVolume > 0) {
            ((secondHalfVolume - firstHalfVolume) / firstHalfVolume) * 100
        } else 0.0

        val summary = StringBuilder()
        
        when {
            avgSessionsPerWeek >= 4 -> summary.append("Great consistency with ${String.format("%.1f", avgSessionsPerWeek)} sessions per week! ")
            avgSessionsPerWeek >= 3 -> summary.append("Good training frequency at ${String.format("%.1f", avgSessionsPerWeek)} sessions per week. ")
            avgSessionsPerWeek >= 2 -> summary.append("Moderate training frequency at ${String.format("%.1f", avgSessionsPerWeek)} sessions per week. Consider adding more sessions for faster progress. ")
            else -> summary.append("Low training frequency. Try to train more consistently for better results. ")
        }

        when {
            volumeTrend > 10 -> summary.append("Volume is trending up ${String.format("%.0f", volumeTrend)}% - excellent progressive overload!")
            volumeTrend > 0 -> summary.append("Volume is slightly up - keep pushing!")
            volumeTrend > -10 -> summary.append("Volume is stable - consider adding weight or reps to keep progressing.")
            else -> summary.append("Volume is down - you may need a deload, or focus on recovery.")
        }

        return summary.toString()
    }

    private fun setupMuscleMap(
        sessions: List<com.liftpath.models.TrainingSession>,
        exerciseLibrary: List<com.liftpath.models.ExerciseLibraryItem>
    ) {
        val muscleTrends = ProgressAnalysisHelper.getMuscleTrends(sessions, exerciseLibrary, 4)
        
        if (muscleTrends.isEmpty()) {
            binding.cardMuscleMap.visibility = View.GONE
            return
        }

        binding.cardMuscleMap.visibility = View.VISIBLE

        binding.webviewMuscleMap.apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
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

        binding.webviewMuscleMap.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                updateMuscleMap(muscleTrends)
            }
        }

        // Load the progress HTML file which supports more color categories
        binding.webviewMuscleMap.loadUrl("file:///android_asset/muscle_map_progress.html")
    }

    private fun updateMuscleMap(muscleTrends: Map<TargetMuscle, Float?>) {
        if (muscleTrends.isEmpty()) {
            return
        }

        // Convert to JSON format expected by setMuscleProgress function
        // The function expects a map of muscle names to progress percentages
        val progressMap = JSONObject()
        muscleTrends.forEach { (muscle, progress) ->
            if (progress != null) {
                progressMap.put(muscle.name, progress.toDouble())
            } else {
                progressMap.put(muscle.name, JSONObject.NULL)
            }
        }

        // Convert JSONObject to JavaScript object literal string
        val progressMapJson = progressMap.toString()

        // Call JavaScript setMuscleProgress function (from muscle_map_progress.html)
        // This function supports more granular coloring based on progress percentage
        val jsCode = """
            (function() {
                try {
                    var progressMap = JSON.parse('$progressMapJson');
                    if (typeof setMuscleProgress === 'function') {
                        setMuscleProgress(progressMap);
                        return 'setMuscleProgress called';
                    } else if (typeof window.setMuscleProgress === 'function') {
                        window.setMuscleProgress(progressMap);
                        return 'window.setMuscleProgress called';
                    } else {
                        console.error('setMuscleProgress function not found!');
                        return 'ERROR: setMuscleProgress not found';
                    }
                } catch (e) {
                    console.error('Error calling setMuscleProgress:', e);
                    return 'ERROR: ' + e.message;
                }
            })();
        """.trimIndent()

        binding.webviewMuscleMap.evaluateJavascript(jsCode) { result ->
            // Log result for debugging if needed
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.webviewMuscleMap.destroy()
        _binding = null
    }
}
