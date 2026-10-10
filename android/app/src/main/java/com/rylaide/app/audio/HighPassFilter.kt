package com.rylaide.app.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Removes low rumble (wind buffeting, engine and road vibration) from the mic
 * before the speech check and the voice compression see it.
 *
 * Wind noise is mostly very low frequency, below about 200 Hz, while speech
 * intelligibility lives well above that. A gentle 2nd-order filter at 200 Hz
 * takes out much of the rumble and costs almost nothing. It does NOT remove
 * hiss-type wind noise in the same range as speech; that needs a noise model
 * (planned for Step 3) and, above all, a mic that is shielded from the wind.
 */
class HighPassFilter(
    sampleRate: Int = IntercomAudioEngine.SAMPLE_RATE,
    cutoffHz: Double = 200.0
) {
    private val b0: Double
    private val b1: Double
    private val b2: Double
    private val a1: Double
    private val a2: Double

    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    init {
        val q = 0.7071
        val w0 = 2.0 * PI * cutoffHz / sampleRate
        val alpha = sin(w0) / (2.0 * q)
        val cosW0 = cos(w0)
        val a0 = 1.0 + alpha
        b0 = ((1.0 + cosW0) / 2.0) / a0
        b1 = (-(1.0 + cosW0)) / a0
        b2 = b0
        a1 = (-2.0 * cosW0) / a0
        a2 = (1.0 - alpha) / a0
    }

    /** Filters [samples] in place. */
    fun process(samples: ShortArray) {
        for (i in samples.indices) {
            val x0 = samples[i].toDouble()
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x0
            y2 = y1; y1 = y0
            samples[i] = y0.coerceIn(-32768.0, 32767.0).toInt().toShort()
        }
    }

    fun reset() {
        x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0
    }
}
