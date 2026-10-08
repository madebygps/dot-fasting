package com.madebygps.dotfasting.glyph

enum class GlyphCapabilityState {
    SDK_NOT_INCLUDED,
    UNSUPPORTED_HARDWARE,
    SERVICE_UNAVAILABLE,
    DISABLED,
    SETUP_REQUIRED,
    READY,
}

data class GlyphCapability(
    val state: GlyphCapabilityState,
    val sdkIncluded: Boolean,
    val hardwareSupported: Boolean?,
    val enabled: Boolean,
) {
    internal fun withServiceAvailability(available: Boolean?): GlyphCapability {
        if (state != GlyphCapabilityState.READY) return this
        return copy(
            state = when (available) {
                true -> GlyphCapabilityState.READY
                false -> GlyphCapabilityState.SERVICE_UNAVAILABLE
                null -> GlyphCapabilityState.SETUP_REQUIRED
            },
        )
    }
}
