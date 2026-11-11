// Copyright (c) 2026 Shubhan Punde · https://github.com/Drakeion2532/Rylaide · All rights reserved · sp2532-19f472e3b826
package com.rylaide.app.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkTimingsTest {
    private var now = 1_000_000L
    private val t = LinkTimings { now }

    @Test fun firstConnectIsTimedFromReset() {
        t.reset()
        now += 6_200
        t.onLinked()
        assertEquals(6.2, t.firstConnectSeconds, 0.001)
    }

    @Test fun laterConnectsDoNotChangeFirstConnect() {
        t.reset(); now += 3_000; t.onLinked()
        now += 5_000; t.onLinked()
        assertEquals(3.0, t.firstConnectSeconds, 0.001)
    }

    @Test fun outageIsTimedUntilTheLinkReturns() {
        t.reset(); now += 2_000; t.onLinked()
        now += 10_000; t.onAllLost()
        now += 4_500; t.onLinked()
        assertEquals(listOf(4.5), t.recoverySeconds)
    }

    @Test fun repeatedLossDoesNotRestartTheOutage() {
        t.reset(); now += 1_000; t.onLinked()
        t.onAllLost(); now += 2_000; t.onAllLost(); now += 1_000; t.onLinked()
        assertEquals(listOf(3.0), t.recoverySeconds)
    }

    @Test fun currentOutageShowsWhileDown() {
        t.reset(); now += 1_000; t.onLinked()
        assertEquals(-1.0, t.currentOutageSeconds(), 0.0)
        t.onAllLost(); now += 2_500
        assertEquals(2.5, t.currentOutageSeconds(), 0.001)
    }

    @Test fun summaryReadsInPlainWords() {
        t.reset(); now += 8_000; t.onLinked()
        t.onAllLost(); now += 5_000; t.onLinked()
        val s = t.summary(1)
        assertTrue(s.contains("First connect: 8.0 s"))
        assertTrue(s.contains("Drops: 1"))
        assertTrue(s.contains("5.0 s"))
    }
}
