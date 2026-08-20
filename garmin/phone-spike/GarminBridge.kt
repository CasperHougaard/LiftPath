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
 * This is the only file in the app that mentions Garmin, which is deliberate — it is also the
 * only file that cannot compile until the Connect IQ Mobile SDK `.aar` is in `app/libs/`. Once
 * it is, move this into `app/src/main/java/com/liftpath/garmin/` and wire [start] into
 * `LiftPathApplication`. See `garmin/README.md`.
 *
 * Nothing here knows what a workout is. It talks to [WatchLink] and nothing else.
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
    private var device: IQDevice? = null

    private var pendingState: WatchState? = null
    private var sendScheduled = false

    fun start() {
        event("initialising Connect IQ")
        connectIQ.initialize(appContext, true, object : ConnectIQ.ConnectIQListener {
            override fun onSdkReady() {
                event("SDK ready")
                attachToFirstDevice()
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

    private fun attachToFirstDevice() {
        val devices = try {
            connectIQ.connectedDevices
        } catch (e: Exception) {
            // InvalidStateException / ServiceUnavailableException both land here, and both mean
            // Garmin Connect Mobile is not in a state to talk to us.
            event("device query failed: ${e.javaClass.simpleName}")
            Log.e(TAG, "connectedDevices failed", e)
            return
        }

        if (devices.isNullOrEmpty()) {
            // Nearly always the missing <queries> entry for com.garmin.android.apps.connectmobile:
            // package visibility hides Garmin Connect and this looks exactly like an unpaired watch.
            event("no devices — check the <queries> entry, then the pairing")
            return
        }

        val target = devices.first()
        device = target
        event("device ${target.friendlyName} (${target.status})")

        try {
            connectIQ.registerForAppEvents(target, iqApp) { _, _, message, status ->
                onWatchMessage(message, status)
            }
        } catch (e: Exception) {
            event("registerForAppEvents failed: ${e.javaClass.simpleName}")
            Log.e(TAG, "registerForAppEvents failed", e)
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
            connectIQ.shutdown(appContext)
        }.onFailure { Log.w(TAG, "shutdown failed", it) }
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
