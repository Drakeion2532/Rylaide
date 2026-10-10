/// Same role as its Android counterpart: the single audio engine used for
/// BOTH the offline mesh path (via Multipeer Connectivity) and the internet
/// path. Uses the GoogleWebRTC pod/package so the codec (Opus) and
/// connection behavior match Android exactly.
///
/// TODO:
///  - Add GoogleWebRTC via Swift Package Manager or CocoaPods
///  - Initialize an RTCPeerConnectionFactory once at app startup
///  - Create an RTCPeerConnection per connected rider
///  - Feed local mic audio (post-VAD) into the connection
///  - Play remote audio through whatever BluetoothAudioManager configured
final class WebRtcAudioEngine {

    func createPeerConnection(toRemoteRiderId riderId: String) {
        // TODO: set up RTCPeerConnection, wire SDP/ICE callbacks to
        // whichever signaling path PathSelector says is active
    }

    func closeConnection(riderId: String) {
        // TODO: tear down cleanly
    }
}
