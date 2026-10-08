package com.madebygps.dotfasting.data

import android.content.Context
import androidx.room.withTransaction
import com.madebygps.dotfasting.domain.FastSession
import com.madebygps.dotfasting.domain.FastingClock
import com.madebygps.dotfasting.domain.FastingException
import com.madebygps.dotfasting.domain.project
import com.madebygps.dotfasting.domain.reconcileTiming
import com.madebygps.dotfasting.domain.validateGoal
import com.madebygps.dotfasting.domain.validateInterval
import com.madebygps.dotfasting.system.AndroidFastingClock
import com.madebygps.dotfasting.system.RefreshCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FastingRepository(
    context: Context,
    private val database: FastingDatabase = FastingDatabase.get(context),
    private val clock: FastingClock = AndroidFastingClock(context),
    private val refreshEffects: suspend () -> Unit = { RefreshCoordinator(context).refresh() },
) {
    private val settings = SettingsStore(context)
    private val dao = database.fastingDao()

    val sessions: Flow<List<FastSession>> =
        dao.observeSessions().map { rows -> rows.map(FastSessionEntity::toDomain) }

    suspend fun start(goalMillis: Long): FastSession {
        validateGoal(goalMillis)
        val session = database.withTransaction {
            if (dao.active() != null) throw FastingException.AlreadyActive()
            val now = clock.snapshot()
            val all = dao.all().map(FastSessionEntity::toDomain)
            validateInterval(0, now.wallEpochMillis, null, now.wallEpochMillis, all)
            val new = FastSession(
                startEpochMillis = now.wallEpochMillis,
                goalMillis = goalMillis,
                anchorWallEpochMillis = now.wallEpochMillis,
                anchorElapsedRealtimeMillis = now.elapsedRealtimeMillis,
                anchorBootId = now.bootId,
            )
            val id = dao.insert(FastSessionEntity.fromDomain(new))
            requestRefresh()
            new.copy(id = id)
        }
        withContext(NonCancellable) {
            try {
                settings.rememberGoal(goalMillis)
            } finally {
                refreshAfterCommit()
            }
        }
        return session
    }

    suspend fun end(): FastSession {
        val session = database.withTransaction {
            val active = dao.active()?.toDomain() ?: throw FastingException.NoActiveSession()
            val now = clock.snapshot()
            val projection = project(active, now)
            if (projection.needsTimeReview) throw FastingException.TimeReviewRequired()
            validateInterval(active.id, active.startEpochMillis, now.wallEpochMillis, now.wallEpochMillis,
                dao.all().map(FastSessionEntity::toDomain))
            val ended = active.copy(
                endEpochMillis = now.wallEpochMillis,
                completedDurationMillis = projection.elapsedMillis,
            )
            dao.update(FastSessionEntity.fromDomain(ended))
            requestRefresh()
            ended
        }
        refreshAfterCommit()
        return session
    }

    suspend fun edit(id: Long, startEpochMillis: Long, endEpochMillis: Long?): FastSession {
        val session = database.withTransaction {
            val existing = dao.get(id)?.toDomain() ?: throw FastingException.SessionNotFound(id)
            if ((existing.endEpochMillis == null) != (endEpochMillis == null)) {
                throw FastingException.InvalidState("A timestamp correction cannot start or end a fast. Use End fast.")
            }
            val now = clock.snapshot()
            validateInterval(id, startEpochMillis, endEpochMillis, now.wallEpochMillis,
                dao.all().map(FastSessionEntity::toDomain))
            val elapsed = (endEpochMillis ?: now.wallEpochMillis) - startEpochMillis
            val edited = existing.copy(
                startEpochMillis = startEpochMillis,
                endEpochMillis = endEpochMillis,
                anchorWallEpochMillis = now.wallEpochMillis,
                anchorElapsedRealtimeMillis = now.elapsedRealtimeMillis,
                anchorBootId = now.bootId,
                elapsedAtAnchorMillis = elapsed,
                completedDurationMillis = if (endEpochMillis != null) elapsed else null,
                needsTimeReview = false,
                // Corrections are not an occasion for a catch-up alert.
                goalNotificationSuppressed = existing.goalNotificationSuppressed || elapsed >= existing.goalMillis,
            )
            dao.update(FastSessionEntity.fromDomain(edited))
            requestRefresh()
            edited
        }
        refreshAfterCommit()
        return session
    }

    suspend fun reconcile(): FastSession? = reconcile(suppressCatchUp = true)

    internal suspend fun reconcile(suppressCatchUp: Boolean): FastSession? {
        val active = database.withTransaction {
            val existing = dao.active()?.toDomain()
            val reconciled = existing?.let {
                val now = clock.snapshot()
                val projection = project(it, now)
                reconcileTiming(it, now).copy(
                    goalNotificationSuppressed = it.goalNotificationSuppressed ||
                        (suppressCatchUp && projection.goalMet),
                )
            }
            if (reconciled != null) dao.update(FastSessionEntity.fromDomain(reconciled))
            requestRefresh()
            reconciled
        }
        refreshAfterCommit()
        return active
    }

    private suspend fun requestRefresh() {
        dao.createRefreshState(RefreshStateEntity())
        dao.requestRefresh()
    }

    private suspend fun refreshAfterCommit() = withContext(NonCancellable) {
        try {
            refreshEffects()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw FastingException.RefreshFailed(error)
        }
    }
}
