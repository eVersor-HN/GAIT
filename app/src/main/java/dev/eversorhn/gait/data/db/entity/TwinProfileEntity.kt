package dev.eversorhn.gait.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One Twin per activity type (see docs/activities-and-dimensions.md).
 * v1 only ever creates a single row, for RUNNING.
 */
@Entity(tableName = "twin_profiles")
data class TwinProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityType: String,
    val personaKey: String,
    val twinName: String,
    val fidelity: Float,
    val generation: Int,
    val createdAtEpochMillis: Long,
)
