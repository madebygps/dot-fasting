package com.madebygps.dotfasting.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FastingInsightsTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 9)

    @Test
    fun summarizesCompletedFastsAndIgnoresActiveSession() {
        val sessions = listOf(
            completed(1, "2026-10-07T20:00:00Z", "2026-10-08T12:00:00Z", 16),
            completed(2, "2026-10-08T20:00:00Z", "2026-10-09T14:00:00Z", 16),
            FastSession(3, epoch("2026-10-09T20:00:00Z"), goalMillis = hours(16)),
        )

        val insights = fastingInsights(sessions, zone, today)

        assertEquals(2, insights.completedCount)
        assertEquals(hours(34), insights.totalDurationMillis)
        assertEquals(hours(17), insights.averageDurationMillis)
        assertEquals(2, insights.goalHitCount)
        assertEquals(100, insights.goalHitPercent)
        assertEquals(2, insights.currentStreakDays)
        assertEquals(2, insights.longestStreakDays)
        assertEquals(hours(16), insights.recentDays[5].durationMillis)
        assertEquals(hours(18), insights.recentDays[6].durationMillis)
    }

    @Test
    fun currentStreakRemainsActiveWhenLatestCompletionWasYesterday() {
        val sessions = listOf(
            completed(1, "2026-10-05T20:00:00Z", "2026-10-06T12:00:00Z", 18),
            completed(2, "2026-10-06T20:00:00Z", "2026-10-07T12:00:00Z", 16),
            completed(3, "2026-10-07T20:00:00Z", "2026-10-08T12:00:00Z", 16),
        )

        val insights = fastingInsights(sessions, zone, today)

        assertEquals(3, insights.currentStreakDays)
        assertEquals(3, insights.longestStreakDays)
        assertEquals(2, insights.goalHitCount)
        assertEquals(66, insights.goalHitPercent)
    }

    @Test
    fun emptyHistoryProducesSevenZeroDays() {
        val insights = fastingInsights(emptyList(), zone, today)

        assertEquals(0, insights.completedCount)
        assertEquals(0, insights.currentStreakDays)
        assertEquals(7, insights.recentDays.size)
        assertEquals(today.minusDays(6), insights.recentDays.first().date)
        assertEquals(today, insights.recentDays.last().date)
        assertEquals(List(7) { 0L }, insights.recentDays.map { it.durationMillis })
    }

    private fun completed(id: Long, start: String, end: String, goalHours: Long): FastSession {
        val startMillis = epoch(start)
        val endMillis = epoch(end)
        return FastSession(
            id = id,
            startEpochMillis = startMillis,
            endEpochMillis = endMillis,
            goalMillis = hours(goalHours),
            completedDurationMillis = endMillis - startMillis,
        )
    }

    private fun epoch(value: String): Long = Instant.parse(value).toEpochMilli()
    private fun hours(value: Long): Long = value * 3_600_000L
}
