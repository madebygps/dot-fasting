package com.madebygps.dotfasting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FastingTest {
    private val start = 1_700_000_000_000L
    private val session = FastSession(
        id = 1, startEpochMillis = start, goalMillis = 60_000,
        anchorWallEpochMillis = start, anchorElapsedRealtimeMillis = 10_000,
        anchorBootId = "one",
    )

    private fun clock(elapsed: Long, wall: Long = start + elapsed, boot: String = "one") =
        ClockSnapshot(wall, 10_000 + elapsed, boot)

    @Test fun startsAtZeroWithoutPreset() {
        assertEquals(FastProjection(0, 60_000, 0f, false, false), project(session, clock(0)))
        assertEquals(null, AppSettings().lastGoalMillis)
        assertFalse(AppSettings().notificationsEnabled)
        assertFalse(AppSettings().glyphEnabled)
    }

    @Test fun exactGoalAndOverflowDoNotEndSession() {
        assertEquals(FastProjection(60_000, 0, 1f, true, false), project(session, clock(60_000)))
        val beyond = project(session, clock(90_000))
        assertEquals(90_000, beyond.elapsedMillis)
        assertEquals(0, beyond.remainingMillis)
        assertEquals(1f, beyond.progress)
        assertTrue(beyond.goalMet)
        assertEquals(null, session.endEpochMillis)
    }

    @Test fun fractionalProgress() {
        assertEquals(0.5f, project(session, clock(30_000)).progress)
    }

    @Test fun forwardAndBackwardWallChangesKeepMonotonicDurationAndRequireReview() {
        for (wall in listOf(start + 3_600_000, start - 3_600_000)) {
            val projection = project(session, clock(30_000, wall))
            assertEquals(30_000, projection.elapsedMillis)
            assertTrue(projection.needsTimeReview)
        }
    }

    @Test fun smallWallCorrectionsAreToleratedAndStillDoNotAlterElapsed() {
        val projection = project(session, clock(30_000, start + 30_000 + WALL_DRIFT_TOLERANCE_MILLIS))
        assertEquals(30_000, projection.elapsedMillis)
        assertFalse(projection.needsTimeReview)
        assertTrue(project(session, clock(30_000, start + 30_001 + WALL_DRIFT_TOLERANCE_MILLIS)).needsTimeReview)
    }

    @Test fun reconciliationPersistsReviewAndMonotonicProgress() {
        val changed = reconcileTiming(session, clock(30_000, start + 3_600_000))
        assertTrue(changed.needsTimeReview)
        val next = ClockSnapshot(start + 3_630_000, 70_000, "one")
        assertEquals(60_000, project(changed, next).elapsedMillis)
        assertTrue(project(changed, next).needsTimeReview)
    }

    @Test fun rebootUsesContinuousUtcInterval() {
        val checkpoint = reconcileTiming(session, clock(20_000))
        val reboot = ClockSnapshot(start + 90_000, 5_000, "two")
        assertEquals(90_000, project(checkpoint, reboot).elapsedMillis)
        assertFalse(project(checkpoint, reboot).needsTimeReview)
        val reconciled = reconcileTiming(checkpoint, reboot)
        assertEquals(95_000, project(reconciled, reboot.copy(
            wallEpochMillis = start + 95_000, elapsedRealtimeMillis = 10_000,
        )).elapsedMillis)
    }

    @Test fun rebootBeforeCheckpointRequiresCorrectionAndKeepsLastKnownDuration() {
        val checkpoint = reconcileTiming(session, clock(20_000))
        val reboot = ClockSnapshot(start + 10_000, 1_000, "two")
        val projection = project(checkpoint, reboot)
        assertEquals(20_000, projection.elapsedMillis)
        assertTrue(projection.needsTimeReview)
    }

    @Test fun rebootBeforeStartIsNotCountedAsZeroWithoutReview() {
        assertTrue(project(session, ClockSnapshot(start - 1, 1_000, "two")).needsTimeReview)
    }

    @Test fun monotonicRegressionIsReviewable() {
        val projection = project(session, ClockSnapshot(start + 1_000, 9_000, "one"))
        assertEquals(0, projection.elapsedMillis)
        assertTrue(projection.needsTimeReview)
    }

    @Test fun completedDurationIsFrozenAcrossClockAndBootChanges() {
        val ended = session.copy(endEpochMillis = start + 60_000, completedDurationMillis = 59_000)
        assertEquals(59_000, project(ended, clock(5_000_000, start - 10_000, "two")).elapsedMillis)
        assertFalse(project(ended, clock(5_000_000, start - 10_000, "two")).needsTimeReview)
    }

    @Test fun wallOnlyHistoryHasStableDuration() {
        val historical = FastSession(startEpochMillis = start, endEpochMillis = start + 10_000, goalMillis = 60_000)
        assertEquals(10_000, project(historical, clock(900_000)).elapsedMillis)
    }

    @Test fun durationsSaturateWithoutLongOverflow() {
        val huge = session.copy(elapsedAtAnchorMillis = Long.MAX_VALUE - 10, goalMillis = Long.MAX_VALUE)
        val projection = project(huge, clock(100))
        assertEquals(Long.MAX_VALUE, projection.elapsedMillis)
        assertEquals(0, projection.remainingMillis)
        assertEquals(1f, projection.progress)
    }

    @Test fun goalsRequirePositiveCheckedArithmetic() {
        assertEquals(16 * 3_600_000L, goalMillis(16, 0))
        assertEquals(90 * 60_000L, goalMillis(1, 30))
        assertEquals(Long.MAX_VALUE, Long.MAX_VALUE.also(::validateGoal))
        assertThrows(FastingException.InvalidGoal::class.java) { validateGoal(0) }
        assertThrows(FastingException.InvalidGoal::class.java) { validateGoal(-1) }
        assertThrows(FastingException.InvalidGoal::class.java) { goalMillis(0, 0) }
        assertThrows(FastingException.InvalidGoal::class.java) { goalMillis(1, 60) }
        assertThrows(FastingException.InvalidGoal::class.java) { goalMillis(-1, 1) }
        assertThrows(FastingException.InvalidGoal::class.java) { goalMillis(Long.MAX_VALUE, 1) }
    }

    @Test fun rejectFutureAndReversedIntervals() {
        assertThrows(FastingException.InvalidTimestamp::class.java) { validateInterval(1, 101, null, 100, emptyList()) }
        assertThrows(FastingException.InvalidTimestamp::class.java) { validateInterval(1, -1, null, 100, emptyList()) }
        assertThrows(FastingException.InvalidTimestamp::class.java) { validateInterval(1, 10, 101, 100, emptyList()) }
        assertThrows(FastingException.ReversedInterval::class.java) { validateInterval(1, 20, 10, 100, emptyList()) }
    }

    @Test fun rejectOverlapsIncludingActiveButPermitAdjacent() {
        val rows = listOf(
            FastSession(1, 10, 20, 5),
            FastSession(2, 30, null, 5),
        )
        validateInterval(3, 20, 30, 100, rows)
        validateInterval(1, 10, 20, 100, rows)
        assertThrows(FastingException.OverlappingSession::class.java) { validateInterval(3, 19, 25, 100, rows) }
        assertThrows(FastingException.OverlappingSession::class.java) { validateInterval(3, 20, 31, 100, rows) }
        assertThrows(FastingException.OverlappingSession::class.java) { validateInterval(3, 40, 50, 100, rows) }
        assertThrows(FastingException.OverlappingSession::class.java) { validateInterval(3, 25, null, 100, rows) }
    }

    @Test fun zeroDurationDoesNotOverlap() {
        validateInterval(2, 15, 15, 100, listOf(FastSession(1, 10, 20, 5)))
        validateInterval(2, 10, 20, 100, listOf(FastSession(1, 15, 15, 5)))
    }

    @Test fun noMidnightSplitOrTimezoneInput() {
        val interval = FastSession(startEpochMillis = start, goalMillis = 86_400_000)
        assertEquals(100_000_000, project(interval, clock(100_000_000)).elapsedMillis)
    }
}
