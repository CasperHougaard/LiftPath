package com.liftpath.garmin

import android.content.Context

/**
 * Process-wide owner of the single [GarminBridge].
 *
 * Before this existed the only bridge was the one `GarminDiagnosticsActivity` created for
 * itself, which meant the transport existed *only while that screen was open*. The symptom was
 * confusing rather than obviously broken: the watch app would show "synced" in green and then
 * sit on "No session" forever, because `Communications.transmit` only needs Garmin Connect to
 * accept the message — it does not need anything on the phone to be listening. So the watch
 * reported success for a sync nobody answered.
 *
 * One bridge, owned above every activity, is the fix. Two bridges attached to `WatchLink` at
 * once would be worse than none: `attachTransport` keeps the most recent, so the older one goes
 * silently deaf.
 *
 * Deliberately always-on rather than started when a workout begins. The cost is a binder
 * connection to Garmin Connect for the process lifetime; the benefit is that the watch gets a
 * real `IDLE` answer when no workout is running, instead of silence. Those two look identical on
 * the wrist, and telling them apart is most of what debugging this costs.
 *
 * Main-thread confined. `GarminBridge.event` posts to the main looper, so every mutation here
 * arrives there and no synchronisation is needed.
 */
object WatchTransport {

    /**
     * Rolling diagnostics backlog, so the diagnostics screen can show what happened *before* it
     * was opened — which, now that the bridge starts at process start, is where the interesting
     * events are.
     */
    private const val LOG_CAP = 200

    private val backlog = ArrayDeque<String>()
    private var bridge: GarminBridge? = null
    private var listener: ((String) -> Unit)? = null

    /** Idempotent: a second call is a no-op, not a second bridge. */
    fun start(context: Context) {
        if (bridge != null) return
        val created = GarminBridge(context) { record(it) }
        bridge = created
        created.start()
    }

    private fun record(line: String) {
        backlog.addLast(line)
        while (backlog.size > LOG_CAP) backlog.removeFirst()
        listener?.invoke(line)
    }

    /**
     * Attach a log sink and get everything recorded so far. Only one observer at a time — this
     * serves a single diagnostics screen, and a list would imply a lifecycle nothing needs.
     */
    fun observe(sink: (String) -> Unit): List<String> {
        listener = sink
        return backlog.toList()
    }

    fun stopObserving(sink: (String) -> Unit) {
        if (listener === sink) listener = null
    }

    /** Whether the bridge was ever created. Lets the diagnostics screen say so plainly. */
    fun isStarted(): Boolean = bridge != null
}
