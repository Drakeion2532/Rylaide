package com.rylaide.app.audio

import kotlin.math.sqrt

/**
 * Voice activity detection - runs on the phone, no internet needed.
 *
 * While the rider's mic is open, this decides whether each 20 ms chunk of
 * audio is worth sending. Quiet chunks are dropped, which saves bandwidth and
 * battery and stops the group hearing constant hiss.
 *
 * v1 is a simple loudness (RMS) check with a short "hangover" so the ends of
 * words are not cut off. Be aware of the limit: this measures volume, not
 * whether it is a voice. Loud wind or engine noise can still pass through. A
 * real noise model can replace this later without changing anything else.
 *
 * [threshold] is a guess to be tuned on a real bike. The test screen shows the
 * live level so you can pick a number that works for you.
 */
class VoiceActivityDetector(
    @Volatile var threshold: Double = 900.0,
    private val hangoverFrames: Int = 15 // 15 x 20 ms = 300 ms
) {

    private var hangRemaining = 0

    /** Loudness of the most recent chunk (0 - 32767), for showing on screen. */
    @Volatile
    var lastRms: Double = 0.0
        private set

    fun isSpeech(audioChunk: ShortArray, length: Int = audioChunk.size): Boolean {
        if (length <= 0) return false
        var sum = 0.0
        for (i in 0 until length) {
            val s = audioChunk[i].toDouble()
            sum += s * s
        }
        val rms = sqrt(sum / length)
        lastRms = rms

        if (rms >= threshold) {
            hangRemaining = hangoverFrames
            return true
        }
        if (hangRemaining > 0) {
            hangRemaining--
            return true
        }
        return false
    }

    fun reset() {
        hangRemaining = 0
        lastRms = 0.0
    }
}
