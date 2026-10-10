package com.rylaide.app.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Captures the rider's voice and plays back other riders' voices.
 *
 * Sprint 1 sends raw 16 kHz mono 16-bit audio in 20 ms frames (640 bytes each,
 * about 256 kbit/s). That is wasteful but simple, and fine for two phones
 * linked by Wi-Fi Direct. The next step is Opus (about 16 kbit/s) so a group
 * of riders fits on a Bluetooth link too.
 *
 * Known limits for now:
 *  - Frames from several talkers are played one after another, not mixed.
 *    That is fine for two phones. A real mixer comes before group tests.
 *  - Playback has a small queue; if the link stalls, the oldest audio is
 *    dropped so the rider never hears a growing delay.
 *
 * The caller must hold the RECORD_AUDIO permission before calling [start].
 */
class IntercomAudioEngine(
    private val onMicFrame: (ByteArray) -> Unit
) {

    companion object {
        const val SAMPLE_RATE = 16000
        const val FRAME_SAMPLES = 320 // 20 ms at 16 kHz
        private const val MAX_QUEUED_FRAMES = 15 // 300 ms
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
    private val playQueue = LinkedBlockingQueue<ByteArray>()

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        vad.reset()
        playQueue.clear()

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
            .setBufferSizeInBytes(maxOf(minTrack, FRAME_SAMPLES * 2 * 4))
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
        playQueue.clear()
    }

    /** Called with audio received from another rider. Safe from any thread. */
    fun enqueuePlayback(pcm: ByteArray) {
        if (!running) return
        playQueue.offer(pcm)
        while (playQueue.size > MAX_QUEUED_FRAMES) playQueue.poll()
    }

    private fun captureLoop(rec: AudioRecord) {
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
                onMicFrame(shortsToBytes(buffer))
            }
        }
    }

    private fun playbackLoop(tr: AudioTrack) {
        while (running) {
            val frame = playQueue.poll(100, TimeUnit.MILLISECONDS) ?: continue
            tr.write(frame, 0, frame.size)
        }
    }

    private fun shortsToBytes(samples: ShortArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples)
        return bytes
    }
}
