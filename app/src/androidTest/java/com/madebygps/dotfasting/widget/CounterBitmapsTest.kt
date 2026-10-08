package com.madebygps.dotfasting.widget

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CounterBitmapsTest {
    @Test fun counterHasTrackAndNoArtworkOutsideRing() {
        val bitmap = CounterBitmaps.draw("12:34")
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(0, 0))
        assertEquals(Color.rgb(38, 38, 38), bitmap.getPixel(320, 10))
        assertEquals(640, bitmap.height)
    }

    @Test fun widgetCircleSharesProgressAndHighlight() {
        val accent = Color.rgb(75, 214, 160)
        val full = CounterBitmaps.draw("12:34", progress = 1f, highlight = accent)
        val overflow = CounterBitmaps.draw("12:34", progress = 2f, highlight = accent)
        assertEquals(accent, full.getPixel(320, 10))
        assertEquals(true, full.sameAs(overflow))
    }

    @Test fun counterReflectsDisplayedTimeAndSupportsLargeHours() {
        assertFalse(CounterBitmaps.draw("00:00").sameAs(CounterBitmaps.draw("12:34")))
        assertEquals(640, CounterBitmaps.draw("100000:59").width)
        assertEquals(640, CounterBitmaps.draw("—").width)
    }

    @Test fun idleFlameReplacesDigitsAndKeepsCircle() {
        val flame = CounterBitmaps.draw("00:00", idleFlame = true)
        assertEquals(640, flame.width)
        assertEquals(Color.TRANSPARENT, flame.getPixel(0, 0))
        assertEquals(Color.rgb(38, 38, 38), flame.getPixel(320, 10))
        assertEquals(Color.rgb(242, 242, 242), flame.getPixel(320, 165))
        assertEquals(Color.TRANSPARENT, flame.getPixel(320, 365))
        assertFalse(flame.sameAs(CounterBitmaps.draw("00:00")))
        assertEquals(true, flame.sameAs(CounterBitmaps.draw("16:00", idleFlame = true)))
        assertFalse(flame.sameAs(CounterBitmaps.draw("—")))
    }
}
