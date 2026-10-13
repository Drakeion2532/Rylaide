package com.rylaide.app.audio

import kotlin.math.sqrt

/**
 * Voice activity detection - runs on the phone, no internet needed.
 *
 * While the rider's mic is open, this decides whether each 20 ms chunk of
 * audio is worth sending. Quiet chunks are dropped, which saves bandwidth and
 * battery and stops the group hearing constant hiss.
 *
 * AUTO mode (the default): the detector keeps track of the background level
 * (the "noise floor") and opens the gate when the sound is clearly louder than
 * that. In a quiet room it opens for soft speech; in steady wind or engine
 * noise the gate rises by itself. No slider fiddling needed.
 *
 * MANUAL mode: a fixed [threshold], set with the slider on the test screen.
 *
 * Limit: this measures volume, not whether it is a voice. A sudden loud gust
 * can still open the gate. A real noise model can replace this later without
 * changing anything else.
 */
class VoiceActivityDetector(
    @Volatile var threshold: Double = 900.0,
    private val hangoverFrames: Int = 15 // 15 x 20 ms = 300 ms
) {

    companion object {
        const val MIN_AUTO_THRESHOLD = 60.0    // soft or distant speech in a quiet room still passes
        const val MAX_AUTO_THRESHOLD = 4000.0
        const val SPEECH_OVER_NOISE = 2.5      // gate opens at 2.5x the background level
        const val SENSITIVE_OVER_NOISE = 1.5
    }

    /** Open the gate for quieter voices (1.5x the background instead of 2.5x). */
    @Volatile
    var sensitive: Boolean = false

    /** Never close the gate: everything is sent (after noise clean-up). For when speech keeps getting lost. */
    @Volatile
    var alwaysSend: Boolean = false

    /** True: threshold follows the background noise. False: use [threshold]. */
    @Volatile
    var auto: Boolean = true

    private var hangRemaining = 0
    private var noiseFloor = -1.0
    private var loudStreak = 0 // consecutive loud chunks; speech has gaps, wind does not

    /** Loudness of the most recent chunk (0 - 32767), for showing on screen. */
    @Volatile
    var lastRms: Double = 0.0
        private set

    /** The level the gate is using right now. */
    val effectiveThreshold: Double
        get() = if (auto) {
            (maxOf(noiseFloor, 0.0) * (if (sensitive) SENSITIVE_OVER_NOISE else SPEECH_OVER_NOISE)).coerceIn(MIN_AUTO_THRESHOLD, MAX_AUTO_THRESHOLD)
        } else {
            threshold
        }

    fun isSpeech(audioChunk: ShortArray, length: Int = audioChunk.size): Boolean {
        if (length <= 0) return false
        var sum = 0.0
        for (i in 0 until length) {
            val s = audioChunk[i].toDouble()
            sum += s * s
        }
        val rms = sqrt(sum / length)
        lastRms = rms

        val speech = rms >= effectiveThreshold
        loudStreak = if (speech) loudStreak + 1 else 0
        trackNoiseFloor(rms, speech)

        if (alwaysSend) {
            hangRemaining = hangoverFrames
            return true
        }

        if (speech) {
            hangRemaining = hangoverFrames
            return true
        }
        if (hangRemaining > 0) {
            hangRemaining--
            return true
        }
        return false
    }

    /**
     * Learns the background level: it drops quickly when things get quieter and
     * creeps up slowly when they get louder, so steady wind raises the gate
     * but a spoken sentence does not.
     */
    private fun trackNoiseFloor(rms: Double, speech: Boolean) {
        if (noiseFloor < 0) {
            noiseFloor = rms
            return
        }
        noiseFloor = when {
            rms < noiseFloor -> noiseFloor * 0.9 + rms * 0.1
            // Loud for 3 s without a break is wind or engine, not a sentence:
            // learn it as background quickly.
            speech && loudStreak >= 150 -> noiseFloor + (rms - noiseFloor) * 0.01
            speech -> noiseFloor + (rms - noiseFloor) * 0.0003
            else -> noiseFloor + (rms - noiseFloor) * 0.01
        }
    }

    fun reset() {
        hangRemaining = 0
        lastRms = 0.0
        noiseFloor = -1.0
        loudStreak = 0
    }
}
