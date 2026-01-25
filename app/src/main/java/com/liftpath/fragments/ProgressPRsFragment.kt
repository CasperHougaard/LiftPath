package com.liftpath.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.liftpath.R
import com.liftpath.adapters.PRTimelineAdapter
import com.liftpath.databinding.FragmentProgressPrsBinding
import com.liftpath.helpers.JsonHelper
import com.liftpath.helpers.ProgressAnalysisHelper
import com.liftpath.helpers.ProgressAnalysisHelper.PRType
import java.text.SimpleDateFormat
import java.util.*

class ProgressPRsFragment : Fragment() {

    private var _binding: FragmentProgressPrsBinding? = null
    private val binding get() = _binding!!
    private lateinit var jsonHelper: JsonHelper
    private lateinit var adapter: PRTimelineAdapter
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
                else -> null
            }
            loadPRData()
        }
    }

    private fun setupRecyclerView() {
        adapter = PRTimelineAdapter(emptyList())
        binding.recyclerPrTimeline.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPrTimeline.adapter = adapter
    }

    private fun loadPRData() {
        val trainingData = jsonHelper.readTrainingData()
        val sessions = trainingData.trainings
        val exerciseLibrary = trainingData.exerciseLibrary

        // Get all PRs
        val allPRs = ProgressAnalysisHelper.getRecentPRs(sessions, exerciseLibrary, 365)
            .sortedByDescending { it.date }

        // Filter by type if needed
        val filteredPRs = if (currentFilter == null) {
            allPRs
        } else {
            allPRs.filter { it.prType == currentFilter }
        }

        // Update summary
        updateSummary(allPRs)

        // Update list
        if (filteredPRs.isEmpty()) {
            binding.textEmptyState.visibility = View.VISIBLE
            binding.recyclerPrTimeline.visibility = View.GONE
        } else {
            binding.textEmptyState.visibility = View.GONE
            binding.recyclerPrTimeline.visibility = View.VISIBLE
            adapter.updatePRs(filteredPRs)
        }
    }

    private fun updateSummary(allPRs: List<ProgressAnalysisHelper.PRRecord>) {
        binding.textTotalPrs.text = allPRs.size.toString()

        // This month
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

        // Week streak (consecutive weeks with PRs)
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
        
        // Start from current week
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        val currentWeekStart = calendar.time
        
        var checkWeek = currentWeekStart
        
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

            // Safety limit
            if (streak > 52) break
        }

        return streak
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
