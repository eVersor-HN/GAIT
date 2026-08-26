package dev.eversorhn.momentum.domain.restdays

import dev.eversorhn.momentum.data.db.entity.OpponentType
import dev.eversorhn.momentum.data.db.entity.TwinProfileEntity
import dev.eversorhn.momentum.data.db.entity.VACATION_DAYS_PER_YEAR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestDayPolicyTest {

    private val day = 86_400_000L
    private val now = 1_700_000_000_000L
    private val year = 2026

    private fun profile(
        restDayMask: Int = 0,
        vacationUsed: Int = 0,
        vacationYear: Int = year,
        vacationEnd: Long? = null,
    ) = TwinProfileEntity(
        activityType = "RUNNING",
        opponentType = OpponentType.TWIN,
        personaKey = "hated_person",
        twinName = "Test",
        fidelity = 0.5f,
        generation = 1,
        createdAtEpochMillis = 0L,
        restDayMask = restDayMask,
        vacationDaysUsedThisYear = vacationUsed,
        vacationYear = vacationYear,
        vacationEndEpochMillis = vacationEnd,
    )

    @Test
    fun `no declared rest days means no day is a rest day`() {
        val p = profile()
        (1..7).forEach { assertFalse("day $it", RestDayPolicy.isRestDay(p, it)) }
    }

    @Test
    fun `toggling a day sets and clears exactly that bit`() {
        var p = profile()
        p = RestDayPolicy.toggleRestDay(p, 7) // Sunday
        assertTrue(RestDayPolicy.isRestDay(p, 7))
        assertFalse(RestDayPolicy.isRestDay(p, 6))
        assertEquals(1, RestDayPolicy.declaredRestDayCount(p))
        p = RestDayPolicy.toggleRestDay(p, 7)
        assertFalse(RestDayPolicy.isRestDay(p, 7))
        assertEquals(0, RestDayPolicy.declaredRestDayCount(p))
    }

    @Test
    fun `declared count reflects every set bit`() {
        var p = profile()
        listOf(1, 3, 5, 7).forEach { p = RestDayPolicy.toggleRestDay(p, it) }
        assertEquals(4, RestDayPolicy.declaredRestDayCount(p))
        assertTrue(RestDayPolicy.declaredRestDayCount(p) > RestDayPolicy.ANTI_GAMING_THRESHOLD)
    }

    @Test
    fun `fresh profile has the full vacation bank`() {
        assertEquals(VACATION_DAYS_PER_YEAR, RestDayPolicy.remainingVacationDays(profile(), year))
    }

    @Test
    fun `vacation used in a previous year does not count against this year`() {
        val p = profile(vacationUsed = 30, vacationYear = year - 1)
        assertEquals(VACATION_DAYS_PER_YEAR, RestDayPolicy.remainingVacationDays(p, year))
    }

    @Test
    fun `starting a vacation consumes days and sets an end in the future`() {
        val p = RestDayPolicy.startVacation(profile(), days = 7, nowEpochMillis = now, currentYear = year)
        assertEquals(7, p.vacationDaysUsedThisYear)
        assertEquals(year, p.vacationYear)
        assertEquals(now + 7 * day, p.vacationEndEpochMillis)
        assertTrue(RestDayPolicy.isOnVacation(p, now))
        assertTrue(RestDayPolicy.isOnVacation(p, now + 6 * day))
        assertFalse(RestDayPolicy.isOnVacation(p, now + 8 * day))
    }

    @Test
    fun `vacation is clamped to whatever is left in the bank`() {
        val nearlySpent = profile(vacationUsed = 28)
        val p = RestDayPolicy.startVacation(nearlySpent, days = 10, nowEpochMillis = now, currentYear = year)
        assertEquals(30, p.vacationDaysUsedThisYear) // only 2 were left
        assertEquals(now + 2 * day, p.vacationEndEpochMillis)
    }

    @Test
    fun `an exhausted bank cannot start a vacation`() {
        val spent = profile(vacationUsed = VACATION_DAYS_PER_YEAR)
        val p = RestDayPolicy.startVacation(spent, days = 3, nowEpochMillis = now, currentYear = year)
        assertNull(p.vacationEndEpochMillis)
        assertFalse(RestDayPolicy.isOnVacation(p, now))
    }

    @Test
    fun `ending early clears the active period but keeps the days as spent`() {
        val started = RestDayPolicy.startVacation(profile(), days = 7, nowEpochMillis = now, currentYear = year)
        val ended = RestDayPolicy.endVacationEarly(started)
        assertFalse(RestDayPolicy.isOnVacation(ended, now))
        assertEquals(7, ended.vacationDaysUsedThisYear)
    }
}
