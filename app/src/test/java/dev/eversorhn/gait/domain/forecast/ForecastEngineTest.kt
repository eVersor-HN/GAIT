package dev.eversorhn.gait.domain.forecast

import dev.eversorhn.gait.data.db.entity.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastEngineTest {

    private val engine = ForecastEngine()
    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun session(
        ageDays: Double,
        paceSecPerKm: Double,
        distanceMeters: Double = 5000.0,
        dayOfWeek: Int = 2,
    ) = SessionEntity(
        activityType = "RUNNING",
        startTimeEpochMillis = now - (ageDays * day).toLong(),
        dayOfWeek = dayOfWeek,
        durationSeconds = (paceSecPerKm * distanceMeters / 1000.0).toInt(),
        distanceMeters = distanceMeters,
        avgPaceSecPerKm = paceSecPerKm,
        forecastPaceSecPerKm = null,
        forecastFinishSeconds = null,
    )

    @Test
    fun `empty history yields no forecast (cold start)`() {
        assertNull(engine.forecast(emptyList(), targetDayOfWeek = 2, nowEpochMillis = now))
    }

    @Test
    fun `single session forecasts that session's pace with capped confidence`() {
        val result = engine.forecast(listOf(session(ageDays = 1.0, paceSecPerKm = 300.0)), 2, now)
        assertNotNull(result)
        assertEquals(300.0, result!!.forecastPaceSecPerKm, 0.001)
        assertEquals(1, result.basedOnSessions)
        assertEquals(35, result.confidencePercent) // explicit single-sample floor
    }

    @Test
    fun `recent sessions outweigh old ones`() {
        val history = listOf(
            session(ageDays = 1.0, paceSecPerKm = 300.0),   // fresh, fast
            session(ageDays = 90.0, paceSecPerKm = 400.0),  // ancient, slow
        )
        val result = engine.forecast(history, 2, now)!!
        // With a 21-day half-life the 90-day-old session carries ~5% of the fresh one's weight,
        // so the forecast should sit much closer to 300 than to the 350 midpoint.
        assertTrue("expected < 320 but was ${result.forecastPaceSecPerKm}", result.forecastPaceSecPerKm < 320.0)
    }

    @Test
    fun `same day-of-week sessions get extra weight`() {
        val history = listOf(
            session(ageDays = 2.0, paceSecPerKm = 300.0, dayOfWeek = 2), // matches target
            session(ageDays = 2.0, paceSecPerKm = 360.0, dayOfWeek = 5), // same age, different day
        )
        val result = engine.forecast(history, targetDayOfWeek = 2, nowEpochMillis = now)!!
        // Equal recency, so only the 1.5x day-match weight separates them: (300*1.5 + 360) / 2.5 = 324
        assertEquals(324.0, result.forecastPaceSecPerKm, 0.01)
    }

    @Test
    fun `tight pace cluster is more confident than a scattered one`() {
        val tight = (1..8).map { session(ageDays = it.toDouble(), paceSecPerKm = 300.0 + it * 0.5) }
        val scattered = (1..8).map { session(ageDays = it.toDouble(), paceSecPerKm = 240.0 + it * 25.0) }
        val tightConf = engine.forecast(tight, 2, now)!!.confidencePercent
        val scatteredConf = engine.forecast(scattered, 2, now)!!.confidencePercent
        assertTrue("tight=$tightConf should exceed scattered=$scatteredConf", tightConf > scatteredConf)
    }

    @Test
    fun `neighbor count caps how many sessions feed the forecast`() {
        val history = (1..30).map { session(ageDays = it.toDouble(), paceSecPerKm = 300.0) }
        val result = engine.forecast(history, 2, now, neighborCount = 12)!!
        assertEquals(12, result.basedOnSessions)
    }

    @Test
    fun `finish time is pace times forecast distance`() {
        val result = engine.forecast(listOf(session(ageDays = 1.0, paceSecPerKm = 300.0, distanceMeters = 5000.0)), 2, now)!!
        assertEquals(1500, result.forecastFinishSeconds) // 300 s/km * 5 km
    }
}
