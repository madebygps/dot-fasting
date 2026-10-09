package com.madebygps.dotfasting.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

object WidgetMinuteRefresh {
    const val ACTION_REFRESH = "com.madebygps.dotfasting.WIDGET_MINUTE_REFRESH"
    private const val REQUEST_CODE = 2001
    private const val MINUTE_MILLIS = 60_000L

    fun schedule(context: Context, counterMillis: Long, countDown: Boolean) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context)
        alarms.cancel(pending)
        val delay = nextDelayMillis(counterMillis, countDown)
        val now = SystemClock.elapsedRealtime()
        val trigger = if (Long.MAX_VALUE - now < delay) Long.MAX_VALUE else now + delay
        // A non-wakeup one-shot alarm is intentionally best-effort and is rescheduled after each refresh.
        alarms.set(AlarmManager.ELAPSED_REALTIME, trigger, pending)
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    internal fun nextDelayMillis(counterMillis: Long, countDown: Boolean): Long {
        require(counterMillis >= 0) { "Counter time cannot be negative" }
        if (!countDown || counterMillis == 0L) {
            return MINUTE_MILLIS - counterMillis % MINUTE_MILLIS
        }
        return counterMillis % MINUTE_MILLIS + 1L
    }

    private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, WidgetRefreshReceiver::class.java).setAction(ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
