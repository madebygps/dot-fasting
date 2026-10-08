package com.madebygps.dotfasting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class HistoryCalendarTest {
    @Test fun completedFastsBelongToLocalEndDayRegardlessOfGoal() {
        val start = Instant.parse("2026-10-08T02:00:00Z").toEpochMilli()
        val end = Instant.parse("2026-10-08T05:00:00Z").toEpochMilli()
        val session = FastSession(1, start, end, 24 * 3_600_000L)
        val zone = ZoneId.of("America/New_York")
        assertEquals(LocalDate.of(2026, 10, 8), historyDate(session, zone))
        assertEquals(LocalDate.of(2026, 10, 7), historyDate(session.copy(endEpochMillis = null), zone))
        val second = session.copy(id = 2, endEpochMillis = end + 1000)
        assertEquals(listOf(second, session), historyDays(listOf(session, second), zone).getValue(LocalDate.of(2026, 10, 8)))
    }

    @Test fun cellsStartMondayAndHandleLeapYears() {
        val month = YearMonth.of(2024, 2)
        val cells = calendarCells(month)
        assertEquals(35, cells.size)
        assertNull(cells[0])
        assertEquals(month.atDay(1), cells[3])
        assertEquals(month.atDay(29), cells[31])
        assertEquals(28, calendarCells(YearMonth.of(2021, 2)).size)
    }
}
