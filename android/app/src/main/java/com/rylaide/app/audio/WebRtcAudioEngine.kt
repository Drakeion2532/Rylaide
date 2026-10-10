package com.rylaide.app.audio

/**
 * The single audio engine used for BOTH the offline mesh path and the
 * internet path. WebRTC handles Opus encoding/decoding and the actual
 * peer connection either way - only how the two peers *find* each other
 * differs (see mesh/MeshDiscoveryManager.kt vs signaling/SignalingClient.kt).
 *
 * TODO:
 *  - Initialize a PeerConnectionFactory once at app startup
 *  - Create a new PeerConnection per rider you're connected to
 *  - Feed local mic audio (after VAD filtering) into the peer connection
 *  - Play remote audio through whatever BluetoothAudioManager has set up
 */
class WebRtcAudioEngine {

    fun createPeerConnectionTo(remoteRiderId: String) {
        // TODO: set up PeerConnection, wire up SDP/ICE callbacks to
        // whichever signaling path is active (mesh or server) via PathSelector
    }

    fun closeConnection(remoteRiderId: String) {
        // TODO: tear down the peer connection cleanly
    }
}
