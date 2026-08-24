package com.liftpath

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.liftpath.garmin.WatchTransport
import com.liftpath.helpers.AppearanceManager
import com.liftpath.helpers.CloudSnapshotStore
import com.liftpath.helpers.RestoreCoordinator
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Applies the user's selected palette to every activity from one place.
 *
 * `onActivityPreCreated` runs before `Activity.onCreate`, which is still early enough for
 * `setTheme` to affect inflation. The alternative — a `BaseActivity` calling `setTheme`
 * before `super.onCreate` — would mean changing the superclass of all 26 activities; this
 * achieves the same result in one file. If the timing ever proves too late (symptom:
 * layouts inflate with the manifest's default palette instead of the chosen one), that
 * `BaseActivity` is the fallback.
 *
 * This fires for activities created *after* a theme change too, so the only screen needing
 * an explicit refresh is one already on-screen when the change happens — see the
 * theme-token check in `MainActivity.onResume`.
 */
class LiftPathApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Must happen before any activity is created: it decides whether values-night/
        // resources (including every palette's dark colours) resolve for this process.
        AppCompatDelegate.setDefaultNightMode(AppearanceManager.mode(this).toNightMode())
        registerActivityLifecycleCallbacks(ThemeApplyingCallbacks())

        // Must run before anything below can refresh the cloud snapshot — refreshing before
        // this check resolves would overwrite a just-restored snapshot with a fresh, empty
        // one before the user ever sees the restore prompt (MainActivity reads the result).
        RestoreCoordinator.checkOnAppStart(this)

        // One process-wide Connect IQ bridge. It must be owned here rather than by a screen:
        // when the diagnostics activity owned the only bridge, the watch could talk to Garmin
        // Connect successfully while nothing on the phone was listening, which reads on the
        // wrist as a successful sync into a session that never answers.
        WatchTransport.start(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // Backgrounding is when the OS is likeliest to reclaim the process, so this
                // blocks briefly (bounded) rather than firing-and-forgetting a coroutine that
                // could get orphaned mid-write. The snapshot write is temp-file-then-rename,
                // so a lost race can only ever leave the previous good snapshot in place —
                // never a corrupt one.
                runBlocking {
                    withTimeoutOrNull(2_000) {
                        CloudSnapshotStore.refreshNow(applicationContext)
                    }
                }
            }
        })
    }

    private class ThemeApplyingCallbacks : ActivityLifecycleCallbacks {

        override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
            // An overlay carries only colour, so this is safe on every activity — including
            // RestTimerDialogActivity, which declares Theme.AppCompat.Dialog to float over
            // the lock screen and would break under a full app theme. No exemptions needed.
            activity.setTheme(AppearanceManager.overlayRes(activity))
        }

        // The design-system work happens entirely in onActivityPreCreated; the rest of the
        // interface is required but unused.
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
