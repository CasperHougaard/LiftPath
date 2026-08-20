package com.liftpath.fragments

import android.os.Bundle
import com.google.android.material.tabs.TabLayoutMediator
import com.liftpath.R
import com.liftpath.activities.MainActivity
import com.liftpath.adapters.ProgressPagerAdapter
import com.liftpath.databinding.FragmentProgressHubBinding
import com.liftpath.helpers.TriPathConnection
import com.liftpath.helpers.TriPathStorageHelper
import com.liftpath.helpers.WithingsStorageHelper
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View

class ProgressHubFragment : Fragment() {

    private var _binding: FragmentProgressHubBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentProgressHubBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewPager()
        setupClickListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    private fun setupViewPager() {
        val hasWithingsData = WithingsStorageHelper(requireContext()).hasData()
        // Fuel only exists when TriPath is connected and has actually handed something over.
        val hasTriPathData = TriPathConnection.isActive(requireContext()) &&
            TriPathStorageHelper(requireContext()).hasData()
        // Fragment-based adapter, not activity-based: the pages must live in this fragment's
        // child FragmentManager or they would be orphaned when the tab is hidden.
        val adapter = ProgressPagerAdapter(this, hasWithingsData, hasTriPathData)
        binding.viewPagerProgress.adapter = adapter

        // Connect TabLayout with ViewPager2. Titles come from the adapter's page list, since two
        // of the pages are conditional and positions therefore shift.
        TabLayoutMediator(binding.tabLayoutMain, binding.viewPagerProgress) { tab, position ->
            tab.text = adapter.pages.getOrNull(position)?.titleRes?.let { getString(it) } ?: ""
        }.attach()

        // A sub-tab may have been requested before this fragment existed — e.g. Today's
        // "view all scans" link, or its History tile pointing at Sessions.
        (activity as? MainActivity)?.consumePendingProgressTab()?.let { requested ->
            if (requested in 0 until adapter.itemCount) {
                binding.viewPagerProgress.setCurrentItem(requested, false)
            }
        }
    }

    /** Opens one of the analytics pages by position. Safe to call before the view exists. */
    fun showTab(index: Int) {
        val pager = _binding?.viewPagerProgress ?: return
        val count = pager.adapter?.itemCount ?: return
        if (index in 0 until count) pager.setCurrentItem(index, true)
    }

    private fun setupClickListeners() {
        // A tab has nowhere to go back to; collapse the header's back button.
        binding.cardBack.visibility = View.GONE
    }
}
