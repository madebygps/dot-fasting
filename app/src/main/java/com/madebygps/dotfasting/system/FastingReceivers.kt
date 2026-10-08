package com.madebygps.dotfasting.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.room.withTransaction
import com.madebygps.dotfasting.data.FastingDatabase
import com.madebygps.dotfasting.data.FastingRepository
import com.madebygps.dotfasting.data.RefreshStateEntity
import com.madebygps.dotfasting.data.SettingsStore
import com.madebygps.dotfasting.domain.project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GoalAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != GoalAlarmScheduler.ACTION_GOAL) return
        val id = intent.getLongExtra(GoalAlarmScheduler.EXTRA_SESSION_ID, 0)
        val bootId = intent.getStringExtra(GoalAlarmScheduler.EXTRA_BOOT_ID)
        val pending = goAsync()
        receiverScope.launch {
            try {
                GoalAlarmHandler(context).handle(id, bootId)
            } catch (error: Exception) {
                Log.e(TAG, "Goal alarm failed; persisted state will be reconciled on next launch.", error)
            } finally {
                pending.finish()
            }
        }
    }
}

class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            )
        ) return
        val pending = goAsync()
        receiverScope.launch {
            try {
                FastingRepository(context).reconcile()
            } catch (error: Exception) {
                Log.e(TAG, "Clock reconciliation failed; reopen Dot Fasting to review timing.", error)
            } finally {
                pending.finish()
            }
        }
    }
}

internal class GoalAlarmHandler(context: Context) {
    private val context = context.applicationContext

    suspend fun handle(sessionId: Long, scheduledBootId: String?) {
        val clock = AndroidFastingClock(context).snapshot()
        val database = FastingDatabase.get(context)
        val settings = SettingsStore(context).settings.first()
        val notifications = GoalNotifications(context)
        if (scheduledBootId != clock.bootId) {
            FastingRepository(context).reconcile()
            return
        }
        val claimed = database.withTransaction {
            val dao = database.fastingDao()
            val session = dao.active()?.toDomain()
            if (session == null || session.id != sessionId) return@withTransaction false
            val projection = project(session, clock)
            if (!settings.notificationsEnabled || !projection.goalMet || projection.needsTimeReview ||
                notifications.capability() != GoalNotificationCapability.AVAILABLE
            ) return@withTransaction false
            val result = dao.claimGoalNotification(sessionId) == 1
            if (result) {
                dao.createRefreshState(RefreshStateEntity())
                dao.requestRefresh()
            }
            result
        }
        // Claim first: process death must not produce a second audible goal alert.
        if (claimed && SettingsStore(context).settings.first().notificationsEnabled) notifications.showGoalMet()
        FastingRepository(context).reconcile(suppressCatchUp = false)
    }
}

private const val TAG = "DotFasting"
private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
