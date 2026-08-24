package com.liftpath.garmin

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.liftpath.watch.WatchCommand
import com.liftpath.watch.WatchLink
import com.liftpath.watch.WatchState

/**
 * The Connect IQ transport: turns [WatchState] into messages for the Fenix 8, and messages
 * from it into [WatchCommand]s.
 *
 * Nothing here knows what a workout is. It talks to [WatchLink] and nothing else — that seam is
 * what keeps every Connect IQ import inside this package.
 *
 * **Do not construct this directly.** [WatchTransport] owns the single process-wide instance;
 * two bridges attached to [WatchLink] at once means the second silently replaces the first, so
 * the older one goes deaf with no error anywhere.
 */
class GarminBridge(
    context: Context,
    /** Diagnostics sink. Wire to a log screen while bringing this up, then drop it. */
    private val onEvent: (String) -> Unit = {}
) : WatchLink.Transport {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    // WIRELESS routes through the Garmin Connect Mobile app, the only transport available on a
    // real phone. The SDK's TETHERED type talks to the simulator over ADB instead, which is how
    // you test this half before flashing anything to the watch.
    private val connectIQ: ConnectIQ =
        ConnectIQ.getInstance(appContext, ConnectIQ.IQConnectType.WIRELESS)

    private val iqApp = IQApp(APP_ID)

    /** The device we are currently mirroring to, or null when none is connected. */
    private var device: IQDevice? = null

    /** Paired devices we hold a status subscription on, so [stop] can release them. */
    private val watched = mutableListOf<IQDevice>()

    private var pendingState: WatchState? = null
    private var sendScheduled = false

    fun start() {
        event("initialising Connect IQ")
        connectIQ.initialize(appContext, true, object : ConnectIQ.ConnectIQListener {
            override fun onSdkReady() {
                event("SDK ready")
                watchForDevices()
            }

            override fun onInitializeError(status: ConnectIQ.IQSdkErrorStatus?) {
                event("init failed: $status")
            }

            override fun onSdkShutDown() {
                event("SDK shut down")
                WatchLink.detachTransport(this@GarminBridge)
            }
        })
    }

    /**
     * Subscribe to every *paired* device and attach when one actually connects.
     *
     * The previous version took a single snapshot of [ConnectIQ.getConnectedDevices] at
     * `onSdkReady` and gave up permanently if it came back empty. That is empty far more often
     * than it sounds: while the watch is plugged into USB for a sideload, before Bluetooth has
     * settled after a phone reboot, any time the watch is simply out of range. Because the bridge
     * is created in `Application.onCreate`, "give up permanently" meant until the process was
     * cold-started — and closing the app does not do that, so there was no way to recover from
     * the UI at all.
     *
     * [ConnectIQ.getKnownDevices] lists paired devices regardless of whether they are connected
     * right now, which is what makes a subscription possible in the first place.
     */
    private fun watchForDevices() {
        val known = try {
            // Known, not connected: we want to hear about the watch arriving later, and a device
            // that is out of range does not appear in the connected list at all.
            connectIQ.knownDevices?.takeIf { it.isNotEmpty() } ?: connectIQ.connectedDevices
        } catch (e: Exception) {
            // InvalidStateException / ServiceUnavailableException both land here, and both mean
            // Garmin Connect Mobile is not in a state to talk to us.
            event("device query failed: ${e.javaClass.simpleName}")
            Log.e(TAG, "device query failed", e)
            return
        }

        if (known.isNullOrEmpty()) {
            // Nearly always the missing <queries> entry for com.garmin.android.apps.connectmobile:
            // package visibility hides Garmin Connect and this looks exactly like an unpaired watch.
            event("no paired devices — check the <queries> entry, then the pairing")
            return
        }

        event("watching ${known.size} paired device(s)")
        known.forEach { candidate ->
            runCatching {
                connectIQ.registerForDeviceEvents(candidate) { changed, status ->
                    onDeviceStatus(changed, status)
                }
                watched.add(candidate)
            }.onFailure {
                event("registerForDeviceEvents failed for ${candidate.friendlyName}")
                Log.w(TAG, "registerForDeviceEvents failed", it)
            }

            // Already connected at startup, so no status change is coming and waiting for one
            // would hang forever.
            if (candidate.status == IQDevice.IQDeviceStatus.CONNECTED) {
                attach(candidate)
            }
        }
    }

    /**
     * The [IQDevice] handed to a status callback is a bare identifier — `friendlyName` comes back
     * empty and `status` as `UNKNOWN`, which makes the log read as if a nameless device appeared.
     * The instances from [ConnectIQ.getKnownDevices] are populated, so resolve against those.
     */
    private fun nameOf(target: IQDevice): String =
        watched.firstOrNull { it.deviceIdentifier == target.deviceIdentifier }
            ?.friendlyName
            ?.takeIf { it.isNotBlank() }
            ?: target.friendlyName?.takeIf { it.isNotBlank() }
            ?: "device ${target.deviceIdentifier}"

    private fun onDeviceStatus(changed: IQDevice?, status: IQDevice.IQDeviceStatus?) {
        if (changed == null) return
        event("${nameOf(changed)} -> $status")

        if (status == IQDevice.IQDeviceStatus.CONNECTED) {
            attach(changed)
            return
        }

        // Only tear down for the device we are actually using; a second paired watch going out of
        // range must not take the live one's transport with it.
        if (device?.deviceIdentifier == changed.deviceIdentifier) {
            runCatching { connectIQ.unregisterForApplicationEvents(changed, iqApp) }
            device = null
            WatchLink.detachTransport(this)
        }
    }

    private fun attach(target: IQDevice) {
        // Re-attaching the same device would register a second app-event listener for it.
        if (device?.deviceIdentifier == target.deviceIdentifier) return

        device = target
        event("attaching ${nameOf(target)}")

        try {
            connectIQ.registerForAppEvents(target, iqApp) { _, _, message, status ->
                onWatchMessage(message, status)
            }
        } catch (e: Exception) {
            event("registerForAppEvents failed: ${e.javaClass.simpleName}")
            Log.e(TAG, "registerForAppEvents failed", e)
            // Undo the assignment above, or the identity guard at the top of this method would
            // treat a half-attached device as attached and never retry when it reconnects.
            device = null
            return
        }

        // Registering succeeds even for an app Garmin Connect has not catalogued, which is the
        // normal case for a sideloaded build. So this lookup is diagnostic only — never a gate,
        // or the integration would refuse to run on exactly the setup it is built for.
        connectIQ.getApplicationInfo(APP_ID, target, object : ConnectIQ.IQApplicationInfoListener {
            override fun onApplicationInfoReceived(app: IQApp?) {
                event("watch app catalogued, v${app?.version()}")
            }

            override fun onApplicationNotInstalled(applicationId: String?) {
                event("watch app not catalogued (expected when sideloaded)")
            }
        })

        WatchLink.attachTransport(this)
    }

    private fun onWatchMessage(message: List<Any>?, status: ConnectIQ.IQMessageStatus?) {
        if (status != ConnectIQ.IQMessageStatus.SUCCESS) {
            event("inbound message status $status")
            return
        }
        // The watch's transmitted dictionary arrives as the first element of the list.
        val command = WatchCommand.parse(message?.firstOrNull())
        if (command == null) {
            event("unparseable message: ${message?.joinToString()}")
            return
        }
        event("cmd $command")
        WatchLink.submit(command)
    }

    // --- WatchLink.Transport ---

    /**
     * Coalesced rather than sent immediately. A single logged set can trigger several
     * `persistDraft()` calls in a burst (superset highlighting, intent locking), and Bluetooth
     * is slow enough that sending each one would queue messages behind each other for seconds.
     * Only the newest state matters — this is a projection, not an event stream.
     */
    override fun onWatchStateChanged(state: WatchState) {
        pendingState = state
        if (sendScheduled) return
        sendScheduled = true
        mainHandler.postDelayed({
            sendScheduled = false
            pendingState?.let { flush(it) }
            pendingState = null
        }, SEND_DEBOUNCE_MS)
    }

    private fun flush(state: WatchState) {
        val target = device ?: return
        try {
            connectIQ.sendMessage(target, iqApp, state.toWire()) { _, _, status ->
                if (status != ConnectIQ.IQMessageStatus.SUCCESS) {
                    event("state send failed: $status")
                }
            }
        } catch (e: Exception) {
            event("sendMessage failed: ${e.javaClass.simpleName}")
            Log.e(TAG, "sendMessage failed", e)
        }
    }

    fun stop() {
        WatchLink.detachTransport(this)
        val target = device
        runCatching {
            if (target != null) connectIQ.unregisterForApplicationEvents(target, iqApp)
            watched.forEach { connectIQ.unregisterForDeviceEvents(it) }
            connectIQ.shutdown(appContext)
        }.onFailure { Log.w(TAG, "shutdown failed", it) }
        watched.clear()
        device = null
    }

    private fun event(message: String) {
        Log.d(TAG, message)
        mainHandler.post { onEvent(message) }
    }

    companion object {
        private const val TAG = "GarminBridge"
        private const val SEND_DEBOUNCE_MS = 400L

        /**
         * Must equal the `id` in the watch app's manifest.xml with dashes stripped. A mismatch
         * produces no error on either side — messages are simply never delivered — so if the
         * watch shows "No session" during an open workout, check this before anything else.
         */
        const val APP_ID = "672412e7d04c4f2fb161521358b4010f"
    }
}
