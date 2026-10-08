package com.madebygps.dotfasting.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphFrameRendererTest {
    @Test
    fun progressRingSaturatesAtOneHundredPercent() {
        val state = activeState(elapsedMillis = 24 * HOUR, goalMillis = 24 * HOUR)

        val exactlyMet = GlyphFrameRenderer.render(GlyphView.PROGRESS, state, state.sampledAtElapsedRealtime)
        val overGoal = GlyphFrameRenderer.render(
            GlyphView.PROGRESS,
            state.copy(elapsedMillis = 72 * HOUR),
            state.sampledAtElapsedRealtime,
        )

        assertEquals(exactlyMet, overGoal)
        assertTrue(countLitRingPixels(exactlyMet) > 0)
    }

    @Test
    fun elapsedDisplayContinuesByMinuteAfterGoal() {
        val state = activeState(elapsedMillis = 25 * HOUR, goalMillis = 16 * HOUR)
        val minuteLater = state.copy(elapsedMillis = state.elapsedMillis + MINUTE)

        val first = GlyphFrameRenderer.render(GlyphView.ELAPSED, state, state.sampledAtElapsedRealtime)
        val second = GlyphFrameRenderer.render(GlyphView.ELAPSED, minuteLater, state.sampledAtElapsedRealtime)

        assertNotEquals(first, second)
    }

    @Test
    fun remainingViewShowsMarkerAtAndAfterGoal() {
        val atGoal = activeState(elapsedMillis = 16 * HOUR, goalMillis = 16 * HOUR)
        val afterGoal = atGoal.copy(elapsedMillis = 20 * HOUR)
        val beforeGoal = atGoal.copy(elapsedMillis = atGoal.elapsedMillis - MINUTE)

        val met = GlyphFrameRenderer.render(GlyphView.REMAINING, atGoal, atGoal.sampledAtElapsedRealtime)
        val stillMet = GlyphFrameRenderer.render(GlyphView.REMAINING, afterGoal, afterGoal.sampledAtElapsedRealtime)
        val remaining = GlyphFrameRenderer.render(GlyphView.REMAINING, beforeGoal, beforeGoal.sampledAtElapsedRealtime)

        assertEquals(met, stillMet)
        assertNotEquals(met, remaining)
        assertTrue(met.toBooleanArray().any { it })
    }

    @Test
    fun elapsedRealtimeAdvancesWithoutWallClockProjection() {
        val state = activeState(elapsedMillis = HOUR, goalMillis = 12 * HOUR)
        assertEquals(HOUR + MINUTE, state.elapsedAt(state.sampledAtElapsedRealtime + MINUTE))
        assertFalse(state.elapsedAt(state.sampledAtElapsedRealtime - MINUTE) > HOUR)
    }

    @Test
    fun viewCycleIsDisplayOnlyAndReturnsToProgress() {
        assertEquals(GlyphView.ELAPSED, GlyphView.PROGRESS.next())
        assertEquals(GlyphView.REMAINING, GlyphView.PROGRESS.next().next())
        assertEquals(GlyphView.PROGRESS, GlyphView.PROGRESS.next().next().next())
    }

    @Test
    fun unchangedMinutePixelsAreNotSentAgainAndResetAllowsResend() {
        val state = activeState(elapsedMillis = 25 * HOUR, goalMillis = 16 * HOUR)
        val changes = GlyphFrameChanges()
        val first = GlyphFrameRenderer.render(GlyphView.ELAPSED, state, state.sampledAtElapsedRealtime)
        assertTrue(changes.differs(first))
        changes.markDisplayed(first)
        val sameMinute = GlyphFrameRenderer.render(
            GlyphView.ELAPSED, state, state.sampledAtElapsedRealtime + 59_999,
        )
        assertFalse(changes.differs(sameMinute))
        val nextMinute = GlyphFrameRenderer.render(
            GlyphView.ELAPSED, state, state.sampledAtElapsedRealtime + MINUTE,
        )
        assertTrue(changes.differs(nextMinute))
        changes.reset()
        assertTrue(changes.differs(first))
    }

    @Test
    fun longDurationsNeverWrapAtOneHundredHours() {
        val oneHour = activeState(HOUR, HOUR)
        val hundredAndOne = activeState(101 * HOUR, HOUR)
        assertNotEquals(
            GlyphFrameRenderer.render(GlyphView.ELAPSED, oneHour, oneHour.sampledAtElapsedRealtime),
            GlyphFrameRenderer.render(GlyphView.ELAPSED, hundredAndOne, hundredAndOne.sampledAtElapsedRealtime),
        )
        val maximum = activeState(Long.MAX_VALUE, HOUR)
        val pixels = GlyphFrameRenderer.render(
            GlyphView.ELAPSED, maximum, maximum.sampledAtElapsedRealtime,
        ).toBooleanArray()
        assertEquals(625, pixels.size)
        assertTrue(pixels.any { it })
    }

    @Test
    fun timeReviewIsExplicitAndDoesNotAdvanceQuestionableTime() {
        val state = activeState(HOUR, HOUR).copy(needsTimeReview = true)
        assertEquals(HOUR, state.elapsedAt(state.sampledAtElapsedRealtime + HOUR))
        val review = GlyphFrameRenderer.render(GlyphView.PROGRESS, state, state.sampledAtElapsedRealtime)
        assertEquals(review, GlyphFrameRenderer.render(GlyphView.ELAPSED, state, state.sampledAtElapsedRealtime))
        assertNotEquals(
            review,
            GlyphFrameRenderer.render(GlyphView.PROGRESS, state.copy(needsTimeReview = false), state.sampledAtElapsedRealtime),
        )
    }

    @Test
    fun idleIsIndependentOfSelectedView() {
        val idle = GlyphFastState.idle()
        val frame = GlyphFrameRenderer.render(GlyphView.PROGRESS, idle, 0)
        assertEquals(frame, GlyphFrameRenderer.render(GlyphView.ELAPSED, idle, Long.MAX_VALUE))
        assertTrue(frame.toBooleanArray().any { it })
    }

    @Test
    fun elapsedSaturatesWithoutOverflow() {
        val state = activeState(Long.MAX_VALUE - 1, HOUR)
        assertEquals(Long.MAX_VALUE, state.elapsedAt(state.sampledAtElapsedRealtime + MINUTE))
    }

    @Test
    fun progressBeginsAtNoonAndMovesClockwise() {
        val zero = activeState(0, HOUR)
        val half = zero.copy(elapsedMillis = HOUR / 2)
        val full = zero.copy(elapsedMillis = HOUR)
        val emptyRing = GlyphFrameRenderer.render(GlyphView.PROGRESS, zero, zero.sampledAtElapsedRealtime)
        val halfRing = GlyphFrameRenderer.render(GlyphView.PROGRESS, half, half.sampledAtElapsedRealtime)
        val fullRing = GlyphFrameRenderer.render(GlyphView.PROGRESS, full, full.sampledAtElapsedRealtime)
        assertEquals(0, countLitRingPixels(emptyRing))
        assertTrue(halfRing.isLit(12, 2))
        assertTrue(halfRing.isLit(22, 12))
        assertFalse(halfRing.isLit(12, 22))
        assertFalse(halfRing.isLit(2, 12))
        assertTrue(fullRing.isLit(12, 22))
        assertTrue(fullRing.isLit(2, 12))
    }

    @Test
    fun maximumDurationFitsPhysicalRoundDisplay() {
        val state = activeState(Long.MAX_VALUE, HOUR)
        val frame = GlyphFrameRenderer.render(GlyphView.ELAPSED, state, state.sampledAtElapsedRealtime)
        for (y in 0 until 25) {
            for (x in 0 until 25) {
                if (frame.isLit(x, y)) {
                    val distanceSquared = (x - 12) * (x - 12) + (y - 12) * (y - 12)
                    assertTrue("Pixel ($x, $y) exceeds round display", distanceSquared <= 156)
                }
            }
        }
    }

    private fun countLitRingPixels(frame: GlyphFrame): Int {
        var count = 0
        for (y in 0 until GlyphFrameRenderer.SIZE) {
            for (x in 0 until GlyphFrameRenderer.SIZE) {
                val dx = x - GlyphFrameRenderer.SIZE / 2
                val dy = y - GlyphFrameRenderer.SIZE / 2
                val radius = kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
                if (kotlin.math.abs(radius - 10.0) < 0.7 && frame.isLit(x, y)) count++
            }
        }
        return count
    }

    private fun activeState(elapsedMillis: Long, goalMillis: Long) = GlyphFastState(
        active = true,
        elapsedMillis = elapsedMillis,
        goalMillis = goalMillis,
        sampledAtElapsedRealtime = 1_000L,
    )

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
