package com.madebygps.dotfasting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationArtTest {
    @Test fun settingsMatchesHabitsTwelveDotRingAndCenter() {
        val dots = NavigationArt.settings()
        assertEquals(13, dots.size)
        assertEquals(0.88f, dots.first().x, 0.0001f)
        assertEquals(0.5f, dots.first().y, 0.0001f)
        assertEquals(0.55f / 9, dots.first().radius, 0.0001f)
        assertEquals(IconDot(0.5f, 0.5f, 0.9f / 9), dots.last())
    }

    @Test fun calendarUsesCompanionDotWeightAndStaysInsideCanvas() {
        NavigationArt.calendar().forEach {
            assertEquals(0.45f / 9, it.radius, 0.0001f)
            assertTrue(it.x - it.radius > 0 && it.x + it.radius < 1)
            assertTrue(it.y - it.radius > 0 && it.y + it.radius < 1)
        }
    }

    @Test fun timerGlyphsMatchHabitsIncludingColonSpacing() {
        assertEquals(listOf("111", "001", "011", "001", "111"), DotArt.digits.getValue('3'))
        assertEquals(listOf("000", "010", "000", "010", "000"), DotArt.digits.getValue(':'))
    }
}
