package com.madebygps.dotfasting.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.madebygps.dotfasting.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FastingWidgetTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun minuteTimerUsesDottedFontAndElapsedText() {
        val state = WidgetState(WidgetStatus.RUNNING, elapsedMillis = 65_000, progress = 750, goalMillis = 60_000)
        val root = widgetViews(context, state).apply(context, null)
        val timer = root.findViewById<TextView>(R.id.widget_timer)
        assertEquals(View.VISIBLE, timer.visibility)
        assertEquals("0:01", timer.text)
        fun render(typeface: Typeface?) = createBitmap(600, 90).also { bitmap ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = 64f
                color = android.graphics.Color.WHITE
            }
            Canvas(bitmap).drawText("0123456789", 0f, 70f, paint)
        }
        assertTrue(render(ResourcesCompat.getFont(context, R.font.widget_digits)).sameAs(render(timer.typeface)))
        assertFalse(render(Typeface.DEFAULT).sameAs(render(timer.typeface)))
        assertEquals(View.VISIBLE, root.findViewById<ImageView>(R.id.widget_ring).visibility)
        assertTrue(root.findViewById<ImageView>(R.id.widget_ring).drawable != null)
        assertEquals(context.getString(R.string.widget_goal, "0h 1m"), root.findViewById<TextView>(R.id.widget_goal).text)
    }

    @Test fun everyWidgetVariantInflatesWithTheSameLiveTimerState() {
        val state = WidgetState(
            WidgetStatus.RUNNING,
            elapsedMillis = 65_000,
            progress = 500,
            goalMillis = 3_600_000,
            startEpochMillis = 1_700_000_000_000,
        )
        WidgetVariant.entries.forEach { variant ->
            val root = widgetViews(context, state, variant).apply(context, null)
            assertEquals(View.VISIBLE, root.findViewById<TextView>(R.id.widget_timer).visibility)
            assertEquals("0:01", root.findViewById<TextView>(R.id.widget_timer).text)
            assertEquals(View.VISIBLE, root.findViewById<ImageView>(R.id.widget_ring).visibility)
        }
    }

    @Test fun remainingModeUsesCountdownTextAndLabel() {
        val state = WidgetState(
            WidgetStatus.RUNNING,
            elapsedMillis = 65_000,
            progress = 250,
            goalMillis = 3_600_000,
            remainingMillis = 3_535_000,
            countDown = true,
        )
        val root = widgetViews(context, state).apply(context, null)
        assertEquals("0:58", root.findViewById<TextView>(R.id.widget_timer).text)
        assertEquals(
            context.getString(R.string.widget_remaining),
            root.findViewById<TextView>(R.id.widget_label).text,
        )
    }

    @Test fun largeWidgetShowsExpandedFastingDetails() {
        val state = WidgetState(
            WidgetStatus.RUNNING,
            elapsedMillis = 65_000,
            progress = 250,
            goalMillis = 3_600_000,
            startEpochMillis = 1_700_000_000_000,
        )
        val root = widgetViews(context, state, WidgetVariant.LARGE).apply(context, null)
        assertEquals(View.VISIBLE, root.findViewById<TextView>(R.id.widget_status).visibility)
        assertEquals(View.VISIBLE, root.findViewById<TextView>(R.id.widget_started).visibility)
        assertEquals(View.VISIBLE, root.findViewById<TextView>(R.id.widget_goal).visibility)
    }

    @Test fun endingFastHidesTimerAndProgressEvenWhenReapplyingExistingViews() {
        val running = widgetViews(context, WidgetState(WidgetStatus.RUNNING))
        val root = running.apply(context, null)
        widgetViews(context, WidgetState(WidgetStatus.IDLE)).reapply(context, root)
        assertEquals(View.GONE, root.findViewById<TextView>(R.id.widget_timer).visibility)
        assertEquals(View.GONE, root.findViewById<ImageView>(R.id.widget_ring).visibility)
        assertEquals(context.getString(R.string.widget_idle), root.findViewById<TextView>(R.id.widget_message).text)
    }

    @Test fun clockReviewAndStorageFailureAreVisibleInsteadOfAStaleTimer() {
        for ((status, message) in listOf(
            WidgetStatus.REVIEW to R.string.widget_review,
            WidgetStatus.UNAVAILABLE to R.string.widget_unavailable,
        )) {
            val root = widgetViews(context, WidgetState(status)).apply(context, null)
            assertEquals(View.GONE, root.findViewById<TextView>(R.id.widget_timer).visibility)
            assertEquals(context.getString(message), root.findViewById<TextView>(R.id.widget_message).text)
        }
    }
}
