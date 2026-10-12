package com.rylaide.app.mesh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RelayPacketTest {

    @Test
    fun roundTripKeepsEveryField() {
        val body = byteArrayOf(9, 8, 7, 6)
        val bytes = RelayPacket.encode(RelayPacket.TYPE_AUDIO, 0xDEADBEEF.toInt(), 3, 65535, body)
        val p = RelayPacket.parse(bytes)!!
        assertEquals(RelayPacket.TYPE_AUDIO, p.type)
        assertEquals(0xDEADBEEF.toInt(), p.origin)
        assertEquals(3, p.ttl)
        assertEquals(65535, p.seq)
        assertArrayEquals(body, bytes.copyOfRange(RelayPacket.HEADER_SIZE, bytes.size))
    }

    @Test
    fun fromSeqOffsetOnIsWhatTheAudioEngineExpects() {
        val opus = byteArrayOf(1, 2, 3, 4, 5)
        val bytes = RelayPacket.encode(RelayPacket.TYPE_AUDIO, 42, 4, 0x1234, opus)
        val forEngine = bytes.copyOfRange(RelayPacket.SEQ_OFFSET, bytes.size)
        assertArrayEquals(byteArrayOf(0x12, 0x34, 1, 2, 3, 4, 5), forEngine)
    }

    @Test
    fun rejectsShortAndUnknownMessages() {
        assertNull(RelayPacket.parse(ByteArray(0)))
        assertNull(RelayPacket.parse(ByteArray(RelayPacket.HEADER_SIZE - 1) { RelayPacket.TYPE_AUDIO }))
        val unknown = RelayPacket.encode(RelayPacket.TYPE_AUDIO, 1, 1, 1, ByteArray(0))
        unknown[0] = 99
        assertNull(RelayPacket.parse(unknown))
        assertNotNull(RelayPacket.parse(RelayPacket.encode(RelayPacket.TYPE_PRESENCE, 1, 1, 1, ByteArray(0))))
    }

    @Test
    fun withTtlChangesOnlyTheHopCount() {
        val original = RelayPacket.encode(RelayPacket.TYPE_AUDIO, 77, 4, 500, byteArrayOf(1, 2, 3))
        val passedOn = RelayPacket.withTtl(original, 3)
        assertEquals(4, RelayPacket.parse(original)!!.ttl) // the original is untouched
        val p = RelayPacket.parse(passedOn)!!
        assertEquals(3, p.ttl)
        assertEquals(77, p.origin)
        assertEquals(500, p.seq)
        for (i in original.indices) if (i != 5) assertEquals(original[i], passedOn[i])
    }

    @Test
    fun hopsTravelledCountsFromOne() {
        assertEquals(1, RelayPacket.hopsTravelled(RelayPacket.DEFAULT_TTL))
        assertEquals(2, RelayPacket.hopsTravelled(RelayPacket.DEFAULT_TTL - 1))
        assertEquals(RelayPacket.DEFAULT_TTL, RelayPacket.hopsTravelled(1))
    }
}
