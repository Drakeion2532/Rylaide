package com.rylaide.app.audio

import android.content.Context
import android.media.AudioManager

/**
 * Routes mic input and speaker output through the rider's paired Bluetooth
 * headset (HFP profile) instead of the phone's own mic/speaker.
 *
 * This works identically regardless of headset brand - EJEAS, Cardo, Sena,
 * or a generic headset - because HFP is a standard Bluetooth profile, not
 * something any manufacturer controls. We are NOT talking to the headset's
 * own proprietary intercom mesh here, just its standard phone-audio profile.
 *
 * Only used when AudioRouteManager decides Bluetooth is the best available
 * route - if a wired headset is plugged in, that's preferred instead (see
 * AudioRouteManager) since it needs no explicit connection step at all.
 */
class BluetoothAudioManager(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun connectToHeadset() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.startBluetoothSco()
        audioManager.isBluetoothScoOn = true
    }

    fun disconnect() {
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
    }

    fun isHeadsetConnected(): Boolean = audioManager.isBluetoothScoOn
}
