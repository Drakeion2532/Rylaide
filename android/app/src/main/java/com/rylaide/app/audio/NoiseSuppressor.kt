package com.rylaide.app.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Wind and background noise reducer that runs on the phone (pure Kotlin, no
 * downloads, no native code).
 *
 * How it works, in plain words:
 *  1. Split the sound into 257 narrow frequency bands, 100 times a second.
 *  2. Keep a running guess of how loud the steady background is in each band
 *     (fan, engine hum, road hiss). It rises slowly and falls quickly, so a
 *     voice is not mistaken for background.
 *  3. Turn each band down by how much of it looks like background.
 *  4. Wind is a special case: it is a low rumble with no pitch. Voice also has
 *     low sound, but voice has a pitch (a repeating pattern) and wind does not.
 *     When a chunk is low-heavy and has no pitch, the low bands are treated as
 *     wind, followed quickly as gusts come and go, and turned down harder.
 *
 * It never turns a band fully off (the floor), because that sounds like a bad
 * phone line. Delay added: 10 ms.
 *
 * Works on 16 kHz mono, in pieces that are a multiple of [HOP] samples.
 */
class NoiseSuppressor {

    /** How hard to turn background noise down. Higher levels sound a little more processed. */
    enum class Level(val label: String, val floorGain: Double, val windFloor: Double) {
        OFF("Off", 1.0, 1.0),
        LOW("Low", 0.35, 0.25),
        MEDIUM("Medium", 0.2, 0.12),
        HIGH("High", 0.12, 0.06)
    }


    companion object {
        const val HOP = 160
        const val WIN = 320
        const val FFT_SIZE = 512
        const val BINS = FFT_SIZE / 2 + 1

        /** Bands below about 1 kHz (31.25 Hz each). */
        const val LOW_BANDS = 33
        private const val WARMUP_HOPS = 25
    }

    /** Strength of the reducer. [Level.OFF] passes audio straight through. */
    @Volatile var level: Level = Level.HIGH

    /** Shortcut: false means off, true means on at the current (or High) level. */
    var enabled: Boolean
        get() = level != Level.OFF
        set(value) { level = if (!value) Level.OFF else if (level == Level.OFF) Level.HIGH else level }

    /** True while the last chunk looked like wind (for the on-screen test info). */
    @Volatile var windDetected: Boolean = false
        private set

    private val window = DoubleArray(WIN) { 0.5 - 0.5 * cos(2.0 * PI * it / WIN) }
        .also { w -> for (i in w.indices) w[i] = sqrt(w[i]) }

    private val cosT = DoubleArray(FFT_SIZE / 2) { cos(2.0 * PI * it / FFT_SIZE) }
    private val sinT = DoubleArray(FFT_SIZE / 2) { sin(2.0 * PI * it / FFT_SIZE) }

    private val prevHop = DoubleArray(HOP)
    private val olaTail = DoubleArray(HOP)
    private val noise = DoubleArray(BINS)
    private val prevGain = DoubleArray(BINS)
    private val prevPost = DoubleArray(BINS)
    private val power = DoubleArray(BINS)
    private val smoothPower = DoubleArray(BINS)
    private val gain = DoubleArray(BINS)
    private val re = DoubleArray(FFT_SIZE)
    private val im = DoubleArray(FFT_SIZE)
    private val raw = DoubleArray(WIN)
    private val newHop = DoubleArray(HOP)
    private val sq = DoubleArray(WIN + 1)
    private var hopsSeen = 0

    fun reset() {
        prevHop.fill(0.0)
        olaTail.fill(0.0)
        noise.fill(0.0)
        prevGain.fill(1.0)
        prevPost.fill(1.0)
        hopsSeen = 0
        windDetected = false
    }

    init { reset() }

    /** Cleans [samples] in place. The length must be a multiple of [HOP]. */
    fun process(samples: ShortArray) {
        if (level == Level.OFF) {
            // Keep the history moving so switching on mid-stream does not click.
            return
        }
        require(samples.size % HOP == 0) { "size must be a multiple of $HOP" }
        var pos = 0
        while (pos < samples.size) {
            for (i in 0 until HOP) newHop[i] = samples[pos + i].toDouble()
            processHop()
            // processHop() leaves the finished 10 ms in newHop (delayed by one hop).
            for (i in 0 until HOP) {
                samples[pos + i] = newHop[i].toInt().coerceIn(-32768, 32767).toShort()
            }
            pos += HOP
        }
    }

    private fun processHop() {
        System.arraycopy(prevHop, 0, raw, 0, HOP)
        System.arraycopy(newHop, 0, raw, HOP, HOP)
        System.arraycopy(newHop, 0, prevHop, 0, HOP)

        for (i in 0 until WIN) re[i] = raw[i] * window[i]
        for (i in WIN until FFT_SIZE) re[i] = 0.0
        im.fill(0.0)
        fft(inverse = false)
        for (k in 0 until BINS) power[k] = re[k] * re[k] + im[k] * im[k]

        if (hopsSeen == 0) {
            for (k in 0 until BINS) {
                noise[k] = power[k] + 1.0
                smoothPower[k] = power[k]
            }
        }
        for (k in 0 until BINS) smoothPower[k] = 0.8 * smoothPower[k] + 0.2 * power[k]
        val warm = hopsSeen < WARMUP_HOPS
        hopsSeen++

        var low = 0.0
        for (k in 2 until LOW_BANDS) low += power[k]
        var high = 1e-9
        for (k in LOW_BANDS until BINS) high += power[k]
        val voiced = pitchStrength() > 0.5
        val windy = !voiced && low > 3.0 * high
        windDetected = windy

        // 1. Update the background guess. It follows a smoothed copy of the sound,
        //    falls gently when things get quieter, and barely rises when a band is
        //    much louder than the guess (that is probably a voice, not background).
        for (k in 0 until BINS) {
            val p = smoothPower[k]
            val n = noise[k]
            val rate = when {
                warm -> 0.3
                windy && k < LOW_BANDS -> if (p > n) 0.2 else 0.3
                p <= n -> 0.05
                p / (n + 1e-9) > 4.0 -> 0.0004
                else -> 0.004
            }
            noise[k] = n + rate * (p - n)
        }

        // 2. Work out how much of each band is voice and turn the rest down.
        val lv = level
        for (k in 0 until BINS) {
            val post = power[k] / (noise[k] + 1e-9)
            val prior = 0.92 * prevGain[k] * prevGain[k] * prevPost[k] +
                0.08 * max(post - 1.0, 0.0)
            var g = max(prior / (1.0 + prior), lv.floorGain)
            if (windy && k < LOW_BANDS) g = min(g, lv.windFloor)
            gain[k] = g
            prevPost[k] = post
            prevGain[k] = g
        }

        // 3. Apply and go back to sound.
        for (k in 0 until BINS) {
            re[k] *= gain[k]
            im[k] *= gain[k]
        }
        for (k in 1 until FFT_SIZE / 2) {
            re[FFT_SIZE - k] = re[k]
            im[FFT_SIZE - k] = -im[k]
        }
        fft(inverse = true)

        // 4. Overlap-add. The first half completes the previous hop.
        for (i in 0 until HOP) {
            newHop[i] = olaTail[i] + re[i] * window[i] / FFT_SIZE
            olaTail[i] = re[HOP + i] * window[HOP + i] / FFT_SIZE
        }
    }

    /** 0..1, how strongly the current chunk repeats at a voice pitch (80 to 400 Hz). */
    private fun pitchStrength(): Double {
        sq[0] = 0.0
        for (i in 0 until WIN) sq[i + 1] = sq[i] + raw[i] * raw[i]
        var best = 0.0
        var lag = 40
        while (lag <= 200) {
            var dot = 0.0
            for (i in 0 until WIN - lag) dot += raw[i] * raw[i + lag]
            val e1 = sq[WIN - lag]
            val e2 = sq[WIN] - sq[lag]
            val c = dot / (sqrt(e1 * e2) + 1e-9)
            if (c > best) best = c
            lag += 2
        }
        return best
    }

    /** In-place radix-2 FFT on [re] and [im]. The inverse is not scaled. */
    private fun fft(inverse: Boolean) {
        val n = FFT_SIZE
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val half = len / 2
            val step = n / len
            var start = 0
            while (start < n) {
                for (k in 0 until half) {
                    val wr = cosT[k * step]
                    val wi = if (inverse) sinT[k * step] else -sinT[k * step]
                    val a = start + k
                    val b = a + half
                    val xr = re[b] * wr - im[b] * wi
                    val xi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                }
                start += len
            }
            len = len shl 1
        }
    }
}
