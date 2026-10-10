package com.rylaide.app.audio

/**
 * Voice activity detection - runs on-device, no internet needed.
 * While the rider's mic is "unmuted", this filters out wind/engine noise
 * and silence, only forwarding chunks that look like actual speech into
 * the WebRTC audio engine. Reduces bandwidth, battery use, and how
 * annoying continuous open-mic audio is for the rest of the group.
 *
 * v1 note: start simple (energy-based threshold), a full ML-based VAD
 * model can replace this later without changing anything else.
 */
class VoiceActivityDetector {

    fun isSpeech(audioChunk: ShortArray): Boolean {
        // TODO: replace with a real VAD algorithm/model.
        // Simplest starting point: RMS energy threshold over the chunk.
        return true
    }
}
