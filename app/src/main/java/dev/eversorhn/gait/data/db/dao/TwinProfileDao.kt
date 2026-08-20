package dev.eversorhn.gait.data.db.dao

import androidx.room.Dao
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

    @Query("SELECT * FROM twin_profiles WHERE activityType = :activityType LIMIT 1")
    fun observeProfile(activityType: String): Flow<TwinProfileEntity?>

    @Query("SELECT * FROM twin_profiles WHERE activityType = :activityType LIMIT 1")
    suspend fun getProfile(activityType: String): TwinProfileEntity?
}
