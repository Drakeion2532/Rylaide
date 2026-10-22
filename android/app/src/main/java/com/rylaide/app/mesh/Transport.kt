package com.rylaide.app.mesh

/** One road a packet can travel on: the offline link, UDP relay, or WebSocket relay. */
interface Transport {
    val name: String

    /** The road is connected. */
    val isUp: Boolean

    /** The road can take a new voice packet right now (false while it is backed up). */
    val acceptingVoice: Boolean get() = isUp

    /** Sends one packet. Returns true if it was handed to the road. */
    fun send(bytes: ByteArray): Boolean
}

/** Adapter for the internet paths ([InternetLink], [UdpLink]). */
class InternetTransport(private val path: () -> InternetPath, override val name: String = "internet") : Transport {
    override val isUp: Boolean get() = path().up
    override fun send(bytes: ByteArray): Boolean = path().send(bytes)
}

/** Adapter for the Nearby offline link. The manager passes in what it already knows. */
class NearbyTransport(
    private val hasPeers: () -> Boolean,
    private val backedUp: () -> Boolean,
    private val sender: (ByteArray) -> Unit,
    override val name: String = "offline"
) : Transport {
    override val isUp: Boolean get() = hasPeers()
    override val acceptingVoice: Boolean get() = hasPeers() && !backedUp()
    override fun send(bytes: ByteArray): Boolean {
        if (!hasPeers()) return false
        sender(bytes)
        return true
    }
}

enum class Priority { VOICE, OTHER }

/**
 * Sends a packet on every usable road. The same packet on two roads is fine because
 * [RelayRouter] drops the second copy, so changing roads never needs a hand-over.
 */
class TransportManager(private val transports: List<Transport>) {
    /** Returns how many roads took the packet (0 means nobody could carry it). */
    fun send(bytes: ByteArray, priority: Priority = Priority.VOICE): Int {
        var sent = 0
        for (t in transports) {
            val ok = if (priority == Priority.VOICE) t.acceptingVoice else t.isUp
            if (ok && t.send(bytes)) sent++
        }
        return sent
    }

    fun anyUp(): Boolean = transports.any { it.isUp }
}
