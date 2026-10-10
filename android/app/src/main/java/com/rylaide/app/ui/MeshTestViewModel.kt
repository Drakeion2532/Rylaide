package com.rylaide.app.ui

import android.app.Application
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.media.AudioManager
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
    private val engine = IntercomAudioEngine { frame ->
        mesh.sendAudio(frame)
        framesSent.incrementAndGet()
    }

    val currentLevel: Double get() = engine.vad.lastRms
    private var thresholdState by mutableStateOf(engine.vad.threshold)
    var vadThreshold: Double
        get() = thresholdState
        set(value) {
            thresholdState = value
            engine.vad.threshold = value
        }

    /** Call only after the runtime permissions have been granted. */
    fun start(groupCode: String, name: String) {
        if (running) return
        val context = getApplication<Application>()
        audioRoute = AudioRouteManager(context).connectBestAvailableRoute().name
        framesSent.set(0)
        framesReceived.set(0)
        engine.start()
        mesh.start(groupCode, name)
        running = true
        addLog("Started. Audio route: $audioRoute")
        fun granted(p: String) =
            ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        addLog(
            "Build 3 (Android ${android.os.Build.VERSION.SDK_INT}). Permissions: " +
                "coarse=${granted(Manifest.permission.ACCESS_COARSE_LOCATION)} " +
                "fine=${granted(Manifest.permission.ACCESS_FINE_LOCATION)} " +
                "mic=${granted(Manifest.permission.RECORD_AUDIO)}"
        )
    }

    fun stop() {
        if (!running) return
        engine.micOpen = false
        micOpen = false
        engine.stop()
        mesh.stop()
        val audioManager = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
    }

    override fun onAudioFrame(fromEndpointId: String, pcm: ByteArray) {
        framesReceived.incrementAndGet()
        engine.enqueuePlayback(pcm)
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
