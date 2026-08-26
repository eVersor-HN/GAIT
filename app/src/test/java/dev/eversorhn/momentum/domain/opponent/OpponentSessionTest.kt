package dev.eversorhn.momentum.domain.opponent

import dev.eversorhn.momentum.data.db.entity.SessionEntity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpponentSessionTest {

    private val day = 86_400_000L
    private val now = 20_000L * day + 12 * 3_600_000L

    private fun session(ageDays: Int, iso: Int) = SessionEntity(
        activityType = "RUNNING",
        startTimeEpochMillis = now - ageDays * day,
        dayOfWeek = iso,
        durationSeconds = 1800,
        distanceMeters = 6000.0,
        avgPaceSecPerKm = 300.0,
        forecastPaceSecPerKm = null,
        forecastFinishSeconds = null,
    )

    /** An epoch day whose ISO weekday is [iso]. */
    private fun epochDayWithIso(iso: Int): Long {
        var d = 20_000L
        while ((((d + 3) % 7 + 7) % 7 + 1).toInt() != iso) d++
        return d
    }

    @Test
    fun `it keeps your schedule and skips the days you never train`() {
        val sessions = (1..6).map { session(it * 7, 2) }
        assertNotNull(OpponentSession.planFor(1L, epochDayWithIso(2), sessions, 300.0, 6000.0, 0L))
        assertNull(OpponentSession.planFor(1L, epochDayWithIso(5), sessions, 300.0, 6000.0, 0L))
    }

    @Test
    fun `a day you declared off is a day off for it too`() {
        val sessions = (1..7).map { session(it, ((it % 7) + 1)) }
        val d = epochDayWithIso(3)
        assertNull(OpponentSession.planFor(1L, d, sessions, 300.0, 6000.0, 0L, plannedDaysOff = setOf(d)))
        assertNull(OpponentSession.planFor(1L, d, sessions, 300.0, 6000.0, 0L, weeklyRestDays = setOf(3)))
    }

    @Test
    fun `the live view fills up over the session and stops at both ends`() {
        val sessions = (1..7).map { session(it, ((it % 7) + 1)) }
        val d = epochDayWithIso(3)
        val plan = OpponentSession.planFor(1L, d, sessions, 300.0, 6000.0, 0L)!!
        val start = d * day + plan.startMinuteOfDay * 60_000L

        assertNull("nothing before it starts", OpponentSession.liveAt(plan, d, start - 60_000L, 0L))
        val early = OpponentSession.liveAt(plan, d, start + 60_000L, 0L)!!
        assertTrue("barely under way", early.coveredMeters < plan.distanceMeters * 0.2)
        assertTrue("still time on the clock", early.remainingSeconds > 0)
        val late = OpponentSession.liveAt(plan, d, start + (plan.durationSeconds - 10) * 1000L, 0L)!!
        assertTrue("nearly all of it", late.coveredMeters > plan.distanceMeters * 0.9)
        assertNull("nothing after it ends", OpponentSession.liveAt(plan, d, start + (plan.durationSeconds + 60) * 1000L, 0L))
    }

    @Test
    fun `no forecast means no session to share`() {
        val sessions = (1..7).map { session(it, ((it % 7) + 1)) }
        assertNull(OpponentSession.planFor(1L, epochDayWithIso(3), sessions, null, 6000.0, 0L))
        assertNull(OpponentSession.planFor(1L, epochDayWithIso(3), sessions, 300.0, null, 0L))
    }
}
