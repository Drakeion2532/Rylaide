package com.rylaide.app.mesh

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList

class UdpLinkTest {
    private val server = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
    private val got = CopyOnWriteArrayList<ByteArray>()
    @Volatile private var clientAddr: DatagramPacket? = null
    @Volatile private var answer = true

    init {
        Thread({
            val buf = ByteArray(2048)
            while (!server.isClosed) {
                val p = DatagramPacket(buf, buf.size)
                try { server.receive(p) } catch (_: Throwable) { return@Thread }
                val copy = buf.copyOf(p.length)
                got.add(copy)
                clientAddr = DatagramPacket(ByteArray(0), 0, p.address, p.port)
                if (copy[0] == UdpLink.TYPE_JOIN && answer) {
                    server.send(DatagramPacket(byteArrayOf(UdpLink.TYPE_ACK), 1, p.address, p.port))
                }
            }
        }, "fake-udp-server").apply { isDaemon = true }.start()
    }

    @After fun tearDown() { server.close() }

    private class Recorder : InternetPath.Listener {
        val states = CopyOnWriteArrayList<Boolean>()
        val messages = CopyOnWriteArrayList<ByteArray>()
        override fun onInternetState(up: Boolean) { states.add(up) }
        override fun onInternetMessage(bytes: ByteArray) { messages.add(bytes) }
    }

    private fun waitFor(ms: Long = 5000, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (cond()) return true; Thread.sleep(20) }
        return cond()
    }

    @Test fun joinsTheRoomComesUpAndExchangesVoiceBothWays() {
        val rec = Recorder()
        val link = UdpLink(rec)
        link.start("udp://127.0.0.1:${server.localPort}", "TEST")
        assertTrue("never came up", waitFor { link.up })
        val join = got.first()
        assertEquals(UdpLink.TYPE_JOIN, join[0])
        assertEquals("TEST", String(join, 2, join[1].toInt()))

        assertTrue(link.send(byteArrayOf(4, 9, 8, 7)))
        assertTrue(waitFor { got.any { it[0] == UdpLink.TYPE_DATA } })
        val data = got.first { it[0] == UdpLink.TYPE_DATA }
        assertEquals(listOf<Byte>(4, 9, 8, 7), data.drop(2 + data[1].toInt()))

        val to = clientAddr!!
        server.send(DatagramPacket(byteArrayOf(UdpLink.TYPE_DATA, 5, 1, 2), 4, to.address, to.port))
        assertTrue(waitFor { rec.messages.size == 1 })
        assertEquals(listOf<Byte>(5, 1, 2), rec.messages[0].toList())
        link.stop()
        assertFalse(link.up)
    }

    @Test fun staysDownAndRefusesToSendWhenTheServerNeverAnswers() {
        answer = false
        val link = UdpLink(Recorder())
        link.start("udp://127.0.0.1:${server.localPort}", "TEST")
        Thread.sleep(600)
        assertFalse(link.up)
        assertFalse(link.send(byteArrayOf(1, 2, 3)))
        link.stop()
    }

    @Test fun badAddressDoesNothing() {
        val link = UdpLink(Recorder())
        link.start("udp://nonsense", "TEST")
        assertFalse(link.up)
        assertFalse(link.send(byteArrayOf(1)))
    }
}
