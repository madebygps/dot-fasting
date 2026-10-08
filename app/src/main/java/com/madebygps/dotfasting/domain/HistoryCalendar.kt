package com.madebygps.dotfasting.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

fun historyDate(session: FastSession, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(session.endEpochMillis ?: session.startEpochMillis).atZone(zone).toLocalDate()

fun historyDays(sessions: List<FastSession>, zone: ZoneId): Map<LocalDate, List<FastSession>> =
    sessions.groupBy { historyDate(it, zone) }.mapValues { (_, records) ->
        records.sortedByDescending { it.endEpochMillis ?: it.startEpochMillis }
    }

fun calendarCells(month: YearMonth): List<LocalDate?> {
    val leading = month.atDay(1).dayOfWeek.value - 1
    val cells = List<LocalDate?>(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    return cells + List<LocalDate?>((7 - cells.size % 7) % 7) { null }
}
