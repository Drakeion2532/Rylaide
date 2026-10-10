package com.rylaide.app.pathselector

import com.rylaide.app.mesh.MeshDiscoveryManager
import com.rylaide.app.signaling.SignalingClient

/**
 * The most important piece of custom logic in the whole app. Continuously
 * checks connectivity and decides, for each connection to another rider,
 * whether to use the offline mesh path or the internet path - and re-checks
 * as conditions change mid-ride, without dropping the call.
 *
 * Decision order per rider we're trying to reach:
 *   1. Are we in mesh range of them right now? -> use mesh (works with zero
 *      internet, this is checked first because it's free and always works
 *      at close range)
 *   2. Otherwise, is internet available? -> use internet path (direct P2P
 *      if possible, TURN relay as fallback - WebRtcAudioEngine handles
 *      that distinction internally via ICE)
 *   3. Otherwise -> no path available, surface this clearly in the UI
 *      rather than failing silently
 *
 * TODO:
 *  - Register as a listener on both MeshDiscoveryManager and network
 *    connectivity changes (ConnectivityManager.NetworkCallback)
 *  - On any change, re-evaluate the best path per connected rider
 *  - Trigger a WebRTC ICE restart when switching from mesh to internet or
 *    vice versa, so the existing call doesn't drop
 */
class PathSelector(
    private val meshDiscoveryManager: MeshDiscoveryManager,
    private val signalingClient: SignalingClient
) {

    fun evaluateBestPath(riderId: String): ConnectionPath {
        // TODO: implement the real decision logic described above
        return ConnectionPath.NONE
    }
}

enum class ConnectionPath {
    MESH,
    INTERNET,
    NONE
}
