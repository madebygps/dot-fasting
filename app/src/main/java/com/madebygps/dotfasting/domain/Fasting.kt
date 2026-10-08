package com.madebygps.dotfasting.domain

data class ClockSnapshot(
    val wallEpochMillis: Long,
    val elapsedRealtimeMillis: Long,
    val bootId: String,
)

fun interface FastingClock {
    fun snapshot(): ClockSnapshot
}

data class FastSession(
    val id: Long = 0,
    val startEpochMillis: Long,
    val endEpochMillis: Long? = null,
    val goalMillis: Long,
    val anchorWallEpochMillis: Long? = null,
    val anchorElapsedRealtimeMillis: Long? = null,
    val anchorBootId: String? = null,
    val elapsedAtAnchorMillis: Long = 0,
    val completedDurationMillis: Long? = null,
    val needsTimeReview: Boolean = false,
    val goalNotificationDelivered: Boolean = false,
    val goalNotificationSuppressed: Boolean = false,
)

data class FastProjection(
    val elapsedMillis: Long,
    val remainingMillis: Long,
    val progress: Float,
    val goalMet: Boolean,
    val needsTimeReview: Boolean,
)

data class AppSettings(
    val highlightArgb: Long = 0xFFE8343A,
    val lastGoalMillis: Long? = null,
    val notificationsEnabled: Boolean = false,
    val glyphEnabled: Boolean = false,
    val countDown: Boolean = false,
)

fun counterMillis(projection: FastProjection, countDown: Boolean): Long =
    if (countDown) projection.remainingMillis.coerceAtLeast(0) else projection.elapsedMillis.coerceAtLeast(0)

fun counterText(millis: Long, seconds: Boolean = true): String {
    val total = millis.coerceAtLeast(0) / 1000
    return if (seconds) "%02d:%02d:%02d".format(java.util.Locale.ROOT, total / 3600, total / 60 % 60, total % 60)
    else "%02d:%02d".format(java.util.Locale.ROOT, total / 3600, total / 60 % 60)
}

sealed class FastingException(message: String) : IllegalArgumentException(message) {
    class InvalidGoal : FastingException("Choose a positive goal duration that fits in milliseconds.")
    class InvalidTimestamp : FastingException("Start and end must be valid dates, not in the future.")
    class ReversedInterval : FastingException("The end cannot be before the start.")
    class OverlappingSession : FastingException("This interval overlaps another fast, including an active fast.")
    class AlreadyActive : FastingException("A fast is already active. End it before starting another.")
    class NoActiveSession : FastingException("There is no active fast to end.")
    class SessionNotFound(val sessionId: Long) : FastingException("Fast $sessionId no longer exists.")
    class InvalidState(message: String) : FastingException(message)
    class TimeReviewRequired : FastingException("The device clock changed. Correct the start time before ending this fast.")
    class InvalidHighlight : FastingException("Highlight must be an opaque 32-bit ARGB color.")
    class ClockUnavailable : FastingException("The device boot identity is unavailable; timing cannot be verified.")
    class RefreshFailed(cause: Exception) : FastingException(
        "Your change was saved, but background displays could not refresh. Reopen the app to retry.",
    ) {
        init { initCause(cause) }
    }
}

fun validateGoal(goalMillis: Long) {
    if (goalMillis <= 0) throw FastingException.InvalidGoal()
}

fun goalMillis(hours: Long, minutes: Long): Long {
    if (hours < 0 || minutes !in 0..59) throw FastingException.InvalidGoal()
    return try {
        Math.multiplyExact(Math.addExact(Math.multiplyExact(hours, 60), minutes), 60_000)
            .also(::validateGoal)
    } catch (_: ArithmeticException) {
        throw FastingException.InvalidGoal()
    }
}

fun validateInterval(
    id: Long,
    startEpochMillis: Long,
    endEpochMillis: Long?,
    nowEpochMillis: Long,
    sessions: List<FastSession>,
) {
    if (startEpochMillis < 0 || startEpochMillis > nowEpochMillis ||
        (endEpochMillis != null && (endEpochMillis < 0 || endEpochMillis > nowEpochMillis))
    ) throw FastingException.InvalidTimestamp()
    if (endEpochMillis != null && endEpochMillis < startEpochMillis) {
        throw FastingException.ReversedInterval()
    }
    val end = endEpochMillis ?: Long.MAX_VALUE
    if (end == startEpochMillis) return
    if (sessions.any {
            it.id != id && it.endEpochMillis != it.startEpochMillis &&
                startEpochMillis < (it.endEpochMillis ?: Long.MAX_VALUE) &&
                it.startEpochMillis < end
        }
    ) throw FastingException.OverlappingSession()
}

private fun nonnegativeDifference(end: Long, start: Long): Long =
    if (end <= start) 0 else try {
        Math.subtractExact(end, start)
    } catch (_: ArithmeticException) {
        Long.MAX_VALUE
    }

private fun saturatedAdd(first: Long, second: Long): Long =
    if (Long.MAX_VALUE - first < second) Long.MAX_VALUE else first + second

private fun wallDrifted(session: FastSession, clock: ClockSnapshot): Boolean {
    val wall = session.anchorWallEpochMillis ?: return false
    val monotonic = session.anchorElapsedRealtimeMillis ?: return false
    if (clock.elapsedRealtimeMillis < monotonic) return true
    val delta = clock.elapsedRealtimeMillis - monotonic
    val expected = saturatedAdd(wall.coerceAtLeast(0), delta)
    // Tolerate routine network-time corrections; larger jumps require user review.
    return nonnegativeDifference(maxOf(expected, clock.wallEpochMillis), minOf(expected, clock.wallEpochMillis)) >
        WALL_DRIFT_TOLERANCE_MILLIS
}

const val WALL_DRIFT_TOLERANCE_MILLIS = 60_000L

/**
 * UTC dates are for display/history. A known boot uses monotonic elapsed time, including sleep.
 * A reboot falls back to UTC; undetectable changes while powered off cannot be reconstructed.
 */
fun project(session: FastSession, clock: ClockSnapshot): FastProjection {
    validateGoal(session.goalMillis)
    val sameBoot = session.anchorBootId != null && session.anchorBootId == clock.bootId
    val monotonicAnchor = session.anchorElapsedRealtimeMillis
    val completed = session.endEpochMillis != null
    val impossibleReboot = !completed && !sameBoot &&
        (clock.wallEpochMillis < session.startEpochMillis ||
            (session.anchorWallEpochMillis != null && clock.wallEpochMillis < session.anchorWallEpochMillis) ||
            nonnegativeDifference(clock.wallEpochMillis, session.startEpochMillis) < session.elapsedAtAnchorMillis)
    val review = session.needsTimeReview || (!completed &&
        ((sameBoot && wallDrifted(session, clock)) || impossibleReboot))
    val elapsed = when {
        completed -> session.completedDurationMillis
            ?: nonnegativeDifference(session.endEpochMillis, session.startEpochMillis)
        sameBoot && monotonicAnchor != null -> saturatedAdd(
            session.elapsedAtAnchorMillis.coerceAtLeast(0),
            nonnegativeDifference(clock.elapsedRealtimeMillis, monotonicAnchor),
        )
        impossibleReboot -> session.elapsedAtAnchorMillis.coerceAtLeast(0)
        else -> nonnegativeDifference(clock.wallEpochMillis, session.startEpochMillis)
    }.coerceAtLeast(0)
    return FastProjection(
        elapsedMillis = elapsed,
        remainingMillis = (session.goalMillis - elapsed).coerceAtLeast(0),
        progress = (elapsed.toDouble() / session.goalMillis).coerceIn(0.0, 1.0).toFloat(),
        goalMet = elapsed >= session.goalMillis,
        needsTimeReview = review,
    )
}

fun reconcileTiming(session: FastSession, clock: ClockSnapshot): FastSession {
    if (session.endEpochMillis != null) return session
    val projection = project(session, clock)
    return session.copy(
        anchorWallEpochMillis = clock.wallEpochMillis,
        anchorElapsedRealtimeMillis = clock.elapsedRealtimeMillis,
        anchorBootId = clock.bootId,
        elapsedAtAnchorMillis = projection.elapsedMillis,
        needsTimeReview = projection.needsTimeReview,
    )
}
