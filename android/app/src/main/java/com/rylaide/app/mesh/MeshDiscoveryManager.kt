package com.rylaide.app.mesh

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds and connects to nearby riders running Rylaide with no internet.
 * Wraps Google's Nearby Connections API (Bluetooth + Wi-Fi Direct underneath).
 *
 * How two phones find each other:
 *  - Every phone advertises AND discovers at the same time (P2P_CLUSTER).
 *  - The advertised name is "GROUPCODE|riderId|displayName". Phones only talk
 *    to endpoints whose group code matches.
 *  - Both phones will see each other, so to avoid both sending a connection
 *    request at once, only the phone with the SMALLER riderId initiates.
 *
 * Messages sent over the link are small BYTES payloads. The first byte is the
 * message type so SOS, positions and chat can share the same link later.
 *
 * Sprint 1 limits (on purpose, to be fixed later):
 *  - Connections are auto-accepted for any phone using the same group code.
 *    Before launch, add a proper check (shared secret or the authentication
 *    digits) so a stranger who guesses the code cannot join.
 */
class MeshDiscoveryManager(
    context: Context,
    private val listener: Listener
) {

    interface Listener {
        fun onStatus(message: String)
        fun onPeersChanged(peers: List<Peer>)
        fun onAudioFrame(fromEndpointId: String, pcm: ByteArray)
    }

    data class Peer(val endpointId: String, val name: String)

    companion object {
        private const val SERVICE_ID = "com.rylaide.app.mesh"
        const val TYPE_AUDIO: Byte = 1
        private const val RETRY_DELAY_MS = 2000L
    }

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val riderId: String = UUID.randomUUID().toString().take(6)
    private var groupCode: String = ""
    private var displayName: String = ""

    @Volatile
    private var running = false

    // endpointId -> name, for phones we are fully connected to
    private val connected = ConcurrentHashMap<String, String>()

    // endpointId -> remote riderId, for phones we have seen but may not be connected to
    private val discovered = ConcurrentHashMap<String, String>()
    private val pending = ConcurrentHashMap.newKeySet<String>()

    // endpointId -> display name shown in the UI
    private val names = ConcurrentHashMap<String, String>()

    private fun localEndpointName() = "$groupCode|$riderId|$displayName"

    private fun parse(endpointName: String): Triple<String, String, String>? {
        val parts = endpointName.split("|", limit = 3)
        if (parts.size < 3) return null
        return Triple(parts[0], parts[1], parts[2])
    }

    fun start(groupCode: String, displayName: String) {
        stop()
        this.groupCode = groupCode.trim().uppercase().replace("|", "")
        this.displayName = displayName.trim().replace("|", "").ifEmpty { "Rider" }
        running = true
        startAdvertising()
        startDiscovery()
    }

    fun stop() {
        running = false
        mainHandler.removeCallbacksAndMessages(null)
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        connected.clear()
        discovered.clear()
        pending.clear()
        names.clear()
        notifyPeers()
    }

    fun connectedCount(): Int = connected.size

    /** Sends one audio frame (raw 16-bit PCM) to everyone connected. */
    fun sendAudio(pcm: ByteArray) {
        if (connected.isEmpty()) return
        val message = ByteArray(pcm.size + 1)
        message[0] = TYPE_AUDIO
        System.arraycopy(pcm, 0, message, 1, pcm.size)
        client.sendPayload(connected.keys.toList(), Payload.fromBytes(message))
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        client.startAdvertising(localEndpointName(), SERVICE_ID, lifecycleCallback, options)
            .addOnSuccessListener { listener.onStatus("Advertising as ${displayName} (group $groupCode)") }
            .addOnFailureListener { listener.onStatus("Advertising failed: ${it.message}") }
    }

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        client.startDiscovery(SERVICE_ID, discoveryCallback, options)
            .addOnSuccessListener { listener.onStatus("Looking for riders in group $groupCode…") }
            .addOnFailureListener { listener.onStatus("Discovery failed: ${it.message}") }
    }

    private fun requestConnectionTo(endpointId: String) {
        if (!running || connected.containsKey(endpointId) || !pending.add(endpointId)) return
        client.requestConnection(localEndpointName(), endpointId, lifecycleCallback)
            .addOnFailureListener {
                pending.remove(endpointId)
                listener.onStatus("Connection request failed: ${it.message}")
            }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val (code, theirId, _) = parse(info.endpointName) ?: return
            if (code != groupCode || theirId == riderId) return
            discovered[endpointId] = theirId
            // Only the phone with the smaller id starts the connection.
            if (riderId < theirId) requestConnectionTo(endpointId)
        }

        override fun onEndpointLost(endpointId: String) {
            discovered.remove(endpointId)
            pending.remove(endpointId)
        }
    }

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val parsed = parse(info.endpointName)
            if (parsed == null || parsed.first != groupCode) {
                client.rejectConnection(endpointId)
                pending.remove(endpointId)
                return
            }
            discovered[endpointId] = parsed.second
            names[endpointId] = parsed.third
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            pending.remove(endpointId)
            if (result.status.isSuccess) {
                val name = names[endpointId] ?: endpointId
                connected[endpointId] = name
                listener.onStatus("Connected to rider $name")
                notifyPeers()
            } else {
                listener.onStatus("Could not connect (${result.status.statusMessage ?: result.status.statusCode})")
            }
        }

        override fun onDisconnected(endpointId: String) {
            connected.remove(endpointId)
            pending.remove(endpointId)
            notifyPeers()
            listener.onStatus("A rider went out of range. Reconnecting…")
            // If they are still around, try again. Discovery stays on, so if
            // they come back later it will find them again by itself.
            val theirId = discovered[endpointId]
            if (running && theirId != null && riderId < theirId) {
                mainHandler.postDelayed({ requestConnectionTo(endpointId) }, RETRY_DELAY_MS)
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            if (bytes.size < 2) return
            if (bytes[0] == TYPE_AUDIO) {
                listener.onAudioFrame(endpointId, bytes.copyOfRange(1, bytes.size))
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Nothing to track for small BYTES payloads.
        }
    }

    private fun notifyPeers() {
        val list = connected.map { Peer(it.key, it.value) }
        mainHandler.post { listener.onPeersChanged(list) }
    }
}
