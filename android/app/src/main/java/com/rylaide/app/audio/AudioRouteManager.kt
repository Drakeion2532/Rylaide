package com.rylaide.app.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

enum class AudioRoute {
    WIRED_HEADSET,   // 3.5 mm or USB-C earphones, and USB / direct plug-in receivers
    BLUETOOTH_HEADSET,
    PHONE_SPEAKER
}

/**
 * Picks the best available audio path for the rider and keeps following it as
 * devices come and go, in this priority order:
 *
 *   1. Wired: 3.5 mm headset, USB-C earphones, or a USB / plug-in receiver
 *      (many wireless-mic receivers show up to Android as a USB audio device).
 *   2. Bluetooth headset (any brand; call audio profile).
 *   3. The phone's own speaker and mic, so the app always works.
 *
 * On Android 12 and newer the app asks Android to use the chosen device
 * ("communication device"), which is the supported way. On Android 11 and
 * older it uses the older Bluetooth call (SCO) switches plus pinning the mic
 * and speaker to the device.
 *
 * Call [connectBestAvailableRoute] whenever a device is added or removed.
 * Calling it again is safe.
 */
class AudioRouteManager(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothAudioManager = BluetoothAudioManager(context)

    private fun isWired(device: AudioDeviceInfo) =
        device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            device.type == AudioDeviceInfo.TYPE_USB_ACCESSORY

    /** Bluetooth call audio (what a headset mic needs), including newer LE Audio headsets. */
    private fun isBluetoothCall(device: AudioDeviceInfo) =
        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET

    private fun isBluetoothAny(device: AudioDeviceInfo) =
        isBluetoothCall(device) || device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP

    private fun inputs() = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
    private fun outputs() = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

    /** The wired earphone output, if one is plugged in. */
    fun wiredOutput(): AudioDeviceInfo? = outputs().firstOrNull { isWired(it) }

    /** The wired headset microphone, if the plugged-in accessory has one. */
    fun wiredInput(): AudioDeviceInfo? = inputs().firstOrNull { isWired(it) }

    /** Plain-text list of what Android reports, for the test log. */
    fun describeDevices(): String {
        fun name(d: AudioDeviceInfo) = when (d.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "phone mic"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "phone speaker"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "phone earpiece"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired headset"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired headphones"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB device"
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB accessory"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth call"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth music"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE headset"
            else -> "type ${d.type}"
        }
        val ins = inputs().joinToString { name(it) }
        val outs = outputs().joinToString { name(it) }
        return "Audio devices. Inputs: [$ins] Outputs: [$outs]"
    }

    fun detectBestRoute(): AudioRoute {
        if (wiredOutput() != null) return AudioRoute.WIRED_HEADSET
        if (outputs().any { isBluetoothAny(it) }) return AudioRoute.BLUETOOTH_HEADSET
        return AudioRoute.PHONE_SPEAKER
    }

    /**
     * The mic and speaker devices to pin the audio streams to for [route], or
     * null to let Android choose.
     */
    fun preferredDevices(route: AudioRoute, phoneMic: Boolean = false): Pair<AudioDeviceInfo?, AudioDeviceInfo?> {
        val builtInMic = inputs().firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        return when (route) {
            AudioRoute.WIRED_HEADSET ->
                (if (phoneMic && builtInMic != null) builtInMic else wiredInput()) to wiredOutput()
            AudioRoute.BLUETOOTH_HEADSET ->
                (if (phoneMic && builtInMic != null) builtInMic
                 else inputs().firstOrNull { isBluetoothCall(it) }) to
                    outputs().firstOrNull { isBluetoothCall(it) }
            AudioRoute.PHONE_SPEAKER -> null to null
        }
    }

    /** Switches to the best device right now. Safe to call again whenever devices change. */
    fun connectBestAvailableRoute(): AudioRoute {
        val route = detectBestRoute()
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        if (Build.VERSION.SDK_INT >= 31) {
            val candidates = audioManager.availableCommunicationDevices
            val chosen = when (route) {
                AudioRoute.WIRED_HEADSET -> candidates.firstOrNull { isWired(it) }
                AudioRoute.BLUETOOTH_HEADSET -> candidates.firstOrNull { isBluetoothCall(it) }
                AudioRoute.PHONE_SPEAKER -> candidates.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            }
            if (chosen != null) audioManager.setCommunicationDevice(chosen)
            else audioManager.clearCommunicationDevice()
        } else {
            when (route) {
                AudioRoute.WIRED_HEADSET -> {
                    bluetoothAudioManager.disconnect()
                    audioManager.isSpeakerphoneOn = false
                }
                AudioRoute.BLUETOOTH_HEADSET -> {
                    audioManager.isSpeakerphoneOn = false
                    bluetoothAudioManager.connectToHeadset()
                }
                AudioRoute.PHONE_SPEAKER -> {
                    bluetoothAudioManager.disconnect()
                    audioManager.isSpeakerphoneOn = true
                }
            }
        }
        return route
    }

    /** True once a Bluetooth call device is really available (the headset finished connecting). */
    fun bluetoothCallReady(): Boolean = outputs().any { isBluetoothCall(it) }

    /** Gives audio back to the phone when the intercom stops. */
    fun release() {
        if (Build.VERSION.SDK_INT >= 31) {
            audioManager.clearCommunicationDevice()
        } else {
            bluetoothAudioManager.disconnect()
            audioManager.isSpeakerphoneOn = false
        }
        audioManager.mode = AudioManager.MODE_NORMAL
    }
}
