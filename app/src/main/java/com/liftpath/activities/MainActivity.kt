package com.liftpath.activities

import android.content.Context
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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

    /**
     * Cold-start reveal gate. False only between [onCreate] and the moment
     * [animateSplashExit] hands over, so a screen with an entrance animation can wait for
     * the reveal instead of playing behind it. See [onEntranceReady].
     */
    /**
     * Opens ~60% of the way through the traced stroke. The entrance cascade overlaps the
     * reveal deliberately — waiting for a clear screen would put a beat of empty canvas
     * between the mark landing and the first card arriving, which reads as two animations
     * instead of one.
     */
    private val cascadeGate = Gate()

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
        setupBottomNav()

        // Restore the last tab, but only on a cold start. On a configuration change the
        // FragmentManager has already restored its fragments, and re-selecting would stack
        // a second copy on top.
        if (savedInstanceState == null) {
            // A cold start is the only case with a reveal to wait for, and the gates have to
            // close before the first fragment is added — the fragment asks on the way up.
            cascadeGate.close()
            // Safety net. A gated caller drops its views to alpha = 0 immediately, so a reveal
            // that never hands over (an exit listener that does not fire, a window that never
            // draws) would leave them permanently invisible. Late beats never.
            binding.root.postDelayed(cascadeGate::release, ENTRANCE_TIMEOUT_MS)

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

    /**
     * The cold-start reveal: one continuous move from the OS splash badge to a settled home
     * screen.
     *
     *   0ms    the whole splash view cross-dissolves out (background and icon together)
     *   90ms   the mark begins tracing itself in the resolved palette's accent
     *   330ms  the cards start rising, while the stroke turns its corner
     *   490ms  the stroke lands, a bloom pushes out from it and the mark dissolves
     *   ~950ms settled
     *
     * Two things about this are load-bearing rather than decorative.
     *
     * **The splash view goes early.** `windowSplashScreenBackground` is opaque and the splash
     * sits above this activity's content, so anything animated underneath it cannot be seen.
     * The previous version of this method faded a tinted mark in beneath the splash and only
     * called `provider.remove()` at the *end* — so the palette reveal it was written to
     * perform was invisible on every launch. Fading `provider.view` rather than
     * `provider.iconView` is what turns the hand-off into something the user actually sees.
     *
     * **The cards are released before the stroke finishes.** Waiting for the bloom leaves a
     * beat of empty canvas between the mark landing and the first card arriving, which reads
     * as three separate animations. Overlapping them reads as one.
     */
    private fun animateSplashExit(provider: SplashScreenViewProvider) {
        val logo = binding.imageSplashLogo
        val bloom = binding.viewRevealBloom

        provider.view.animate()
            .alpha(0f)
            .setDuration(SPLASH_FADE_MS)
            .withEndAction { provider.remove() }
            .start()

        // Visible from the outset, but lp_logo_trace draws nothing until it is started, so
        // there is no mark on screen until the stroke begins under the tail of that fade.
        logo.alpha = 1f
        logo.postDelayed({ (logo.drawable as? Animatable)?.start() }, TRACE_START_DELAY_MS)

        logo.postDelayed({
            bloom.scaleX = BLOOM_SCALE_FROM
            bloom.scaleY = BLOOM_SCALE_FROM
            bloom.alpha = BLOOM_ALPHA_PEAK
            bloom.animate()
                .scaleX(BLOOM_SCALE_TO)
                .scaleY(BLOOM_SCALE_TO)
                .alpha(0f)
                .setDuration(BLOOM_MS)
                // Light spreads fast and thins out; accelerating into it would read as a
                // shockwave, which is a different and much cheaper-looking idea.
                .setInterpolator(DecelerateInterpolator(1.6f))
                .start()

            logo.animate()
                .alpha(0f)
                .setDuration(LOGO_FADE_MS)
                .start()
        }, TRACE_START_DELAY_MS + TRACE_MS)

        binding.root.postDelayed(cascadeGate::release, TRACE_START_DELAY_MS + CASCADE_HANDOVER_MS)
    }

    /**
     * Runs [action] when the reveal hands over to the entrance cascade — or immediately, if
     * there is no reveal in flight (a returning activity, a configuration change, a tab opened
     * later).
     *
     * A gated screen is responsible for its *whole* entrance, including anything that resolves
     * asynchronously. Do not hand a late-arriving view its own separate timing; hold the
     * cascade for it, as `WorkoutFragment` does for the body-status card. A card animating on
     * its own schedule next to a fading mark reads as a straggler.
     */
    fun onEntranceReady(action: () -> Unit) = cascadeGate.await(action)

    /**
     * Holds actions until released, then runs them and stays open.
     *
     * Releasing is idempotent, since both the reveal and its backstop timeout call it and
     * whichever lands first wins.
     */
    private class Gate {
        private val pending = mutableListOf<() -> Unit>()
        private var released = true

        fun close() {
            released = false
        }

        fun await(action: () -> Unit) {
            if (released) action() else pending += action
        }

        fun release() {
            if (released) return
            released = true
            // Copied before running: an action is free to register another.
            val actions = pending.toList()
            pending.clear()
            actions.forEach { it() }
        }
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

        // ------------------------------------------------- cold-start reveal timings

        /** Cross-dissolve from the OS badge to the themed canvas. */
        const val SPLASH_FADE_MS = 180L

        /** The stroke starts under the tail of that fade rather than after it. */
        const val TRACE_START_DELAY_MS = 90L

        /**
         * MUST match the `objectAnimator` duration in `lp_logo_trace.xml`. The framework gives
         * no completion callback for an `AnimatedVectorDrawable` loaded through `srcCompat`,
         * so the bloom is timed rather than chained — and if these two drift, the bloom fires
         * either over an unfinished stroke or after a visible pause.
         */
        const val TRACE_MS = 400L

        /** ~60% into the stroke: the cards rise as it turns the corner. */
        const val CASCADE_HANDOVER_MS = 240L

        const val BLOOM_MS = 420L
        const val BLOOM_SCALE_FROM = 0.35f
        const val BLOOM_SCALE_TO = 2.4f

        /**
         * lp_hero_glow already peaks at 40% in its centre, so this is a second multiplier on
         * top of that — past ~0.4 the bloom stops reading as light and starts reading as a
         * coloured flash.
         */
        const val BLOOM_ALPHA_PEAK = 0.34f

        const val LOGO_FADE_MS = 260L

        /**
         * Backstop for [releaseEntrance]. Comfortably longer than the reveal, short enough
         * that a launch which somehow skips the exit animation still shows its cards quickly.
         */
        const val ENTRANCE_TIMEOUT_MS = 1_500L
    }
}
