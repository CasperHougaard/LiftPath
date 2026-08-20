package com.liftpath.adapters

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.liftpath.R
import com.liftpath.fragments.*

/**
 * Pages for the Progress hub.
 *
 * Takes a Fragment rather than a FragmentActivity because Progress is now a bottom-nav tab:
 * its pages must live in the hub fragment's *child* FragmentManager, or they would be
 * orphaned when the tab is hidden and leak when it is shown again.
 *
 * Two pages are conditional — Body Scan needs Withings data, Fuel needs a connected TriPath — so
 * positions are not fixed. [pages] is the source of truth for what is where; the hub reads it for
 * tab titles rather than assuming an index.
 */
class ProgressPagerAdapter(
    fragment: Fragment,
    val hasWithingsData: Boolean = false,
    val hasTriPathData: Boolean = false
) : FragmentStateAdapter(fragment) {

    enum class Page(val titleRes: Int) {
        OVERVIEW(R.string.tab_overview),
        EXERCISES(R.string.tab_exercises),
        MUSCLES(R.string.tab_muscles),
        SESSIONS(R.string.tab_sessions),
        PRS(R.string.tab_prs),
        BODY_SCAN(R.string.tab_body_scan),
        FUEL(R.string.tab_fuel)
    }

    /** The pages actually shown, in order. Conditional ones are simply absent. */
    val pages: List<Page> = buildList {
        add(Page.OVERVIEW)
        add(Page.EXERCISES)
        add(Page.MUSCLES)
        add(Page.SESSIONS)
        add(Page.PRS)
        if (hasWithingsData) add(Page.BODY_SCAN)
        if (hasTriPathData) add(Page.FUEL)
    }

    override fun getItemCount(): Int = pages.size

    override fun createFragment(position: Int): Fragment = when (pages[position]) {
        Page.OVERVIEW -> ProgressOverviewFragment()
        Page.EXERCISES -> ProgressExercisesFragment()
        Page.MUSCLES -> ProgressMusclesFragment()
        Page.SESSIONS -> ProgressSessionsFragment()
        Page.PRS -> ProgressPRsFragment()
        Page.BODY_SCAN -> ProgressWithingsFragment()
        Page.FUEL -> ProgressFuelFragment()
    }

    /** Position of [page], or null when it is not currently shown. */
    fun positionOf(page: Page): Int? = pages.indexOf(page).takeIf { it >= 0 }

    companion object {
        const val TAB_OVERVIEW = 0
        const val TAB_EXERCISES = 1
        const val TAB_MUSCLES = 2
        const val TAB_SESSIONS = 3
        const val TAB_PRS = 4
        const val TAB_BODY_SCAN = 5
    }
}
