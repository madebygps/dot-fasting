package com.madebygps.dotfasting.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CounterTest {
    @Test fun displayDirectionDoesNotChangeGoalOrManualEndBehavior() {
        val state = FastProjection(3_661_000, 7_139_000, 0.34f, false, false)
        assertEquals("01:01:01", counterText(counterMillis(state, false)))
        assertEquals("01:58:59", counterText(counterMillis(state, true)))
        assertEquals("01:58", counterText(counterMillis(state, true), seconds = false))
        val met = state.copy(elapsedMillis = 14_400_000, remainingMillis = 0, goalMet = true)
        assertEquals("00:00:00", counterText(counterMillis(met, true)))
        assertEquals("04:00:00", counterText(counterMillis(met, false)))
    }

    @Test fun longDurationsDoNotWrapAtOneDay() {
        assertEquals("100:00:00", counterText(360_000_000))
        assertEquals("00:00:00", counterText(-1))
    }
}
