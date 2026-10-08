package com.madebygps.dotfasting.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.madebygps.dotfasting.R
import com.madebygps.dotfasting.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class FastingWidgetTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun nativeTimerUsesDottedFontAndMonotonicBase() {
        val state = WidgetState(WidgetStatus.RUNNING, timerBaseMillis = -100_000, progress = 750, goalMillis = 60_000)
        val root = widgetViews(context, state).apply(context, null)
        val timer = root.findViewById<Chronometer>(R.id.widget_timer)
        assertEquals(View.VISIBLE, timer.visibility)
        assertEquals(-100_000L, timer.base)
        assertFalse(timer.isCountDown)
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
        assertEquals(750, root.findViewById<ProgressBar>(R.id.widget_progress).progress)
        assertEquals(context.getString(R.string.widget_goal, "0h 1m"), root.findViewById<TextView>(R.id.widget_goal).text)
    }

    @Test fun timerTicksWithoutAnotherRemoteViewsUpdate() {
        val ticks = CountDownLatch(2)
        val firstText = AtomicReference<String>()
        val lastText = AtomicReference<String>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val state = WidgetState(WidgetStatus.RUNNING, timerBaseMillis = SystemClock.elapsedRealtime() - 65_000)
                val root = widgetViews(activity, state).apply(activity, null)
                root.findViewById<Chronometer>(R.id.widget_timer).setOnChronometerTickListener { timer ->
                    firstText.compareAndSet(null, timer.text.toString())
                    lastText.set(timer.text.toString())
                    ticks.countDown()
                }
                activity.setContentView(root)
            }
            assertTrue("Launcher timer did not tick", ticks.await(4, TimeUnit.SECONDS))
            assertFalse(firstText.get() == lastText.get())
        }
    }

    @Test fun endingFastHidesTimerAndProgressEvenWhenReapplyingExistingViews() {
        val running = widgetViews(context, WidgetState(WidgetStatus.RUNNING))
        val root = running.apply(context, null)
        widgetViews(context, WidgetState(WidgetStatus.IDLE)).reapply(context, root)
        assertEquals(View.GONE, root.findViewById<Chronometer>(R.id.widget_timer).visibility)
        assertEquals(View.GONE, root.findViewById<ProgressBar>(R.id.widget_progress).visibility)
        assertEquals(context.getString(R.string.widget_idle), root.findViewById<TextView>(R.id.widget_message).text)
    }

    @Test fun clockReviewAndStorageFailureAreVisibleInsteadOfAStaleTimer() {
        for ((status, message) in listOf(
            WidgetStatus.REVIEW to R.string.widget_review,
            WidgetStatus.UNAVAILABLE to R.string.widget_unavailable,
        )) {
            val root = widgetViews(context, WidgetState(status)).apply(context, null)
            assertEquals(View.GONE, root.findViewById<Chronometer>(R.id.widget_timer).visibility)
            assertEquals(context.getString(message), root.findViewById<TextView>(R.id.widget_message).text)
        }
    }
}
