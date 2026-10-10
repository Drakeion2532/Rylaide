import Foundation

enum ConnectionPath {
    case mesh
    case internetPath
    case none
}

/// The most important piece of custom logic in the app, mirroring
/// Android's PathSelector exactly. Decides per connected rider whether to
/// use the offline mesh or the internet path, re-evaluating continuously
/// as conditions change mid-ride without dropping the call.
///
/// Decision order per rider:
///   1. In mesh range right now? -> use mesh
///   2. Otherwise, internet available? -> use internet path (direct P2P
///      if possible, TURN fallback handled internally by WebRTC's ICE)
///   3. Otherwise -> no path, surface this clearly in the UI
///
/// TODO:
///  - Observe NWPathMonitor for internet connectivity changes
///  - Observe MeshDiscoveryManager for peers entering/leaving range
///  - Trigger a WebRTC ICE restart on path switches so calls don't drop
final class PathSelector {

    private let meshDiscoveryManager: MeshDiscoveryManager
    private let signalingClient: SignalingClient

    init(meshDiscoveryManager: MeshDiscoveryManager, signalingClient: SignalingClient) {
        self.meshDiscoveryManager = meshDiscoveryManager
        self.signalingClient = signalingClient
    }

    func evaluateBestPath(riderId: String) -> ConnectionPath {
        // TODO: implement the real decision logic described above
        return .none
    }
}
