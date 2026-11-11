// Copyright (c) 2026 Shubhan Punde · https://github.com/Drakeion2532/Rylaide · All rights reserved · sp2532-19f472e3b826
package com.rylaide.app.audio

/**
 * Helpers for the delay test. One phone sends a short beep (the "ping") through
 * the real voice path: Opus, encryption, link, jitter buffer, decoder. The other
 * phone hears it just before playback and answers with a different beep (the
 * "pong"). The first phone times ping-out to pong-in on its own clock, so the two
 * phones never need synchronised clocks.
 */
object ToneProbe {
    const val SAMPLE_RATE = IntercomAudioEngine.SAMPLE_RATE
    const val PING_HZ = 1000.0
    const val PONG_HZ = 1600.0
    /** 6 frames = 120 ms of beep. */
    const val TONE_FRAMES = 6
    const val AMPLITUDE = 12000.0
    /** Share of the frame's energy that must sit on the beep's pitch. Speech never gets near this. */
    const val MIN_PURITY = 0.6
    /** A frame quieter than this (average level) is never counted as a beep. */
    const val MIN_RMS = 2500.0

    /** Fills [out] with the next 20 ms of a beep. [startSample] keeps the wave continuous between frames. */
    fun fill(out: ShortArray, freq: Double, startSample: Int) {
        for (i in out.indices) {
            val t = (startSample + i).toDouble() / SAMPLE_RATE
            out[i] = (AMPLITUDE * Math.sin(2.0 * Math.PI * freq * t)).toInt().toShort()
        }
    }

    /** Share (0 to 1) of the frame's energy found at [freq]. */
    fun purity(pcm: ShortArray, freq: Double): Double {
        val n = pcm.size
        var energy = 0.0
        val w = 2.0 * Math.PI * freq / SAMPLE_RATE
        val coeff = 2.0 * Math.cos(w)
        var s1 = 0.0
        var s2 = 0.0
        for (v in pcm) {
            val x = v.toDouble()
            energy += x * x
            val s0 = x + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        if (energy <= 0.0) return 0.0
        val power = s1 * s1 + s2 * s2 - coeff * s1 * s2
        return (2.0 * power / (n * energy)).coerceIn(0.0, 1.0)
    }

    /** Returns [PING_HZ], [PONG_HZ], or 0.0 when the frame holds no beep. */
    fun detect(pcm: ShortArray): Double {
        var energy = 0.0
        for (v in pcm) energy += v.toDouble() * v
        if (Math.sqrt(energy / pcm.size) < MIN_RMS) return 0.0
        if (purity(pcm, PING_HZ) >= MIN_PURITY) return PING_HZ
        if (purity(pcm, PONG_HZ) >= MIN_PURITY) return PONG_HZ
        return 0.0
    }

    /** Summary of a finished test, from round-trip times in milliseconds. */
    class Summary(val sent: Int, val answered: Int, val minMs: Long, val medianMs: Long, val maxMs: Long)

    fun summarize(sent: Int, roundTripsMs: List<Long>): Summary {
        if (roundTripsMs.isEmpty()) return Summary(sent, 0, 0, 0, 0)
        val s = roundTripsMs.sorted()
        return Summary(sent, s.size, s.first(), s[s.size / 2], s.last())
    }
}
