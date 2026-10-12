package com.rylaide.app.audio

import java.util.Random
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoiseSuppressorTest {

    private val frame = 320
    private val rng = Random(7)

    private fun rms(a: ShortArray): Double {
        var s = 0.0
        for (v in a) s += v.toDouble() * v
        return sqrt(s / a.size)
    }

    /** Steady hiss like a fan: slightly low-pass filtered random noise. */
    private fun fanFrame(level: Double, state: DoubleArray): ShortArray {
        val out = ShortArray(frame)
        for (i in 0 until frame) {
            state[0] = 0.9 * state[0] + rng.nextGaussian()
            out[i] = (state[0] * level / 2.3).toInt().toShort()
        }
        return out
    }

    /** Low rumble with gusts and no pitch, like wind on a mic. */
    private fun windFrame(level: Double, state: DoubleArray, gust: Double): ShortArray {
        val out = ShortArray(frame)
        for (i in 0 until frame) {
            state[0] += 0.12 * (rng.nextGaussian() - state[0])
            out[i] = (state[0] * level * gust).toInt().toShort()
        }
        return out
    }

    /** A steady voiced sound: 120 Hz with many harmonics. */
    private fun voiceFrame(index: Int, level: Double): ShortArray {
        val out = ShortArray(frame)
        for (i in 0 until frame) {
            val t = (index * frame + i) / 16000.0
            var v = 0.0
            for (k in 1..25) v += sin(2 * PI * 120.0 * k * t) / k
            out[i] = (v * level).toInt().toShort()
        }
        return out
    }

    private fun mix(a: ShortArray, b: ShortArray) =
        ShortArray(a.size) { (a[it] + b[it]).coerceIn(-32768, 32767).toShort() }

    @Test
    fun steadyFanNoiseIsTurnedDown() {
        val ns = NoiseSuppressor()
        val st = DoubleArray(1)
        var inRms = 0.0
        var outRms = 0.0
        var n = 0
        repeat(150) { idx ->
            val f = fanFrame(800.0, st)
            val before = rms(f)
            ns.process(f)
            if (idx >= 50) { inRms += before; outRms += rms(f); n++ }
        }
        assertTrue("fan noise should drop by at least 6 dB, was ${inRms / n} -> ${outRms / n}", outRms < inRms * 0.5)
    }

    @Test
    fun windRumbleIsTurnedDownHard() {
        val ns = NoiseSuppressor()
        val st = DoubleArray(1)
        var inRms = 0.0
        var outRms = 0.0
        var n = 0
        repeat(200) { idx ->
            val gust = if ((idx / 20) % 2 == 0) 1.0 else 2.5
            val f = windFrame(1200.0, st, gust)
            val before = rms(f)
            ns.process(f)
            if (idx >= 50) { inRms += before; outRms += rms(f); n++ }
        }
        assertTrue("wind should drop by at least 6 dB, was ${inRms / n} -> ${outRms / n}", outRms < inRms * 0.5)
    }

    @Test
    fun voiceOverNoiseKeepsMostOfItsLoudness() {
        val ns = NoiseSuppressor()
        val st = DoubleArray(1)
        // Half a second of noise only so it can learn the background.
        repeat(25) { ns.process(fanFrame(500.0, st)) }
        var inRms = 0.0
        var outRms = 0.0
        var n = 0
        repeat(100) { idx ->
            val f = mix(voiceFrame(idx, 2500.0), fanFrame(500.0, st))
            val clean = voiceFrame(idx, 2500.0)
            ns.process(f)
            if (idx >= 10) { inRms += rms(clean); outRms += rms(f); n++ }
        }
        assertTrue("voice kept ${outRms / inRms}", outRms / inRms > 0.75)
        assertTrue("voice not boosted ${outRms / inRms}", outRms / inRms < 1.3)
    }

    @Test
    fun lowLevelTurnsNoiseDownLessThanHighLevel() {
        fun residual(level: NoiseSuppressor.Level): Double {
            val ns = NoiseSuppressor()
            ns.level = level
            val st = DoubleArray(1)
            val r = Random(11)
            var out = 0.0
            var n = 0
            repeat(150) { idx ->
                val f = ShortArray(frame)
                for (i in 0 until frame) {
                    st[0] = 0.9 * st[0] + r.nextGaussian()
                    f[i] = (st[0] * 800.0 / 2.3).toInt().toShort()
                }
                ns.process(f)
                if (idx >= 50) { out += rms(f); n++ }
            }
            return out / n
        }
        val low = residual(NoiseSuppressor.Level.LOW)
        val high = residual(NoiseSuppressor.Level.HIGH)
        assertTrue("low=$low should be louder than high=$high", low > high * 1.3)
    }

    @Test
    fun switchedOffPassesAudioUnchanged() {
        val ns = NoiseSuppressor()
        ns.enabled = false
        val f = voiceFrame(0, 3000.0)
        val copy = f.copyOf()
        ns.process(f)
        assertTrue(f.contentEquals(copy))
    }

    @Test
    fun keepsFrameLength() {
        val ns = NoiseSuppressor()
        val f = ShortArray(frame)
        ns.process(f)
        assertEquals(frame, f.size)
    }
}
