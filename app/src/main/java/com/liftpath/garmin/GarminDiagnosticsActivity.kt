package com.liftpath.garmin

import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.liftpath.R
import com.liftpath.helpers.lpColor
import com.liftpath.watch.WatchLink

/**
 * Bring-up screen for the watch link: a scrolling event log from [GarminBridge], plus a button
 * that re-sends the current projection.
 *
 * Worth keeping past bring-up. When the watch shows "No session" during an open workout there
 * are four possible causes — no paired device, package visibility, a mismatched app id, or no
 * attached host — and this screen distinguishes them in one glance. Guessing between them from
 * the watch face is miserable.
 *
 * Built in code rather than as a layout: no ids to leak into the resource table, and it never
 * needs to be themed beyond the token colours below.
 */
class GarminDiagnosticsActivity : AppCompatActivity() {

    private lateinit var log: TextView

    // Held as a field so onDestroy can detach exactly this sink rather than whatever happens to
    // be attached — a recreated instance may register before the outgoing one tears down.
    private val sink: (String) -> Unit = { append(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        log = TextView(this).apply {
            setTextAppearance(R.style.TextAppearance_LP_Body)
            setTextColor(lpColor(R.attr.lpInk))
            setPadding(32, 32, 32, 32)
        }

        val resend = Button(this).apply {
            text = "Re-send current state"
            setOnClickListener {
                val state = WatchLink.latestState
                append("host attached: ${WatchLink.hasActiveSession()}, ${state.exercises.size} exercises")
                WatchLink.publish(state)
            }
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(lpColor(R.attr.lpCanvas))
                addView(resend, LinearLayout.LayoutParams(MATCH, WRAP))
                addView(
                    ScrollView(this@GarminDiagnosticsActivity).apply { addView(log) },
                    LinearLayout.LayoutParams(MATCH, 0).apply { weight = 1f }
                )
            }
        )

        // The bridge belongs to LiftPathApplication now, so this screen only listens. It used to
        // create its own, which meant the transport existed only while this screen was open —
        // and the watch cannot tell "nobody listening" from "no workout".
        append(if (WatchTransport.isStarted()) "bridge: owned by application" else "bridge: NOT STARTED")
        WatchTransport.observe(sink).forEach { append(it) }
    }

    private fun append(line: String) {
        log.append("$line\n")
    }

    override fun onDestroy() {
        // Detach the log sink only. The bridge outlives this screen by design — stopping it here
        // is what made the transport exist only while diagnostics were open.
        WatchTransport.stopObserving(sink)
        super.onDestroy()
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
