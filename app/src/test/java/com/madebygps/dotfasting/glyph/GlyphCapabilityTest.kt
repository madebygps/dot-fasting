package com.madebygps.dotfasting.glyph

import org.junit.Assert.assertEquals
import org.junit.Test

class GlyphCapabilityTest {
    @Test
    fun unknownConnectionIsNotReady() {
        assertEquals(GlyphCapabilityState.SETUP_REQUIRED, supported.withServiceAvailability(null).state)
    }

    @Test
    fun actualConnectionAndFailureRemainDistinct() {
        assertEquals(GlyphCapabilityState.READY, supported.withServiceAvailability(true).state)
        assertEquals(GlyphCapabilityState.SERVICE_UNAVAILABLE, supported.withServiceAvailability(false).state)
    }

    @Test
    fun connectionCannotOverrideHardwareSdkOrOptOut() {
        listOf(
            GlyphCapabilityState.SDK_NOT_INCLUDED,
            GlyphCapabilityState.UNSUPPORTED_HARDWARE,
            GlyphCapabilityState.DISABLED,
        ).forEach { state ->
            val unavailable = supported.copy(state = state)
            assertEquals(unavailable, unavailable.withServiceAvailability(true))
            assertEquals(unavailable, unavailable.withServiceAvailability(false))
            assertEquals(unavailable, unavailable.withServiceAvailability(null))
        }
    }

    private val supported = GlyphCapability(
        state = GlyphCapabilityState.READY,
        sdkIncluded = true,
        hardwareSupported = true,
        enabled = true,
    )
}
