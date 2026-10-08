package com.madebygps.dotfasting.glyph

import android.content.Context

internal object GlyphPlatformAdapter : GlyphPlatform {
    override fun capability(context: Context, enabled: Boolean) = GlyphCapability(
        state = GlyphCapabilityState.SDK_NOT_INCLUDED,
        sdkIncluded = false,
        hardwareSupported = null,
        enabled = enabled,
    )
}
