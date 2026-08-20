package dev.eversorhn.gait.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One Twin per activity type (see docs/activities-and-dimensions.md).
 * v1 only ever creates a single row, for RUNNING.
 */
/** Yearly PTO-style allowance -- see "Vacation days" in docs/telemetry-and-forecasting.md. */
const val VACATION_DAYS_PER_YEAR = 30

object OpponentType {
    const val TWIN = "twin"
    const val HORDE = "horde"
}

/**
 * One opponent per activity type (see docs/activities-and-dimensions.md). Doubles as either
 * a Rival Twin or a Zombie Horde profile -- see docs/zombie-mode.md for why they share a
 * table: `fidelity` reads as Horde Proximity and `generation` as Wave number when
 * [opponentType] is [OpponentType.HORDE], and `personaKey` holds the horde intensity key
 * instead of a persona key. v1 only ever creates a single row, for RUNNING.
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
    /** Bit (dayOfWeek - 1) set = that ISO day-of-week is a declared rest day. */
    val restDayMask: Int = 0,
    val vacationDaysUsedThisYear: Int = 0,
    /** Calendar year [vacationDaysUsedThisYear] applies to; a new year resets the count. */
    val vacationYear: Int = 0,
    /** Set and in the future while an active vacation period is running. */
    val vacationEndEpochMillis: Long? = null,
    val opponentType: String = OpponentType.TWIN,
)
