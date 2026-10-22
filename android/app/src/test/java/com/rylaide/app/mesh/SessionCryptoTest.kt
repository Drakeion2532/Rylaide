package com.rylaide.app.mesh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SessionCryptoTest {
    private val secret = ByteArray(32) { it.toByte() }
    private val audio: Byte = 4
    private val origin = 0x1234ABCD
    private val voice = ByteArray(40) { (it * 3).toByte() }

    @Test fun roundTrip() {
        val a = SessionCrypto(secret); val b = SessionCrypto(secret)
        val sealed = a.seal(audio, origin, voice)
        assertEquals(voice.size + SessionCrypto.OVERHEAD, sealed.size)
        assertArrayEquals(voice, b.open(audio, origin, sealed))
    }

    @Test fun wrongSecretIsRefused() {
        val sealed = SessionCrypto(secret).seal(audio, origin, voice)
        assertNull(SessionCrypto(ByteArray(32) { 9 }).open(audio, origin, sealed))
    }

    @Test fun anyChangedByteIsRefused() {
        val a = SessionCrypto(secret)
        val sealed = a.seal(audio, origin, voice)
        for (i in sealed.indices) {
            val bad = sealed.copyOf(); bad[i] = (bad[i].toInt() xor 1).toByte()
            assertNull("byte $i", SessionCrypto(secret).open(audio, origin, bad))
        }
    }

    @Test fun typeAndOriginAreAuthenticated() {
        val sealed = SessionCrypto(secret).seal(audio, origin, voice)
        assertNull(SessionCrypto(secret).open(5, origin, sealed))
        assertNull(SessionCrypto(secret).open(audio, origin + 1, sealed))
    }

    @Test fun replayIsRefused() {
        val a = SessionCrypto(secret); val b = SessionCrypto(secret)
        val sealed = a.seal(audio, origin, voice)
        assertNotNull(b.open(audio, origin, sealed))
        assertNull(b.open(audio, origin, sealed))
    }

    @Test fun outOfOrderInsideWindowIsAcceptedOnce() {
        val a = SessionCrypto(secret); val b = SessionCrypto(secret)
        val p = List(10) { a.seal(audio, origin, voice) }
        assertNotNull(b.open(audio, origin, p[9]))
        assertNotNull(b.open(audio, origin, p[3]))
        assertNull(b.open(audio, origin, p[3]))
        assertNotNull(b.open(audio, origin, p[0]))
    }

    @Test fun veryOldPacketIsRefused() {
        val a = SessionCrypto(secret); val b = SessionCrypto(secret)
        val first = a.seal(audio, origin, voice)
        repeat(100) { a.seal(audio, origin, voice) }
        val late = a.seal(audio, origin, voice)
        assertNotNull(b.open(audio, origin, late))
        assertNull(b.open(audio, origin, first))
    }

    @Test fun restartedRiderIsAcceptedBecauseSessionIdDiffers() {
        val b = SessionCrypto(secret)
        val first = SessionCrypto(secret).seal(audio, origin, voice)
        val afterRestart = SessionCrypto(secret).seal(audio, origin, voice)
        assertNotNull(b.open(audio, origin, first))
        assertNotNull(b.open(audio, origin, afterRestart))
    }

    @Test fun samePlaintextGivesDifferentCiphertext() {
        val a = SessionCrypto(secret)
        val x = a.seal(audio, origin, voice); val y = a.seal(audio, origin, voice)
        assert(!x.contentEquals(y))
    }

    @Test fun hkdfMatchesRfc5869Case1() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = ByteArray(13) { it.toByte() }
        val info = ByteArray(10) { (0xf0 + it).toByte() }
        val okm = Hkdf.derive(ikm, salt, info, 32)
        val expected = "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf"
        assertEquals(expected, okm.joinToString("") { "%02x".format(it) })
    }
}
