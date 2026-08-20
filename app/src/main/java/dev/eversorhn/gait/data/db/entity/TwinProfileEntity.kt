package dev.eversorhn.gait.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Yearly PTO-style allowance -- see "Vacation days" in docs/telemetry-and-forecasting.md. */
const val VACATION_DAYS_PER_YEAR = 30

object OpponentType {
    const val TWIN = "twin"
    const val HORDE = "horde"
}

/**
 * One opponent per activity type (see docs/activities-and-dimensions.md) -- either a Rival
 * Twin or a Zombie Horde, per [opponentType]. The two share [fidelity] and [generation]
 * because they genuinely are the same numbers (relabeled Proximity/Wave for a Horde in the
 * UI); everything type-specific lives in its own nullable column rather than reusing a
 * field with a different meaning. v1 only ever creates a single row, for RUNNING.
 */
@Entity(tableName = "twin_profiles")
data class TwinProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityType: String,
    val opponentType: String = OpponentType.TWIN,
    /** Twin only: key into Personas. Null for a Horde. */
    val personaKey: String? = null,
    /** Horde only: key into HordeIntensity. Null for a Twin. */
    val hordeIntensity: String? = null,
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

/** Extension rather than a member so Room never has to reason about a non-column property. */
val TwinProfileEntity.isHorde: Boolean get() = opponentType == OpponentType.HORDE
