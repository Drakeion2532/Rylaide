package com.rylaide.app.mesh

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * The fastest internet path: plain UDP to a small relay (see /server/udp-relay).
 * No connection set-up, no waiting for lost packets (a lost voice packet is simply
 * skipped, which is what a live call wants), and no TCP queue building up.
 *
 * Datagrams, all starting with a type byte:
 *  - 1 [room length][room]      join / keep-alive, sent every 3 s (also keeps the phone's NAT open)
 *  - 2 [room length][room][..]  voice or other data for the other riders in the room
 *  - 3                          from the server: "I heard you" (so we know the path is up)
 *  - 2 [..]                     from the server: data from another rider
 *
 * The path counts as up while the server has answered within the last 9 s.
 * Address: udp://host:port
 */
class UdpLink(private val listener: InternetPath.Listener) : InternetPath {

    @Volatile override var up: Boolean = false
        private set

    private val lock = Any()
    private var socket: DatagramSocket? = null
    private var server: InetSocketAddress? = null
    private var header: ByteArray = ByteArray(0)
    private var wanted = false
    private var lastAckMs = 0L
    private var host = ""
    private var port = 0
    private var room = ""
    private val timer = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "rylaide-udp-timer").apply { isDaemon = true }
    }
    private var tick: ScheduledFuture<*>? = null

    override fun start(serverUrl: String, room: String) {
        val u = serverUrl.trim().removePrefix("udp://").trimEnd('/')
        val colon = u.lastIndexOf(':')
        if (colon <= 0) return
        val p = u.substring(colon + 1).toIntOrNull() ?: return
        synchronized(lock) {
            this.host = u.substring(0, colon)
            this.port = p
            this.room = room.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(32)
            if (this.room.isEmpty()) return
            wanted = true
        }
        open()
    }

    override fun stop() {
        synchronized(lock) {
            wanted = false
            tick?.cancel(false)
            tick = null
            socket?.close()
            socket = null
        }
        setUp(false)
    }

    /** The phone changed network: its old socket is useless, so open a fresh one and re-resolve the server. */
    override fun reconnectNow() {
        synchronized(lock) { if (!wanted) return }
        open()
    }

    override fun send(bytes: ByteArray): Boolean {
        val s: DatagramSocket
        val to: InetSocketAddress
        val head: ByteArray
        synchronized(lock) {
            s = socket ?: return false
            to = server ?: return false
            head = header
        }
        if (!up) return false
        val out = ByteArray(1 + head.size + bytes.size)
        out[0] = TYPE_DATA
        System.arraycopy(head, 0, out, 1, head.size)
        System.arraycopy(bytes, 0, out, 1 + head.size, bytes.size)
        return try {
            s.send(DatagramPacket(out, out.size, to)); true
        } catch (_: Throwable) { false }
    }

    private fun open() {
        // Resolving the name can take a moment, so never do it on the caller's thread.
        Thread({
            val h: String
            val p: Int
            val r: String
            synchronized(lock) { if (!wanted) return@Thread; h = host; p = port; r = room }
            val addr = try { InetSocketAddress(InetAddress.getByName(h), p) } catch (_: Throwable) { null }
            synchronized(lock) {
                if (!wanted) return@Thread
                socket?.close()
                tick?.cancel(false)
                if (addr == null) {
                    socket = null
                    server = null
                    scheduleRetry()
                    return@Thread
                }
                val s = try { DatagramSocket() } catch (_: Throwable) { null }
                if (s == null) { scheduleRetry(); return@Thread }
                s.soTimeout = 0
                socket = s
                server = addr
                val rb = r.toByteArray(Charsets.UTF_8)
                header = byteArrayOf(rb.size.toByte()) + rb
                lastAckMs = 0
                Thread({ receiveLoop(s) }, "rylaide-udp-recv").apply { isDaemon = true }.start()
                tick = timer.scheduleAtFixedRate({ keepAlive() }, 0, KEEPALIVE_MS, TimeUnit.MILLISECONDS)
            }
        }, "rylaide-udp-open").apply { isDaemon = true }.start()
    }

    private fun scheduleRetry() {
        try { timer.schedule({ open() }, 2000, TimeUnit.MILLISECONDS) } catch (_: Throwable) { }
    }

    private fun keepAlive() {
        val s: DatagramSocket
        val to: InetSocketAddress
        val head: ByteArray
        synchronized(lock) {
            if (!wanted) return
            s = socket ?: return
            to = server ?: return
            head = header
        }
        val out = ByteArray(1 + head.size)
        out[0] = TYPE_JOIN
        System.arraycopy(head, 0, out, 1, head.size)
        try { s.send(DatagramPacket(out, out.size, to)) } catch (_: Throwable) { }
        if (up && System.currentTimeMillis() - lastAckMs > UP_TIMEOUT_MS) {
            setUp(false)
            // Nothing heard for a while: the network probably changed under us. Start over.
            reconnectNow()
        }
    }

    private fun receiveLoop(s: DatagramSocket) {
        val buf = ByteArray(2048)
        while (true) {
            val pkt = DatagramPacket(buf, buf.size)
            try { s.receive(pkt) } catch (_: Throwable) { return }
            synchronized(lock) { if (s !== socket) return }
            if (pkt.length < 1) continue
            when (buf[0]) {
                TYPE_ACK -> { lastAckMs = System.currentTimeMillis(); setUp(true) }
                TYPE_DATA -> if (pkt.length > 1) {
                    lastAckMs = System.currentTimeMillis()
                    listener.onInternetMessage(buf.copyOfRange(1, pkt.length))
                }
            }
        }
    }

    private fun setUp(value: Boolean) {
        if (up == value) return
        up = value
        listener.onInternetState(value)
    }

    companion object {
        const val TYPE_JOIN: Byte = 1
        const val TYPE_DATA: Byte = 2
        const val TYPE_ACK: Byte = 3
        const val KEEPALIVE_MS = 3000L
        const val UP_TIMEOUT_MS = 9000L
    }
}
