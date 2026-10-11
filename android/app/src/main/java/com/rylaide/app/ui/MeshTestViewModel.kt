package com.rylaide.app.ui

import android.app.Application
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.rylaide.app.audio.AudioRouteManager
import com.rylaide.app.audio.IntercomAudioEngine
import com.rylaide.app.mesh.MeshDiscoveryManager
import java.util.concurrent.atomic.AtomicLong

/**
 * Holds the Sprint 1 test: mesh link + audio engine. Lives in a ViewModel so
 * rotating the screen does not drop the connection.
 */
class MeshTestViewModel(app: Application) : AndroidViewModel(app), MeshDiscoveryManager.Listener {

    var running by mutableStateOf(false)
        private set
    var status by mutableStateOf("Not started")
        private set
    var peers by mutableStateOf<List<MeshDiscoveryManager.Peer>>(emptyList())
        private set
    var micOpen by mutableStateOf(false)
        private set
    var audioRoute by mutableStateOf("-")
        private set

    val log = mutableStateListOf<String>()

    val framesSent = AtomicLong(0)
    val framesReceived = AtomicLong(0)

    private val mesh = MeshDiscoveryManager(app, this)
    private val engine = IntercomAudioEngine { packet ->
        mesh.sendAudio(packet)
        framesSent.incrementAndGet()
    }

    val currentLevel: Double get() = engine.vad.lastRms
    val concealedFrames: Long get() = engine.concealedFrames
    val lateFrames: Long get() = engine.lateFrames
    val bufferedMs: Int get() = engine.bufferedMs
    private var thresholdState by mutableStateOf(engine.vad.threshold)
    var vadThreshold: Double
        get() = thresholdState
        set(value) {
            thresholdState = value
            engine.vad.threshold = value
        }

    private var autoLevelState by mutableStateOf(true)
    /** Automatic mic level: the gate follows the background noise and quiet voices get a boost. */
    var autoLevel: Boolean
        get() = autoLevelState
        set(value) {
            autoLevelState = value
            engine.vad.auto = value
        }

    /** The level above which your voice is sent right now. */
    val gateLevel: Double get() = engine.vad.effectiveThreshold
    val micBoost: Double get() = engine.micBoost
    val playbackBufferMs: Int get() = engine.playbackBufferMs

    private val routeManager by lazy { AudioRouteManager(getApplication<Application>()) }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = onDevicesChanged()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = onDevicesChanged()
    }

    /** Plug or unplug earphones mid-ride and the app follows. */
    private fun onDevicesChanged() {
        if (!running) return
        val newRoute = routeManager.detectBestRoute()
        if (newRoute.name != audioRoute) {
            audioRoute = routeManager.connectBestAvailableRoute().name
            applyRoute()
            addLog("Audio route changed to $audioRoute")
            addLog(routeManager.describeDevices())
        }
    }

    private fun applyRoute() {
        if (audioRoute == "WIRED_HEADSET") {
            engine.setPreferredDevices(routeManager.wiredInput(), routeManager.wiredOutput())
        } else {
            engine.setPreferredDevices(null, null)
        }
    }

    /** Call only after the runtime permissions have been granted. */
    fun start(groupCode: String, name: String) {
        if (running) return
        val context = getApplication<Application>()
        audioRoute = routeManager.connectBestAvailableRoute().name
        framesSent.set(0)
        framesReceived.set(0)
        engine.start()
        applyRoute()
        mesh.start(groupCode, name)
        running = true
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        addLog("Started. Audio route: $audioRoute")
        fun granted(p: String) =
            ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        addLog(
            "Build 8, auto level + low delay (Android ${android.os.Build.VERSION.SDK_INT}). Permissions: " +
                "coarse=${granted(Manifest.permission.ACCESS_COARSE_LOCATION)} " +
                "fine=${granted(Manifest.permission.ACCESS_FINE_LOCATION)} " +
                "mic=${granted(Manifest.permission.RECORD_AUDIO)}"
        )
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val locationOn = androidx.core.location.LocationManagerCompat.isLocationEnabled(locationManager)
        addLog("Audio: ${engine.effectsInfo}")
        addLog("Playback buffer: ${engine.playbackBufferMs} ms (part of the delay you hear)")
        addLog(routeManager.describeDevices())
        addLog("Phone Location switch: " + if (locationOn) "ON" else "OFF - turn it on, Nearby needs it")
        val bluetoothOn = android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        addLog("Bluetooth: " + if (bluetoothOn) "ON" else "OFF - turn it on")
    }

    fun stop() {
        if (!running) return
        engine.micOpen = false
        micOpen = false
        engine.stop()
        mesh.stop()
        val audioManager = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
        audioManager.isSpeakerphoneOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
        running = false
        status = "Stopped"
        addLog("Stopped")
    }

    fun setMic(open: Boolean) {
        micOpen = open
        engine.micOpen = open
    }

    private fun addLog(line: String) {
        log.add(0, line)
        while (log.size > 30) log.removeAt(log.size - 1)
    }

    // ---- MeshDiscoveryManager.Listener (called on the main thread) ----

    override fun onStatus(message: String) {
        status = message
        addLog(message)
    }

    override fun onPeersChanged(peers: List<MeshDiscoveryManager.Peer>) {
        this.peers = peers
        engine.keepOnly(peers.map { it.endpointId })
    }

    override fun onAudioFrame(fromEndpointId: String, packet: ByteArray) {
        framesReceived.incrementAndGet()
        engine.enqueuePlayback(fromEndpointId, packet)
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
