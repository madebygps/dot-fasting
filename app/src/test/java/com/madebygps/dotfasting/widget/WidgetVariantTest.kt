package com.madebygps.dotfasting.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetVariantTest {
    @Test fun compactTilesUseRingOnlyLayout() {
        assertEquals(WidgetVariant.COMPACT, WidgetVariant.forSize(70f, 70f))
    }

    @Test fun shortOrWideWidgetsUseDetailedHorizontalLayout() {
        assertEquals(WidgetVariant.WIDE, WidgetVariant.forSize(250f, 90f))
        assertEquals(WidgetVariant.WIDE, WidgetVariant.forSize(300f, 180f))
    }

    @Test fun mediumSquareWidgetsUseStackedRingLayout() {
        assertEquals(WidgetVariant.RING, WidgetVariant.forSize(180f, 180f))
    }

    @Test fun largeSquareWidgetsUseLargeDetailedLayout() {
        assertEquals(WidgetVariant.LARGE, WidgetVariant.forSize(260f, 260f))
    }
}
