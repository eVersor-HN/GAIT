package dev.eversorhn.gait.domain.transfer

import dev.eversorhn.gait.data.db.entity.SessionEntity
import dev.eversorhn.gait.domain.ledger.Ledger
import dev.eversorhn.gait.domain.roster.Archetype
import dev.eversorhn.gait.domain.roster.AssetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class AssetTransferTest {

    private fun session(id: Long, daysAgo: Long, pace: Double, forecast: Double?, hour: Int = 7) = SessionEntity(
        id = id,
        activityType = "RUNNING",
        startTimeEpochMillis = (20_100L - daysAgo) * 86_400_000L + hour * 3_600_000L,
        dayOfWeek = (((20_100L - daysAgo) % 7 + 3) % 7 + 1).toInt(),
        durationSeconds = (pace * 5).toInt(),
        distanceMeters = 5000.0,
        avgPaceSecPerKm = pace,
        forecastPaceSecPerKm = forecast,
        forecastFinishSeconds = null,
    )

    @Test
    fun `encode then decode round-trips every field`() {
        val sessions = (0 until 12).map { i -> session(i.toLong(), i.toLong(), 320.0 - i, 326.0) }
        val a = AssetTransfer.assess(sessions, Ledger.from(sessions), 62, "Eve R.", AssetKind.HUMAN_F, 20_000, 20_100, 0b0000001, ZoneOffset.UTC)
        val text = AssetTransfer.encode(a)
        assertTrue(text.contains(AssetTransfer.FORMAT))
        val b = AssetTransfer.decode("some mail text\n$text\nregards")
        assertNotNull(b)
        assertEquals(a.id, b!!.id)
        assertEquals(a.name, b.name)
        assertEquals(a.kind, b.kind)
        assertEquals(a.archetype, b.archetype)
        assertEquals(a.talent, b.talent, 0.1)
        assertEquals(a.consistency, b.consistency, 0.001)
        assertEquals(a.grit, b.grit, 0.001)
        assertEquals(a.trend, b.trend, 0.01)
        assertEquals(a.trainingMinute, b.trainingMinute)
        assertEquals(a.restMask, b.restMask)
        assertEquals(a.strengths, b.strengths)
        assertEquals(a.weaknesses, b.weaknesses)
        assertEquals(a.assessment, b.assessment)
    }

    @Test
    fun `assessment reads the data - improving, winning asset is a comeback or better, not a fader`() {
        // Pace improving by 1 s/km per session, beating the forecast every time.
        // Newest first (as the DAO returns): today's pace 320, eleven days ago 331 → getting faster.
        val sessions = (0 until 12).map { i -> session(i.toLong(), i.toLong(), 320.0 + i, 340.0) }
        val a = AssetTransfer.assess(sessions, Ledger.from(sessions), 50, "X", AssetKind.HUMAN_M, 20_000, 20_100, 0, ZoneOffset.UTC)
        assertTrue(a.trend > 0)
        assertTrue(a.archetype != Archetype.FADER)
        assertTrue(a.strengths.any { it.contains("streak") || it.contains("improving") })
        assertEquals(7 * 60, a.trainingMinute)
    }

    @Test
    fun `garbage is rejected`() {
        assertNull(AssetTransfer.decode("hello"))
        assertNull(AssetTransfer.decode("=== ${AssetTransfer.FORMAT} ===\nname: \n=== END ==="))
    }
}
