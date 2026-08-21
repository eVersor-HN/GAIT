package dev.eversorhn.gait.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A day the user marked off in advance on the Rest & Vacation calendar (local epoch-day).
 * Treated like a declared rest day when it arrives: sessions still count, Fidelity frozen,
 * Composure neutral, no notifications, no stake. The opponent doesn't get to hold a planned
 * day against you — that's the point of planning it.
 */
@Entity(tableName = "planned_days_off")
data class PlannedDayOffEntity(
    @PrimaryKey val epochDay: Long,
    val createdAtEpochMillis: Long,
)
