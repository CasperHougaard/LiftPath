package com.liftpath.helpers

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * Plays a short confirmation beep, but only when audio is currently routed to a Bluetooth
 * device (headphones/speaker). [ToneGenerator] writes straight to [AudioManager.STREAM_MUSIC]
 * without requesting audio focus, so the beep mixes into whatever is already playing over
 * Bluetooth instead of pausing or ducking it.
 */
object BluetoothBeepHelper {

    private const val BEEP_DURATION_MS = 150
    private const val RELEASE_DELAY_MS = 300L
    private const val VOLUME_PERCENT = 80

    private val BLUETOOTH_OUTPUT_TYPES = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER
    )

    /** No-op unless the active output route is Bluetooth. */
    fun playIfBluetoothActive(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val isBluetoothActive = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .any { it.type in BLUETOOTH_OUTPUT_TYPES }
        if (!isBluetoothActive) return

        val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME_PERCENT)
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_DURATION_MS)
        Handler(Looper.getMainLooper()).postDelayed({ toneGenerator.release() }, RELEASE_DELAY_MS)
    }
}
