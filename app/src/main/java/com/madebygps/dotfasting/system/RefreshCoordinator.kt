package com.madebygps.dotfasting.system

import android.content.Context
import android.content.Intent
import com.madebygps.dotfasting.data.FastingDatabase
import com.madebygps.dotfasting.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Drains the Room refresh outbox. A crash before acknowledgement is replayed by reconcile().
 * Display consumers must be idempotent and read persisted state rather than intent payloads.
 */
class RefreshCoordinator(context: Context) {
    private val context = context.applicationContext

    suspend fun refresh() = mutex.withLock {
        val dao = FastingDatabase.get(context).fastingDao()
        val state = dao.refreshState()
        val active = dao.active()?.toDomain()
        val settings = SettingsStore(context).settings.first()
        GoalAlarmScheduler(context).reschedule(active, settings.notificationsEnabled)
        if (!settings.notificationsEnabled || active == null || active.needsTimeReview) {
            GoalNotifications(context).cancel()
        }
        context.sendBroadcast(Intent(ACTION_REFRESH).setPackage(context.packageName))
        if (state != null) dao.markDispatched(state.revision)
    }

    companion object {
        const val ACTION_REFRESH = "com.madebygps.dotfasting.REFRESH"
        private val mutex = Mutex()
    }
}
