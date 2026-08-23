package dev.eversorhn.gait.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.eversorhn.gait.data.db.entity.TwinMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TwinMessageDao {
    @Insert
    suspend fun insert(message: TwinMessageEntity): Long

    @Query("SELECT * FROM twin_messages WHERE profileId = :profileId ORDER BY epochMillis DESC")
    suspend fun getAll(profileId: Long): List<TwinMessageEntity>

    @Query("SELECT * FROM twin_messages WHERE profileId = :profileId ORDER BY epochMillis DESC LIMIT :limit")
    fun observeRecent(profileId: Long, limit: Int): Flow<List<TwinMessageEntity>>

    @Query("DELETE FROM twin_messages WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)

    @Query("DELETE FROM twin_messages")
    suspend fun deleteAll()
}
