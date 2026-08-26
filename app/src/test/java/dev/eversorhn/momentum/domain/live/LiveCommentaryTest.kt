package dev.eversorhn.momentum.domain.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveCommentaryTest {

    @Test
    fun `speaks at every kilometre mark with the current zone`() {
        val c = LiveCommentary(cooldownSeconds = 10)
        assertNull(c.onTick(10, 400.0, 5.0))
        val t = c.onTick(300, 1000.0, 12.0)
        assertEquals(LiveCommentary.Trigger.KmMark(1, LiveZone.AHEAD, 12.0), t)
        assertNull(c.onTick(301, 1010.0, 12.0))
        val t2 = c.onTick(600, 2000.0, -1.0)
        assertEquals(LiveCommentary.Trigger.KmMark(2, LiveZone.LEVEL, -1.0), t2)
    }

    @Test
    fun `lead change fires once per flip, never inside the level band, and respects cooldown`() {
        val c = LiveCommentary(cooldownSeconds = 45, levelBandSecPerKm = 4.0)
        assertNull(c.onTick(40, 100.0, 10.0))   // establishes AHEAD silently
        assertNull(c.onTick(50, 150.0, 2.0))    // level band: nothing
        val flip = c.onTick(60, 200.0, -9.0)
        assertEquals(LiveCommentary.Trigger.LeadChange(LiveZone.BEHIND, -9.0), flip)
        assertNull(c.onTick(70, 250.0, -12.0))  // same side, no repeat
        assertNull(c.onTick(80, 300.0, 9.0))    // flipped back but inside cooldown
        val later = c.onTick(106, 400.0, 9.0)   // cooldown over, still flipped → fires
        assertEquals(LiveCommentary.Trigger.LeadChange(LiveZone.AHEAD, 9.0), later)
    }

    @Test
    fun `per-session cap holds`() {
        val c = LiveCommentary(cooldownSeconds = 0, maxLines = 2)
        assertTrue(c.onTick(100, 1000.0, 1.0) is LiveCommentary.Trigger.KmMark)
        assertTrue(c.onTick(200, 2000.0, 1.0) is LiveCommentary.Trigger.KmMark)
        assertNull(c.onTick(300, 3000.0, 1.0))
    }

    @Test
    fun `no pace yet means no callout, even at a kilometre mark`() {
        val c = LiveCommentary(cooldownSeconds = 0)
        assertNull(c.onTick(100, 1000.0, null))
    }
}
