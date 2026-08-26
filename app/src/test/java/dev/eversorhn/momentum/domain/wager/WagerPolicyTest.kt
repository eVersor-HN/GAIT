package dev.eversorhn.momentum.domain.wager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WagerPolicyTest {

    @Test
    fun `the opponent only stakes when confident, with history, and not on a rest day`() {
        assertTrue(WagerPolicy.shouldStake(55, 3, isRestPeriod = false))
        assertFalse(WagerPolicy.shouldStake(54, 10, isRestPeriod = false))
        assertFalse(WagerPolicy.shouldStake(90, 2, isRestPeriod = false))
        assertFalse(WagerPolicy.shouldStake(90, 10, isRestPeriod = true))
    }

    @Test
    fun `round stake is 1, 2 when staked, 4 when called`() {
        assertEquals(1, WagerPolicy.roundStake(hasOpenStake = false, called = false))
        assertEquals(1, WagerPolicy.roundStake(hasOpenStake = false, called = true))
        assertEquals(2, WagerPolicy.roundStake(hasOpenStake = true, called = false))
        assertEquals(4, WagerPolicy.roundStake(hasOpenStake = true, called = true))
    }

    @Test
    fun `epoch day respects the zone offset`() {
        // 23:30 UTC on day 0 is already day 1 at UTC+1.
        val t = 23L * 3600_000 + 30 * 60_000
        assertEquals(0L, WagerPolicy.epochDay(t, 0))
        assertEquals(1L, WagerPolicy.epochDay(t, 3600_000))
        assertEquals(-1L, WagerPolicy.epochDay(0L, -1))
    }
}
