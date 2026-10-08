package com.madebygps.dotfasting.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.madebygps.dotfasting.domain.FastSession
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "fast_sessions", indices = [Index(value = ["activeSlot"], unique = true)])
data class FastSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startEpochMillis: Long,
    val endEpochMillis: Long?,
    val goalMillis: Long,
    val anchorWallEpochMillis: Long?,
    val anchorElapsedRealtimeMillis: Long?,
    val anchorBootId: String?,
    val elapsedAtAnchorMillis: Long,
    val completedDurationMillis: Long?,
    val needsTimeReview: Boolean,
    val goalNotificationDelivered: Boolean,
    val goalNotificationSuppressed: Boolean,
    // SQLite permits multiple NULLs in a unique index, but only one active slot.
    val activeSlot: Int?,
) {
    fun toDomain() = FastSession(
        id, startEpochMillis, endEpochMillis, goalMillis, anchorWallEpochMillis,
        anchorElapsedRealtimeMillis, anchorBootId, elapsedAtAnchorMillis,
        completedDurationMillis, needsTimeReview, goalNotificationDelivered, goalNotificationSuppressed,
    )

    companion object {
        fun fromDomain(session: FastSession) = FastSessionEntity(
            session.id, session.startEpochMillis, session.endEpochMillis, session.goalMillis,
            session.anchorWallEpochMillis, session.anchorElapsedRealtimeMillis, session.anchorBootId,
            session.elapsedAtAnchorMillis, session.completedDurationMillis, session.needsTimeReview,
            session.goalNotificationDelivered, session.goalNotificationSuppressed,
            if (session.endEpochMillis == null) 1 else null,
        )
    }
}

@Entity(tableName = "refresh_state")
data class RefreshStateEntity(
    @PrimaryKey val id: Int = 1,
    val revision: Long = 0,
    val dispatchedRevision: Long = 0,
)

@Dao
interface FastingDao {
    @Query("SELECT * FROM fast_sessions ORDER BY startEpochMillis DESC, id DESC")
    fun observeSessions(): Flow<List<FastSessionEntity>>

    @Query("SELECT * FROM fast_sessions ORDER BY startEpochMillis DESC, id DESC")
    suspend fun all(): List<FastSessionEntity>

    @Query("SELECT * FROM fast_sessions WHERE activeSlot = 1 LIMIT 1")
    suspend fun active(): FastSessionEntity?

    @Query("SELECT * FROM fast_sessions WHERE id = :id")
    suspend fun get(id: Long): FastSessionEntity?

    @Insert
    suspend fun insert(session: FastSessionEntity): Long

    @Update
    suspend fun update(session: FastSessionEntity)

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun createRefreshState(state: RefreshStateEntity)

    @Query("UPDATE refresh_state SET revision = revision + 1 WHERE id = 1")
    suspend fun requestRefresh()

    @Query("SELECT * FROM refresh_state WHERE id = 1")
    suspend fun refreshState(): RefreshStateEntity?

    @Query("UPDATE refresh_state SET dispatchedRevision = :revision WHERE id = 1 AND dispatchedRevision < :revision")
    suspend fun markDispatched(revision: Long)

    @Query("UPDATE fast_sessions SET goalNotificationDelivered = 1 WHERE id = :id AND activeSlot = 1 AND goalNotificationDelivered = 0 AND goalNotificationSuppressed = 0")
    suspend fun claimGoalNotification(id: Long): Int
}

@Database(entities = [FastSessionEntity::class, RefreshStateEntity::class], version = 1, exportSchema = true)
abstract class FastingDatabase : RoomDatabase() {
    abstract fun fastingDao(): FastingDao

    companion object {
        @Volatile private var instance: FastingDatabase? = null

        fun get(context: Context): FastingDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, FastingDatabase::class.java, "dot_fasting.db",
            ).build().also { instance = it }
        }
    }
}
