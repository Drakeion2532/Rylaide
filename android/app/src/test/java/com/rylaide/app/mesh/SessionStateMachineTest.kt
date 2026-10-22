package com.rylaide.app.mesh

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStateMachineTest {
    private var t = 0L
    private val m = SessionStateMachine { t }
    private val UP = PathState.UP
    private val DEG = PathState.DEGRADED
    private val DOWN = PathState.DOWN

    @Test fun startsConnecting() { assertEquals(SessionState.CONNECTING, m.update(DOWN, DOWN)) }

    @Test fun firstPathLeavesConnectingImmediately() {
        assertEquals(SessionState.MESH, m.update(UP, DOWN))
    }

    @Test fun bothPathsGiveBoth() {
        m.update(UP, DOWN); t += 6_000
        assertEquals(SessionState.BOTH, m.update(UP, UP))
    }

    @Test fun holdsStateForFiveSeconds() {
        m.update(UP, DOWN); t += 6_000; m.update(UP, UP)
        t += 1_000
        assertEquals(SessionState.BOTH, m.update(UP, DOWN))
        t += 4_500
        assertEquals(SessionState.MESH, m.update(UP, DOWN))
    }

    @Test fun internetOnlyAndDegradedAndDisconnected() {
        m.update(DOWN, UP); assertEquals(SessionState.INTERNET, m.state)
        t += 6_000; assertEquals(SessionState.DEGRADED, m.update(DOWN, DEG))
        t += 6_000; assertEquals(SessionState.DISCONNECTED, m.update(DOWN, DOWN))
    }

    @Test fun oneUpOneDegradedShowsTheUpOne() {
        assertEquals(SessionState.MESH, m.update(UP, DEG))
    }
}
