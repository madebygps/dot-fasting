package com.madebygps.dotfasting.glyph

import android.content.Context
import android.os.Build
import com.nothing.ketchum.Common

internal object GlyphPlatformAdapter : GlyphPlatform {
    fun supportsHardware(): Boolean =
        Build.MANUFACTURER.equals("Nothing", ignoreCase = true) &&
            Build.MODEL.equals("A024", ignoreCase = true) &&
            Common.getDeviceMatrixLength() == GlyphFrameRenderer.SIZE

    override fun capability(context: Context, enabled: Boolean): GlyphCapability {
        val supported = supportsHardware()
        val state = when {
            !supported -> GlyphCapabilityState.UNSUPPORTED_HARDWARE
            !enabled -> GlyphCapabilityState.DISABLED
            else -> GlyphCapabilityState.READY
        }
        return GlyphCapability(
            state = state,
            sdkIncluded = true,
            hardwareSupported = supported,
            enabled = enabled,
        )
    }
}
