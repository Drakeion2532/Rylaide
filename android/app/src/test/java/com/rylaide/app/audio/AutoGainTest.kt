package com.rylaide.app.audio

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoGainTest {

    private fun chunk(level: Int) = ShortArray(IntercomAudioEngine.FRAME_SAMPLES) { i ->
        (if (i % 2 == 0) level else -level).toShort()
    }

    @Test
    fun boostsAQuietVoiceButNotMoreThanTheLimit() {
        val gain = AutoGain()
        var last = chunk(300)
        repeat(200) {
            last = chunk(300)
            gain.process(last, 300.0, true)
        }
        assertTrue("gain was ${gain.currentGain}", gain.currentGain > 3.0 && gain.currentGain <= 8.0)
        assertTrue(abs(last[0].toInt()) > 900)
    }

    @Test
    fun leavesAnAlreadyLoudVoiceAlone() {
        val gain = AutoGain()
        repeat(100) { gain.process(chunk(5000), 5000.0, true) }
        assertEquals(1.0, gain.currentGain, 0.01)
    }

    @Test
    fun doesNotChangeWhileNobodyIsSpeaking() {
        val gain = AutoGain()
        repeat(100) { gain.process(chunk(80), 80.0, false) }
        assertEquals(1.0, gain.currentGain, 0.0001)
    }
}
