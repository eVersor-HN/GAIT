package dev.eversorhn.momentum.domain.composure

import dev.eversorhn.momentum.data.db.entity.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposureEngineTest {

    private val engine = ComposureEngine()

    /** delta = forecast - actual; positive means the user beat the forecast. */
    private fun session(forecastPace: Double?, actualPace: Double) = SessionEntity(
        activityType = "RUNNING",
        startTimeEpochMillis = 0L,
        dayOfWeek = 1,
        durationSeconds = 1500,
        distanceMeters = 5000.0,
        avgPaceSecPerKm = actualPace,
        forecastPaceSecPerKm = forecastPace,
        forecastFinishSeconds = null,
    )

    @Test
    fun `fewer than three forecasted sessions stays Watchful`() {
        val two = listOf(session(300.0, 250.0), session(300.0, 250.0))
        assertEquals(ComposureState.WATCHFUL, engine.evaluate(two))
    }

    @Test
    fun `sessions without a forecast are ignored, not treated as zero`() {
        val history = listOf(session(null, 250.0), session(null, 250.0), session(null, 250.0), session(null, 250.0))
        assertEquals(ComposureState.WATCHFUL, engine.evaluate(history))
    }

    @Test
    fun `a session far better than the user's own baseline is Cowed`() {
        // Baseline: consistently on-forecast (delta 0 +- small). Latest: beat forecast by 40 s/km.
        val history = listOf(
            session(300.0, 260.0), // newest: +40
            session(300.0, 301.0), // -1
            session(300.0, 299.0), // +1
            session(300.0, 300.0), //  0
            session(300.0, 302.0), // -2
        )
        assertEquals(ComposureState.COWED, engine.evaluate(history))
    }

    @Test
    fun `a session far worse than the user's own baseline is Predatory`() {
        val history = listOf(
            session(300.0, 340.0), // newest: -40
            session(300.0, 301.0),
            session(300.0, 299.0),
            session(300.0, 300.0),
            session(300.0, 302.0),
        )
        assertEquals(ComposureState.PREDATORY, engine.evaluate(history))
    }

    @Test
    fun `the same absolute delta is Watchful for a user whose baseline is noisy`() {
        // Same +40 latest, but the baseline swings +-40 routinely -> not unusual for this person.
        val history = listOf(
            session(300.0, 260.0), // +40
            session(300.0, 340.0), // -40
            session(300.0, 260.0), // +40
            session(300.0, 340.0), // -40
            session(300.0, 260.0), // +40
        )
        assertEquals(ComposureState.WATCHFUL, engine.evaluate(history))
    }

    @Test
    fun `a perfectly flat baseline cannot produce a z-score and stays Watchful`() {
        val history = listOf(session(300.0, 250.0), session(300.0, 300.0), session(300.0, 300.0), session(300.0, 300.0))
        assertEquals(ComposureState.WATCHFUL, engine.evaluate(history))
    }

    @Test
    fun `gap-predatory needs at least three historical gaps`() {
        assertFalse(engine.isGapPredatory(daysSinceLastSession = 30.0, historicalGapsDays = listOf(2.0, 2.0)))
    }

    @Test
    fun `a gap far beyond the user's own rhythm is predatory`() {
        val gaps = listOf(2.0, 3.0, 2.0, 3.0, 2.0, 3.0)
        assertTrue(engine.isGapPredatory(daysSinceLastSession = 10.0, historicalGapsDays = gaps))
    }

    @Test
    fun `a gap within the user's normal rhythm is not predatory`() {
        val gaps = listOf(2.0, 3.0, 2.0, 3.0, 2.0, 3.0)
        assertFalse(engine.isGapPredatory(daysSinceLastSession = 3.0, historicalGapsDays = gaps))
    }

    @Test
    fun `a user who always trains every 7 days is not predatory at day 7`() {
        // Perfectly regular weekly runner: stddev ~0 -> never predatory, rather than
        // dividing by zero and flagging everything.
        val gaps = listOf(7.0, 7.0, 7.0, 7.0)
        assertFalse(engine.isGapPredatory(daysSinceLastSession = 7.0, historicalGapsDays = gaps))
    }
}
