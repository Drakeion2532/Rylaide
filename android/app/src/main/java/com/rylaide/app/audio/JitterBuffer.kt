package com.rylaide.app.audio

import java.util.TreeMap

/**
 * Holds incoming voice packets from ONE rider for a few frames before playing
 * them, so small delays on the radio link do not turn into choppy audio.
 *
 * Packets carry a 16-bit sequence number. This buffer puts them back in order,
 * drops ones that arrive too late, and tells the player when a packet is
 * missing so the codec can fill the gap.
 *
 * Playback only starts once [prebufferFrames] packets are waiting (that is the
 * small delay you trade for smoothness). If the rider stops talking and the
 * buffer stays empty, it goes idle and prebuffers again at the next word.
 */
class JitterBuffer(
    private val prebufferFrames: Int = 3,   // 3 x 20 ms = 60 ms
    private val maxFrames: Int = 8,          // never hold more than 160 ms, so delay cannot build up
    private val catchUpExtraFrames: Int = 2  // above prebuffer + this, play faster to shed the extra delay
) {

    sealed class Result {
        class Packet(val data: ByteArray) : Result()
        /** A packet in the middle of speech never arrived: use codec concealment. */
        object Missing : Result()
        /** Nothing to play right now (silence). */
        object Nothing : Result()
    }

    private val packets = TreeMap<Long, ByteArray>()
    private var highestSeq = -1L        // highest sequence number seen, unwrapped
    private var nextSeq = -1L           // next sequence number to play
    private var playing = false
    private var emptyStreak = 0

    @Volatile var lateDropped = 0L
        private set
    @Volatile var concealed = 0L
        private set
    @Volatile var bufferedFrames = 0
        private set
    /** Frames skipped on purpose to shed delay after a burst of packets. */
    @Volatile var skipped = 0L
        private set

    @Synchronized
    fun put(seq16: Int, data: ByteArray) {
        val seq = unwrap(seq16)
        if (playing && seq < nextSeq) {
            lateDropped++
            return
        }
        packets[seq] = data
        if (packets.size > maxFrames) {
            // Fell behind: jump forward to the newest audio instead of lagging.
            val drop = packets.size - maxFrames
            repeat(drop) { packets.pollFirstEntry() }
            if (playing) nextSeq = packets.firstKey()
        }
        if (!playing && packets.size >= prebufferFrames) {
            playing = true
            nextSeq = packets.firstKey()
            emptyStreak = 0
        }
        bufferedFrames = packets.size
    }

    @Synchronized
    fun take(): Result {
        if (!playing) return Result.Nothing
        val data = packets.remove(nextSeq)
        if (data != null) {
            nextSeq++
            emptyStreak = 0
            // After a burst (the radio delivers a pile of packets at once) the extra
            // packets would otherwise sit in the queue as permanent delay. While
            // there are too many waiting, drop one per frame played, so the delay
            // drains within a fraction of a second instead of staying for the whole talk.
            if (packets.size > prebufferFrames + catchUpExtraFrames &&
                packets.isNotEmpty() && packets.firstKey() == nextSeq
            ) {
                packets.pollFirstEntry()
                nextSeq++
                skipped++
            }
            bufferedFrames = packets.size
            return Result.Packet(data)
        }
        if (packets.isNotEmpty()) {
            // Later packets are here but this one is not: it was lost.
            nextSeq++
            concealed++
            bufferedFrames = packets.size
            return Result.Missing
        }
        // Empty: the rider stopped talking, or the link stalled.
        emptyStreak++
        if (emptyStreak > 5) {
            playing = false
            highestSeq = -1L
            nextSeq = -1L
        }
        bufferedFrames = 0
        return Result.Nothing
    }

    /** Turns the 16-bit counter (which wraps at 65536) into an ever-growing number. */
    private fun unwrap(seq16: Int): Long {
        if (highestSeq < 0) {
            highestSeq = seq16.toLong()
            return highestSeq
        }
        val diff = (((seq16 - (highestSeq and 0xFFFF).toInt()) + 32768) and 0xFFFF) - 32768
        val seq = highestSeq + diff
        if (seq > highestSeq) highestSeq = seq
        return seq
    }
}
