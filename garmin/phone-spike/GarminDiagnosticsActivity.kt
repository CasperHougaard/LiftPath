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
    private lateinit var bridge: GarminBridge

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

        bridge = GarminBridge(this) { append(it) }
        bridge.start()
    }

    private fun append(line: String) {
        log.append("$line\n")
    }

    override fun onDestroy() {
        // Only correct because this screen owns its own bridge. Once LiftPathApplication owns a
        // long-lived one, drop this and let the application manage the lifecycle — two bridges
        // attached to WatchLink at once means the second silently replaces the first.
        bridge.stop()
        super.onDestroy()
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
