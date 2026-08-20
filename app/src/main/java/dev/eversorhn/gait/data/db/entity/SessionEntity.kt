package dev.eversorhn.gait.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A completed (or manually logged) session. This is the training corpus the
 * k-nearest-analog Forecast engine queries — see docs/telemetry-and-forecasting.md.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityType: String,
    val startTimeEpochMillis: Long,
    val dayOfWeek: Int, // 1 (Monday) .. 7 (Sunday), ISO-8601
    val durationSeconds: Int,
    val distanceMeters: Double,
    val avgPaceSecPerKm: Double,
    val forecastPaceSecPerKm: Double?,
    val forecastFinishSeconds: Int?,
    val isRestDay: Boolean = false,
)
