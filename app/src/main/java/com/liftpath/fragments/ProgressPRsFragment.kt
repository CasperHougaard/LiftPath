package com.liftpath.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.liftpath.R
import com.liftpath.adapters.ExercisePRStatsAdapter
import com.liftpath.databinding.FragmentProgressPrsBinding
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.ProgressAnalysisHelper
import com.liftpath.helpers.ProgressAnalysisHelper.PRType
import java.text.SimpleDateFormat
import java.util.*

/**
 * PR page: "Player Stats Card" list of exercises with best 1RM, weight, volume, reps.
 * Sorted by lastPrDate DESC (exercise with most recent PR at top).
 */
class ProgressPRsFragment : Fragment() {

    private var _binding: FragmentProgressPrsBinding? = null
    private val binding get() = _binding!!
    private lateinit var jsonHelper: JsonHelper
    private lateinit var adapter: ExercisePRStatsAdapter
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

    private var currentFilter: PRType? = null // null = All

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProgressPrsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        jsonHelper = JsonHelper(requireContext())

        setupFilterChips()
        setupRecyclerView()
        loadPRData()
    }

    private fun setupFilterChips() {
        binding.chipGroupPrFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when (checkedIds.firstOrNull()) {
                R.id.chip_all -> null
                R.id.chip_strength_pr -> PRType.WEIGHT
                R.id.chip_volume_pr -> PRType.VOLUME
                R.id.chip_1rm_pr -> PRType.ONE_RM
                R.id.chip_reps_pr -> PRType.REPS
                else -> null
            }
            loadPRData()
        }
    }

    private fun setupRecyclerView() {
        adapter = ExercisePRStatsAdapter(emptyList())
        binding.recyclerPrTimeline.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPrTimeline.adapter = adapter
    }

    private fun loadPRData() {
        val trainingData = jsonHelper.readTrainingData()
        val sessions = trainingData.trainings
        val exerciseLibrary = trainingData.exerciseLibrary

        // Player Stats Card list: one summary per exercise, sorted by lastPrDate DESC
        var summaries = ProgressAnalysisHelper.getExerciseStatsSummaries(sessions, exerciseLibrary)
            .sortedByDescending { it.lastPrDate }

        // Filter by type if needed (has this type of PR)
        summaries = if (currentFilter == null) {
            summaries
        } else {
            summaries.filter { summary ->
                when (currentFilter) {
                    PRType.WEIGHT -> summary.bestWeight != null
                    PRType.VOLUME -> summary.bestVolume != null
                    PRType.ONE_RM -> summary.best1RM != null
                    PRType.REPS -> summary.bestRepsRecord != null
                    else -> true
                }
            }
        }

        // Summary card: total PR count, this month, week streak (from PR events)
        val allPRs = ProgressAnalysisHelper.getRecentPRs(sessions, exerciseLibrary, 365 * 10)
        updateSummary(allPRs)

        if (summaries.isEmpty()) {
            binding.textEmptyState.visibility = View.VISIBLE
            binding.recyclerPrTimeline.visibility = View.GONE
        } else {
            binding.textEmptyState.visibility = View.GONE
            binding.recyclerPrTimeline.visibility = View.VISIBLE
            adapter.updateSummaries(summaries)
        }
    }

    private fun updateSummary(allPRs: List<ProgressAnalysisHelper.PRRecord>) {
        binding.textTotalPrs.text = allPRs.size.toString()

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val startOfMonth = calendar.time

        val thisMonthPRs = allPRs.count { pr ->
            try {
                val date = dateFormat.parse(pr.date)
                date != null && date.time >= startOfMonth.time
            } catch (e: Exception) {
                false
            }
        }
        binding.textThisMonth.text = thisMonthPRs.toString()

        val weekStreak = calculateWeekStreak(allPRs)
        binding.textStreak.text = weekStreak.toString()
    }

    private fun calculateWeekStreak(prs: List<ProgressAnalysisHelper.PRRecord>): Int {
        if (prs.isEmpty()) return 0

        val prDates = prs.mapNotNull { pr ->
            try {
                dateFormat.parse(pr.date)
            } catch (e: Exception) {
                null
            }
        }.distinct().sortedDescending()

        if (prDates.isEmpty()) return 0

        var streak = 0
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        var checkWeek = calendar.time

        while (true) {
            val weekEnd = Calendar.getInstance().apply {
                time = checkWeek
                add(Calendar.DAY_OF_YEAR, 7)
            }.time

            val hasPRInWeek = prDates.any { date ->
                date.time >= checkWeek.time && date.time < weekEnd.time
            }

            if (hasPRInWeek) {
                streak++
                calendar.time = checkWeek
                calendar.add(Calendar.DAY_OF_YEAR, -7)
                checkWeek = calendar.time
            } else {
                break
            }
            if (streak > 52) break
        }

        return streak
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
