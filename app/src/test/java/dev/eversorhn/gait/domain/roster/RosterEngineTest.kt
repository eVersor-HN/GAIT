package dev.eversorhn.gait.domain.roster

import dev.eversorhn.gait.domain.ledger.LedgerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RosterEngineTest {

    private val empty = LedgerState(0, 0, emptyList())

    @Test
    fun `snapshot is deterministic and ranks every live asset exactly once`() {
        val a = RosterEngine.snapshot(20_000, 20_100, 12 * 60, empty, 50, empty)
        val b = RosterEngine.snapshot(20_000, 20_100, 12 * 60, empty, 50, empty)
        assertEquals(a.standings.map { it.asset.id to it.index }, b.standings.map { it.asset.id to it.index })
        val ranks = a.standings.map { it.rank } + a.user.rank + listOfNotNull(a.twin?.rank)
        assertEquals(ranks.size, ranks.toSet().size)
        assertEquals((1..ranks.size).toList(), ranks.sorted())
        assertTrue("headcount ${a.standings.size}", a.standings.size in 850..RosterEngine.ROSTER_SIZE)
        assertTrue(a.standings.zipWithNext().all { (x, y) -> x.index >= y.index })
    }

    @Test
    fun `the division has a history - people have been decommissioned and rehired before the user enrolled`() {
        val s = RosterEngine.snapshot(20_000, 20_100, 12 * 60, empty, 50, empty)
        assertTrue("expected firings over ~520 days, got ${s.decommissioned.size}", s.decommissioned.size >= 400)
        assertTrue(s.standings.any { it.asset.hireIndex > 0 })
        assertTrue(s.standings.any { it.asset.kind == AssetKind.SYNTH })
        val f = s.standings.count { it.asset.kind == AssetKind.HUMAN_F }
        val m = s.standings.count { it.asset.kind == AssetKind.HUMAN_M }
        assertTrue("gender split $f:$m", f.toDouble() / (f + m) in 0.42..0.58)
    }

    @Test
    fun `results land at training time - the board moves through the day`() {
        val early = RosterEngine.snapshot(20_000, 20_100, 4 * 60, empty, 50, empty)
        val late = RosterEngine.snapshot(20_000, 20_100, 23 * 60 + 59, empty, 50, empty)
        val changed = early.standings.zip(late.standings.sortedBy { it.asset.slot }.let { l -> early.standings.map { e -> l.first { it.asset.slot == e.asset.slot } } })
            .count { (e, l) -> e.index != l.index }
        assertTrue("expected most assets to move between 04:00 and 23:59, got $changed", changed > 400)
        assertTrue(early.movers.size <= 8)
    }

    @Test
    fun `user index sits at 500 on enrolment, rises with lead, falls with fidelity`() {
        assertEquals(500.0, RosterEngine.userIndex(empty, 50), 0.5)
        assertTrue(RosterEngine.userIndex(LedgerState(6, 0, emptyList()), 50) > 650)
        assertTrue(RosterEngine.userIndex(LedgerState(0, 6, emptyList()), 50) < 350)
        assertTrue(RosterEngine.userIndex(empty, 96) < RosterEngine.userIndex(empty, 50))
        assertTrue(RosterEngine.userIndex(LedgerState(40, 0, emptyList()), 50) < RosterEngine.CEILING)
    }
}
