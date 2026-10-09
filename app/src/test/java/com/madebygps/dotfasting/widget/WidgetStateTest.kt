package com.madebygps.dotfasting.widget

import com.madebygps.dotfasting.domain.ClockSnapshot
import com.madebygps.dotfasting.domain.FastSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStateTest {
    private val clock = ClockSnapshot(1_000_000, 500_000, "boot")
    private val highlight = 0xFF4BD6A0.toInt()
    private val session = FastSession(
        startEpochMillis = 800_000,
        goalMillis = 400_000,
        anchorWallEpochMillis = 900_000,
        anchorElapsedRealtimeMillis = 400_000,
        anchorBootId = "boot",
        elapsedAtAnchorMillis = 100_000,
    )

    @Test fun idleHasNoRunningTimer() {
        val state = widgetState(null, clock, highlight)
        assertEquals(WidgetStatus.IDLE, state.status)
        assertFalse(state.active)
        assertEquals(highlight, state.highlight)
    }

    @Test fun stateIncludesMonotonicElapsedTimeForMinuteDisplay() {
        val state = widgetState(session, clock, highlight)
        assertEquals(WidgetStatus.RUNNING, state.status)
        assertTrue(state.active)
        assertEquals(200_000L, state.elapsedMillis)
        assertEquals(200_000L, state.counterMillis)
        assertEquals(500, state.progress)
        assertEquals(highlight, state.highlight)
        assertEquals(session.startEpochMillis, state.startEpochMillis)
    }

    @Test fun countDownUsesRemainingTimeForMinuteDisplay() {
        val state = widgetState(session.copy(goalMillis = 500_000), clock, highlight, countDown = true)
        assertTrue(state.countDown)
        assertEquals(300_000L, state.remainingMillis)
        assertEquals(300_000L, state.counterMillis)
    }

    @Test fun goalNeverStopsElapsedTimerAndProgressCapsAtFull() {
        val state = widgetState(session.copy(goalMillis = 100_000), clock, highlight)
        assertEquals(WidgetStatus.RUNNING, state.status)
        assertEquals(200_000L, state.elapsedMillis)
        assertEquals(1000, state.progress)
    }

    @Test fun remainingTimerStopsAtZeroAfterGoal() {
        val state = widgetState(session.copy(goalMillis = 100_000), clock, highlight, countDown = true)
        assertEquals(0L, state.remainingMillis)
        assertEquals(0L, state.counterMillis)
    }

    @Test fun rebootReanchorsElapsedTimeFromWallTime() {
        val reboot = ClockSnapshot(1_100_000, 10_000, "new-boot")
        val state = widgetState(session, reboot, highlight)
        assertEquals(WidgetStatus.RUNNING, state.status)
        assertEquals(300_000L, state.elapsedMillis)
    }

    @Test fun clockConflictStopsDisplayUntilReviewed() {
        val state = widgetState(session, clock.copy(wallEpochMillis = 2_000_000), highlight)
        assertEquals(WidgetStatus.REVIEW, state.status)
        assertTrue(state.active)
    }

    @Test fun editedStartTimeChangesTimerBase() {
        val edited = session.copy(elapsedAtAnchorMillis = 50_000)
        assertEquals(150_000L, widgetState(edited, clock, highlight).elapsedMillis)
    }
}
