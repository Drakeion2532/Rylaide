package com.rylaide.app.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
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
        private const val PLAYBACK_BUFFER_FRAMES = 3 // 60 ms
        private const val PRE_ROLL_FRAMES = 4 // 80 ms of audio before speech starts
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
    @Volatile private var preferredInput: AudioDeviceInfo? = null
    @Volatile private var preferredOutput: AudioDeviceInfo? = null

    private val encoder = OpusVoiceEncoder()
    private val highPass = HighPassFilter()
    private val autoGain = AutoGain()
    /** The app's own wind and background noise reducer (see NoiseSuppressor). */
    val noiseReducer = NoiseSuppressor()

    /**
     * Extra mic sensitivity (1.0 = normal). Applied BEFORE the speech check, so a
     * voice from further away still opens the gate.
     */
    @Volatile var micSensitivity: Double = 1.0

    /** How much the app is boosting your voice right now (1.0 = no boost). */
    val micBoost: Double get() = autoGain.currentGain

    /** Length of the phone's playback buffer in milliseconds (part of the delay you hear). */
    @Volatile var playbackBufferMs: Int = 0
        private set
    private var sendSeq = 0

    private class PeerStream {
        val jitter = JitterBuffer()
        val decoder = OpusVoiceDecoder()
        @Volatile var lastHeardMs = 0L
    }
    private val peers = ConcurrentHashMap<String, PeerStream>()

    /**
     * Turns the phone's own noise suppressor on or off. On some phones it
     * removes a voice that is not right next to the mic. Returns false if the
     * phone has none.
     */
    fun setPhoneNoiseSuppressor(on: Boolean): Boolean {
        val ns = noiseSuppressor ?: return false
        ns.enabled = on
        return true
    }

    /** Which built-in phone audio clean-up is switched on, for the test log. */
    val effectsInfo: String
        get() = "phone echo canceller=${echoCanceler != null}, phone noise suppressor=${noiseSuppressor != null}"

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
        highPass.reset()
        autoGain.reset()
        noiseReducer.reset()

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
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        // Ask for a small buffer: the playback loop keeps it full, so its size
        // is delay the rider hears. The phone may refuse to go lower than its minimum.
        tr.setBufferSizeInFrames(FRAME_SAMPLES * PLAYBACK_BUFFER_FRAMES)
        playbackBufferMs = tr.bufferSizeInFrames * 1000 / SAMPLE_RATE
        track = tr
        applyPreferredDevices()
        tr.play()

        captureThread = Thread({ captureLoop(rec) }, "rylaide-capture").also { it.start() }
        playbackThread = Thread({ playbackLoop(tr) }, "rylaide-playback").also { it.start() }
    }

    /**
     * Pins the mic and speaker to specific devices (for example wired earphones)
     * instead of leaving the choice to the phone. Pass null to let Android choose.
     * Safe to call while running.
     */
    fun setPreferredDevices(input: AudioDeviceInfo?, output: AudioDeviceInfo?) {
        preferredInput = input
        preferredOutput = output
        applyPreferredDevices()
    }

    private fun applyPreferredDevices() {
        recorder?.setPreferredDevice(preferredInput)
        track?.setPreferredDevice(preferredOutput)
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
    private var lastPruneMs = 0L

    /** [riderKey] is the rider who made the voice, even if it reached us through other riders. */
    fun enqueuePlayback(riderKey: String, packet: ByteArray) {
        if (!running || packet.size < 3) return
        val seq = ((packet[0].toInt() and 0xFF) shl 8) or (packet[1].toInt() and 0xFF)
        val stream = peers.getOrPut(riderKey) { PeerStream() }
        val now = System.currentTimeMillis()
        stream.lastHeardMs = now
        stream.jitter.put(seq, packet.copyOfRange(2, packet.size))
        if (now - lastPruneMs > 2000) {
            lastPruneMs = now
            peers.entries.removeAll { now - it.value.lastHeardMs > 15000 }
        }
    }

    private fun captureLoop(rec: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buffer = ShortArray(FRAME_SAMPLES)
        // The speech check only notices a word after it has started, so the
        // first syllable would be cut off. Keep the last few quiet frames and
        // send them first when speech begins.
        val preRoll = java.util.ArrayDeque<ShortArray>()
        var speaking = false
        rec.startRecording()
        while (running) {
            var read = 0
            while (read < FRAME_SAMPLES && running) {
                val n = rec.read(buffer, read, FRAME_SAMPLES - read)
                if (n <= 0) return
                read += n
            }
            if (!running) return
            // Always read so the recorder never backs up. Remove wind rumble,
            // then only send when the mic is open and the chunk looks like speech.
            highPass.process(buffer)
            noiseReducer.process(buffer)
            applySensitivity(buffer)
            if (!micOpen) {
                speaking = false
                preRoll.clear()
                vad.isSpeech(buffer) // keeps the on-screen level meter live
                continue
            }
            if (vad.isSpeech(buffer)) {
                if (!speaking) {
                    speaking = true
                    for (old in preRoll) sendFrame(old)
                    preRoll.clear()
                }
                autoGain.process(buffer, vad.lastRms, true)
                sendFrame(buffer)
            } else {
                speaking = false
                preRoll.addLast(buffer.copyOf())
                while (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
            }
        }
    }

    private fun applySensitivity(samples: ShortArray) {
        val g = micSensitivity
        if (g <= 1.001) return
        for (i in samples.indices) {
            samples[i] = (samples[i] * g).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun sendFrame(pcm: ShortArray) {
        val opus = encoder.encode(pcm) ?: return
        val packet = ByteArray(opus.size + 2)
        packet[0] = (sendSeq shr 8).toByte()
        packet[1] = sendSeq.toByte()
        sendSeq = (sendSeq + 1) and 0xFFFF
        System.arraycopy(opus, 0, packet, 2, opus.size)
        onMicPacket(packet)
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
