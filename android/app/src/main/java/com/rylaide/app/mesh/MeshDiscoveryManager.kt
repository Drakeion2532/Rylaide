package com.rylaide.app.mesh

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.BandwidthInfo
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionOptions
import com.google.android.gms.nearby.connection.ConnectionType
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
 * Voice packets are [type][sequence 2 bytes][Opus data].
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
        fun onAudioFrame(fromRiderKey: String, packet: ByteArray)
    }

    data class Peer(val endpointId: String, val name: String)

    /**
     * How the phone-to-phone link may use the phone's radios. Build 13 logs showed
     * the link dropping each time Nearby changed between Bluetooth and Wi-Fi, so
     * these are for finding which setting stays connected best.
     */
    enum class LinkType(val label: String) {
        /** Google decides. May switch to Wi-Fi in a way that briefly breaks the link. */
        AUTO("Auto (Google decides)"),
        /** No switch that disrupts the phone's own Wi-Fi. Our best guess for stability. */
        STEADY("Steady (no disruptive switch)"),
        /** Stay on low-power Bluetooth and never move to Wi-Fi. For testing. */
        BLUETOOTH_ONLY("Bluetooth only (test)")
    }

    companion object {
        private const val SERVICE_ID = "com.rylaide.app.mesh"
        private const val INTERNET = "internet"
        private const val INTERNET_SEEN_MS = 3000L
        private const val TYPE_PING: Byte = 2
        private const val TYPE_PONG: Byte = 3
        private const val PING_INTERVAL_MS = 2000L
        private const val HEALTH_INTERVAL_MS = 1000L
        private const val CONGESTED_RTT_MS = 1000
        private const val RECOVERED_RTT_MS = 400
        private const val POSITION_INTERVAL_MS = 2000L
        private const val POSITION_EXPIRY_MS = 30_000L
        private const val PRESENCE_INTERVAL_MS = 3000L
        private const val PRESENCE_EXPIRY_MS = 10000L
        private const val RETRY_DELAY_MS = 2000L
        // The phone with the larger id waits this long for the other phone to
        // connect first, then requests the connection itself. This avoids the
        // long waits when only one of the two phones manages to discover the other.
        private const val SECOND_CHANCE_DELAY_MS = 3000L
        // If nobody has connected for this long, restart searching. Bluetooth
        // scanning on some phones quietly stalls.
        private const val WATCHDOG_MS = 8000L
        // Restarting the search throws away a search that may be about to succeed
        // (Bluetooth discovery can take a while), so only do it after this long
        // with nobody found at all.
        private const val SEARCH_RESTART_MS = 25000L
    }

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    // A fresh id on every Start, so other phones never mix a restarted rider up with the old one.
    private var riderId: String = UUID.randomUUID().toString().take(6)
    private var myOrigin: Int = riderId.hashCode()
    private var router = RelayRouter(myOrigin)
    private var presenceSeq = 0

    /** A rider we can hear, directly or through other riders. [hops] is 1 for a direct link. */
    data class Reach(val name: String, val hops: Int)

    private class ReachEntry(val name: String, val hops: Int, val seenAtMs: Long)
    private val reach = ConcurrentHashMap<Int, ReachEntry>()

    /** Riders heard in the last 10 seconds, nearest first. Includes riders reached through relays. */
    val reachable: List<Reach>
        get() {
            val now = System.currentTimeMillis()
            return reach.values
                .filter { now - it.seenAtMs < PRESENCE_EXPIRY_MS }
                .map { Reach(it.name, it.hops) }
                .sortedBy { it.hops }
        }

    private fun originKey(origin: Int): String = Integer.toHexString(origin)

    // ---- Where riders are (for distance readouts while testing) ----

    @Volatile private var myPosition: Position? = null
    private var myPositionAtMs = 0L
    private var positionSeq = 0

    private class SeenPosition(val pos: Position, val atMs: Long)
    private val positions = ConcurrentHashMap<Int, SeenPosition>()

    /** How far a rider is from us right now, from both phones' GPS. */
    data class RiderDistance(val name: String, val meters: Double, val accuracyM: Int, val ageSec: Int)

    /** Called by the app whenever the phone has a new GPS fix. */
    fun updateMyPosition(p: Position) {
        myPosition = p
        myPositionAtMs = System.currentTimeMillis()
    }

    /** Distance to every rider whose position we have heard in the last 30 s, nearest first. */
    val distances: List<RiderDistance>
        get() {
            val me = myPosition ?: return emptyList()
            val now = System.currentTimeMillis()
            return positions.entries.mapNotNull { (origin, seen) ->
                val age = now - seen.atMs
                if (age > POSITION_EXPIRY_MS) return@mapNotNull null
                val name = reach[origin]?.name ?: "Rider " + originKey(origin).takeLast(4)
                RiderDistance(
                    name, GeoMath.distanceMeters(me, seen.pos),
                    me.accuracyM + seen.pos.accuracyM, (age / 1000).toInt()
                )
            }.sortedBy { it.meters }
        }

    private fun distanceToText(origin: Int): String {
        val me = myPosition ?: return "distance unknown (no GPS fix on this phone)"
        val seen = positions[origin] ?: return "distance unknown (no GPS from them)"
        val age = ((System.currentTimeMillis() - seen.atMs) / 1000).toInt()
        return "about ${GeoMath.format(GeoMath.distanceMeters(me, seen.pos))}, GPS position ${age} s old"
    }

    private val positionTick = object : Runnable {
        override fun run() {
            if (!running) return
            val me = myPosition
            if (me != null && System.currentTimeMillis() - myPositionAtMs < POSITION_EXPIRY_MS &&
                (connected.isNotEmpty() || internet.up)
            ) {
                val msg = RelayPacket.encode(
                    RelayPacket.TYPE_POSITION, myOrigin, RelayPacket.DEFAULT_TTL,
                    positionSeq, PositionCodec.encode(me)
                )
                positionSeq = (positionSeq + 1) and 0xFFFF
                if (connected.isNotEmpty()) client.sendPayload(connected.keys.toList(), Payload.fromBytes(msg))
                internet.send(msg)
            }
            mainHandler.postDelayed(this, POSITION_INTERVAL_MS)
        }
    }

    private val presenceTick = object : Runnable {
        override fun run() {
            if (!running) return
            if (connected.isNotEmpty() || internet.up) {
                val msg = RelayPacket.encode(
                    RelayPacket.TYPE_PRESENCE, myOrigin, RelayPacket.DEFAULT_TTL,
                    presenceSeq, displayName.toByteArray(Charsets.UTF_8)
                )
                presenceSeq = (presenceSeq + 1) and 0xFFFF
                if (connected.isNotEmpty()) client.sendPayload(connected.keys.toList(), Payload.fromBytes(msg))
                internet.send(msg)
            }
            mainHandler.postDelayed(this, PRESENCE_INTERVAL_MS)
        }
    }
    private var groupCode: String = ""
    private var displayName: String = ""

    @Volatile
    private var running = false
    private var startedAtMs = 0L
    private var firstConnectionLogged = false

    // endpointId -> name, for phones we are fully connected to
    private val connected = ConcurrentHashMap<String, String>()

    // endpointId -> remote riderId, for phones we have seen but may not be connected to
    private val discovered = ConcurrentHashMap<String, String>()
    private val pending = ConcurrentHashMap.newKeySet<String>()

    // endpointId -> display name shown in the UI
    private val names = ConcurrentHashMap<String, String>()

    /**
     * Round-trip time to the other phone in milliseconds (average of the last
     * few checks), or -1 if not measured yet. Half of it is roughly the one-way
     * delay of the phone-to-phone link, before the headset adds its own.
     */
    @Volatile var linkRttMs: Int = -1
        private set
    @Volatile var linkRttWorstMs: Int = -1
        private set
    private val rttWindow = ArrayDeque<Int>()

    /**
     * True while the link is badly backed up (a check took over 1 s). Nearby delivers
     * every packet in order, so on a bad link voice piles up and arrives seconds late.
     * While backed up we stop adding new voice so the pile can drain and the next words
     * are fresh; it resumes when a check comes back under 0.4 s.
     */
    @Volatile var congested: Boolean = false
        private set
    @Volatile var audioDropped: Int = 0
        private set

    private fun recordRtt(ms: Int) {
        offlineHealth.onRtt(ms)
        if (!congested && ms > CONGESTED_RTT_MS) {
            congested = true
            mainHandler.post { listener.onStatus("Link backed up (${ms} ms). Skipping old voice so new words are not late") }
        } else if (congested && ms < RECOVERED_RTT_MS) {
            congested = false
            mainHandler.post { listener.onStatus("Link recovered (${ms} ms)") }
        }
        synchronized(rttWindow) {
            rttWindow.addLast(ms)
            while (rttWindow.size > 10) rttWindow.removeFirst()
            linkRttMs = rttWindow.average().toInt()
            linkRttWorstMs = rttWindow.maxOrNull() ?: -1
        }
    }

    // ---- Health of each road and the one state the rider sees ----
    private val clockMs: () -> Long = { android.os.SystemClock.elapsedRealtime() }
    private var offlineHealth = PathHealth(clockMs)
    private var internetHealth = PathHealth(clockMs)
    private var stateMachine = SessionStateMachine(clockMs)

    @Volatile var sessionState: SessionState = SessionState.CONNECTING
        private set
    @Volatile var offlinePathState: PathState = PathState.DOWN
        private set
    @Volatile var internetPathState: PathState = PathState.DOWN
        private set

    private val healthTick = object : Runnable {
        override fun run() {
            if (!running) return
            offlineHealth.setLinkUp(connected.isNotEmpty())
            val netUp = internet.up
            internetHealth.setLinkUp(netUp)
            if (netUp) internetHealth.onLinkAlive()
            offlinePathState = offlineHealth.evaluate()
            internetPathState = internetHealth.evaluate()
            val before = sessionState
            sessionState = stateMachine.update(offlinePathState, internetPathState)
            if (sessionState != before) {
                listener.onStatus("${stamp()} Session: $before -> $sessionState (offline $offlinePathState, internet $internetPathState)")
            }
            mainHandler.postDelayed(this, HEALTH_INTERVAL_MS)
        }
    }

    private val pinger = object : Runnable {
        override fun run() {
            if (!running) return
            if (connected.isNotEmpty()) {
                val m = ByteArray(9)
                m[0] = TYPE_PING
                java.nio.ByteBuffer.wrap(m, 1, 8).putLong(System.nanoTime())
                client.sendPayload(connected.keys.toList(), Payload.fromBytes(m))
            }
            mainHandler.postDelayed(this, PING_INTERVAL_MS)
        }
    }

    private fun localEndpointName() = "$groupCode|$riderId|$displayName"

    private fun parse(endpointName: String): Triple<String, String, String>? {
        val parts = endpointName.split("|", limit = 3)
        if (parts.size < 3) return null
        return Triple(parts[0], parts[1], parts[2])
    }

    /** Times a connected phone dropped since Start (a rough measure of how stable the link is). */
    @Volatile var drops: Int = 0
        private set

    private var strategy: Strategy = Strategy.P2P_CLUSTER
    private var linkType: LinkType = LinkType.STEADY

    /**
     * [twoPhoneFastMode] uses Nearby's 1-to-1 mode, which Google describes as the
     * highest bandwidth, but allows only one connection. Both phones must use
     * the same mode.
     */
    fun start(
        groupCode: String,
        displayName: String,
        twoPhoneFastMode: Boolean = false,
        linkType: LinkType = LinkType.STEADY,
        relayServerUrl: String = ""
    ) {
        this.linkType = linkType
        strategy = if (twoPhoneFastMode) Strategy.P2P_POINT_TO_POINT else Strategy.P2P_CLUSTER
        drops = 0
        stop()
        riderId = UUID.randomUUID().toString().take(6)
        myOrigin = riderId.hashCode()
        router = RelayRouter(myOrigin)
        presenceSeq = 0
        reach.clear()
        listener.onStatus("Link mode: " + (if (twoPhoneFastMode) "2-phone fast (point-to-point)" else "group (cluster)") + ", link type: ${linkType.label}")
        this.groupCode = groupCode.trim().uppercase().replace("|", "")
        this.displayName = displayName.trim().replace("|", "").ifEmpty { "Rider" }
        running = true
        startedAtMs = System.currentTimeMillis()
        lastSearchRestartMs = startedAtMs
        searchRestarts = 0
        firstConnectionLogged = false
        startAdvertising()
        startDiscovery()
        mainHandler.postDelayed(watchdog, WATCHDOG_MS)
        offlineHealth = PathHealth(clockMs)
        internetHealth = PathHealth(clockMs)
        stateMachine = SessionStateMachine(clockMs)
        sessionState = SessionState.CONNECTING
        mainHandler.postDelayed(healthTick, HEALTH_INTERVAL_MS)
        mainHandler.postDelayed(pinger, PING_INTERVAL_MS)
        mainHandler.postDelayed(presenceTick, PRESENCE_INTERVAL_MS)
        mainHandler.postDelayed(positionTick, POSITION_INTERVAL_MS)
        positions.clear()
        positionSeq = 0
        synchronized(rttWindow) { rttWindow.clear() }
        linkRttMs = -1
        linkRttWorstMs = -1
        internetLastSeen.clear()
        playedViaInternet = 0
        playedViaNearby = 0
        if (relayServerUrl.isNotBlank()) {
            val udp = relayServerUrl.trim().startsWith("udp://")
            internet = if (udp) UdpLink(internetListener) else InternetLink(internetListener)
            internet.start(relayServerUrl, this.groupCode)
            listener.onStatus("Internet path: connecting to the relay server (" + (if (udp) "UDP, fastest" else "WebSocket") + ")…")
        } else {
            listener.onStatus("Internet path: off (no relay server set). Offline link only.")
        }
    }

    // ---- Internet path (second road next to the offline link) ----

    private val internetListener = object : InternetLink.Listener {
        override fun onInternetState(up: Boolean) {
            mainHandler.post {
                if (running) listener.onStatus(if (up) "Internet path: connected" else "Internet path: down, offline link carries on")
            }
        }

        override fun onInternetMessage(bytes: ByteArray) {
            mainHandler.post { if (running && bytes.size >= 4) when (bytes[0]) {
                RelayPacket.TYPE_AUDIO, RelayPacket.TYPE_PRESENCE, RelayPacket.TYPE_POSITION -> handleRelayed(INTERNET, bytes)
            } }
        }
    }

    // WebSocket by default; plain UDP when the address starts with udp:// (fastest).
    @Volatile private var internet: InternetPath = InternetLink(internetListener)

    /** Voice packets that were played from the internet copy / the offline copy (whichever arrived first). */
    @Volatile var playedViaInternet: Int = 0
        private set
    @Volatile var playedViaNearby: Int = 0
        private set

    /** True while the relay server connection is up. */
    val internetUp: Boolean get() = internet.up

    /** Call when the phone's network changes so the internet path reconnects at once. */
    fun internetReconnect() = internet.reconnectNow()

    // origin -> when we last got a packet from it over the internet
    private val internetLastSeen = ConcurrentHashMap<Int, Long>()

    private var lastSearchRestartMs = 0L
    private var searchRestarts = 0

    /** Seconds since Start, for the log, so we can see where the connect time goes. */
    private fun stamp(): String = "[%.1f s]".format((System.currentTimeMillis() - startedAtMs) / 1000.0)

    /**
     * While we have no connection:
     *  - if a rider has been found but not connected, ask again (that is quick);
     *  - if nobody has been found for a while, restart the search (discovery first,
     *    advertising every second time), but never sooner than [SEARCH_RESTART_MS].
     */
    private val watchdog = object : Runnable {
        override fun run() {
            if (!running) return
            if (connected.isEmpty() && pending.isEmpty()) {
                if (discovered.isNotEmpty()) {
                    listener.onStatus("${stamp()} Found a rider, not connected yet. Asking again…")
                    for (id in discovered.keys.toList()) requestConnectionTo(id)
                } else if (System.currentTimeMillis() - lastSearchRestartMs >= SEARCH_RESTART_MS) {
                    searchRestarts++
                    lastSearchRestartMs = System.currentTimeMillis()
                    listener.onStatus("${stamp()} Nobody found yet. Restarting the search (#$searchRestarts)…")
                    client.stopDiscovery()
                    startDiscovery()
                    if (searchRestarts % 2 == 0) {
                        client.stopAdvertising()
                        startAdvertising()
                    }
                }
            }
            mainHandler.postDelayed(this, WATCHDOG_MS)
        }
    }

    fun stop() {
        running = false
        mainHandler.removeCallbacksAndMessages(null)
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        internet.stop()
        connected.clear()
        discovered.clear()
        pending.clear()
        names.clear()
        notifyPeers()
    }

    fun connectedCount(): Int = connected.size

    /**
     * Sends one voice packet ([sequence 2 bytes][Opus data]) to everyone connected.
     * It is wrapped so that other riders can pass it on to riders we cannot reach.
     */
    fun sendAudio(packet: ByteArray) {
        if (packet.size < 3) return
        val seq = ((packet[0].toInt() and 0xFF) shl 8) or (packet[1].toInt() and 0xFF)
        val message = RelayPacket.encode(
            RelayPacket.TYPE_AUDIO, myOrigin, RelayPacket.DEFAULT_TTL, seq,
            packet.copyOfRange(2, packet.size)
        )
        if (transports.send(message, Priority.VOICE) == 0 && connected.isNotEmpty()) audioDropped++
    }

    // The roads a packet can take; voice goes on every usable one.
    private val transports = TransportManager(
        listOf(
            NearbyTransport(
                hasPeers = { connected.isNotEmpty() },
                backedUp = { congested },
                sender = { client.sendPayload(connected.keys.toList(), Payload.fromBytes(it)) }
            ),
            InternetTransport({ internet })
        )
    )

    private fun startAdvertising() {
        val builder = AdvertisingOptions.Builder().setStrategy(strategy)
        when (linkType) {
            LinkType.AUTO -> {}
            LinkType.STEADY -> builder.setConnectionType(ConnectionType.NON_DISRUPTIVE)
            LinkType.BLUETOOTH_ONLY -> builder
                .setConnectionType(ConnectionType.NON_DISRUPTIVE)
                .setLowPower(true)
        }
        val options = builder.build()
        client.startAdvertising(localEndpointName(), SERVICE_ID, lifecycleCallback, options)
            .addOnSuccessListener { listener.onStatus("Advertising as ${displayName} (group $groupCode)") }
            .addOnFailureListener { listener.onStatus("Advertising failed: ${it.message}") }
    }

    private fun startDiscovery() {
        val builder = DiscoveryOptions.Builder().setStrategy(strategy)
        if (linkType == LinkType.BLUETOOTH_ONLY) builder.setLowPower(true)
        val options = builder.build()
        client.startDiscovery(SERVICE_ID, discoveryCallback, options)
            .addOnSuccessListener { listener.onStatus("Looking for riders in group $groupCode…") }
            .addOnFailureListener { listener.onStatus("Discovery failed: ${it.message}") }
    }

    private fun requestConnectionTo(endpointId: String) {
        if (!running || connected.containsKey(endpointId) || !pending.add(endpointId)) return
        listener.onStatus("${stamp()} Asking ${names[endpointId] ?: endpointId} to connect…")
        val builder = ConnectionOptions.Builder()
        when (linkType) {
            LinkType.AUTO -> {}
            LinkType.STEADY -> builder.setConnectionType(ConnectionType.NON_DISRUPTIVE)
            LinkType.BLUETOOTH_ONLY -> builder
                .setConnectionType(ConnectionType.NON_DISRUPTIVE)
                .setLowPower(true)
        }
        client.requestConnection(localEndpointName(), endpointId, lifecycleCallback, builder.build())
            .addOnFailureListener {
                pending.remove(endpointId)
                listener.onStatus("Connection request failed: ${it.message}")
                // 8011 means that phone is no longer where we saw it. Forget it and
                // search again instead of retrying a dead address.
                if (it.message?.contains("8011") == true) {
                    discovered.remove(endpointId)
                    if (running && connected.isEmpty()) {
                        mainHandler.postDelayed({
                            if (running && connected.isEmpty() && pending.isEmpty()) {
                                client.stopDiscovery()
                                startDiscovery()
                            }
                        }, 1000L)
                    }
                }
            }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val (code, theirId, _) = parse(info.endpointName) ?: return
            if (code != groupCode || theirId == riderId) return
            if (discovered.put(endpointId, theirId) == null) {
                listener.onStatus("${stamp()} Found rider ${parse(info.endpointName)?.third ?: theirId}")
            }
            if (riderId < theirId) {
                // The phone with the smaller id starts the connection at once.
                requestConnectionTo(endpointId)
            } else {
                // The other phone gets a short head start, then we try ourselves
                // in case it never found us.
                mainHandler.postDelayed({
                    if (running && discovered.containsKey(endpointId)) requestConnectionTo(endpointId)
                }, SECOND_CHANCE_DELAY_MS)
            }
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
            listener.onStatus("${stamp()} Handshake with ${parsed.third}")
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            pending.remove(endpointId)
            if (result.status.isSuccess) {
                val name = names[endpointId] ?: endpointId
                connected[endpointId] = name
                val seconds = (System.currentTimeMillis() - startedAtMs) / 1000
                if (!firstConnectionLogged) {
                    firstConnectionLogged = true
                    listener.onStatus("Connected to rider $name after $seconds s")
                } else {
                    listener.onStatus("Connected to rider $name")
                }
                notifyPeers()
            } else {
                listener.onStatus("Could not connect (${result.status.statusMessage ?: result.status.statusCode}), trying again")
                // Try again shortly if they are still around.
                if (running && discovered.containsKey(endpointId)) {
                    mainHandler.postDelayed({ requestConnectionTo(endpointId) }, RETRY_DELAY_MS)
                }
            }
        }

        override fun onBandwidthChanged(endpointId: String, info: BandwidthInfo) {
            // Shows whether the link moved from slow Bluetooth to faster Wi-Fi.
            listener.onStatus("Link speed code for ${names[endpointId] ?: endpointId}: ${info.quality} (1 low, 2 medium, 3 high)")
        }

        override fun onDisconnected(endpointId: String) {
            val wasConnected = connected.remove(endpointId) != null
            if (wasConnected) drops++
            pending.remove(endpointId)
            notifyPeers()
            val remoteId = discovered[endpointId]
            val where = if (wasConnected && remoteId != null) " Distance then: " + distanceToText(remoteId.hashCode()) + "." else ""
            listener.onStatus("A rider went out of range. Reconnecting…$where")
            // If they are still around, try again. Discovery stays on, so if
            // they come back later it will find them again by itself.
            if (running && discovered.containsKey(endpointId)) {
                mainHandler.postDelayed({ requestConnectionTo(endpointId) }, RETRY_DELAY_MS)
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            if (bytes.size < 4) return
            offlineHealth.onPacketReceived()
            when (bytes[0]) {
                RelayPacket.TYPE_AUDIO, RelayPacket.TYPE_PRESENCE, RelayPacket.TYPE_POSITION -> handleRelayed(endpointId, bytes)
                TYPE_PING -> {
                    val pong = bytes.copyOf()
                    pong[0] = TYPE_PONG
                    client.sendPayload(endpointId, Payload.fromBytes(pong))
                }
                TYPE_PONG -> if (bytes.size >= 9) {
                    val sentAt = java.nio.ByteBuffer.wrap(bytes, 1, 8).long
                    recordRtt(((System.nanoTime() - sentAt) / 1_000_000L).toInt())
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Nothing to track for small BYTES payloads.
        }
    }

    /** Plays a message that came from a rider (maybe far away) and passes it on to the others. */
    private fun handleRelayed(fromEndpointId: String, bytes: ByteArray) {
        val p = RelayPacket.parse(bytes) ?: return
        val fromInternet = fromEndpointId == INTERNET
        if (fromInternet) internetLastSeen[p.origin] = System.currentTimeMillis()
        val decision = router.accept(p.type, p.origin, p.seq, p.ttl)
        if (decision.forward) {
            val passOn = RelayPacket.withTtl(bytes, p.ttl - 1)
            val others = connected.keys.filter { it != fromEndpointId }
            if (others.isNotEmpty()) client.sendPayload(others, Payload.fromBytes(passOn))
            // Act as a bridge: a rider with no internet reaches the internet through us.
            // Skip it when that rider is already on the internet themselves.
            if (!fromInternet && internet.up) {
                val seenOnInternet = internetLastSeen[p.origin]
                if (seenOnInternet == null || System.currentTimeMillis() - seenOnInternet > INTERNET_SEEN_MS) {
                    internet.send(passOn)
                }
            }
        }
        if (!decision.deliver) return
        if (p.type == RelayPacket.TYPE_AUDIO) {
            if (fromInternet) playedViaInternet++ else playedViaNearby++
            listener.onAudioFrame(originKey(p.origin), bytes.copyOfRange(RelayPacket.SEQ_OFFSET, bytes.size))
        } else if (p.type == RelayPacket.TYPE_POSITION) {
            PositionCodec.decode(bytes, RelayPacket.HEADER_SIZE)?.let {
                positions[p.origin] = SeenPosition(it, System.currentTimeMillis())
            }
        } else {
            val name = String(bytes, RelayPacket.HEADER_SIZE, bytes.size - RelayPacket.HEADER_SIZE, Charsets.UTF_8)
            reach[p.origin] = ReachEntry(name, RelayPacket.hopsTravelled(p.ttl), System.currentTimeMillis())
        }
    }

    private fun notifyPeers() {
        val list = connected.map { Peer(it.key, it.value) }
        mainHandler.post { listener.onPeersChanged(list) }
    }
}
