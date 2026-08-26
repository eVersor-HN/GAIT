package dev.eversorhn.momentum.domain.ledger

import dev.eversorhn.momentum.data.db.entity.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LedgerTest {

    private fun session(
        id: Long,
        actual: Double,
        forecast: Double?,
        stake: Int = 1,
        day: Int = 1,
        restDay: Boolean = false,
        t: Long = id,
    ) = SessionEntity(
        id = id,
        activityType = "RUNNING",
        startTimeEpochMillis = t,
        dayOfWeek = day,
        durationSeconds = 1800,
        distanceMeters = 5000.0,
        avgPaceSecPerKm = actual,
        forecastPaceSecPerKm = forecast,
        forecastFinishSeconds = null,
        isRestDay = restDay,
        stake = stake,
    )

    @Test
    fun `a round goes to the user only when they beat the forecast, ties go to the twin`() {
        assertEquals(Side.USER, Ledger.winnerOf(session(1, 299.0, 300.0)))
        assertEquals(Side.TWIN, Ledger.winnerOf(session(1, 300.0, 300.0)))
        assertEquals(Side.TWIN, Ledger.winnerOf(session(1, 310.0, 300.0)))
        assertNull(Ledger.winnerOf(session(1, 290.0, null)))
        assertNull(Ledger.winnerOf(session(1, 290.0, 300.0, restDay = true)))
    }

    @Test
    fun `points sum the stakes and the lead follows`() {
        val newestFirst = listOf(
            session(4, 290.0, 300.0, stake = 4),  // user, called stake
            session(3, 310.0, 300.0, stake = 2),  // twin, staked
            session(2, 305.0, 300.0),             // twin
            session(1, 300.0, null),              // baseline, no round
        )
        val l = Ledger.from(newestFirst)
        assertEquals(3, l.roundsPlayed)
        assertEquals(4, l.userPoints)
        assertEquals(3, l.twinPoints)
        assertEquals(1, l.lead)
        assertEquals(Side.USER, l.leader)
        assertEquals(Side.USER to 1, l.streak)
        assertEquals(listOf(Side.TWIN, Side.TWIN, Side.USER), l.form())
    }

    @Test
    fun `streak counts consecutive newest rounds and weekday ownership finds the twin's best day`() {
        val newestFirst = listOf(
            session(5, 310.0, 300.0, day = 1),
            session(4, 310.0, 300.0, day = 1),
            session(3, 305.0, 300.0, day = 3),
            session(2, 290.0, 300.0, day = 3),
            session(1, 290.0, 300.0, day = 2),
        )
        val l = Ledger.from(newestFirst)
        assertEquals(Side.TWIN to 3, l.streak)
        assertEquals(1 to (0 to 2), l.opponentStrongestWeekday())
        assertNull(l.userStrongestWeekday()) // day 2 has only one round, day 3 is split
        assertEquals(0.5f, Ledger.from(emptyList()).userShare)
    }
}
