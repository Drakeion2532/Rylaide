// Copyright (c) 2026 Shubhan Punde · https://github.com/Drakeion2532/Rylaide · All rights reserved · sp2532-19f472e3b826
package com.rylaide.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class ToneProbeTest {
    private fun frame(freq: Double): ShortArray {
        val f = ShortArray(IntercomAudioEngine.FRAME_SAMPLES)
        ToneProbe.fill(f, freq, 0)
        return f
    }

    @Test fun pingIsDetectedAsPing() = assertEquals(ToneProbe.PING_HZ, ToneProbe.detect(frame(ToneProbe.PING_HZ)), 0.0)

    @Test fun pongIsDetectedAsPong() = assertEquals(ToneProbe.PONG_HZ, ToneProbe.detect(frame(ToneProbe.PONG_HZ)), 0.0)

    @Test fun silenceIsNotABeep() = assertEquals(0.0, ToneProbe.detect(ShortArray(320)), 0.0)

    @Test fun otherPitchIsNotABeep() = assertEquals(0.0, ToneProbe.detect(frame(400.0)), 0.0)

    @Test fun loudNoiseIsNotABeep() {
        val r = Random(1)
        val f = ShortArray(320) { (r.nextGaussian() * 6000).toInt().toShort() }
        assertEquals(0.0, ToneProbe.detect(f), 0.0)
    }

    @Test fun beepSurvivesSomeNoise() {
        val r = Random(2)
        val f = frame(ToneProbe.PING_HZ)
        for (i in f.indices) f[i] = (f[i] + r.nextGaussian() * 1500).toInt().toShort()
        assertEquals(ToneProbe.PING_HZ, ToneProbe.detect(f), 0.0)
    }

    @Test fun summaryUsesMedian() {
        val s = ToneProbe.summarize(5, listOf(120L, 100L, 300L, 110L))
        assertEquals(4, s.answered)
        assertEquals(100L, s.minMs)
        assertEquals(120L, s.medianMs)
        assertEquals(300L, s.maxMs)
        assertTrue(ToneProbe.summarize(3, emptyList()).answered == 0)
    }
}
