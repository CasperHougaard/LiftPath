package com.liftpath.activities

import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayoutMediator
import com.liftpath.R
import com.liftpath.adapters.ProgressPagerAdapter
import com.liftpath.databinding.ActivityProgressBinding
import com.liftpath.helpers.WithingsStorageHelper

class ProgressActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProgressBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "View Progress"

        setupBackgroundAnimation()
        setupViewPager()
        setupClickListeners()
    }

    private fun setupBackgroundAnimation() {
        val drawable = binding.imageBgAnimation.drawable
        if (drawable is Animatable) {
            drawable.start()
        }
    }

    private fun setupViewPager() {
        val hasWithingsData = WithingsStorageHelper(this).hasData()
        val adapter = ProgressPagerAdapter(this, hasWithingsData)
        binding.viewPagerProgress.adapter = adapter

        // Connect TabLayout with ViewPager2
        TabLayoutMediator(binding.tabLayoutMain, binding.viewPagerProgress) { tab, position ->
            tab.text = when (position) {
                ProgressPagerAdapter.TAB_OVERVIEW -> getString(R.string.tab_overview)
                ProgressPagerAdapter.TAB_EXERCISES -> getString(R.string.tab_exercises)
                ProgressPagerAdapter.TAB_MUSCLES -> getString(R.string.tab_muscles)
                ProgressPagerAdapter.TAB_SESSIONS -> getString(R.string.tab_sessions)
                ProgressPagerAdapter.TAB_PRS -> getString(R.string.tab_prs)
                ProgressPagerAdapter.TAB_BODY_SCAN -> getString(R.string.tab_body_scan)
                else -> ""
            }
        }.attach()

        // Default to Overview tab
        binding.viewPagerProgress.currentItem = ProgressPagerAdapter.TAB_OVERVIEW
    }

    private fun setupClickListeners() {
        binding.buttonBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.buttonSettings.setOnClickListener {
            val intent = Intent(this, ProgressionSettingsActivity::class.java)
            startActivity(intent)
        }
    }
}
