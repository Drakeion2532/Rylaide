import CoreLocation

/// GPS position always works offline (satellite signal). This handles the
/// map imagery underneath, which needs pre-cached tiles to render with no
/// connectivity.
///
/// TODO:
///  - Wire up Mapbox or HERE SDK (match whichever you picked for Android)
///  - Add an explicit "download map for this area" action before a ride
///  - Show live GPS position on cached tiles regardless of connectivity
///  - Share position with the group over whichever path PathSelector says
///    is currently active
final class MapManager: NSObject, CLLocationManagerDelegate {

    private let locationManager = CLLocationManager()

    func downloadOfflineTiles(regionPlaceholder: Any) {
        // TODO: implement offline tile pre-caching
    }

    func getCurrentLocation() {
        // TODO: request location via CLLocationManager
    }
}
