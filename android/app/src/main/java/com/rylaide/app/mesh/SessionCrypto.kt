package com.rylaide.app.mesh

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** HKDF-SHA256 (RFC 5869): turns the ride secret into separate keys. */
object Hkdf {
    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

    fun derive(secret: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..32)
        val prk = hmac(if (salt.isEmpty()) ByteArray(32) else salt, secret)
        return hmac(prk, info + byteArrayOf(1)).copyOf(length)
    }
}

/**
 * Encrypts and checks every packet body with the ride secret (AES-256-GCM).
 *
 * Wire body: [sender session id 4 B][counter 4 B][ciphertext + 16 B tag].
 * The nonce is origin + session id + counter, so it never repeats: the session id is random
 * per start, so a rider who restarts cannot reuse an old counter. The type and the origin
 * are authenticated but sent in the clear (the router needs them). The TTL is NOT
 * authenticated, because relaying riders must lower it.
 *
 * Replay: each (origin, session id) keeps a 64-packet window; repeats and very old packets are refused.
 *
 * Not protected: someone holding the ride secret can join; traffic volume and who is
 * connected stay visible to the relay.
 */
class SessionCrypto(rideSecret: ByteArray, private val random: SecureRandom = SecureRandom()) {
    companion object {
        const val OVERHEAD = 4 + 4 + 16
        private const val WINDOW = 64
        private val SALT = "rylaide-ride-v1".toByteArray()
    }

    private val key = SecretKeySpec(Hkdf.derive(rideSecret, SALT, "aead".toByteArray(), 32), "AES")
    private val sessionId = random.nextInt()
    private var counter = 0

    private class Window { var highest = -1L; var bits = 0L }
    private val windows = HashMap<Long, Window>()

    private fun nonce(origin: Int, sid: Int, ctr: Int): ByteArray =
        ByteBuffer.allocate(12).putInt(origin).putInt(sid).putInt(ctr).array()

    private fun aad(type: Byte, origin: Int): ByteArray =
        ByteBuffer.allocate(5).put(type).putInt(origin).array()

    @Synchronized
    fun seal(type: Byte, origin: Int, plaintext: ByteArray): ByteArray {
        val ctr = counter++
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce(origin, sessionId, ctr)))
        c.updateAAD(aad(type, origin))
        val ct = c.doFinal(plaintext)
        return ByteBuffer.allocate(8 + ct.size).putInt(sessionId).putInt(ctr).put(ct).array()
    }

    /** Returns the plaintext, or null if the packet is forged, damaged, from another ride, or a repeat. */
    @Synchronized
    fun open(type: Byte, origin: Int, body: ByteArray): ByteArray? {
        if (body.size < OVERHEAD) return null
        val bb = ByteBuffer.wrap(body)
        val sid = bb.int
        val ctr = bb.int
        val plain = try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce(origin, sid, ctr)))
            c.updateAAD(aad(type, origin))
            c.doFinal(body, 8, body.size - 8)
        } catch (_: Exception) {
            return null
        }
        return if (acceptCounter(origin, sid, ctr.toLong() and 0xFFFFFFFFL)) plain else null
    }

    private fun acceptCounter(origin: Int, sid: Int, ctr: Long): Boolean {
        val id = (origin.toLong() shl 32) xor (sid.toLong() and 0xFFFFFFFFL)
        val w = windows.getOrPut(id) { Window() }
        if (w.highest < 0) { w.highest = ctr; w.bits = 1L; return true }
        if (ctr > w.highest) {
            val shift = ctr - w.highest
            w.bits = if (shift >= WINDOW) 1L else (w.bits shl shift.toInt()) or 1L
            w.highest = ctr
            return true
        }
        val back = w.highest - ctr
        if (back >= WINDOW) return false
        val mask = 1L shl back.toInt()
        if (w.bits and mask != 0L) return false
        w.bits = w.bits or mask
        return true
    }
}
