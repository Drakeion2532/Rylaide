// Copyright (c) 2026 Shubhan Punde · https://github.com/Drakeion2532/Rylaide · All rights reserved · sp2532-19f472e3b826
package com.rylaide.app.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {
    @Test fun initiatorRetriesFirstAfterOneSecond() =
        assertEquals(1000L, ReconnectPolicy.delayMs(0, true, 0.0))

    @Test fun otherPhoneWaitsAHeadStart() =
        assertEquals(4000L, ReconnectPolicy.delayMs(0, false, 0.0))

    @Test fun waitGrowsThenStops() {
        assertEquals(1500L, ReconnectPolicy.delayMs(1, true, 0.0))
        assertEquals(4000L, ReconnectPolicy.delayMs(6, true, 0.0))
        assertEquals(4000L, ReconnectPolicy.delayMs(60, true, 0.0))
    }

    @Test fun jitterStaysInRange() {
        assertEquals(1400L, ReconnectPolicy.delayMs(0, true, 1.0))
        assertEquals(1000L, ReconnectPolicy.delayMs(0, true, -5.0))
        assertEquals(1400L, ReconnectPolicy.delayMs(0, true, 9.0))
    }

    @Test fun phonesNeverRetryTogether() {
        // For the same attempt, the two phones' waits differ by at least the head start minus the jitter.
        val gap = ReconnectPolicy.delayMs(2, false, 0.0) - ReconnectPolicy.delayMs(2, true, 1.0)
        assertTrue(gap >= ReconnectPolicy.OTHER_PHONE_HEAD_START_MS - ReconnectPolicy.JITTER_MS)
    }
}
