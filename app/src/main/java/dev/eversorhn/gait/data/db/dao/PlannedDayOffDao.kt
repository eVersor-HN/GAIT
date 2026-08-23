package dev.eversorhn.gait.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.eversorhn.gait.data.db.entity.PlannedDayOffEntity

@Dao
interface PlannedDayOffDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(day: PlannedDayOffEntity)

    @Query("DELETE FROM planned_days_off WHERE profileId = :profileId AND epochDay = :epochDay")
    suspend fun delete(profileId: Long, epochDay: Long)

    @Query("SELECT * FROM planned_days_off WHERE profileId = :profileId ORDER BY epochDay")
    suspend fun getAll(profileId: Long): List<PlannedDayOffEntity>

    @Query("SELECT COUNT(*) FROM planned_days_off WHERE profileId = :profileId AND epochDay = :epochDay")
    suspend fun count(profileId: Long, epochDay: Long): Int

    @Query("DELETE FROM planned_days_off WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)

    @Query("DELETE FROM planned_days_off")
    suspend fun deleteAll()
}
