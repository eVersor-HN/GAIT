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

    @Query("SELECT * FROM sessions WHERE profileId = :profileId ORDER BY startTimeEpochMillis DESC")
    fun observeSessions(profileId: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE profileId = :profileId ORDER BY startTimeEpochMillis DESC")
    suspend fun getSessions(profileId: Long): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE profileId = :profileId ORDER BY startTimeEpochMillis DESC LIMIT :limit")
    suspend fun getRecentSessions(profileId: Long, limit: Int): List<SessionEntity>

    @Query("SELECT COUNT(*) FROM sessions WHERE profileId = :profileId")
    suspend fun countSessions(profileId: Long): Int

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sessions WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}
