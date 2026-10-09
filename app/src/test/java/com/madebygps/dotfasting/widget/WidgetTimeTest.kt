package com.madebygps.dotfasting.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class WidgetTimeTest {
    @Test fun elapsedShowsUnlimitedHoursAndWholeMinutes() {
        assertEquals("0:00", WidgetTime.elapsed(59_999))
        assertEquals("0:01", WidgetTime.elapsed(60_000))
        assertEquals("17:59", WidgetTime.elapsed(64_799_999))
        assertEquals("25:01", WidgetTime.elapsed(90_060_000))
    }

    @Test fun durationDoesNotWrapAtTwentyFourHours() {
        assertEquals("25h 1m", WidgetTime.duration(90_060_000))
        assertEquals("18h", WidgetTime.duration(64_800_000))
        assertEquals("0h", WidgetTime.duration(0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeDuration() { WidgetTime.duration(-1) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeElapsedTime() { WidgetTime.elapsed(-1) }

    @Test fun targetFormattingRespectsTimeZone() {
        val epoch = Instant.parse("2026-03-08T07:00:00Z").toEpochMilli()
        assertEquals("03:00", WidgetTime.clock(epoch, ZoneId.of("America/New_York")))
        assertEquals("07:00", WidgetTime.clock(epoch, ZoneId.of("UTC")))
    }
}
