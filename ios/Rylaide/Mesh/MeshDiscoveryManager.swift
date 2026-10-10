import MultipeerConnectivity

/// Finds and connects to nearby riders running this app, with no internet
/// required. Apple only allows Wi-Fi Direct-equivalent networking through
/// this framework - there's no raw Wi-Fi Direct API on iOS the way there
/// is on Android.
///
/// TODO:
///  - Create an MCSession, MCNearbyServiceAdvertiser, and
///    MCNearbyServiceBrowser
///  - Start advertising and browsing using the ride group code as the
///    service type/discovery info
///  - On a successful connection, hand the MCSession to PathSelector so it
///    can exchange WebRTC signaling messages over it directly
final class MeshDiscoveryManager: NSObject {

    func startAdvertisingAndBrowsing(groupCode: String) {
        // TODO: implement MultipeerConnectivity setup
    }

    func stop() {
        // TODO: stop advertiser/browser cleanly
    }
}
