package com.rylaide.app.mesh

/** What the rider sees. */
enum class SessionState { CONNECTING, BOTH, INTERNET, MESH, DEGRADED, DISCONNECTED }

/**
 * Turns the health of the offline link and the internet path into one rider-facing state.
 * The shown state holds for at least [HOLD_MS] before changing, so the screen never flaps.
 * This only affects what is displayed; audio is sent on every usable path regardless.
 */
class SessionStateMachine(private val clock: () -> Long) {
    companion object { const val HOLD_MS = 5_000L }

    var state = SessionState.CONNECTING
        private set
    private var changedAt = clock()
    private var everConnected = false

    fun target(offline: PathState, internet: PathState): SessionState {
        val off = offline == PathState.UP
        val net = internet == PathState.UP
        return when {
            off && net -> SessionState.BOTH
            net -> SessionState.INTERNET
            off -> SessionState.MESH
            offline == PathState.DEGRADED || internet == PathState.DEGRADED -> SessionState.DEGRADED
            everConnected -> SessionState.DISCONNECTED
            else -> SessionState.CONNECTING
        }
    }

    fun update(offline: PathState, internet: PathState): SessionState {
        val now = clock()
        val wanted = target(offline, internet)
        if (offline != PathState.DOWN || internet != PathState.DOWN) everConnected = true
        if (wanted != state) {
            val leavingConnecting = state == SessionState.CONNECTING
            if (leavingConnecting || now - changedAt >= HOLD_MS) {
                state = wanted
                changedAt = now
            }
        }
        return state
    }
}
