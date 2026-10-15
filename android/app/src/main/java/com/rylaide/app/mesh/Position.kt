package com.rylaide.app.mesh

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A rider's GPS fix as shared with other riders: where, and how sure the phone is (metres). */
data class Position(val lat: Double, val lon: Double, val accuracyM: Int)

object PositionCodec {
    /** 10 bytes: latitude and longitude in millionths of a degree (4 bytes each), accuracy in metres (2 bytes). */
    const val SIZE = 10

    fun encode(p: Position): ByteArray {
        val la = Math.round(p.lat * 1_000_000).toInt()
        val lo = Math.round(p.lon * 1_000_000).toInt()
        val ac = min(p.accuracyM.coerceAtLeast(0), 65535)
        return byteArrayOf(
            (la ushr 24).toByte(), (la ushr 16).toByte(), (la ushr 8).toByte(), la.toByte(),
            (lo ushr 24).toByte(), (lo ushr 16).toByte(), (lo ushr 8).toByte(), lo.toByte(),
            (ac shr 8).toByte(), ac.toByte()
        )
    }

    /** Reads a position from [bytes] starting at [offset], or null if it is too short or not a real place. */
    fun decode(bytes: ByteArray, offset: Int): Position? {
        if (bytes.size - offset < SIZE) return null
        fun int(i: Int) = ((bytes[offset + i].toInt() and 0xFF) shl 24) or
            ((bytes[offset + i + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + i + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + i + 3].toInt() and 0xFF)
        val lat = int(0) / 1_000_000.0
        val lon = int(4) / 1_000_000.0
        val acc = ((bytes[offset + 8].toInt() and 0xFF) shl 8) or (bytes[offset + 9].toInt() and 0xFF)
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return Position(lat, lon, acc)
    }
}

object GeoMath {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Straight-line distance over the ground between two points, in metres (haversine). */
    fun distanceMeters(a: Position, b: Position): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    /** "85 m" under a kilometre, "1.2 km" above. */
    fun format(meters: Double): String =
        if (meters < 1000) "${meters.toInt()} m" else "%.1f km".format(meters / 1000)
}
