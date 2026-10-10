package com.rylaide.app.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpusCodecTest {

    private fun tone(frame: Int): ShortArray = ShortArray(IntercomAudioEngine.FRAME_SAMPLES) { i ->
        val t = (frame * IntercomAudioEngine.FRAME_SAMPLES + i).toDouble() / IntercomAudioEngine.SAMPLE_RATE
        (sin(2 * PI * 440 * t) * 8000).toInt().toShort()
    }

    @Test
    fun compressesAFrameToWellUnderTheRawSize() {
        val packet = OpusVoiceEncoder().encode(tone(0))
        assertNotNull(packet)
        // Raw is 640 bytes; Opus at 16 kbit/s should be around 40 bytes.
        assertTrue("packet was ${packet!!.size} bytes", packet.size in 1..120)
    }

    @Test
    fun decodedAudioIsNotSilent() {
        val enc = OpusVoiceEncoder()
        val dec = OpusVoiceDecoder()
        val out = ShortArray(IntercomAudioEngine.FRAME_SAMPLES)
        var loudest = 0
        for (f in 0 until 10) {
            val packet = enc.encode(tone(f))!!
            assertTrue(dec.decode(packet, out))
            loudest = maxOf(loudest, out.maxOf { abs(it.toInt()) })
        }
        assertTrue("loudest sample was $loudest", loudest > 2000)
    }

    @Test
    fun canMakeUpAMissingFrame() {
        val enc = OpusVoiceEncoder()
        val dec = OpusVoiceDecoder()
        val out = ShortArray(IntercomAudioEngine.FRAME_SAMPLES)
        for (f in 0 until 5) dec.decode(enc.encode(tone(f))!!, out)
        assertTrue(dec.conceal(out))
    }
}
