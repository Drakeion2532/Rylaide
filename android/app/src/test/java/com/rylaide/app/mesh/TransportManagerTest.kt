package com.rylaide.app.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportManagerTest {
    private class Fake(override val name: String, var up: Boolean = true, var accepting: Boolean = true) : Transport {
        val sent = mutableListOf<ByteArray>()
        override val isUp get() = up
        override val acceptingVoice get() = up && accepting
        override fun send(bytes: ByteArray): Boolean { sent += bytes; return true }
    }

    private val msg = byteArrayOf(1, 2, 3)

    @Test fun sendsOnEveryUsableRoad() {
        val a = Fake("offline"); val b = Fake("internet")
        assertEquals(2, TransportManager(listOf(a, b)).send(msg))
        assertEquals(1, a.sent.size); assertEquals(1, b.sent.size)
    }

    @Test fun skipsDownRoad() {
        val a = Fake("offline", up = false); val b = Fake("internet")
        assertEquals(1, TransportManager(listOf(a, b)).send(msg))
        assertTrue(a.sent.isEmpty())
    }

    @Test fun backedUpRoadSkipsVoiceButStillGetsOtherMessages() {
        val a = Fake("offline", accepting = false); val b = Fake("internet")
        val m = TransportManager(listOf(a, b))
        assertEquals(1, m.send(msg, Priority.VOICE))
        assertEquals(2, m.send(msg, Priority.OTHER))
        assertEquals(1, a.sent.size)
    }

    @Test fun nobodyUpSendsZero() {
        val m = TransportManager(listOf(Fake("x", up = false), Fake("y", up = false)))
        assertEquals(0, m.send(msg)); assertFalse(m.anyUp())
    }

    @Test fun nearbyAdapterFollowsPeersAndBackup() {
        var peers = true; var backed = false; val out = mutableListOf<ByteArray>()
        val n = NearbyTransport({ peers }, { backed }, { out += it })
        assertTrue(n.acceptingVoice); n.send(msg); assertEquals(1, out.size)
        backed = true; assertFalse(n.acceptingVoice); assertTrue(n.isUp)
        peers = false; assertFalse(n.isUp); assertFalse(n.send(msg))
    }
}
