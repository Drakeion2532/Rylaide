package com.rylaide.app.mesh

enum class PathState { UP, DEGRADED, DOWN }

/**
 * Judges one transport (offline link, UDP, WebSocket) from what it reports.
 * Pure logic: time comes from [clock], so it can be tested without waiting.
 *
 * Starting thresholds (tuned later from real logs):
 *  - DEGRADED: no packet for 4 s (two missed pings), round trip over 400 ms for 3 s, or more than 10 % loss over 5 s.
 *  - DOWN: link closed, or no packet for 8 s.
 *  - Back to UP only after 5 s of good readings (round trip under 250 ms, loss under 5 %).
 */
class PathHealth(private val clock: () -> Long) {
    companion object {
        const val SILENT_DEGRADED_MS = 4_000L
        const val SILENT_DOWN_MS = 8_000L
        const val RTT_BAD_MS = 400
        const val RTT_BAD_FOR_MS = 3_000L
        const val RTT_GOOD_MS = 250
        const val LOSS_WINDOW_MS = 5_000L
        const val LOSS_BAD = 0.10
        const val LOSS_GOOD = 0.05
        const val MIN_LOSS_SAMPLES = 10
        const val RECOVER_MS = 5_000L
    }

    private var linkUp = false
    private var lastRx = 0L
    private var rtt = 0
    private var rttBadSince = -1L
    private var goodSince = -1L
    private var state = PathState.DOWN
    // (time, lost?) outcomes inside the loss window
    private val outcomes = ArrayDeque<Pair<Long, Boolean>>()

    @Synchronized fun setLinkUp(up: Boolean) {
        if (up && !linkUp) { lastRx = clock(); goodSince = -1L }
        linkUp = up
    }

    @Synchronized fun onPacketReceived() { lastRx = clock(); addOutcome(false) }
    @Synchronized fun onPacketLost() = addOutcome(true)

    /** The transport itself says it is alive (for example a server ack), without counting as voice traffic. */
    @Synchronized fun onLinkAlive() { lastRx = clock() }

    @Synchronized fun onRtt(ms: Int) {
        rtt = ms
        val now = clock()
        if (ms > RTT_BAD_MS) { if (rttBadSince < 0) rttBadSince = now } else rttBadSince = -1L
    }

    private fun addOutcome(lost: Boolean) { outcomes.addLast(clock() to lost) }

    private fun lossRate(now: Long): Double {
        while (outcomes.isNotEmpty() && now - outcomes.first().first > LOSS_WINDOW_MS) outcomes.removeFirst()
        if (outcomes.size < MIN_LOSS_SAMPLES) return 0.0
        return outcomes.count { it.second }.toDouble() / outcomes.size
    }

    /** Current verdict. Call as often as you like (for example once a second). */
    @Synchronized fun evaluate(): PathState {
        val now = clock()
        if (!linkUp || now - lastRx > SILENT_DOWN_MS) {
            goodSince = -1L
            state = PathState.DOWN
            return state
        }
        val loss = lossRate(now)
        val bad = now - lastRx > SILENT_DEGRADED_MS ||
            (rttBadSince >= 0 && now - rttBadSince >= RTT_BAD_FOR_MS) ||
            loss > LOSS_BAD
        if (bad) {
            goodSince = -1L
            state = PathState.DEGRADED
            return state
        }
        if (state == PathState.UP) return state
        // Coming back from DEGRADED or DOWN: needs clearly good readings for RECOVER_MS.
        val good = rtt < RTT_GOOD_MS && loss < LOSS_GOOD && now - lastRx < SILENT_DEGRADED_MS
        if (!good) { goodSince = -1L; state = PathState.DEGRADED; return state }
        if (goodSince < 0) goodSince = now
        state = if (now - goodSince >= RECOVER_MS) PathState.UP else PathState.DEGRADED
        return state
    }
}
