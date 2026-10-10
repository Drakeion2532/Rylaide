import Foundation

/// WebSocket client for the backend signaling server. Message shapes must
/// stay in sync with backend/src/signaling/socketServer.ts - same
/// contract as the Android SignalingClient.
///
/// TODO:
///  - Connect using URLSessionWebSocketTask to wss://<your-render-url>/signaling
///  - Implement createGroup(name:), joinGroup(code:)
///  - Forward incoming "signal" messages to WebRtcAudioEngine
///  - Send local SDP/ICE candidates via "signal" messages
final class SignalingClient {

    private let serverUrl: URL

    init(serverUrl: URL) {
        self.serverUrl = serverUrl
    }

    func connect() {
        // TODO: open WebSocket connection
    }

    func createGroup(name: String) {
        // TODO: send { type: "create-group", name }
    }

    func joinGroup(code: String) {
        // TODO: send { type: "join-group", code }
    }

    func sendSignal(targetRiderId: String, payload: [String: Any]) {
        // TODO: send { type: "signal", targetRiderId, payload }
    }
}
