package dev.eversorhn.gait.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One Twin per activity type (see docs/activities-and-dimensions.md).
 * v1 only ever creates a single row, for RUNNING.
 */
/** Yearly PTO-style allowance -- see "Vacation days" in docs/telemetry-and-forecasting.md. */
const val VACATION_DAYS_PER_YEAR = 30

@Entity(tableName = "twin_profiles")
data class TwinProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityType: String,
    val personaKey: String,
    val twinName: String,
    val fidelity: Float,
    val generation: Int,
    val createdAtEpochMillis: Long,
    /** Bit (dayOfWeek - 1) set = that ISO day-of-week is a declared rest day. */
    val restDayMask: Int = 0,
    val vacationDaysUsedThisYear: Int = 0,
    /** Calendar year [vacationDaysUsedThisYear] applies to; a new year resets the count. */
    val vacationYear: Int = 0,
    /** Set and in the future while an active vacation period is running. */
    val vacationEndEpochMillis: Long? = null,
)
