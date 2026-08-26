package dev.eversorhn.momentum.domain.roster

import dev.eversorhn.momentum.domain.ledger.LedgerState
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
    fun `user index starts at the bottom on enrolment, rises with lead, falls with fidelity`() {
        val start = RosterEngine.userIndex(empty, 50)
        assertTrue("a new asset starts under the floor, got $start", start < RosterEngine.FLOOR)
        val s = RosterEngine.snapshot(20_000, 20_100, 12 * 60, empty, 50, empty)
        // Every asset now enters provisional at the bottom, so a new user sits among the newest
        // hires rather than alone in last place — but still in the bottom tenth of the board.
        assertTrue("new user starts at the bottom (rank ${s.user.rank} of ${s.enrolled})", s.user.rank > s.enrolled * 0.9)
        assertTrue("the model starts behind the user at parity", s.twin!!.rank > s.user.rank)
        assertTrue(RosterEngine.userIndex(LedgerState(6, 0, emptyList()), 50) > start + 150)
        assertTrue(RosterEngine.userIndex(LedgerState(0, 6, emptyList()), 50) < start)
        assertTrue(RosterEngine.userIndex(empty, 96) < RosterEngine.userIndex(empty, 50))
        assertTrue(RosterEngine.userIndex(LedgerState(40, 0, emptyList()), 50) < RosterEngine.CEILING)
    }

    @Test
    fun `a day away costs index and hands it to the model`() {
        val level = LedgerState(4, 2, emptyList())
        val away = LedgerState(4, 2, emptyList(), daysSinceLastSession = 8)
        assertTrue("absence has to cost ground", RosterEngine.userIndex(away, 60) < RosterEngine.userIndex(level, 60))
        assertTrue("the model has to gain it", RosterEngine.twinIndex(away, 60) > RosterEngine.twinIndex(level, 60))
        // One day off is a rest day, not a decline.
        val oneDay = LedgerState(4, 2, emptyList(), daysSinceLastSession = 1)
        assertEquals(RosterEngine.userIndex(level, 60), RosterEngine.userIndex(oneDay, 60), 0.001)
        // Capped: a long holiday costs ground, not the whole board.
        val holiday = LedgerState(4, 2, emptyList(), daysSinceLastSession = 300)
        assertEquals(
            RosterEngine.userIndex(LedgerState(4, 2, emptyList(), daysSinceLastSession = 22), 60),
            RosterEngine.userIndex(holiday, 60),
            0.001,
        )
    }

    @Test
    fun `hires enter at the bottom and climb out of it`() {
        // A board built well after founding contains hires of every tenure; the newest of them
        // must sit below the settled ones rather than appearing mid-table.
        val s = RosterEngine.snapshot(20_000, 20_300, 23 * 60, empty, 50, empty)
        val newest = s.standings.filter { it.asset.hiredDay > 20_300 - RosterEngine.PROVISIONAL_DAYS && it.asset.hiredDay > 19_700 }
        assertTrue("expected some provisional hires on the board", newest.isNotEmpty())
        val settled = s.standings.filter { 20_300 - it.asset.hiredDay > 200 }
        val medianNew = newest.map { it.index }.sorted()[newest.size / 2]
        val medianOld = settled.map { it.index }.sorted()[settled.size / 2]
        assertTrue("new hires ($medianNew) must rank below settled staff ($medianOld)", medianNew < medianOld)
    }

}
