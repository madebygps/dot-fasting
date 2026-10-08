package com.madebygps.dotfasting.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RingGeometryTest {
    @Test fun startsAtTwelveAndSweepsClockwise() {
        assertEquals(-90f, RingGeometry.START_DEGREES)
        assertEquals(0f, RingGeometry.sweep(0f))
        assertEquals(180f, RingGeometry.sweep(0.5f))
        assertEquals(360f, RingGeometry.sweep(1f))
    }

    @Test fun clampsAllBoundariesIncludingNonFiniteInputs() {
        assertEquals(0f, RingGeometry.sweep(-1f))
        assertEquals(360f, RingGeometry.sweep(2f))
        assertEquals(0f, RingGeometry.sweep(Float.NaN))
        assertEquals(0f, RingGeometry.sweep(Float.NEGATIVE_INFINITY))
        assertEquals(360f, RingGeometry.sweep(Float.POSITIVE_INFINITY))
    }
}
