package com.rylaide.app.mesh

/**
 * The wire format for messages that can be passed on from rider to rider.
 *
 * ```
 * byte 0      type (AUDIO, PRESENCE or POSITION)
 * bytes 1-4   origin: who first made this message (a number picked per Start)
 * byte 5      hops left: starts at DEFAULT_TTL, each phone that passes it on lowers it
 * bytes 6-7   sequence number, counts up per origin and per type
 * bytes 8..   body (Opus voice, or the rider's name for presence)
 * ```
 *
 * Bytes 6 onwards are exactly what the audio engine already understands
 * ([sequence 2 bytes][Opus data]), so the receiver can hand them over unchanged.
 */
object RelayPacket {
    const val TYPE_AUDIO: Byte = 4
    const val TYPE_PRESENCE: Byte = 5
    const val TYPE_POSITION: Byte = 6

    /** How many phones a message may pass through. 4 covers a line of five riders. */
    const val DEFAULT_TTL = 4
    const val HEADER_SIZE = 8
    const val SEQ_OFFSET = 6

    private const val TTL_OFFSET = 5

    class Parsed(val type: Byte, val origin: Int, val ttl: Int, val seq: Int)

    fun encode(type: Byte, origin: Int, ttl: Int, seq: Int, body: ByteArray): ByteArray {
        val out = ByteArray(HEADER_SIZE + body.size)
        out[0] = type
        out[1] = (origin ushr 24).toByte()
        out[2] = (origin ushr 16).toByte()
        out[3] = (origin ushr 8).toByte()
        out[4] = origin.toByte()
        out[TTL_OFFSET] = ttl.coerceIn(0, 255).toByte()
        out[6] = (seq shr 8).toByte()
        out[7] = seq.toByte()
        System.arraycopy(body, 0, out, HEADER_SIZE, body.size)
        return out
    }

    /** Returns null for anything too short or of another type. */
    fun parse(bytes: ByteArray): Parsed? {
        if (bytes.size < HEADER_SIZE) return null
        val type = bytes[0]
        if (type != TYPE_AUDIO && type != TYPE_PRESENCE && type != TYPE_POSITION) return null
        val origin = ((bytes[1].toInt() and 0xFF) shl 24) or
            ((bytes[2].toInt() and 0xFF) shl 16) or
            ((bytes[3].toInt() and 0xFF) shl 8) or
            (bytes[4].toInt() and 0xFF)
        val ttl = bytes[TTL_OFFSET].toInt() and 0xFF
        val seq = ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
        return Parsed(type, origin, ttl, seq)
    }

    /** A copy of [bytes] with a different hop count, to pass on to the next phone. */
    fun withTtl(bytes: ByteArray, ttl: Int): ByteArray {
        val copy = bytes.copyOf()
        copy[TTL_OFFSET] = ttl.coerceIn(0, 255).toByte()
        return copy
    }

    /** Hops a message has travelled so far, given the hop count it arrived with. 1 means direct. */
    fun hopsTravelled(arrivedTtl: Int): Int = DEFAULT_TTL - arrivedTtl + 1
}
