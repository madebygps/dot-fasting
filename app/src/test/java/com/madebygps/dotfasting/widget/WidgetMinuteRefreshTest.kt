package com.madebygps.dotfasting.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetMinuteRefreshTest {
    @Test fun schedulesAtNextElapsedMinuteBoundary() {
        assertEquals(60_000L, WidgetMinuteRefresh.nextDelayMillis(0, countDown = false))
        assertEquals(59_999L, WidgetMinuteRefresh.nextDelayMillis(1, countDown = false))
        assertEquals(1L, WidgetMinuteRefresh.nextDelayMillis(59_999, countDown = false))
        assertEquals(60_000L, WidgetMinuteRefresh.nextDelayMillis(60_000, countDown = false))
        assertEquals(30_000L, WidgetMinuteRefresh.nextDelayMillis(90_000, countDown = false))
    }

    @Test fun schedulesWhenDisplayedRemainingMinuteWillChange() {
        assertEquals(1L, WidgetMinuteRefresh.nextDelayMillis(60_000, countDown = true))
        assertEquals(5_001L, WidgetMinuteRefresh.nextDelayMillis(65_000, countDown = true))
        assertEquals(30_001L, WidgetMinuteRefresh.nextDelayMillis(90_000, countDown = true))
        assertEquals(60_000L, WidgetMinuteRefresh.nextDelayMillis(0, countDown = true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeCounterTime() {
        WidgetMinuteRefresh.nextDelayMillis(-1, countDown = false)
    }
}
