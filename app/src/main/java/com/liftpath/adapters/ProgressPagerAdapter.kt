package com.liftpath.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.liftpath.fragments.*

class ProgressPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ProgressOverviewFragment()
            1 -> ProgressExercisesFragment()
            2 -> ProgressMusclesFragment()
            3 -> ProgressSessionsFragment()
            4 -> ProgressPRsFragment()
            else -> ProgressOverviewFragment()
        }
    }

    companion object {
        const val TAB_OVERVIEW = 0
        const val TAB_EXERCISES = 1
        const val TAB_MUSCLES = 2
        const val TAB_SESSIONS = 3
        const val TAB_PRS = 4
    }
}
