package com.rylaide.app.maps

/**
 * GPS position always works offline (satellite signal, no internet needed).
 * This module handles the map imagery underneath that GPS dot, which DOES
 * need pre-cached tiles to show up with no connectivity.
 *
 * TODO:
 *  - Wire up Mapbox or HERE SDK (pick one, both have workable free tiers)
 *  - Add an explicit "download map for this area" action before a ride
 *  - Show the rider's live GPS position on cached tiles regardless of
 *    connectivity
 *  - Share position with the rest of the group over whichever path
 *    (mesh or internet) PathSelector says is currently active
 */
class MapManager {

    fun downloadOfflineTiles(regionBoundsPlaceholder: Any) {
        // TODO: implement offline tile pre-caching
    }

    fun getCurrentLocation() {
        // TODO: use LocationManager/FusedLocationProviderClient
    }
}
