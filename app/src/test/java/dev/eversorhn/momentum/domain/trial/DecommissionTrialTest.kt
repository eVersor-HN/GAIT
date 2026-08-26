package dev.eversorhn.momentum.domain.trial

import dev.eversorhn.momentum.data.db.entity.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecommissionTrialTest {

    private fun session(paceSecPerKm: Double, distanceMeters: Double = 5000.0) = SessionEntity(
        activityType = "RUNNING",
        startTimeEpochMillis = 0L,
        dayOfWeek = 2,
        durationSeconds = (paceSecPerKm * distanceMeters / 1000.0).toInt(),
        distanceMeters = distanceMeters,
        avgPaceSecPerKm = paceSecPerKm,
        forecastPaceSecPerKm = null,
        forecastFinishSeconds = null,
    )

    @Test
    fun `eligible exactly at the threshold, not below`() {
        assertTrue(DecommissionTrial.isEligible(DecommissionTrial.THRESHOLD))
        assertTrue(DecommissionTrial.isEligible(0.99f))
        assertFalse(DecommissionTrial.isEligible(0.949f))
    }

    @Test
    fun `progress meter is the fraction of the way to the threshold, clamped`() {
        assertEquals(0, DecommissionTrial.progressPercent(0f))
        assertEquals(100, DecommissionTrial.progressPercent(0.95f))
        assertEquals(100, DecommissionTrial.progressPercent(1f))
        assertEquals(50, DecommissionTrial.progressPercent(0.475f))
    }

    @Test
    fun `target is the fastest pace ever held over a real distance`() {
        val history = listOf(
            session(330.0),
            session(300.0),            // strongest real session
            session(240.0, 400.0),     // a sprint: under the 1 km minimum, ignored
            session(360.0),
        )
        assertEquals(300.0, DecommissionTrial.targetPaceSecPerKm(history)!!, 0.0001)
    }

    @Test
    fun `no target without a qualifying session`() {
        assertNull(DecommissionTrial.targetPaceSecPerKm(emptyList()))
        assertNull(DecommissionTrial.targetPaceSecPerKm(listOf(session(300.0, 500.0))))
    }

    @Test
    fun `verdict needs the minimum distance and a strictly faster pace`() {
        assertEquals(DecommissionTrial.Verdict.TOO_SHORT, DecommissionTrial.judge(999.0, 200.0, 300.0))
        assertEquals(DecommissionTrial.Verdict.WON, DecommissionTrial.judge(1000.0, 299.0, 300.0))
        assertEquals(DecommissionTrial.Verdict.LOST, DecommissionTrial.judge(1000.0, 300.0, 300.0))
        assertEquals(DecommissionTrial.Verdict.LOST, DecommissionTrial.judge(5000.0, 301.0, 300.0))
    }
}
