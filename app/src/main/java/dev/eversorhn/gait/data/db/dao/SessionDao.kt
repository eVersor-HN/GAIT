package dev.eversorhn.gait.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.eversorhn.gait.data.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("SELECT * FROM sessions WHERE activityType = :activityType ORDER BY startTimeEpochMillis DESC")
    fun observeSessions(activityType: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE activityType = :activityType ORDER BY startTimeEpochMillis DESC")
    suspend fun getSessions(activityType: String): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE activityType = :activityType ORDER BY startTimeEpochMillis DESC LIMIT :limit")
    suspend fun getRecentSessions(activityType: String, limit: Int): List<SessionEntity>

    @Query("SELECT COUNT(*) FROM sessions WHERE activityType = :activityType")
    suspend fun countSessions(activityType: String): Int
}
