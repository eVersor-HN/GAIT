package dev.eversorhn.gait.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.eversorhn.gait.data.db.entity.PlannedDayOffEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedDayOffDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(day: PlannedDayOffEntity)

    @Query("DELETE FROM planned_days_off WHERE epochDay = :epochDay")
    suspend fun delete(epochDay: Long)

    @Query("SELECT * FROM planned_days_off ORDER BY epochDay")
    suspend fun getAll(): List<PlannedDayOffEntity>

    @Query("SELECT * FROM planned_days_off ORDER BY epochDay")
    fun observeAll(): Flow<List<PlannedDayOffEntity>>

    @Query("SELECT COUNT(*) FROM planned_days_off WHERE epochDay = :epochDay")
    suspend fun count(epochDay: Long): Int

    @Query("DELETE FROM planned_days_off")
    suspend fun deleteAll()
}
