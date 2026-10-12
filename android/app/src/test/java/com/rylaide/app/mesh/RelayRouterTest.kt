package com.rylaide.app.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayRouterTest {

    private val audio = RelayPacket.TYPE_AUDIO
    private val presence = RelayPacket.TYPE_PRESENCE

    @Test
    fun newMessageIsPlayedAndPassedOn() {
        val r = RelayRouter(myOrigin = 1)
        val d = r.accept(audio, origin = 2, seq = 0, ttl = 4)
        assertTrue(d.deliver)
        assertTrue(d.forward)
    }

    @Test
    fun secondCopyFromAnotherPathIsDropped() {
        val r = RelayRouter(1)
        assertTrue(r.accept(audio, 2, 10, 4).deliver)
        val again = r.accept(audio, 2, 10, 3)
        assertFalse(again.deliver)
        assertFalse(again.forward)
    }

    @Test
    fun ownMessagesComingBackAreDropped() {
        val r = RelayRouter(1)
        val d = r.accept(audio, origin = 1, seq = 5, ttl = 3)
        assertFalse(d.deliver)
        assertFalse(d.forward)
    }

    @Test
    fun lastHopIsPlayedButNotPassedOn() {
        val r = RelayRouter(1)
        val d = r.accept(audio, 2, 0, ttl = 1)
        assertTrue(d.deliver)
        assertFalse(d.forward)
        assertTrue(r.accept(audio, 2, 1, ttl = 2).forward)
    }

    @Test
    fun sequenceNumbersWrappingAroundAreNotTakenForDuplicates() {
        val r = RelayRouter(1)
        val seqs = (65530..65535).toList() + (0..10).toList()
        for (s in seqs) assertTrue("seq $s should be new", r.accept(audio, 2, s, 4).deliver)
        for (s in seqs) assertFalse("seq $s should now be a duplicate", r.accept(audio, 2, s, 4).deliver)
    }

    @Test
    fun lateButNeverSeenPacketIsStillPlayed() {
        val r = RelayRouter(1)
        assertTrue(r.accept(audio, 2, 10, 4).deliver)
        assertTrue(r.accept(audio, 2, 12, 4).deliver)
        assertTrue("11 arrived late but is new", r.accept(audio, 2, 11, 4).deliver)
        assertFalse(r.accept(audio, 2, 11, 4).deliver)
    }

    @Test
    fun veryOldPacketsAreDropped() {
        val r = RelayRouter(1, window = 128)
        assertTrue(r.accept(audio, 2, 500, 4).deliver)
        assertFalse(r.accept(audio, 2, 100, 4).deliver)
    }

    @Test
    fun bigJumpForwardClearsTheMemory() {
        val r = RelayRouter(1, window = 128)
        for (s in 0..50) r.accept(audio, 2, s, 4)
        assertTrue(r.accept(audio, 2, 5000, 4).deliver)
        assertTrue(r.accept(audio, 2, 5001, 4).deliver)
        assertFalse(r.accept(audio, 2, 5000, 4).deliver)
    }

    @Test
    fun differentRidersAndMessageTypesKeepSeparateCounters() {
        val r = RelayRouter(1)
        assertTrue(r.accept(audio, 2, 5, 4).deliver)
        assertTrue(r.accept(audio, 3, 5, 4).deliver)
        assertTrue(r.accept(presence, 2, 5, 4).deliver)
        assertFalse(r.accept(audio, 2, 5, 4).deliver)
    }

    @Test
    fun restartedRiderWithNewIdIsNotMistakenForOld() {
        val r = RelayRouter(1)
        for (s in 0..300) r.accept(audio, origin = 100, seq = s, ttl = 4)
        // The rider tapped Stop and Start: new origin, counter back at 0.
        assertTrue(r.accept(audio, origin = 101, seq = 0, ttl = 4).deliver)
    }

    @Test
    fun manyPacketsInOrderAreAllPlayedExactlyOnce() {
        val r = RelayRouter(1)
        var played = 0
        for (s in 0 until 5000) {
            val seq = s and 0xFFFF
            if (r.accept(audio, 2, seq, 4).deliver) played++
        }
        assertEquals(5000, played)
    }
}
