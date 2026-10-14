package com.rylaide.app.mesh

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class InternetLinkTest {
    private lateinit var server: MockWebServer
    private val serverSockets = CopyOnWriteArrayList<WebSocket>()
    private val serverGot = CopyOnWriteArrayList<ByteArray>()
    private val paths = CopyOnWriteArrayList<String>()

    private val echoListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) { serverSockets.add(webSocket) }
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) { serverGot.add(bytes.toByteArray()) }
    }

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { try { server.shutdown() } catch (_: Throwable) {} }

    private fun waitFor(ms: Long = 5000, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (cond()) return true; Thread.sleep(20) }
        return cond()
    }

    private class Recorder : InternetLink.Listener {
        val states = CopyOnWriteArrayList<Boolean>()
        val messages = CopyOnWriteArrayList<ByteArray>()
        override fun onInternetState(up: Boolean) { states.add(up) }
        override fun onInternetMessage(bytes: ByteArray) { messages.add(bytes) }
    }

    @Test fun sendsAndReceivesVoicePacketsInTheGroupRoom() {
        server.enqueue(MockResponse().withWebSocketUpgrade(echoListener))
        val rec = Recorder()
        val link = InternetLink(rec)
        link.start(server.url("/").toString().replace("http", "ws"), "TEST")
        assertTrue("never came up", waitFor { link.up })
        assertEquals("/room/TEST", server.takeRequest(2, TimeUnit.SECONDS)!!.path)

        assertTrue(link.send(byteArrayOf(4, 9, 8, 7)))
        assertTrue(waitFor { serverGot.size == 1 })
        assertEquals(listOf<Byte>(4, 9, 8, 7), serverGot[0].toList())

        serverSockets[0].send(byteArrayOf(5, 1, 2).toByteString())
        assertTrue(waitFor { rec.messages.size == 1 })
        assertEquals(listOf<Byte>(5, 1, 2), rec.messages[0].toList())
        link.stop()
        assertFalse(link.up)
    }

    @Test fun reconnectsByItselfAfterTheServerDropsTheConnection() {
        server.enqueue(MockResponse().withWebSocketUpgrade(echoListener))
        server.enqueue(MockResponse().withWebSocketUpgrade(echoListener))
        val rec = Recorder()
        val link = InternetLink(rec)
        link.start(server.url("/").toString().replace("http", "ws"), "TEST")
        assertTrue(waitFor { link.up })
        serverSockets[0].close(1001, "going away")
        assertTrue("did not come back", waitFor(8000) { serverSockets.size == 2 && link.up })
        assertTrue(rec.states.containsAll(listOf(true, false)))
        link.stop()
    }

    @Test fun sendReturnsFalseWhenTheInternetIsDown() {
        val link = InternetLink(Recorder())
        assertFalse(link.send(byteArrayOf(1, 2, 3)))
        link.start("", "TEST")
        assertFalse(link.up)
    }
}
