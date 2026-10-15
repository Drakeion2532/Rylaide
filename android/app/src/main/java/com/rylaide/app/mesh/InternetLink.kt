package com.rylaide.app.mesh

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The internet path for voice. It is a second road next to the offline phone link,
 * not a replacement: the same voice packets go down both, and the receiver plays
 * whichever copy arrives first (the relay router already throws away duplicates).
 *
 * It connects to a small relay server (see /server in the repo) over a WebSocket,
 * into a "room" named after the group code. Anything a rider sends into the room
 * goes to every other rider in it. It reconnects by itself, quickly after the phone
 * changes network (Wi-Fi to mobile data and back), and never throws: if the internet
 * is gone the offline link simply carries on alone.
 */
class InternetLink(
    private val listener: InternetPath.Listener,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()
) : InternetPath {
    interface Listener : InternetPath.Listener

    @Volatile override var up: Boolean = false
        private set

    private val lock = Any()
    private var url: String? = null
    private var socket: WebSocket? = null
    private var wanted = false
    private var backoffMs = FIRST_RETRY_MS
    private val timer = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "rylaide-internet").apply { isDaemon = true }
    }

    /** [serverUrl] like wss://relay.example.workers.dev, [room] the group code. */
    override fun start(serverUrl: String, room: String) {
        val base = serverUrl.trim().trimEnd('/')
        if (base.isEmpty()) return
        synchronized(lock) {
            url = "$base/room/${room.filter { it.isLetterOrDigit() || it == '-' || it == '_' }}"
            wanted = true
            backoffMs = FIRST_RETRY_MS
        }
        open()
    }

    override fun stop() {
        synchronized(lock) {
            wanted = false
            socket?.cancel()
            socket = null
        }
        setUp(false)
    }

    /** Call when the phone's network changed: skip the wait and reconnect now. */
    override fun reconnectNow() {
        synchronized(lock) {
            if (!wanted) return
            backoffMs = FIRST_RETRY_MS
        }
        open()
    }

    /** Sends a packet. False if the internet path is down (the offline path is unaffected). */
    override fun send(bytes: ByteArray): Boolean {
        val s = synchronized(lock) { socket } ?: return false
        if (!up) return false
        return s.send(bytes.toByteString())
    }

    private fun open() {
        val target: String
        synchronized(lock) {
            if (!wanted) return
            target = url ?: return
            socket?.cancel()
        }
        val request = try {
            Request.Builder().url(target).build()
        } catch (t: IllegalArgumentException) {
            return
        }
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(lock) {
                    if (webSocket !== socket) return
                    backoffMs = FIRST_RETRY_MS
                }
                setUp(true)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                listener.onInternetMessage(bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                // The server hung up. Answer the close, then reconnect.
                try { webSocket.close(1000, null) } catch (_: Throwable) { }
                lost(webSocket)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = lost(webSocket)

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = lost(webSocket)
        })
        synchronized(lock) { socket = ws }
    }

    private fun lost(ws: WebSocket) {
        val delay: Long
        synchronized(lock) {
            if (ws !== socket) return
            socket = null
            if (!wanted) return
            delay = backoffMs
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_RETRY_MS)
        }
        setUp(false)
        try {
            timer.schedule({ open() }, delay, TimeUnit.MILLISECONDS)
        } catch (_: Throwable) {
            // Timer shut down: we are stopping.
        }
    }

    private fun setUp(value: Boolean) {
        if (up == value) return
        up = value
        listener.onInternetState(value)
    }

    companion object {
        const val FIRST_RETRY_MS = 500L
        const val MAX_RETRY_MS = 5000L
    }
}
