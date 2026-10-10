package com.rylaide.app.audio

import org.concentus.OpusApplication
import org.concentus.OpusDecoder
import org.concentus.OpusEncoder
import org.concentus.OpusException
import org.concentus.OpusSignal

/**
 * Opus voice compression (pure Java, from the Concentus library).
 * Turns a 20 ms chunk of audio (640 bytes) into roughly 40 bytes, about
 * 16x smaller than the raw audio Step 1 sent.
 */
class OpusVoiceEncoder {
    private val encoder = OpusEncoder(
        IntercomAudioEngine.SAMPLE_RATE, 1, OpusApplication.OPUS_APPLICATION_VOIP
    ).apply {
        bitrate = BITRATE
        signalType = OpusSignal.OPUS_SIGNAL_VOICE
        complexity = 5
        useVBR = true
    }
    private val out = ByteArray(400)

    /** Returns the compressed packet, or null if encoding failed. */
    fun encode(pcm: ShortArray): ByteArray? = try {
        val n = encoder.encode(pcm, 0, IntercomAudioEngine.FRAME_SAMPLES, out, 0, out.size)
        out.copyOf(n)
    } catch (_: OpusException) {
        null
    }

    companion object {
        const val BITRATE = 16000
    }
}

/** One decoder per remote rider, because Opus keeps state between frames. */
class OpusVoiceDecoder {
    private val decoder = OpusDecoder(IntercomAudioEngine.SAMPLE_RATE, 1)

    /** Decodes one packet into [pcm]. Returns false if it could not. */
    fun decode(packet: ByteArray, pcm: ShortArray): Boolean = try {
        decoder.decode(packet, 0, packet.size, pcm, 0, IntercomAudioEngine.FRAME_SAMPLES, false)
        true
    } catch (_: OpusException) {
        false
    }

    /** Makes up audio for a packet that never arrived. */
    fun conceal(pcm: ShortArray): Boolean = try {
        decoder.decode(null, 0, 0, pcm, 0, IntercomAudioEngine.FRAME_SAMPLES, false)
        true
    } catch (_: OpusException) {
        false
    }
}
