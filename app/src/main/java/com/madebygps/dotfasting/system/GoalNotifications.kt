package com.madebygps.dotfasting.system

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

enum class GoalNotificationCapability {
    AVAILABLE, PERMISSION_REQUIRED, APP_BLOCKED, CHANNEL_BLOCKED,
}

class GoalNotifications(context: Context) {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(NotificationManager::class.java)

    fun capability(): GoalNotificationCapability {
        ensureChannel()
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return GoalNotificationCapability.PERMISSION_REQUIRED
        }
        if (!manager.areNotificationsEnabled()) return GoalNotificationCapability.APP_BLOCKED
        if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) {
            return GoalNotificationCapability.CHANNEL_BLOCKED
        }
        return GoalNotificationCapability.AVAILABLE
    }

    internal fun showGoalMet(): Boolean {
        if (capability() != GoalNotificationCapability.AVAILABLE) return false
        val openApp = PendingIntent.getActivity(
            context, 1002,
            Intent().setClassName(context.packageName, "com.madebygps.dotfasting.MainActivity")
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Dot Fasting")
            .setContentText("Goal met")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return true
    }

    fun cancel() {
        manager.cancel(NOTIFICATION_ID)
    }

    /** Idempotent; lets Settings open the channel before the first alert. */
    fun ensureChannel() {
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "Fasting goal", NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "An optional notification when your chosen fasting goal is met."
        })
    }

    companion object {
        const val CHANNEL_ID = "dot_fasting_goal"
        private const val NOTIFICATION_ID = 1001
    }
}
