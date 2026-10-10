package com.rylaide.app.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import java.util.concurrent.ConcurrentHashMap

/**
 * Captures the rider's voice and plays back other riders' voices.
 *
 * Step 2: voice is compressed with Opus (about 16 kbit/s, 16x smaller than the
 * raw audio of Step 1). Each packet is [sequence number: 2 bytes][Opus data].
 * Every remote rider gets their own jitter buffer and decoder, and a mixer adds
 * all talkers together once per 20 ms, so several riders can speak at once.
 *
 * The caller must hold the RECORD_AUDIO permission before calling [start].
 */
class IntercomAudioEngine(
    private val onMicPacket: (ByteArray) -> Unit
) {

    companion object {
        const val SAMPLE_RATE = 16000
        const val FRAME_SAMPLES = 320 // 20 ms at 16 kHz
    }

    /** Open mic: when true, speech is sent to the group. */
    @Volatile
    var micOpen: Boolean = false

    val vad = VoiceActivityDetector()

    @Volatile
    private var running = false

    private var recorder: AudioRecord? = null
    private var track: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var captureThread: Thread? = null
    private var playbackThread: Thread? = null
    private val encoder = OpusVoiceEncoder()
    private var sendSeq = 0

    private class PeerStream {
        val jitter = JitterBuffer()
        val decoder = OpusVoiceDecoder()
    }
    private val peers = ConcurrentHashMap<String, PeerStream>()

    /** Frames the codec had to make up because a packet never arrived. */
    val concealedFrames: Long get() = peers.values.sumOf { it.jitter.concealed }

    /** Packets that arrived too late to play. */
    val lateFrames: Long get() = peers.values.sumOf { it.jitter.lateDropped }

    /** How much audio is waiting to be played, in milliseconds (all riders added). */
    val bufferedMs: Int get() = peers.values.sumOf { it.jitter.bufferedFrames } * 20

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        vad.reset()
        peers.clear()
        sendSeq = 0

        val minRecord = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minRecord, FRAME_SAMPLES * 2 * 4)
        )
        recorder = rec

        // Ask the phone to cancel echo and background noise if it can.
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(rec.audioSessionId)?.also { it.enabled = true }
        }
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(rec.audioSessionId)?.also { it.enabled = true }
        }

        val minTrack = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val tr = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minTrack, FRAME_SAMPLES * 2 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = tr
        tr.play()

        captureThread = Thread({ captureLoop(rec) }, "rylaide-capture").also { it.start() }
        playbackThread = Thread({ playbackLoop(tr) }, "rylaide-playback").also { it.start() }
    }

    fun stop() {
        if (!running) return
        running = false
        captureThread?.join(500)
        playbackThread?.join(500)
        captureThread = null
        playbackThread = null

        echoCanceler?.release()
        noiseSuppressor?.release()
        echoCanceler = null
        noiseSuppressor = null

        recorder?.let {
            try { it.stop() } catch (_: IllegalStateException) { }
            it.release()
        }
        recorder = null

        track?.let {
            try { it.stop() } catch (_: IllegalStateException) { }
            it.release()
        }
        track = null
        peers.clear()
    }

    /**
     * Called with a voice packet received from another rider. Safe from any
     * thread. [packet] is the 2-byte sequence number followed by Opus data.
     */
    fun enqueuePlayback(endpointId: String, packet: ByteArray) {
        if (!running || packet.size < 3) return
        val seq = ((packet[0].toInt() and 0xFF) shl 8) or (packet[1].toInt() and 0xFF)
        val stream = peers.getOrPut(endpointId) { PeerStream() }
        stream.jitter.put(seq, packet.copyOfRange(2, packet.size))
    }

    /** Forget riders who are no longer connected. */
    fun keepOnly(endpointIds: Collection<String>) {
        peers.keys.retainAll(endpointIds.toSet())
    }

    private fun captureLoop(rec: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buffer = ShortArray(FRAME_SAMPLES)
        rec.startRecording()
        while (running) {
            var read = 0
            while (read < FRAME_SAMPLES && running) {
                val n = rec.read(buffer, read, FRAME_SAMPLES - read)
                if (n <= 0) return
                read += n
            }
            if (!running) return
            // Always read so the recorder never backs up, but only send when
            // the mic is open and the chunk looks like speech.
            if (micOpen && vad.isSpeech(buffer)) {
                val opus = encoder.encode(buffer) ?: continue
                val packet = ByteArray(opus.size + 2)
                packet[0] = (sendSeq shr 8).toByte()
                packet[1] = sendSeq.toByte()
                sendSeq = (sendSeq + 1) and 0xFFFF
                System.arraycopy(opus, 0, packet, 2, opus.size)
                onMicPacket(packet)
            }
        }
    }

    /**
     * Every 20 ms: take one frame from each talking rider, add them together
     * and play the result. Writing to the AudioTrack blocks until it has room,
     * which keeps this loop paced at real time.
     */
    private fun playbackLoop(tr: AudioTrack) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val mix = IntArray(FRAME_SAMPLES)
        val pcm = ShortArray(FRAME_SAMPLES)
        val out = ShortArray(FRAME_SAMPLES)
        while (running) {
            java.util.Arrays.fill(mix, 0)
            for (stream in peers.values) {
                val ok = when (val r = stream.jitter.take()) {
                    is JitterBuffer.Result.Packet -> stream.decoder.decode(r.data, pcm)
                    JitterBuffer.Result.Missing -> stream.decoder.conceal(pcm)
                    JitterBuffer.Result.Nothing -> false
                }
                if (ok) for (i in 0 until FRAME_SAMPLES) mix[i] += pcm[i].toInt()
            }
            for (i in 0 until FRAME_SAMPLES) {
                out[i] = mix[i].coerceIn(-32768, 32767).toShort()
            }
            val written = tr.write(out, 0, FRAME_SAMPLES)
            if (written < 0) Thread.sleep(20) // track not ready: avoid a busy loop
        }
    }
}
