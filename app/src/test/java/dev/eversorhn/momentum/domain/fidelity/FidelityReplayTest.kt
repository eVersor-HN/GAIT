package dev.eversorhn.momentum.domain.fidelity

import dev.eversorhn.momentum.data.db.entity.SessionEntity
import dev.eversorhn.momentum.domain.trial.DecommissionTrial
import org.junit.Assert.assertEquals
import org.junit.Test

class FidelityReplayTest {

    private fun session(
        actual: Double,
        forecast: Double?,
        restDay: Boolean = false,
        duelWon: Boolean? = null,
        t: Long = 0L,
    ) = SessionEntity(
        activityType = "RUNNING",
        startTimeEpochMillis = t,
        dayOfWeek = 2,
        durationSeconds = 1800,
        distanceMeters = 5000.0,
        avgPaceSecPerKm = actual,
        forecastPaceSecPerKm = forecast,
        forecastFinishSeconds = null,
        isRestDay = restDay,
        isDuel = duelWon != null,
        duelWon = duelWon,
    )

    @Test
    fun `session fidelity is one minus the normalised pace error, clamped`() {
        assertEquals(1f, FidelityReplay.sessionFidelity(300.0, 300.0), 0.0001f)
        assertEquals(0.9f, FidelityReplay.sessionFidelity(300.0, 270.0), 0.0001f)
        assertEquals(0.9f, FidelityReplay.sessionFidelity(300.0, 330.0), 0.0001f)
        assertEquals(0f, FidelityReplay.sessionFidelity(300.0, 900.0), 0.0001f)
        assertEquals(0f, FidelityReplay.sessionFidelity(0.0, 300.0), 0.0001f)
    }

    @Test
    fun `replay starts at the initial value and applies only forecasted non-rest sessions`() {
        // Newest first, like the DAO returns.
        val newestFirst = listOf(
            session(actual = 300.0, forecast = 300.0, t = 3),  // exact: pushes fidelity up
            session(actual = 300.0, forecast = 300.0, restDay = true, t = 2), // frozen
            session(actual = 300.0, forecast = null, t = 1),   // baseline, no forecast
        )
        val h = FidelityReplay.history(newestFirst)
        assertEquals(listOf(0.5f, FidelityReplay.step(0.5f, 1f)), h)
        assertEquals(0.6f, h.last(), 0.0001f)
    }

    @Test
    fun `a won duel resets the curve`() {
        val newestFirst = listOf(
            session(actual = 300.0, forecast = 300.0, t = 3),
            session(actual = 280.0, forecast = 300.0, duelWon = true, t = 2),
            session(actual = 300.0, forecast = 300.0, t = 1),
        )
        val h = FidelityReplay.history(newestFirst)
        assertEquals(4, h.size)
        assertEquals(DecommissionTrial.RESET_FIDELITY, h[2], 0.0001f)
        assertEquals(FidelityReplay.step(DecommissionTrial.RESET_FIDELITY, 1f), h[3], 0.0001f)
    }

    @Test
    fun `replay matches the finalizer's running update step by step`() {
        var running = 0.5f
        val sessions = (1..5).map { i -> session(actual = 300.0 + i * 5, forecast = 300.0, t = i.toLong()) }
        sessions.forEach { running = FidelityReplay.step(running, FidelityReplay.sessionFidelity(300.0, it.avgPaceSecPerKm)) }
        val h = FidelityReplay.history(sessions.asReversed())
        assertEquals(running, h.last(), 0.0001f)
    }
}
