package com.rylaide.app.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

enum class AudioRoute {
    WIRED_HEADSET,
    BLUETOOTH_HEADSET,
    PHONE_SPEAKER
}

/**
 * Picks the best available audio path for the rider, in this priority order:
 *
 *   1. Wired headset/earphones (3.5mm or USB-C) - if one's plugged in, use
 *      it. Wired accessories route automatically once the app is in
 *      MODE_IN_COMMUNICATION - no explicit "connect" call needed, unlike
 *      Bluetooth.
 *   2. Bluetooth headset (any brand - EJEAS, Cardo, Sena, generic) - needs
 *      an explicit SCO connection, handled by BluetoothAudioManager.
 *   3. Phone's own speaker/mic - last resort, so the app still works even
 *      with nothing plugged in (useful for testing, not for actual riding).
 *
 * Call refreshRoute() whenever something plugs in or disconnects mid-ride -
 * wire this up to an AudioDeviceCallback (registerAudioDeviceCallback) so it
 * reacts automatically rather than only checking once at startup.
 */
class AudioRouteManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothAudioManager = BluetoothAudioManager(context)

    fun detectBestRoute(): AudioRoute {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        val hasWired = outputs.any { device ->
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        if (hasWired) return AudioRoute.WIRED_HEADSET

        val hasBluetooth = outputs.any { device ->
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
        if (hasBluetooth) return AudioRoute.BLUETOOTH_HEADSET

        return AudioRoute.PHONE_SPEAKER
    }

    fun connectBestAvailableRoute(): AudioRoute {
        val route = detectBestRoute()
        when (route) {
            AudioRoute.WIRED_HEADSET -> {
                // No explicit "connect" step - Android routes to the wired
                // accessory automatically once we're in communication mode.
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = false
            }
            AudioRoute.BLUETOOTH_HEADSET -> {
                bluetoothAudioManager.connectToHeadset()
            }
            AudioRoute.PHONE_SPEAKER -> {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = true
            }
        }
        return route
    }
}
