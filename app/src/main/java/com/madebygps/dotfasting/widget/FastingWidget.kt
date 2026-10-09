package com.madebygps.dotfasting.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.madebygps.dotfasting.MainActivity
import com.madebygps.dotfasting.R
import com.madebygps.dotfasting.domain.RingGeometry
import com.madebygps.dotfasting.system.RefreshCoordinator

// Keep the receiver identity so widgets already on the home screen are upgraded in place.
open class FastingWidgetReceiver : AppWidgetProvider() {
    override fun onEnabled(context: Context) = refresh(context)

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        refresh(context)

    override fun onDeleted(context: Context, ids: IntArray) = refresh(context)

    override fun onDisabled(context: Context) = refresh(context)

    private fun refresh(context: Context) {
        context.sendBroadcast(
            Intent(context, WidgetRefreshReceiver::class.java).setAction(RefreshCoordinator.ACTION_REFRESH),
        )
    }
}

class CompactFastingWidgetReceiver : FastingWidgetReceiver()

class WideFastingWidgetReceiver : FastingWidgetReceiver()

internal enum class WidgetVariant(
    val layout: Int,
    val provider: Class<out AppWidgetProvider>,
    val ringSizePixels: Int,
) {
    COMPACT(R.layout.fasting_widget_compact, CompactFastingWidgetReceiver::class.java, 160),
    RING(R.layout.fasting_widget, FastingWidgetReceiver::class.java, 256),
    WIDE(R.layout.fasting_widget_wide, WideFastingWidgetReceiver::class.java, 192),
}

internal fun widgetViews(
    context: Context,
    state: WidgetState,
    variant: WidgetVariant = WidgetVariant.RING,
): RemoteViews =
    RemoteViews(context.packageName, variant.layout).apply {
        val running = state.status == WidgetStatus.RUNNING
        setViewVisibility(R.id.widget_timer, if (running) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.widget_message, if (running) View.GONE else View.VISIBLE)
        setViewVisibility(R.id.widget_ring, if (running) View.VISIBLE else View.GONE)
        setTextViewText(R.id.widget_timer, WidgetTime.elapsed(state.counterMillis))
        setImageViewBitmap(
            R.id.widget_ring,
            progressRingBitmap(state.progress, state.highlight, variant.ringSizePixels),
        )
        setTextViewText(
            R.id.widget_message,
            context.getString(
                when (variant) {
                    WidgetVariant.COMPACT -> when (state.status) {
                        WidgetStatus.IDLE -> R.string.widget_idle_compact
                        WidgetStatus.REVIEW -> R.string.widget_review_compact
                        WidgetStatus.UNAVAILABLE -> R.string.widget_unavailable_compact
                        WidgetStatus.RUNNING -> R.string.widget_elapsed
                    }
                    else -> when (state.status) {
                        WidgetStatus.IDLE -> R.string.widget_idle
                        WidgetStatus.REVIEW -> R.string.widget_review
                        WidgetStatus.UNAVAILABLE -> R.string.widget_unavailable
                        WidgetStatus.RUNNING -> R.string.widget_elapsed
                    }
                },
            ),
        )
        setContentDescription(
            R.id.widget_ring,
            context.getString(R.string.widget_progress_description, state.progress / 10),
        )
        if (variant != WidgetVariant.COMPACT) {
            setViewVisibility(R.id.widget_label, if (running) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.widget_goal, if (running) View.VISIBLE else View.GONE)
            setTextViewText(
                R.id.widget_label,
                context.getString(
                    when {
                        state.progress >= 1000 -> R.string.widget_goal_met
                        state.countDown -> R.string.widget_remaining
                        else -> R.string.widget_elapsed
                    },
                ),
            )
            setTextViewText(
                R.id.widget_goal,
                context.getString(R.string.widget_goal, WidgetTime.duration(state.goalMillis)),
            )
        }
        if (variant == WidgetVariant.WIDE) {
            setViewVisibility(R.id.widget_started, if (running) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.widget_status, if (running) View.VISIBLE else View.GONE)
            setTextViewText(
                R.id.widget_started,
                context.getString(
                    R.string.widget_started,
                    state.startEpochMillis?.let { WidgetTime.clock(it) } ?: "",
                ),
            )
            setTextViewText(
                R.id.widget_status,
                context.getString(
                    if (state.progress >= 1000) R.string.widget_goal_met else R.string.widget_fasting,
                ),
            )
        }
        setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context, variant.ordinal, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }

internal fun progressRingBitmap(progress: Int, highlight: Int, sizePixels: Int): Bitmap {
    val size = sizePixels.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val stroke = size * RingGeometry.STROKE_FRACTION
    val inset = stroke / 2f + 1f
    val bounds = RectF(inset, inset, size - inset, size - inset)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.BUTT
        color = 0xFF343434.toInt()
    }
    canvas.drawArc(bounds, 0f, 360f, false, paint)
    if (progress > 0) {
        paint.color = highlight
        canvas.drawArc(
            bounds,
            RingGeometry.START_DEGREES,
            RingGeometry.sweep(progress.coerceIn(0, 1000) / 1000f),
            false,
            paint,
        )
    }
    return bitmap
}
