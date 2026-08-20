package com.liftpath.activities

import android.content.Context
import android.graphics.drawable.Animatable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.fragment.app.Fragment
import com.liftpath.R
import com.liftpath.adapters.ProgressPagerAdapter
import com.liftpath.components.RestorePromptDialogFragment
import com.liftpath.databinding.ActivityMainBinding
import com.liftpath.fragments.LibraryFragment
import com.liftpath.fragments.ProgressHubFragment
import com.liftpath.fragments.WorkoutFragment
import com.liftpath.fragments.PlanFragment
import com.liftpath.helpers.AppearanceManager
import com.liftpath.helpers.LiftPathTheme
import com.liftpath.helpers.Motion
import com.liftpath.helpers.RestoreCoordinator

/**
 * The navigation host: bottom bar + one fragment per destination.
 *
 * Keeps its class name and stays the LAUNCHER deliberately — it is also the `targetActivity`
 * of the `ViewPermissionUsageActivity` alias that Health Connect launches for
 * `VIEW_PERMISSION_USAGE`. Renaming it or pointing the alias elsewhere would break that
 * deep link, so what used to be this activity's content moved into [WorkoutFragment] instead.
 *
 * Tabs are swapped with plain add/show/hide transactions rather than a navigation library.
 * Two reasons: no new dependency for four destinations, and show/hide keeps each tab's view
 * state alive — which matters most for Progress, whose six chart pages are expensive to
 * rebuild and jarring to see reset.
 *
 * Everything below a tab (edit screens, pickers, the active workout) remains an Activity.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /**
     * The resolved palette this window was inflated with — i.e. whichever of the light/dark
     * palette choices was active at the time, given the mode.
     *
     * LiftPathApplication themes activities as they are *created*, so this instance would
     * come back from Settings still wearing the old palette after a palette OR mode change.
     * Comparing on resume and recreating is the cheapest correct fix, and it takes the child
     * fragments with it.
     */
    private var inflatedTheme: LiftPathTheme? = null

    /**
     * A Progress sub-tab requested before the fragment exists (e.g. tapping the momentum card
     * on Workout while Progress has never been opened). Consumed by [ProgressHubFragment] once
     * its view is ready.
     */
    private var pendingProgressTab: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate(). The system-drawn splash it hands back is
        // neutral (see Theme.Fitness) since it was drawn before this process existed; the
        // real palette reveal happens in the exit-animation listener below, once
        // AppearanceManager is readable and the overlay from onActivityPreCreated is
        // already applied to this activity's theme.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        inflatedTheme = AppearanceManager.resolvedTheme(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        splashScreen.setOnExitAnimationListener(::animateSplashExit)
        applyWindowInsets()
        startAmbientBackground()
        setupBottomNav()

        // Restore the last tab, but only on a cold start. On a configuration change the
        // FragmentManager has already restored its fragments, and re-selecting would stack
        // a second copy on top.
        if (savedInstanceState == null) {
            selectTab(restoreSelectedTabId())
            RestoreCoordinator.consumePendingRestoreBundle()?.let {
                RestorePromptDialogFragment.newInstance(it).show(supportFragmentManager, TAG_RESTORE_PROMPT)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (inflatedTheme != null && inflatedTheme != AppearanceManager.resolvedTheme(this)) {
            recreate()
        }
    }

    /**
     * Insets are handled once, here, so no fragment has to think about them:
     * the top inset pads the fragment container, the bottom inset pads the nav bar. The
     * ambient background is left un-inset on purpose so it bleeds under both system bars.
     */
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.navHostContainer.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            binding.bottomNav.updatePadding(bottom = bars.bottom)
            windowInsets
        }
    }

    private fun startAmbientBackground() {
        (binding.imageBgAnimation.drawable as? Animatable)?.start()
    }

    /**
     * The system splash icon is neutral and fixed (drawn before this process existed — see
     * Theme.Fitness). This is the moment that actually shows the chosen palette: the neutral
     * icon scales/fades out while the same mark, now tinted with the resolved theme's
     * ?attr/lpAccent, springs in at the same spot and holds briefly before fading away to
     * reveal the already-themed home screen underneath.
     */
    private fun animateSplashExit(provider: SplashScreenViewProvider) {
        val logo = binding.imageSplashLogo
        logo.scaleX = 0.7f
        logo.scaleY = 0.7f

        provider.iconView.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(Motion.ENTRANCE_FADE_MS)
            .start()

        logo.animate()
            .alpha(1f)
            .setDuration(Motion.ENTRANCE_FADE_MS)
            .start()

        SpringAnimation(logo, DynamicAnimation.SCALE_X, 1f).apply {
            spring.stiffness = SpringForce.STIFFNESS_LOW
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
        }.start()
        SpringAnimation(logo, DynamicAnimation.SCALE_Y, 1f).apply {
            spring.stiffness = SpringForce.STIFFNESS_LOW
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            addEndListener { _, _, _, _ ->
                logo.animate()
                    .alpha(0f)
                    .setStartDelay(180L)
                    .setDuration(260L)
                    .withEndAction { provider.remove() }
                    .start()
            }
        }.start()
    }

    private fun setupBottomNav() {
        // The listener is the ONLY place that swaps fragments. Programmatic navigation goes
        // through [selectTab], which drives the bar and lets the bar drive the swap —
        // assigning selectedItemId from inside this callback would re-enter it and recurse
        // until the stack overflows.
        binding.bottomNav.setOnItemSelectedListener { item ->
            showTabFragment(item.itemId)
            persistSelectedTabId(item.itemId)
            true
        }
        // Re-tapping a tab should not rebuild it; show/hide already makes this a no-op.
        binding.bottomNav.setOnItemReselectedListener { }
    }

    /**
     * Programmatic navigation. Setting `selectedItemId` fires the listener above, which does
     * the actual fragment work — except when the bar is already on [itemId] (true for Workout
     * on a cold start, since it is the first menu item), where no event is emitted and the
     * fragment has to be added directly.
     */
    private fun selectTab(itemId: Int) {
        if (binding.bottomNav.selectedItemId == itemId) {
            showTabFragment(itemId)
            persistSelectedTabId(itemId)
        } else {
            binding.bottomNav.selectedItemId = itemId
        }
    }

    /** Shows the fragment for [itemId], creating it on first visit. */
    private fun showTabFragment(itemId: Int) {
        val tag = tagFor(itemId) ?: return
        val fm = supportFragmentManager
        val tx = fm.beginTransaction()

        TAB_TAGS.forEach { existingTag ->
            fm.findFragmentByTag(existingTag)?.let { if (it.tag != tag) tx.hide(it) }
        }

        val existing = fm.findFragmentByTag(tag)
        if (existing == null) {
            tx.add(R.id.nav_host_container, createFragment(itemId), tag)
        } else {
            tx.show(existing)
        }
        tx.commit()
    }

    private fun createFragment(itemId: Int): Fragment = when (itemId) {
        R.id.nav_plan -> PlanFragment()
        R.id.nav_progress -> ProgressHubFragment()
        R.id.nav_library -> LibraryFragment()
        else -> WorkoutFragment()
    }

    private fun tagFor(itemId: Int): String? = when (itemId) {
        R.id.nav_workout -> TAG_WORKOUT
        R.id.nav_plan -> TAG_PLAN
        R.id.nav_progress -> TAG_PROGRESS
        R.id.nav_library -> TAG_LIBRARY
        else -> null
    }

    // ---------------------------------------------------------------- public navigation

    /**
     * Switches to Progress and opens [subTab].
     *
     * Used by the Workout tab's momentum card, which links to the trend charts that moved into
     * Progress > Overview. Progress > Sessions is also how HistoryActivity could be deleted:
     * that tab is a superset of the old screen.
     */
    fun openProgress(subTab: Int = ProgressPagerAdapter.TAB_OVERVIEW) {
        pendingProgressTab = subTab
        selectTab(R.id.nav_progress)
        // Already created? Tell it directly; otherwise it will consume the pending value.
        (supportFragmentManager.findFragmentByTag(TAG_PROGRESS) as? ProgressHubFragment)
            ?.showTab(subTab)
    }

    /** One-shot read of a sub-tab requested before [ProgressHubFragment] existed. */
    fun consumePendingProgressTab(): Int? = pendingProgressTab.also { pendingProgressTab = null }

    /** Switches to the Plan tab (workout plans and rotations). */
    fun openPlan() = selectTab(R.id.nav_plan)

    // ---------------------------------------------------------------- tab persistence

    private fun restoreSelectedTabId(): Int {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getInt(KEY_SELECTED_TAB, 0)
        // Resource ids are not stable across builds, so a stored id is only trusted when it
        // still matches a real destination.
        return if (tagFor(stored) != null) stored else R.id.nav_workout
    }

    private fun persistSelectedTabId(itemId: Int) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SELECTED_TAB, itemId)
            .apply()
    }

    private companion object {
        /** Shared with WorkoutFragment, and already covered by BackupManager.BACKED_UP_PREFS. */
        const val PREFS_NAME = "main_activity_prefs"
        const val KEY_SELECTED_TAB = "selected_tab"

        const val TAG_WORKOUT = "tab_workout"
        const val TAG_PLAN = "tab_plan"
        const val TAG_PROGRESS = "tab_progress"
        const val TAG_LIBRARY = "tab_library"

        val TAB_TAGS = listOf(TAG_WORKOUT, TAG_PLAN, TAG_PROGRESS, TAG_LIBRARY)

        const val TAG_RESTORE_PROMPT = "restore_prompt"
    }
}
