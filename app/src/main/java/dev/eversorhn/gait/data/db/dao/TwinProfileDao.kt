package dev.eversorhn.gait.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.eversorhn.gait.data.db.entity.TwinProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TwinProfileDao {
    @Insert
    suspend fun insert(profile: TwinProfileEntity): Long

    @Update
    suspend fun update(profile: TwinProfileEntity)

    @Delete
    suspend fun delete(profile: TwinProfileEntity)

    @Query("SELECT * FROM twin_profiles WHERE id = :id")
    fun observeById(id: Long): Flow<TwinProfileEntity?>

    @Query("SELECT * FROM twin_profiles WHERE id = :id")
    suspend fun getById(id: Long): TwinProfileEntity?

    @Query("SELECT * FROM twin_profiles ORDER BY createdAtEpochMillis")
    suspend fun getAll(): List<TwinProfileEntity>

    @Query("SELECT * FROM twin_profiles ORDER BY createdAtEpochMillis")
    fun observeAll(): Flow<List<TwinProfileEntity>>

    @Query("SELECT MIN(createdAtEpochMillis) FROM twin_profiles")
    suspend fun earliestCreatedAt(): Long?

    @Query("DELETE FROM twin_profiles")
    suspend fun deleteAll()
}
