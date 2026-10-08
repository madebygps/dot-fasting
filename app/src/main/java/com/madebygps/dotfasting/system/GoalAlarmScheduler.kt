package com.madebygps.dotfasting.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.madebygps.dotfasting.domain.FastSession
import com.madebygps.dotfasting.domain.project

class GoalAlarmScheduler(context: Context) {
    private val context = context.applicationContext
    private val alarms = this.context.getSystemService(AlarmManager::class.java)

    fun reschedule(session: FastSession?, notificationsEnabled: Boolean = false) {
        val pending = pendingIntent(session)
        alarms.cancel(pending)
        if (session == null) return
        val clock = AndroidFastingClock(context).snapshot()
        val projection = project(session, clock)
        if (projection.needsTimeReview || session.endEpochMillis != null) return
        if (projection.goalMet) {
            // Very short goals may be reached during the start transaction itself.
            // Reboots/edits persist suppression; delivery is still claimed by the receiver.
            val canDeliver = notificationsEnabled && !session.goalNotificationDelivered &&
                !session.goalNotificationSuppressed && session.anchorBootId == clock.bootId &&
                GoalNotifications(context).capability() == GoalNotificationCapability.AVAILABLE
            if (!canDeliver) return
        }
        val trigger = if (Long.MAX_VALUE - clock.elapsedRealtimeMillis < projection.remainingMillis) {
            Long.MAX_VALUE
        } else clock.elapsedRealtimeMillis + projection.remainingMillis
        // No exact-alarm entitlement. Android may deliver later under power restrictions.
        if (notificationsEnabled) {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending)
        } else {
            alarms.set(AlarmManager.ELAPSED_REALTIME, trigger, pending)
        }
    }

    private fun pendingIntent(session: FastSession?) = PendingIntent.getBroadcast(
        context,
        1001,
        Intent(context, GoalAlarmReceiver::class.java)
            .setAction(ACTION_GOAL)
            .putExtra(EXTRA_SESSION_ID, session?.id ?: 0L)
            .putExtra(EXTRA_BOOT_ID, session?.anchorBootId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_GOAL = "com.madebygps.dotfasting.GOAL"
        internal const val EXTRA_SESSION_ID = "session_id"
        internal const val EXTRA_BOOT_ID = "boot_id"
    }
}
