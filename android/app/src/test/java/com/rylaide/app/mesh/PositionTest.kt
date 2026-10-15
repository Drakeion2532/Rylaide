package com.rylaide.app.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PositionTest {
    @Test fun roundTripKeepsPlaceToAboutAMetre() {
        val p = Position(18.520430, 73.856744, 7)
        val back = PositionCodec.decode(PositionCodec.encode(p), 0)!!
        assertEquals(p.lat, back.lat, 1e-6)
        assertEquals(p.lon, back.lon, 1e-6)
        assertEquals(7, back.accuracyM)
    }

    @Test fun southernAndWesternPlacesSurviveNegativeNumbers() {
        val p = Position(-33.868820, -151.209296, 12)
        val back = PositionCodec.decode(PositionCodec.encode(p), 0)!!
        assertEquals(p.lat, back.lat, 1e-6)
        assertEquals(p.lon, back.lon, 1e-6)
    }

    @Test fun decodeReadsFromAnOffsetAndRejectsShortOrImpossibleData() {
        val body = PositionCodec.encode(Position(10.0, 20.0, 5))
        val padded = ByteArray(8) + body
        assertNotNull(PositionCodec.decode(padded, 8))
        assertNull(PositionCodec.decode(padded, 9))
        assertNull(PositionCodec.decode(ByteArray(10) { 0x7F }, 0))
    }

    @Test fun oneThousandthOfADegreeOfLatitudeIsAbout111Metres() {
        val d = GeoMath.distanceMeters(Position(18.000, 73.0, 0), Position(18.001, 73.0, 0))
        assertEquals(111.2, d, 1.0)
    }

    @Test fun knownCityPairIsRightToWithinAFewKm() {
        // Pune to Mumbai, about 120 km in a straight line.
        val d = GeoMath.distanceMeters(Position(18.5204, 73.8567, 0), Position(19.0760, 72.8777, 0))
        assertEquals(120_000.0, d, 5_000.0)
    }

    @Test fun samePlaceIsZeroAndFormatSwitchesToKm() {
        val p = Position(1.0, 2.0, 0)
        assertEquals(0.0, GeoMath.distanceMeters(p, p), 0.001)
        assertEquals("85 m", GeoMath.format(85.4))
        assertEquals("1.2 km", GeoMath.format(1234.0))
    }
}
