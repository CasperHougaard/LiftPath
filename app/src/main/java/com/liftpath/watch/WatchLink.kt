package com.liftpath.watch

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Mediator between the live workout screen and whatever is talking to the watch.
 *
 * Exists so that neither side has to know the other: `ActiveTrainingActivity` implements
 * [Host] and never mentions Garmin, and `GarminBridge` implements [Transport] and never
 * mentions the activity. That is what keeps the Connect IQ `.aar` — which is not in the build
 * until you drop it in `app/libs/` — out of the main compile path.
 *
 * ### Why a mirror rather than an owner
 *
 * The obvious design is to lift live-session state out of the activity entirely so a service
 * owns it. That is the right end state (see `garmin/README.md`) but it is a large, risky
 * change to a 2330-line activity. This is the cheap version: the activity stays the owner of
 * truth and publishes a projection on every mutation; commands are routed back to it.
 *
 * The cost of that shortcut is precise, and worth knowing before relying on it: **the watch
 * only works while `ActiveTrainingActivity` is alive.** A paused activity is fine — phone in
 * a pocket with the screen off is the normal case and it keeps working — but if Android
 * reclaims the activity under memory pressure, the watch goes idle until you reopen the
 * screen. Fixing that means the foreground service, which is the next step, not this one.
 */
object WatchLink {

    private const val TAG = "WatchLink"

    /** The live workout screen. Implemented by `ActiveTrainingActivity`. */
    interface Host {
        /** Called on the main thread. Apply the command to the session. */
        fun onWatchCommand(command: WatchCommand)

        /** Called on the main thread. Project current session state for the watch. */
        fun buildWatchState(): WatchState
    }

    /** The thing holding a connection to the watch. Implemented by `GarminBridge`. */
    interface Transport {
        fun onWatchStateChanged(state: WatchState)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var host: Host? = null

    @Volatile
    private var transport: Transport? = null

    /** Last published state, so a transport attaching mid-session has something to send. */
    @Volatile
    var latestState: WatchState = WatchState.IDLE
        private set

    // --- host side ---

    fun attachHost(host: Host) {
        this.host = host
        publish(host.buildWatchState())
    }

    /**
     * Identity-checked: during activity recreation the incoming instance may attach before the
     * outgoing one is destroyed, and an unconditional `host = null` would then blank a live
     * session's link for the rest of the workout.
     */
    fun detachHost(host: Host) {
        if (this.host !== host) return
        this.host = null
        // Tell the watch the session is gone rather than leaving a stale prescription on
        // screen, which would invite logging a set into a session that no longer exists.
        publish(WatchState.IDLE)
    }

    /** Called by the host after any mutation. Cheap enough to call on every draft persist. */
    fun publish(state: WatchState) {
        latestState = state
        val target = transport ?: return
        try {
            target.onWatchStateChanged(state)
        } catch (e: Exception) {
            // A dead Bluetooth link must never propagate into the logging path.
            Log.w(TAG, "transport rejected state", e)
        }
    }

    // --- transport side ---

    fun attachTransport(transport: Transport) {
        this.transport = transport
        transport.onWatchStateChanged(latestState)
    }

    fun detachTransport(transport: Transport) {
        if (this.transport !== transport) return
        this.transport = null
    }

    /**
     * Called by the transport, off the main thread — the Connect IQ SDK delivers on a binder
     * thread. Hops to main because the host mutates UI-bound state.
     */
    fun submit(command: WatchCommand) {
        mainHandler.post {
            val target = host
            if (target == null) {
                Log.d(TAG, "dropping $command — no active workout screen")
                return@post
            }
            try {
                target.onWatchCommand(command)
            } catch (e: Exception) {
                Log.e(TAG, "host failed to apply $command", e)
            }
        }
    }

    /** True when a workout screen is listening. Lets the transport answer the watch honestly. */
    fun hasActiveSession(): Boolean = host != null
}
