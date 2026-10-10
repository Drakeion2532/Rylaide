package com.rylaide.app.signaling

/**
 * WebSocket client that talks to the backend signaling server (see
 * backend/src/signaling/socketServer.ts - keep message shapes in sync
 * with that file). Only used for the internet path; the mesh path
 * exchanges the same kind of messages directly peer-to-peer instead.
 *
 * TODO:
 *  - Connect to wss://<your-render-url>/signaling
 *  - Implement createGroup(name), joinGroup(code)
 *  - Forward incoming "signal" messages to WebRtcAudioEngine
 *  - Send local SDP/ICE candidates out via "signal" messages
 */
class SignalingClient(private val serverUrl: String) {

    fun connect() {
        // TODO: open WebSocket connection
    }

    fun createGroup(name: String) {
        // TODO: send { type: "create-group", name }
    }

    fun joinGroup(code: String) {
        // TODO: send { type: "join-group", code }
    }

    fun sendSignal(targetRiderId: String, payload: Any) {
        // TODO: send { type: "signal", targetRiderId, payload }
    }
}
