package dev.eversorhn.momentum.domain.restdays

import dev.eversorhn.momentum.data.db.entity.TwinProfileEntity
import dev.eversorhn.momentum.data.db.entity.VACATION_DAYS_PER_YEAR

/**
 * Two mechanisms, matching docs/telemetry-and-forecasting.md "Rest days": a recurring
 * weekly pattern (declared rest days) and an annual PTO-style bank (vacation), spent as
 * a contiguous block rather than day by day.
 */
object RestDayPolicy {

    private const val MILLIS_PER_DAY = 86_400_000L
    /** Past this many declared weekly rest days, the pattern itself becomes noteworthy. */
    const val ANTI_GAMING_THRESHOLD = 3

    fun isRestDay(profile: TwinProfileEntity, dayOfWeek: Int): Boolean =
        (profile.restDayMask shr (dayOfWeek - 1)) and 1 == 1

    fun isOnVacation(profile: TwinProfileEntity, nowEpochMillis: Long): Boolean =
        (profile.vacationEndEpochMillis ?: -1L) >= nowEpochMillis

    fun declaredRestDayCount(profile: TwinProfileEntity): Int = declaredRestDayCountFromMask(profile.restDayMask)

    fun declaredRestDayCountFromMask(mask: Int): Int =
        (0 until 7).count { (mask shr it) and 1 == 1 }

    fun remainingVacationDays(profile: TwinProfileEntity, currentYear: Int): Int {
        val used = if (profile.vacationYear == currentYear) profile.vacationDaysUsedThisYear else 0
        return (VACATION_DAYS_PER_YEAR - used).coerceAtLeast(0)
    }

    fun toggleRestDay(profile: TwinProfileEntity, dayOfWeek: Int): TwinProfileEntity {
        val bit = 1 shl (dayOfWeek - 1)
        return profile.copy(restDayMask = profile.restDayMask xor bit)
    }

    /** Clamps to whatever's left of the yearly bank; returns [profile] unchanged if none is left. */
    fun startVacation(profile: TwinProfileEntity, days: Int, nowEpochMillis: Long, currentYear: Int): TwinProfileEntity {
        val usedSoFar = if (profile.vacationYear == currentYear) profile.vacationDaysUsedThisYear else 0
        val remaining = (VACATION_DAYS_PER_YEAR - usedSoFar).coerceAtLeast(0)
        val actualDays = days.coerceIn(0, remaining)
        if (actualDays <= 0) return profile
        return profile.copy(
            vacationYear = currentYear,
            vacationDaysUsedThisYear = usedSoFar + actualDays,
            vacationEndEpochMillis = nowEpochMillis + actualDays * MILLIS_PER_DAY,
        )
    }

    fun endVacationEarly(profile: TwinProfileEntity): TwinProfileEntity =
        profile.copy(vacationEndEpochMillis = null)
}
