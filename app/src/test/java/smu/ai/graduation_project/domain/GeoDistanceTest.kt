package smu.ai.graduation_project.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoDistanceTest {

    @Test
    fun formatsMetersUnderOneKilometer() {
        assertEquals("0m", GeoDistance.format(0.0))
        assertEquals("320m", GeoDistance.format(320.0))
        assertEquals("999m", GeoDistance.format(999.4))
    }

    @Test
    fun formatsKilometersAtOrAboveOneKilometer() {
        assertEquals("1.0km", GeoDistance.format(1000.0))
        assertEquals("1.2km", GeoDistance.format(1234.0))
        assertEquals("12.3km", GeoDistance.format(12_345.0))
    }
}
