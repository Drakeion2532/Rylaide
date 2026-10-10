package com.rylaide.app.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test

class HighPassFilterTest {

    private fun peakAfterSettling(freq: Double): Int {
        val filter = HighPassFilter()
        var peak = 0
        repeat(40) { frame ->
            val block = ShortArray(IntercomAudioEngine.FRAME_SAMPLES) { i ->
                val t = (frame * IntercomAudioEngine.FRAME_SAMPLES + i).toDouble() / IntercomAudioEngine.SAMPLE_RATE
                (sin(2 * PI * freq * t) * 10000).toInt().toShort()
            }
            filter.process(block)
            if (frame >= 20) peak = maxOf(peak, block.maxOf { abs(it.toInt()) })
        }
        return peak
    }

    @Test
    fun removesVeryLowRumble() {
        val peak = peakAfterSettling(40.0)
        assertTrue("40 Hz rumble peak was $peak", peak < 2500)
    }

    @Test
    fun keepsSpeechRangeAudio() {
        val peak = peakAfterSettling(1000.0)
        assertTrue("1 kHz peak was $peak", peak > 9000)
    }
}
