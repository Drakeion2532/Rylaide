package com.rylaide.app.mesh

import org.junit.Assert.assertEquals
import org.junit.Test

/** feed() also calls evaluate() every step, like the app will once a second. */
class PathHealthTest {
    private var t = 0L
    private val h = PathHealth { t }

    private fun feed(seconds: Int, everyMs: Long = 100, rtt: Int = 80) {
        val end = t + seconds * 1000L
        while (t < end) { t += everyMs; h.onPacketReceived(); h.onRtt(rtt); h.evaluate() }
    }

    @Test fun downUntilLinkIsUp() { assertEquals(PathState.DOWN, h.evaluate()) }

    @Test fun staysDegradedFor5sBeforeUp() {
        h.setLinkUp(true)
        feed(2)
        assertEquals(PathState.DEGRADED, h.evaluate())
        feed(4)
        assertEquals(PathState.UP, h.evaluate())
    }

    @Test fun silenceDegradesThenDowns() {
        h.setLinkUp(true); feed(6); h.evaluate()
        t += 3_500; assertEquals(PathState.DEGRADED, h.evaluate())
        t += 5_000; assertEquals(PathState.DOWN, h.evaluate())
    }

    @Test fun closedLinkIsDownAtOnce() {
        h.setLinkUp(true); feed(6); assertEquals(PathState.UP, h.evaluate())
        h.setLinkUp(false); assertEquals(PathState.DOWN, h.evaluate())
    }

    @Test fun highRoundTripFor3sDegrades() {
        h.setLinkUp(true); feed(6); assertEquals(PathState.UP, h.evaluate())
        feed(2, rtt = 600); assertEquals(PathState.UP, h.evaluate())
        feed(2, rtt = 600); assertEquals(PathState.DEGRADED, h.evaluate())
    }

    @Test fun lossOverTenPercentDegrades() {
        h.setLinkUp(true); feed(6); assertEquals(PathState.UP, h.evaluate())
        repeat(20) { t += 100; h.onPacketReceived() }
        repeat(8) { t += 100; h.onPacketLost() }
        t += 100; h.onPacketReceived()
        assertEquals(PathState.DEGRADED, h.evaluate())
    }

    @Test fun shortGoodSpellDoesNotFlipBackToUp() {
        h.setLinkUp(true); feed(6); h.evaluate()
        feed(2, rtt = 600); feed(2, rtt = 600); assertEquals(PathState.DEGRADED, h.evaluate())
        feed(2); assertEquals(PathState.DEGRADED, h.evaluate())
        feed(4); assertEquals(PathState.UP, h.evaluate())
    }
}
