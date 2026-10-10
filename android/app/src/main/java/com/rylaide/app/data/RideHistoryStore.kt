package com.rylaide.app.data

/**
 * Local storage for ride history/settings. Nothing here needs to touch
 * the backend - this is purely on-device, keeps the app usable and
 * meaningful even for a rider who never has a data connection.
 *
 * TODO: back this with Room (SQLite) once there's more than a placeholder
 */
class RideHistoryStore {

    fun saveRide(distanceMeters: Double, durationSeconds: Long) {
        // TODO: persist to local database
    }

    fun getAllRides(): List<Any> {
        // TODO: query local database
        return emptyList()
    }
}
