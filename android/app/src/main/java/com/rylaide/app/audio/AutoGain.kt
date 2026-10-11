package com.rylaide.app.audio

/**
 * Automatic mic level: makes a quiet voice louder (never louder than 8x) so the
 * other riders hear the same level whether the mic is weak or strong. The gain
 * only changes while the rider is speaking and moves gently, so it does not
 * pump up background noise between words.
 */
class AutoGain(
    private val targetRms: Double = 3000.0,
    private val maxGain: Double = 8.0
) {
    private var gain = 1.0
    private var speechLevel = 0.0

    /** Applies the gain in place. [rms] is the loudness of this chunk before the gain. */
    fun process(samples: ShortArray, rms: Double, speaking: Boolean) {
        if (speaking && rms > 30.0) {
            speechLevel = if (speechLevel == 0.0) rms else speechLevel * 0.9 + rms * 0.1
            val wanted = (targetRms / speechLevel).coerceIn(1.0, maxGain)
            gain += (wanted - gain) * 0.1
        }
        if (gain <= 1.001) return
        for (i in samples.indices) {
            samples[i] = (samples[i] * gain).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    val currentGain: Double get() = gain

    fun reset() {
        gain = 1.0
        speechLevel = 0.0
    }
}
