package com.rylaide.app.ui

import android.app.Application
import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
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
import com.rylaide.app.service.RideService
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
    /** Riders we can hear right now, including ones reached through other riders. */
    val reachableText: String
        get() {
            val list = mesh.reachable
            if (list.isEmpty()) return "none yet"
            return list.joinToString(", ") {
                if (it.hops == 1) "${it.name} (direct)" else "${it.name} (${it.hops} hops)"
            }
        }
    /** Address of the relay server for the internet path, e.g. wss://rylaide-relay.you.workers.dev. Empty = offline only. */
    var relayUrl by mutableStateOf(
        getApplication<Application>().getSharedPreferences("rylaide", Context.MODE_PRIVATE)
            .getString("relayUrl", "") ?: ""
    )
        private set

    fun updateRelayUrl(value: String) {
        relayUrl = value
        getApplication<Application>().getSharedPreferences("rylaide", Context.MODE_PRIVATE)
            .edit().putString("relayUrl", value.trim()).apply()
    }

    val internetUp: Boolean get() = mesh.internetUp
    val playedText: String get() = "Voice played from: internet ${mesh.playedViaInternet} · offline link ${mesh.playedViaNearby}"

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { mesh.internetReconnect() }
    }
    private var networkCallbackSet = false

    val linkRttMs: Int get() = mesh.linkRttMs
    val audioDropped: Int get() = mesh.audioDropped
    val linkRttWorstMs: Int get() = mesh.linkRttWorstMs
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

    private var phoneNsState by mutableStateOf(true)
    /** The phone's own noise suppressor. Turn it off if your voice only comes through close to the mic. */
    var phoneNoiseSuppressor: Boolean
        get() = phoneNsState
        set(value) {
            phoneNsState = value
            engine.setPhoneNoiseSuppressor(value)
            addLog("Phone noise suppressor: " + if (value) "ON" else "OFF")
        }

    /** 2-phone fast link mode. Both phones must match. Takes effect the next time you tap Start. */
    var fastMode by mutableStateOf(false)
    val linkDrops: Int get() = mesh.drops

    private var noiseLevelState by mutableStateOf(com.rylaide.app.audio.NoiseSuppressor.Level.LOW)
    /** Strength of Rylaide's own wind and background noise reducer. Planned as a paid feature later. */
    var noiseLevel: com.rylaide.app.audio.NoiseSuppressor.Level
        get() = noiseLevelState
        set(value) {
            noiseLevelState = value
            engine.noiseReducer.level = value
            addLog("Rylaide noise reducer: ${value.label}")
        }

    var linkType by mutableStateOf(MeshDiscoveryManager.LinkType.STEADY)

    enum class VoiceGate(val label: String) {
        AUTO("Auto"),
        SENSITIVE("Sensitive (picks up quieter voice)"),
        ALWAYS("Always send (nothing is cut off)")
    }
    private var voiceGateState by mutableStateOf(VoiceGate.AUTO)
    /** How easily your voice opens the mic. Use Always send if the other rider misses words. */
    var voiceGate: VoiceGate
        get() = voiceGateState
        set(value) {
            voiceGateState = value
            engine.vad.sensitive = value == VoiceGate.SENSITIVE
            engine.vad.alwaysSend = value == VoiceGate.ALWAYS
            addLog("Voice detection: ${value.label}")
        }

    private var boostState by mutableStateOf(1.0)
    /** Extra loudness of what you hear: 1x, 2x, 3x or 4x. */
    var incomingBoost: Double
        get() = boostState
        set(value) {
            boostState = value
            engine.playbackBoost = value
            addLog("Incoming voice boost: x${"%.0f".format(value)}")
        }

    /** True while the phone is giving the app a silent mic. */
    val micBlocked: Boolean get() = engine.micBlocked
    private var lastMicBlocked = false

    /** Called twice a second by the screen: logs when the mic gets blocked or comes back. */
    fun checkMicBlocked() {
        val now = engine.micBlocked
        if (now != lastMicBlocked) {
            lastMicBlocked = now
            addLog(
                if (now) "MIC BLOCKED: the phone is giving us a silent mic. Is the app in the background, or is a privacy switch off?"
                else "Mic is working again"
            )
        }
    }

    /** Called by the screen when the app goes behind another app or the screen turns off. */
    fun noteAppVisible(visible: Boolean) {
        if (running) addLog(if (visible) "App back on screen" else "App went to the background (screen off or another app)")
    }

    /** Rough end-to-end delay, not counting the headset. See the note on the screen. */
    val estimatedDelayMs: Int
        get() {
            val link = if (mesh.linkRttMs > 0) mesh.linkRttMs / 2 else 100
            val jitter = maxOf(engine.bufferedMs, 40)
            return 20 + 10 + 20 + link + jitter + engine.playbackBufferMs
        }

    /** True while the reducer thinks it hears wind. */
    val windDetected: Boolean get() = engine.noiseReducer.windDetected

    private var sensitivityState by mutableStateOf(1.0)
    /** Extra mic sensitivity, 1x to 8x. Raise it if you have to hold the mic close to talk. */
    var micSensitivity: Double
        get() = sensitivityState
        set(value) {
            sensitivityState = value
            engine.micSensitivity = value
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

    /**
     * Earphones, a Bluetooth headset or a USB receiver coming or going: follow it,
     * without the rider having to stop and start.
     */
    private fun onDevicesChanged() {
        if (!running) return
        val newRoute = routeManager.detectBestRoute()
        val changed = newRoute.name != audioRoute
        // On Android 12+ this is cheap and also catches a Bluetooth headset that
        // finishes connecting a moment after it appears.
        if (changed || android.os.Build.VERSION.SDK_INT >= 31) {
            audioRoute = routeManager.connectBestAvailableRoute().name
        }
        applyRoute()
        if (changed) {
            addLog("Audio route changed to $audioRoute")
            addLog(routeManager.describeDevices())
            if (audioRoute == "BLUETOOTH_HEADSET") scheduleBluetoothRetry()
        }
    }

    /** Some headsets need a second try before the call link comes up. */
    private fun scheduleBluetoothRetry() {
        mainHandler.postDelayed({
            if (running && audioRoute == "BLUETOOTH_HEADSET" && !routeManager.bluetoothCallReady()) {
                routeManager.connectBestAvailableRoute()
                applyRoute()
                addLog("Bluetooth headset: trying the call link again")
            }
        }, 3000)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun applyRoute() {
        val route = runCatching { com.rylaide.app.audio.AudioRoute.valueOf(audioRoute) }.getOrNull() ?: return
        val (input, output) = routeManager.preferredDevices(route)
        engine.setPreferredDevices(input, output)
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
        mesh.start(groupCode, name, fastMode, linkType, relayUrl.trim())
        if (relayUrl.isNotBlank()) {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.registerDefaultNetworkCallback(networkCallback)
                networkCallbackSet = true
            } catch (t: Throwable) {
                addLog("Network watcher could not start: ${t.message}")
            }
        }
        try {
            RideService.start(context)
            addLog("Ride service started (keeps the mic and link alive in the background)")
        } catch (t: Throwable) {
            addLog("Ride service could not start: ${t.message}")
        }
        running = true
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        addLog("Started. Audio route: $audioRoute")
        fun granted(p: String) =
            ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        addLog(
            "Build 20, internet path + offline link, backlog guard + ride service + road fixes, relay through other riders + steady link type + noise levels + link mode switch + link delay readout + wind and noise reducer + auto device switching (Android ${android.os.Build.VERSION.SDK_INT}). Permissions: " +
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
        if (networkCallbackSet) {
            try {
                (getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .unregisterNetworkCallback(networkCallback)
            } catch (_: Throwable) { }
            networkCallbackSet = false
        }
        RideService.stop(getApplication<Application>())
        val audioManager = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        mainHandler.removeCallbacksAndMessages(null)
        routeManager.release()
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
        while (log.size > 60) log.removeAt(log.size - 1)
    }

    // ---- MeshDiscoveryManager.Listener (called on the main thread) ----

    override fun onStatus(message: String) {
        status = message
        addLog(message)
    }

    override fun onPeersChanged(peers: List<MeshDiscoveryManager.Peer>) {
        this.peers = peers
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
