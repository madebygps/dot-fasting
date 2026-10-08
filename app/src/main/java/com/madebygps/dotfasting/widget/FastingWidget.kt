package com.madebygps.dotfasting.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.madebygps.dotfasting.MainActivity
import com.madebygps.dotfasting.R
import com.madebygps.dotfasting.system.RefreshCoordinator

// Keep the receiver identity so widgets already on the home screen are upgraded in place.
class FastingWidgetReceiver : AppWidgetProvider() {
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

internal fun widgetViews(context: Context, state: WidgetState): RemoteViews =
    RemoteViews(context.packageName, R.layout.fasting_widget).apply {
        val running = state.status == WidgetStatus.RUNNING
        setViewVisibility(R.id.widget_timer, if (running) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.widget_message, if (running) View.GONE else View.VISIBLE)
        setChronometerCountDown(R.id.widget_timer, false)
        setChronometer(R.id.widget_timer, state.timerBaseMillis, null, running)
        setTextViewText(
            R.id.widget_label,
            context.getString(if (state.active) R.string.widget_elapsed else R.string.app_name),
        )
        setTextViewText(
            R.id.widget_message,
            context.getString(
                when (state.status) {
                    WidgetStatus.IDLE -> R.string.widget_idle
                    WidgetStatus.REVIEW -> R.string.widget_review
                    WidgetStatus.UNAVAILABLE -> R.string.widget_unavailable
                    WidgetStatus.RUNNING -> R.string.widget_elapsed
                },
            ),
        )
        setViewVisibility(R.id.widget_progress, if (running) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.widget_goal, if (running) View.VISIBLE else View.GONE)
        setProgressBar(R.id.widget_progress, 1000, state.progress, false)
        setColorStateList(R.id.widget_progress, "setProgressTintList", ColorStateList.valueOf(state.highlight))
        setContentDescription(
            R.id.widget_progress,
            context.getString(R.string.widget_progress_description, state.progress / 10),
        )
        setTextViewText(
            R.id.widget_goal,
            context.getString(R.string.widget_goal, WidgetTime.duration(state.goalMillis)),
        )
        setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }
