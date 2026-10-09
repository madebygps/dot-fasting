package com.madebygps.dotfasting.domain

import java.time.LocalDate
import java.time.ZoneId

data class DailyFastingTotal(
    val date: LocalDate,
    val durationMillis: Long,
)

data class FastingInsights(
    val completedCount: Int,
    val totalDurationMillis: Long,
    val averageDurationMillis: Long,
    val goalHitCount: Int,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val recentDays: List<DailyFastingTotal>,
) {
    val goalHitPercent: Int
        get() = if (completedCount == 0) 0 else (goalHitCount * 100 / completedCount)
}

fun fastingInsights(
    sessions: List<FastSession>,
    zone: ZoneId,
    today: LocalDate = LocalDate.now(zone),
): FastingInsights {
    val completed = sessions.mapNotNull { session ->
        val end = session.endEpochMillis ?: return@mapNotNull null
        val duration = (session.completedDurationMillis ?: (end - session.startEpochMillis))
            .coerceAtLeast(0L)
        Triple(session, historyDate(session, zone), duration)
    }
    val totalsByDay = completed.groupBy { it.second }.mapValues { (_, records) ->
        records.fold(0L) { total, record -> saturatedDurationAdd(total, record.third) }
    }
    val completedDays = totalsByDay.keys
    val sortedDays = completedDays.sorted()
    var longestStreak = 0
    var runningStreak = 0
    var previous: LocalDate? = null
    sortedDays.forEach { date ->
        runningStreak = if (previous?.plusDays(1) == date) runningStreak + 1 else 1
        longestStreak = maxOf(longestStreak, runningStreak)
        previous = date
    }
    val currentStreak = when {
        today in completedDays -> streakEndingOn(today, completedDays)
        today.minusDays(1) in completedDays -> streakEndingOn(today.minusDays(1), completedDays)
        else -> 0
    }
    val total = completed.fold(0L) { sum, record -> saturatedDurationAdd(sum, record.third) }
    return FastingInsights(
        completedCount = completed.size,
        totalDurationMillis = total,
        averageDurationMillis = if (completed.isEmpty()) 0L else total / completed.size,
        goalHitCount = completed.count { (session, _, duration) -> duration >= session.goalMillis },
        currentStreakDays = currentStreak,
        longestStreakDays = longestStreak,
        recentDays = (6L downTo 0L).map { daysAgo ->
            val date = today.minusDays(daysAgo)
            DailyFastingTotal(date, totalsByDay[date] ?: 0L)
        },
    )
}

private fun streakEndingOn(end: LocalDate, completedDays: Set<LocalDate>): Int {
    var count = 0
    var date = end
    while (date in completedDays) {
        count += 1
        date = date.minusDays(1)
    }
    return count
}

private fun saturatedDurationAdd(first: Long, second: Long): Long =
    if (Long.MAX_VALUE - first < second) Long.MAX_VALUE else first + second
