package com.madebygps.dotfasting.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetMinuteRefreshTest {
    @Test fun schedulesAtNextElapsedMinuteBoundary() {
        assertEquals(60_000L, WidgetMinuteRefresh.nextDelayMillis(0))
        assertEquals(59_999L, WidgetMinuteRefresh.nextDelayMillis(1))
        assertEquals(1L, WidgetMinuteRefresh.nextDelayMillis(59_999))
        assertEquals(60_000L, WidgetMinuteRefresh.nextDelayMillis(60_000))
        assertEquals(30_000L, WidgetMinuteRefresh.nextDelayMillis(90_000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeElapsedTime() {
        WidgetMinuteRefresh.nextDelayMillis(-1)
    }
}
