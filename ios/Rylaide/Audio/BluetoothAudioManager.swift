import AVFoundation

/// Routes mic input and speaker output through the rider's paired Bluetooth
/// headset instead of the phone's own mic/speaker. Works with any headset
/// brand (EJEAS, Cardo, Sena, generic) since this uses the standard
/// Bluetooth Hands-Free Profile via AVAudioSession, not any manufacturer's
/// proprietary intercom protocol.
///
/// TODO:
///  - Configure AVAudioSession category .playAndRecord with .allowBluetooth
///  - Confirm the session actually routed to the headset (check
///    currentRoute.outputs) before enabling the mic toggle in the UI
///  - Observe routeChangeNotification to detect the headset disconnecting
///    mid-ride and warn the rider rather than failing silently
final class BluetoothAudioManager {

    func connectToHeadset() {
        // TODO: configure AVAudioSession for Bluetooth HFP routing
    }

    func isHeadsetConnected() -> Bool {
        // TODO: check AVAudioSession.sharedInstance().currentRoute
        return false
    }
}
