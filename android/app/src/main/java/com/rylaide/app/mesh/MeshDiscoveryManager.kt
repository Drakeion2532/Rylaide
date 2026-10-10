package com.rylaide.app.mesh

/**
 * Finds and connects to nearby riders running this app, with no internet
 * required. Wraps Google's Nearby Connections API, which itself uses a
 * mix of Bluetooth and Wi-Fi Direct depending on what's fastest/available.
 *
 * Once two phones are connected here, WebRTC session details (SDP/ICE) get
 * exchanged directly over this connection - no signaling server involved
 * for the offline case at all.
 *
 * TODO:
 *  - Start advertising (so others can find you) and discovery (so you can
 *    find others) using Nearby Connections' P2P_CLUSTER strategy for
 *    small groups (~6-10 riders)
 *  - On a successful connection, hand the raw payload channel to
 *    PathSelector so it can exchange WebRTC signaling messages
 *  - Handle a rider going out of range and automatically reconnecting when
 *    they come back - Nearby Connections re-discovers automatically as
 *    long as advertising/discovery stay active in the background
 */
class MeshDiscoveryManager {

    fun startAdvertisingAndDiscovery(groupCode: String) {
        // TODO: implement Nearby Connections advertise + discover
    }

    fun stop() {
        // TODO: stop advertising/discovery cleanly
    }
}
