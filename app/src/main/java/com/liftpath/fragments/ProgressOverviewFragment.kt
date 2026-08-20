package com.liftpath.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.android.material.tabs.TabLayoutMediator
import com.liftpath.R
import com.liftpath.adapters.ChartCarouselAdapter
import com.liftpath.adapters.ChartData
import com.liftpath.adapters.ChartType
import com.liftpath.databinding.FragmentProgressOverviewBinding
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.MuscleMapColorResolver
import com.liftpath.helpers.MuscleMapRenderer
import com.liftpath.helpers.OneRMEstimationHelper
import com.liftpath.helpers.ProgressAnalysisHelper
import com.liftpath.helpers.ReadinessConfig
import com.liftpath.helpers.ReadinessHelper
import com.liftpath.helpers.RestTimerHelper
import com.liftpath.helpers.SetMetrics
import com.liftpath.models.SetIntent
import com.liftpath.models.TargetMuscle
import com.liftpath.models.TrainingData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.liftpath.helpers.lpColor

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

        // Build & Strength RPE + the trend carousel. Both moved here from the home screen when
        // that became the Workout tab; this is the only place in the app that plots a date axis.
        updateBuildStrengthRpeCard(trainingData)
        setupChartsCarousel(trainingData)

        // Muscle Map
        setupMuscleMap(sessions, exerciseLibrary)
    }

    private fun updateBuildStrengthRpeCard(trainingData: TrainingData) {
        val (buildAvg, strengthAvg) = ProgressAnalysisHelper.getBuildStrengthRpeAverages(
            trainingData.trainings,
            dayCount = 21
        )
        val sub = getString(R.string.home_rpe_sub)
        when {
            buildAvg != null && strengthAvg != null -> {
                binding.textOverviewRpeHeadline.text =
                    String.format(Locale.US, "%.1f · %.1f", buildAvg, strengthAvg)
                binding.textOverviewRpeSub.text = sub
            }
            buildAvg != null -> {
                binding.textOverviewRpeHeadline.text = String.format(Locale.US, "%.1f", buildAvg)
                binding.textOverviewRpeSub.text = sub
            }
            strengthAvg != null -> {
                binding.textOverviewRpeHeadline.text = String.format(Locale.US, "%.1f", strengthAvg)
                binding.textOverviewRpeSub.text = sub
            }
            else -> {
                binding.textOverviewRpeHeadline.text = getString(R.string.home_rpe_none_headline)
                binding.textOverviewRpeSub.text = getString(R.string.home_rpe_none_sub)
            }
        }
    }

    /**
     * Volume / average RPE / time-in-gym / fatigue against a date axis — four pages of one
     * carousel, moved verbatim from the old home screen. The fatigue series is padded out to a
     * full 28 days so a break in training plots as zeroes rather than vanishing.
     */
    private fun setupChartsCarousel(trainingData: TrainingData) {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US)

        val volumeEntries = trainingData.trainings
            .mapNotNull { session ->
                val totalVolume = SetMetrics.totalVolumeKg(session.exercises)
                try {
                    val date = dateFormat.parse(session.date) ?: return@mapNotNull null
                    Entry(date.time.toFloat(), totalVolume)
                } catch (e: Exception) {
                    null
                }
            }
            .sortedBy { it.x }

        val rpeEntries = trainingData.trainings
            .mapNotNull { session ->
                val rpeValues = session.exercises.mapNotNull { it.rpe }
                if (rpeValues.isEmpty()) return@mapNotNull null
                val avgRpe = rpeValues.average().toFloat()
                try {
                    val date = dateFormat.parse(session.date) ?: return@mapNotNull null
                    Entry(date.time.toFloat(), avgRpe)
                } catch (e: Exception) {
                    null
                }
            }
            .sortedBy { it.x }

        val timeEntries = trainingData.trainings
            .mapNotNull { session ->
                val durationSeconds = session.durationSeconds
                if (durationSeconds == null || durationSeconds <= 0) return@mapNotNull null
                val durationMinutes = (durationSeconds / 60f)
                try {
                    val date = dateFormat.parse(session.date) ?: return@mapNotNull null
                    Entry(date.time.toFloat(), durationMinutes)
                } catch (e: Exception) {
                    null
                }
            }
            .sortedBy { it.x }

        // Raw fatigue per session, keyed by date so the 28-day sweep below can look it up.
        val config = ReadinessConfig()
        val fatigueByDate = mutableMapOf<String, Pair<Float, SetIntent>>()

        trainingData.trainings.forEach { session ->
            try {
                dateFormat.parse(session.date) ?: return@forEach
                val fatigueScores = ReadinessHelper.calculateFatigueScores(session, trainingData, config)
                val rawFatigue = fatigueScores.systemicFatigue
                if (rawFatigue > 0) {
                    fatigueByDate[session.date] = Pair(rawFatigue, session.getDominantIntent())
                }
            } catch (e: Exception) {
                // Skip invalid dates
            }
        }

        val calendar = Calendar.getInstance()
        val today = calendar.time
        val allDates = mutableListOf<Pair<Long, Pair<Float, SetIntent>>>()

        for (i in 0 until 28) {
            calendar.time = today
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            val date = calendar.time
            val dateStr = dateFormat.format(date)

            val (fatigue, intent) = fatigueByDate[dateStr] ?: Pair(0f, SetIntent.BUILD)
            allDates.add(Pair(date.time, Pair(fatigue, intent)))
        }

        allDates.sortBy { it.first }

        val fatigueEntries = allDates.map { (dateMillis, fatigueData) ->
            Entry(dateMillis.toFloat(), fatigueData.first)
        }
        val dominantIntents = allDates.map { it.second.second }

        val charts = listOf(
            ChartData(
                type = ChartType.VOLUME,
                entries = volumeEntries,
                title = getString(R.string.home_chart_volume_trends),
                color = requireContext().lpColor(R.attr.lpChartVolume),
                yAxisLabel = getString(R.string.home_chart_axis_volume)
            ),
            ChartData(
                type = ChartType.AVG_RPE,
                entries = rpeEntries,
                title = getString(R.string.home_chart_avg_rpe),
                color = requireContext().lpColor(R.attr.lpChartRpe),
                yAxisLabel = getString(R.string.home_chart_axis_rpe)
            ),
            ChartData(
                type = ChartType.TIME_CONSUMPTION,
                entries = timeEntries,
                title = getString(R.string.home_chart_time_consumption),
                color = requireContext().lpColor(R.attr.lpChartTime),
                yAxisLabel = getString(R.string.home_chart_axis_time)
            ),
            ChartData(
                type = ChartType.FATIGUE,
                entries = fatigueEntries,
                title = getString(R.string.home_chart_fatigue),
                // Overridden per-point by intent colour coding in the adapter.
                color = requireContext().lpColor(R.attr.lpChartFatigue),
                yAxisLabel = getString(R.string.home_chart_axis_fatigue),
                dominantIntents = dominantIntents
            )
        )

        binding.viewpagerCharts.adapter = ChartCarouselAdapter(charts)

        // loadOverviewData runs again on every onResume, and a second mediator would attach a
        // second set of listeners to the same pager. The tabs are static, so wire them once.
        if (binding.tabLayoutCharts.tabCount == 0) {
            TabLayoutMediator(binding.tabLayoutCharts, binding.viewpagerCharts) { tab, position ->
                tab.text = when (position) {
                    0 -> getString(R.string.home_chart_tab_volume)
                    1 -> getString(R.string.home_chart_tab_rpe)
                    2 -> getString(R.string.home_chart_tab_time)
                    3 -> getString(R.string.home_chart_tab_fatigue)
                    else -> ""
                }
            }.attach()
        }
    }

    private fun populatePRCards(prs: List<ProgressAnalysisHelper.PRRecord>) {
        binding.layoutPrCards.removeAllViews()
        val ctx = requireContext()
        val cardGapPx = resources.getDimensionPixelSize(R.dimen.lp_card_gap)
        val cardPaddingPx = resources.getDimensionPixelSize(R.dimen.lp_card_padding)
        val hairlinePx = resources.getDimensionPixelSize(R.dimen.lp_hairline_width)

        prs.take(5).forEach { pr ->
            // Hairline card, matching Widget.LP.Card — built in code because these cards are
            // a horizontally-scrolling row generated from data, not a fixed XML list.
            val cardView = MaterialCardView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.pr_card_width),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = cardGapPx }
                radius = resources.getDimension(R.dimen.lp_radius_md)
                cardElevation = 0f
                strokeWidth = hairlinePx
                strokeColor = ctx.lpColor(R.attr.lpHairline)
                setCardBackgroundColor(ctx.lpColor(R.attr.lpSurface))
            }

            val contentLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(cardPaddingPx, cardPaddingPx, cardPaddingPx, cardPaddingPx)
            }

            val exerciseName = TextView(ctx).apply {
                text = pr.exerciseName
                setTextAppearance(R.style.TextAppearance_LP_Label)
                maxLines = 1
            }

            // PR value — format depends on type. Metric.M (mono): this is a number, not prose.
            val prValue = TextView(ctx).apply {
                text = when (pr.prType) {
                    ProgressAnalysisHelper.PRType.VOLUME ->
                        String.format(Locale.US, "%,d kg", pr.value.toInt())
                    // A hold PR's value is seconds, not kilograms.
                    ProgressAnalysisHelper.PRType.TIME_HOLD ->
                        RestTimerHelper.formatDuration(pr.value.toInt())
                    else ->
                        String.format(Locale.US, "%.1f kg", pr.value)
                }
                setTextAppearance(R.style.TextAppearance_LP_Metric_M)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.lp_space_1) }
            }

            // PR type badge — shows what kind of record it is, not the training intent. A
            // solid semantic fill (not a wash) is deliberate here: this badge IS the finding,
            // not a secondary hint, so it earns full-strength colour.
            val intentBadge = TextView(ctx).apply {
                text = when (pr.prType) {
                    ProgressAnalysisHelper.PRType.WEIGHT  -> getString(R.string.weight_pr_label).uppercase()
                    ProgressAnalysisHelper.PRType.ONE_RM  -> getString(R.string.one_rm_pr_label).uppercase()
                    ProgressAnalysisHelper.PRType.VOLUME  -> getString(R.string.volume_pr_label).uppercase()
                    ProgressAnalysisHelper.PRType.TIME_HOLD -> getString(R.string.hold_pr_label).uppercase()
                    else -> pr.prType.name
                }
                setTextAppearance(R.style.TextAppearance_LP_Caption)
                setTextColor(ctx.lpColor(R.attr.lpInkInverse))
                val topMarginPx = resources.getDimensionPixelSize(R.dimen.lp_space_2)
                val hPad = resources.getDimensionPixelSize(R.dimen.lp_space_2)
                val vPad = resources.getDimensionPixelSize(R.dimen.lp_space_1)
                setPadding(hPad, vPad, hPad, vPad)
                setBackgroundResource(R.drawable.badge_rounded_background)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    when (pr.prType) {
                        ProgressAnalysisHelper.PRType.WEIGHT -> ctx.lpColor(R.attr.lpIntentStrength)
                        ProgressAnalysisHelper.PRType.ONE_RM -> ctx.lpColor(R.attr.lpAccent)
                        ProgressAnalysisHelper.PRType.VOLUME -> ctx.lpColor(R.attr.lpIntentBuild)
                        else -> ctx.lpColor(R.attr.lpAccent)
                    }
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = topMarginPx }
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

        val ctx = requireContext()
        distribution.forEach { (intent, percentage) ->
            if (percentage > 0) {
                entries.add(PieEntry(percentage, intent.displayName))
                colors.add(when (intent) {
                    SetIntent.STRENGTH -> ctx.lpColor(R.attr.lpIntentStrength)
                    SetIntent.BUILD -> ctx.lpColor(R.attr.lpIntentBuild)
                    SetIntent.FLUSH -> ctx.lpColor(R.attr.lpIntentFlush)
                    else -> ctx.lpColor(R.attr.lpNeutral)
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
            return getString(R.string.progress_no_recent_training)
        }

        val sessionCount = recentSessions.size
        val avgSessionsPerWeek = sessionCount / 4.0

        // Calculate total volume trend
        val sortedSessions = recentSessions.sortedBy { it.date }
        val halfwayPoint = sortedSessions.size / 2
        val firstHalf = sortedSessions.take(halfwayPoint)
        val secondHalf = sortedSessions.drop(halfwayPoint)

        val firstHalfVolume = firstHalf.sumOf { session ->
            SetMetrics.totalVolumeKg(session.exercises.filterNot { it.isWarmup }).toDouble()
        }
        val secondHalfVolume = secondHalf.sumOf { session ->
            SetMetrics.totalVolumeKg(session.exercises.filterNot { it.isWarmup }).toDouble()
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
        updateMuscleMap(muscleTrends)
    }

    private fun updateMuscleMap(muscleTrends: Map<TargetMuscle, Float?>) {
        if (muscleTrends.isEmpty()) {
            return
        }
        val context = context ?: return

        lifecycleScope.launch {
            val muscleCategories = MuscleMapColorResolver.resolveProgressColors(muscleTrends)
            val maskCategories = MuscleMapColorResolver.flattenToMaskCategories(
                muscleCategories, rank = MuscleMapColorResolver::progressColorRank
            )
            val maskColors = maskCategories.map { (maskResId, category) ->
                maskResId to MuscleMapColorResolver.colorFor(context, category)
            }
            val bitmap = withContext(Dispatchers.Default) {
                MuscleMapRenderer.render(context, maskColors)
            }
            if (_binding == null) return@launch
            binding.imageMuscleMap.setImageBitmap(bitmap)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
