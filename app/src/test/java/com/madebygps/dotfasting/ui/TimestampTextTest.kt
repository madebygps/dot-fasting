package com.madebygps.dotfasting.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class TimestampTextTest {
    @Test fun localTimestampOmitsOffsetWhilePreservingTheLocalDate() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val epoch = Instant.parse("2026-10-08T17:00:00Z").toEpochMilli()
            val text = timestampText(epoch)
            assertFalse(text.contains("-04:00"))
            assertFalse(text.contains("("))
            assertTrue(text.startsWith(dateText(epoch)))
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
