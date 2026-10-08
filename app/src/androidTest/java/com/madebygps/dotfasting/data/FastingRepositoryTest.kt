package com.madebygps.dotfasting.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.madebygps.dotfasting.domain.ClockSnapshot
import com.madebygps.dotfasting.domain.FastSession
import com.madebygps.dotfasting.domain.FastingClock
import com.madebygps.dotfasting.domain.FastingException
import com.madebygps.dotfasting.domain.project
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FastingRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: FastingDatabase
    private lateinit var repository: FastingRepository
    private var now = ClockSnapshot(1_700_000_000_000, 10_000, "one")
    private var refreshes = 0

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, FastingDatabase::class.java).build()
        repository = FastingRepository(context, database, FastingClock { now }) { refreshes++ }
    }

    @After fun teardown() {
        database.close()
    }

    private fun advance(millis: Long) {
        now = now.copy(
            wallEpochMillis = now.wallEpochMillis + millis,
            elapsedRealtimeMillis = now.elapsedRealtimeMillis + millis,
        )
    }

    @Test fun concurrentStartsCreateOnlyOneActiveRow() = runBlocking {
        val outcomes = coroutineScope {
            (1..8).map { async { runCatching { repository.start(60_000) } } }.map { it.await() }
        }
        assertEquals(1, outcomes.count { it.isSuccess })
        assertTrue(outcomes.filter { it.isFailure }.all { it.exceptionOrNull() is FastingException.AlreadyActive })
        assertEquals(1, repository.sessions.first().size)
        assertEquals(1, refreshes)
    }

    @Test fun uniqueIndexAlsoProtectsAgainstDuplicateActiveRows() = runBlocking {
        repository.start(60_000)
        val duplicate = FastSessionEntity.fromDomain(FastSession(startEpochMillis = now.wallEpochMillis, goalMillis = 60_000))
        val outcome = runCatching { database.fastingDao().insert(duplicate) }
        assertTrue(outcome.exceptionOrNull() is SQLiteConstraintException)
        assertEquals(1, database.fastingDao().all().size)
    }

    @Test fun endFreezesAndRepeatedEndFailsClearly() = runBlocking {
        repository.start(60_000)
        advance(70_000)
        val ended = repository.end()
        assertEquals(70_000L, ended.completedDurationMillis)
        now = now.copy(wallEpochMillis = now.wallEpochMillis - 3_600_000, bootId = "two")
        assertEquals(70_000, project(repository.sessions.first().single(), now).elapsedMillis)
        assertTrue(runCatching { repository.end() }.exceptionOrNull() is FastingException.NoActiveSession)
    }

    @Test fun concurrentEndsOnlyCompleteOnce() = runBlocking {
        repository.start(60_000)
        advance(1_000)
        val outcomes = coroutineScope {
            (1..4).map { async { runCatching { repository.end() } } }.map { it.await() }
        }
        assertEquals(1, outcomes.count { it.isSuccess })
        assertTrue(outcomes.filter { it.isFailure }.all { it.exceptionOrNull() is FastingException.NoActiveSession })
        assertEquals(1, database.fastingDao().all().size)
        assertEquals(null, database.fastingDao().active())
    }

    @Test fun closingAndReopeningPreservesSessionAndFrozenDuration() = runBlocking {
        val name = "fasting_repository_roundtrip_test.db"
        database.close()
        context.deleteDatabase(name)
        try {
            database = Room.databaseBuilder(context, FastingDatabase::class.java, name).build()
            repository = FastingRepository(context, database, FastingClock { now }) {}
            val active = repository.start(60_000)
            advance(30_000)
            repository.reconcile()
            database.close()
            database = Room.databaseBuilder(context, FastingDatabase::class.java, name).build()
            repository = FastingRepository(context, database, FastingClock { now }) {}
            assertEquals(active.id, repository.sessions.first().single().id)
            assertEquals(30_000, project(repository.sessions.first().single(), now).elapsedMillis)
            repository.end()
            database.close()
            database = Room.databaseBuilder(context, FastingDatabase::class.java, name).build()
            repository = FastingRepository(context, database, FastingClock { now }) {}
            now = now.copy(wallEpochMillis = now.wallEpochMillis + 600_000, bootId = "two")
            assertEquals(30_000, project(repository.sessions.first().single(), now).elapsedMillis)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun correctionsClearAmbiguityAndDoNotCatchUpNotify() = runBlocking {
        val active = repository.start(60_000)
        now = now.copy(wallEpochMillis = now.wallEpochMillis + 3_600_000)
        assertTrue(repository.reconcile()!!.needsTimeReview)
        assertTrue(runCatching { repository.end() }.exceptionOrNull() is FastingException.TimeReviewRequired)
        val corrected = repository.edit(active.id, now.wallEpochMillis - 90_000, null)
        assertFalse(corrected.needsTimeReview)
        assertTrue(corrected.goalNotificationSuppressed)
        assertEquals(90_000, project(corrected, now).elapsedMillis)
        assertEquals(90_000L, repository.end().completedDurationMillis)
    }

    @Test fun historyEditsAreAtomicAndPermitAdjacency() = runBlocking {
        val first = repository.start(60_000)
        advance(10_000)
        val ended = repository.end()
        val second = repository.start(60_000)
        advance(10_000)
        assertTrue(runCatching {
            repository.edit(first.id, ended.startEpochMillis, second.startEpochMillis + 1)
        }.exceptionOrNull() is FastingException.OverlappingSession)
        assertEquals(ended, repository.sessions.first().first { it.id == first.id })
        assertEquals(10_000L, repository.edit(first.id, ended.startEpochMillis, second.startEpochMillis).completedDurationMillis)
        assertTrue(runCatching {
            repository.edit(second.id, now.wallEpochMillis + 1, null)
        }.exceptionOrNull() is FastingException.InvalidTimestamp)
        assertTrue(runCatching {
            repository.edit(first.id, ended.startEpochMillis, null)
        }.exceptionOrNull() is FastingException.InvalidState)
    }

    @Test fun rebootReconcilesAndSuppressesPastGoal() = runBlocking {
        repository.start(60_000)
        advance(20_000)
        repository.reconcile()
        now = ClockSnapshot(now.wallEpochMillis + 60_000, 1_000, "two")
        val recovered = repository.reconcile()!!
        assertEquals(80_000, project(recovered, now).elapsedMillis)
        assertTrue(recovered.goalNotificationSuppressed)
        assertFalse(recovered.needsTimeReview)
    }

    @Test fun persistedDeliveryMarkerCanOnlyBeClaimedOnce() = runBlocking {
        val session = repository.start(60_000)
        assertEquals(1, database.fastingDao().claimGoalNotification(session.id))
        assertEquals(0, database.fastingDao().claimGoalNotification(session.id))
        assertTrue(repository.sessions.first().single().goalNotificationDelivered)
    }

    @Test fun deletingHistoryPreservesOtherSessionsAndRequestsRefresh() = runBlocking {
        val first = repository.start(60_000)
        advance(10_000)
        repository.end()
        val active = repository.start(60_000)
        val revision = database.fastingDao().refreshState()!!.revision
        repository.delete(first.id)
        assertEquals(listOf(active), repository.sessions.first())
        assertEquals(revision + 1, database.fastingDao().refreshState()!!.revision)
        assertEquals(4, refreshes)
        assertTrue(runCatching { repository.delete(first.id) }.exceptionOrNull() is FastingException.SessionNotFound)
        assertEquals(4, refreshes)
    }

    @Test fun deletingActiveClearsTimerAndAllowsAnotherStart() = runBlocking {
        val active = repository.start(60_000)
        repository.delete(active.id)
        assertTrue(repository.sessions.first().isEmpty())
        assertEquals(null, database.fastingDao().active())
        assertEquals(2, refreshes)
        repository.start(60_000)
        assertEquals(1, repository.sessions.first().size)
    }

    @Test fun deletionStaysCommittedAndRefreshPendingWhenEffectsFail() = runBlocking {
        val active = repository.start(60_000)
        val failing = FastingRepository(context, database, FastingClock { now }) { error("scheduler unavailable") }
        assertTrue(runCatching { failing.delete(active.id) }.exceptionOrNull() is FastingException.RefreshFailed)
        assertTrue(repository.sessions.first().isEmpty())
        val state = database.fastingDao().refreshState()!!
        assertTrue(state.revision > state.dispatchedRevision)
    }

    @Test fun refreshOutboxSurvivesEffectsFailure() = runBlocking {
        val failing = FastingRepository(context, database, FastingClock { now }) { error("scheduler unavailable") }
        assertTrue(runCatching { failing.start(60_000) }.exceptionOrNull() is FastingException.RefreshFailed)
        assertEquals(1, repository.sessions.first().size)
        val state = database.fastingDao().refreshState()!!
        assertTrue(state.revision > state.dispatchedRevision)
        repository.reconcile()
        assertEquals(1, refreshes)
    }
}
