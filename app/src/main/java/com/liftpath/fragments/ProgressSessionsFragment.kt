package com.liftpath.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.liftpath.R
import com.liftpath.activities.TrainingDetailActivity
import com.liftpath.activities.WorkoutReportActivity
import com.liftpath.adapters.SessionComparisonAdapter
import com.liftpath.databinding.FragmentProgressSessionsBinding
import com.liftpath.helpers.JsonHelper
import com.liftpath.models.TrainingSession
import java.text.SimpleDateFormat
import java.util.*

class ProgressSessionsFragment : Fragment() {

    private var _binding: FragmentProgressSessionsBinding? = null
    private val binding get() = _binding!!
    private lateinit var jsonHelper: JsonHelper
    private lateinit var adapter: SessionComparisonAdapter
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    
    private var currentTimeFilterMonths = 3

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProgressSessionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        jsonHelper = JsonHelper(requireContext())
        
        setupTimeFilterSpinner()
        setupRecyclerView()
        loadSessions()
    }

    private fun setupTimeFilterSpinner() {
        val timeFilters = arrayOf("Last month", "Last 3 months", "Last 12 months", "All time")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, timeFilters)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTimeFilter.adapter = adapter
        binding.spinnerTimeFilter.setSelection(1)

        binding.spinnerTimeFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                currentTimeFilterMonths = when (position) {
                    0 -> 1
                    1 -> 3
                    2 -> 12
                    3 -> Int.MAX_VALUE
                    else -> 3
                }
                loadSessions()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun openSessionDetail(session: TrainingSession) {
        val intent = Intent(requireContext(), TrainingDetailActivity::class.java).apply {
            putExtra(TrainingDetailActivity.EXTRA_TRAINING_SESSION, session)
        }
        startActivity(intent)
    }

    private fun openSessionReport(session: TrainingSession) {
        val intent = Intent(requireContext(), WorkoutReportActivity::class.java).apply {
            putExtra(WorkoutReportActivity.EXTRA_TRAINING_SESSION, session)
        }
        startActivity(intent)
    }

    private fun setupRecyclerView() {
        val trainingData = jsonHelper.readTrainingData()
        adapter = SessionComparisonAdapter(
            sessions = emptyList(),
            onSessionClick = { session -> openSessionDetail(session) },
            allSessions = trainingData.trainings,
            onViewReportClick = { session -> openSessionReport(session) }
        )
        binding.recyclerSessions.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSessions.adapter = adapter
    }

    private fun loadSessions() {
        val trainingData = jsonHelper.readTrainingData()
        
        // Filter by time range
        val cutoffDate = if (currentTimeFilterMonths == Int.MAX_VALUE) {
            null
        } else {
            Calendar.getInstance().apply {
                add(Calendar.MONTH, -currentTimeFilterMonths)
            }.time
        }

        val filteredSessions = trainingData.trainings
            .filter { session ->
                if (cutoffDate == null) return@filter true
                try {
                    val date = dateFormat.parse(session.date)
                    date != null && date.time >= cutoffDate.time
                } catch (e: Exception) {
                    false
                }
            }
            .sortedByDescending { it.date }

        if (filteredSessions.isEmpty()) {
            binding.textEmptyState.visibility = View.VISIBLE
            binding.recyclerSessions.visibility = View.GONE
        } else {
            binding.textEmptyState.visibility = View.GONE
            binding.recyclerSessions.visibility = View.VISIBLE
            // Update adapter with all sessions for PR calculation
            val trainingData = jsonHelper.readTrainingData()
            adapter = SessionComparisonAdapter(
                sessions = filteredSessions,
                onSessionClick = { session -> openSessionDetail(session) },
                allSessions = trainingData.trainings,
                onViewReportClick = { session -> openSessionReport(session) }
            )
            binding.recyclerSessions.adapter = adapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
