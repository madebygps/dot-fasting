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
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
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

class LargeFastingWidgetReceiver : FastingWidgetReceiver()

internal enum class WidgetVariant(
    val layout: Int,
    val ringSizePixels: Int,
    val compact: Boolean = false,
    val detailed: Boolean = false,
) {
    COMPACT(R.layout.fasting_widget_compact, 160, compact = true),
    RING(R.layout.fasting_widget, 256),
    WIDE(R.layout.fasting_widget_wide, 192, detailed = true),
    LARGE(R.layout.fasting_widget_large, 384, detailed = true),
    ;

    companion object {
        private const val PADDING_DP = 8f

        fun forSize(widthDp: Float, heightDp: Float): WidgetVariant {
            val width = (widthDp - 2 * PADDING_DP).coerceAtLeast(16f)
            val height = (heightDp - 2 * PADDING_DP).coerceAtLeast(16f)
            return when {
                width < 104f || (height < 56f && width < 180f) -> COMPACT
                height < 125f || width >= 1.45f * height -> WIDE
                width >= 220f && height >= 220f -> LARGE
                else -> RING
            }
        }
    }
}

internal enum class WidgetProvider(
    val provider: Class<out AppWidgetProvider>,
    val defaultVariant: WidgetVariant,
) {
    COMPACT(CompactFastingWidgetReceiver::class.java, WidgetVariant.COMPACT),
    RING(FastingWidgetReceiver::class.java, WidgetVariant.RING),
    WIDE(WideFastingWidgetReceiver::class.java, WidgetVariant.WIDE),
    LARGE(LargeFastingWidgetReceiver::class.java, WidgetVariant.LARGE),
}

internal fun responsiveWidgetViews(
    context: Context,
    state: WidgetState,
    options: Bundle,
    fallback: WidgetVariant,
): RemoteViews {
    val sizes = options.getParcelableArrayList(
        AppWidgetManager.OPTION_APPWIDGET_SIZES,
        SizeF::class.java,
    ).orEmpty()
    if (sizes.isNotEmpty()) {
        return RemoteViews(
            sizes.distinct().associateWith { size ->
                widgetViews(context, state, WidgetVariant.forSize(size.width, size.height))
            },
        )
    }
    val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
    val variant = if (width > 0 && height > 0) {
        WidgetVariant.forSize(width.toFloat(), height.toFloat())
    } else {
        fallback
    }
    return widgetViews(context, state, variant)
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
            progressRingBitmap(
                state.progress,
                state.highlight,
                ContextCompat.getColor(context, R.color.widget_track),
                variant.ringSizePixels,
            ),
        )
        setTextViewText(
            R.id.widget_message,
            context.getString(
                if (variant.compact) {
                    when (state.status) {
                        WidgetStatus.IDLE -> R.string.widget_idle_compact
                        WidgetStatus.REVIEW -> R.string.widget_review_compact
                        WidgetStatus.UNAVAILABLE -> R.string.widget_unavailable_compact
                        WidgetStatus.RUNNING -> R.string.widget_elapsed
                    }
                } else {
                    when (state.status) {
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
        if (!variant.compact) {
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
        if (variant.detailed) {
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

internal fun progressRingBitmap(progress: Int, highlight: Int, track: Int, sizePixels: Int): Bitmap {
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
        color = track
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
