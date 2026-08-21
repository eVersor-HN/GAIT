package dev.eversorhn.gait.domain.roster

import dev.eversorhn.gait.domain.ledger.LedgerState
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The division's roster: ROSTER_SIZE simulated assets plus the user, ranked by Retention Index.
 *
 * Everything is DETERMINISTIC from (slot, hireIndex, day) via hashing — no 1,000 rows in the
 * database, no randomness that changes on re-open, and yesterday's board is simply the same
 * function evaluated at day − 1 (which is what the ▲▼ arrows compare against). Each slot is
 * simulated day by day from the division's founding because firings depend on history:
 * an asset that drops under the floor at a fortnightly review is decommissioned, the slot
 * is rehired three days later with a new person, and the decommissioned ones become the
 * Horde. ~1,000 × ~500 day-steps of cheap arithmetic; cached per process per day.
 *
 * What makes them feel alive isn't visible as such — it's in the parameters: archetype,
 * talent, consistency, grit, trend, a training time of day, rest days, leave periods, the
 * odd injury — and in the fact that their results land at *their* training time, so the
 * board keeps shifting through the day.
 */

enum class AssetKind { HUMAN_F, HUMAN_M, SYNTH }

enum class Archetype(val label: String) {
    GRINDER("the grinder"), METRONOME("the metronome"), SPRINTER("the sprinter"),
    WEEKENDER("the weekend warrior"), COMEBACK("the comeback"), FADER("the fader"),
    EARLY_BIRD("the early bird"), NIGHT_OWL("the night owl"), STEADY("the steady hand"),
}

enum class AssetStatus { ACTIVE, NEW_HIRE, ON_LEAVE, UNDER_REVIEW, INJURED, MAINTENANCE }

/** A simulated person (or synth) occupying a roster slot for one stretch of employment. */
data class SimAsset(
    val slot: Int,
    val hireIndex: Int,
    val id: String,
    val name: String,
    val kind: AssetKind,
    val unit: String,
    val archetype: Archetype,
    /** Long-run level the index reverts to (330..760). */
    val talent: Double,
    /** 0..1; high = small day-to-day swings. Synths are very consistent. */
    val consistency: Double,
    /** 0..1; high = bounces back from bad stretches fast. */
    val grit: Double,
    /** Index points per 30 days of underlying drift (−12..+14). */
    val trend: Double,
    /** Minute of day their session result lands. */
    val trainingMinute: Int,
    /** Bit (isoDay−1) set = rests that weekday. */
    val restMask: Int,
    val hiredDay: Long,
)

data class Standing(
    val asset: SimAsset,
    val index: Int,
    /** Index change vs. yesterday's close. */
    val delta: Int,
    val rank: Int,
    val prevRank: Int?,
    val status: AssetStatus,
)

/** The user's own row, computed from their ledger so it lives in the same index space. */
data class UserStanding(val index: Int, val delta: Int, val rank: Int, val prevRank: Int?)

data class Decommissioned(val asset: SimAsset, val day: Long, val lastIndex: Int)

data class RosterSnapshot(
    val day: Long,
    val standings: List<Standing>,          // ranked, all ROSTER_SIZE
    val user: UserStanding,
    val enrolled: Int,                      // ROSTER_SIZE + 1
    val underReview: Int,
    val onLeave: Int,
    val newHires30d: Int,
    val decommissioned: List<Decommissioned>, // newest first
    val decommissioned30d: Int,
    /** Biggest movers today (abs delta), for the ticker. */
    val movers: List<Standing>,
    val nextReviewInDays: Int,
)

object RosterEngine {

    const val ROSTER_SIZE = 1000
    const val FLOOR = 340.0
    const val CEILING = 1000.0
    const val REVIEW_EVERY_DAYS = 14
    /** How far back the division exists before the user enrolled — gives the board a history. */
    const val PREHISTORY_DAYS = 420L
    private const val REHIRE_GAP_DAYS = 3
    private const val MS_PER_DAY = 86_400_000L

    // ---------------------------------------------------------------- hashing
    // Fixed-arity on purpose: ~500k day-steps × a dozen draws each — vararg arrays would
    // dominate the cost on a phone. splitmix64-style finaliser over the folded parts.
    private const val K1 = -0x61c8864680b583ebL
    private const val K2 = -0x40a7b892e31b1a47L
    private fun fold(h: Long, p: Long): Long { val x = (h xor p) * K2; return x xor (x ushr 29) }
    private fun fin(h: Long): Long { var x = h; x = (x xor (x ushr 31)) * -0x7fb5d329728ea185L; x = (x xor (x ushr 27)) * -0x6b8c0d2b5f4c9e3fL; return x xor (x ushr 33) }
    private fun toUnit(h: Long): Double = (fin(h) ushr 11).toDouble() / (1L shl 53).toDouble()
    /** Uniform [0,1). */
    private fun u(a: Long, b: Long, c: Long): Double = toUnit(fold(fold(fold(K1, a), b), c))
    private fun u(a: Long, b: Long, c: Long, d: Long): Double = toUnit(fold(fold(fold(fold(K1, a), b), c), d))
    /** Roughly normal, mean 0, sd 1 (sum of 4 uniforms). */
    private fun n(a: Long, b: Long, c: Long): Double {
        val base = fold(fold(fold(K1, a), b), c)
        return (toUnit(fold(base, 1)) + toUnit(fold(base, 2)) + toUnit(fold(base, 3)) + toUnit(fold(base, 4)) - 2.0) * 1.7320508
    }
    private fun n(a: Long, b: Long, c: Long, d: Long): Double {
        val base = fold(fold(fold(fold(K1, a), b), c), d)
        return (toUnit(fold(base, 1)) + toUnit(fold(base, 2)) + toUnit(fold(base, 3)) + toUnit(fold(base, 4)) - 2.0) * 1.7320508
    }

    // ---------------------------------------------------------------- asset generation
    fun asset(slot: Int, hireIndex: Int, hiredDay: Long): SimAsset {
        val s = slot.toLong(); val h = hireIndex.toLong()
        val kindRoll = u(s, h, 10)
        val kind = when {
            kindRoll < 0.04 -> AssetKind.SYNTH
            kindRoll < 0.52 -> AssetKind.HUMAN_F
            else -> AssetKind.HUMAN_M
        }
        // A middle initial for some — 1,000 people from two name pools collide otherwise.
        val initial = if (u(s, h, 23) < 0.45) " ${'A' + (u(s, h, 24) * 26).toInt()}." else ""
        val name = when (kind) {
            AssetKind.HUMAN_F -> "${Names.firstF[(u(s, h, 11) * Names.firstF.size).toInt()]}$initial ${Names.last[(u(s, h, 12) * Names.last.size).toInt()]}"
            AssetKind.HUMAN_M -> "${Names.firstM[(u(s, h, 11) * Names.firstM.size).toInt()]}$initial ${Names.last[(u(s, h, 12) * Names.last.size).toInt()]}"
            AssetKind.SYNTH -> "${Names.synthSeries[(u(s, h, 11) * Names.synthSeries.size).toInt()]}-${(u(s, h, 13) * 90 + 10).toInt()} “${Names.synthCallsigns[(u(s, h, 12) * Names.synthCallsigns.size).toInt()]}”"
        }
        val archetype = Archetype.entries[(u(s, h, 14) * Archetype.entries.size).toInt()]
        val talent = when (archetype) {
            Archetype.GRINDER -> 560 + n(s, h, 15) * 60
            Archetype.SPRINTER -> 540 + n(s, h, 15) * 90
            Archetype.FADER -> 520 + n(s, h, 15) * 70
            Archetype.COMEBACK -> 500 + n(s, h, 15) * 80
            else -> 540 + n(s, h, 15) * 75
        }.coerceIn(330.0, 760.0).let { if (kind == AssetKind.SYNTH) (it + 40).coerceAtMost(780.0) else it }
        val consistency = when {
            kind == AssetKind.SYNTH -> 0.92 + u(s, h, 16) * 0.06
            archetype == Archetype.METRONOME || archetype == Archetype.STEADY -> 0.75 + u(s, h, 16) * 0.2
            archetype == Archetype.SPRINTER || archetype == Archetype.WEEKENDER -> 0.25 + u(s, h, 16) * 0.3
            else -> 0.4 + u(s, h, 16) * 0.4
        }
        val grit = if (archetype == Archetype.COMEBACK || archetype == Archetype.GRINDER) 0.7 + u(s, h, 17) * 0.3 else 0.2 + u(s, h, 17) * 0.6
        val trend = when (archetype) {
            Archetype.FADER -> -8.0 - u(s, h, 18) * 8
            Archetype.GRINDER, Archetype.COMEBACK -> 4.0 + u(s, h, 18) * 10
            else -> -4.0 + u(s, h, 18) * 8
        }.let { if (kind == AssetKind.SYNTH) it * 0.3 else it }
        val trainingMinute = when (archetype) {
            Archetype.EARLY_BIRD -> 5 * 60 + (u(s, h, 19) * 90).toInt()
            Archetype.NIGHT_OWL -> 20 * 60 + (u(s, h, 19) * 150).toInt()
            else -> if (kind == AssetKind.SYNTH) 3 * 60 + (u(s, h, 19) * 60).toInt() else 6 * 60 + (u(s, h, 19) * 14 * 60).toInt()
        }
        val restMask = when {
            kind == AssetKind.SYNTH -> 0
            archetype == Archetype.WEEKENDER -> 0b0011111 // rests Mon–Fri
            archetype == Archetype.GRINDER -> 1 shl (u(s, h, 20) * 7).toInt()
            else -> (1 shl (u(s, h, 20) * 7).toInt()) or (1 shl (u(s, h, 21) * 7).toInt())
        }
        val unit = Names.units[(u(s, h, 22) * Names.units.size).toInt()]
        val id = when (kind) {
            AssetKind.SYNTH -> "SX-%04d".format(slot)
            else -> "AX-%04d".format(slot)
        }
        return SimAsset(slot, hireIndex, id, name, kind, unit, archetype, talent, consistency, grit, trend, trainingMinute, restMask, hiredDay)
    }

    // ---------------------------------------------------------------- per-slot day simulation
    private class SlotState(var asset: SimAsset, var index: Double, var injuryUntil: Long, var leaveUntil: Long, var rehireAt: Long)

    private data class DayResult(val index: Double, val status: AssetStatus)

    /**
     * Advance one slot by one day. Leave and injury are deterministic events of (slot, hire,
     * day); a review every REVIEW_EVERY_DAYS days fires decommissioning under the floor.
     */
    private fun step(st: SlotState, day: Long, foundingDay: Long, fired: MutableList<Decommissioned>): DayResult {
        // Vacant slot waiting for a rehire.
        if (st.rehireAt > day) return DayResult(Double.NaN, AssetStatus.ACTIVE)
        if (st.rehireAt == day) {
            st.asset = asset(st.asset.slot, st.asset.hireIndex + 1, day)
            st.index = st.asset.talent - 40 + n(st.asset.slot.toLong(), st.asset.hireIndex.toLong(), 30) * 30
            st.injuryUntil = -1; st.leaveUntil = -1; st.rehireAt = -1
        }
        val a = st.asset
        val s = a.slot.toLong(); val h = a.hireIndex.toLong()
        val isoDay = ((((day % 7) + 7) % 7 + 3) % 7 + 1).toInt() // 1970-01-01 was a Thursday → iso 4
        val tenure = day - a.hiredDay

        // Leave: humans take ~2 periods a year of 5–14 days; synths have 2-day maintenance windows.
        if (st.leaveUntil < day) {
            val roll = u(s, h, day, 40)
            val p = if (a.kind == AssetKind.SYNTH) 1.0 / 60 else 2.0 / 365
            if (roll < p && tenure > 20) {
                st.leaveUntil = day + if (a.kind == AssetKind.SYNTH) 2 else 5 + (u(s, h, day, 41) * 10).toInt()
            }
        }
        if (st.leaveUntil >= day) {
            return DayResult(st.index, if (a.kind == AssetKind.SYNTH) AssetStatus.MAINTENANCE else AssetStatus.ON_LEAVE)
        }
        // Injury: rare; drops the index and suppresses training for 1–3 weeks.
        if (st.injuryUntil < day && a.kind != AssetKind.SYNTH && u(s, h, day, 42) < 1.0 / 220) {
            st.injuryUntil = day + 7 + (u(s, h, day, 43) * 14).toInt()
            st.index -= 35 + u(s, h, day, 44) * 55
        }
        if (st.injuryUntil >= day) return DayResult(st.index, AssetStatus.INJURED)

        // Training day?
        val rests = (a.restMask shr (isoDay - 1)) and 1 == 1
        if (!rests) {
            // Underlying level: talent + trend drift + slow form cycles; the day's result is a
            // noisy draw around it; the index moves a fraction of the way there (mean reversion),
            // faster for gritty assets when they're below level.
            val drift = a.trend * (tenure / 30.0)
            val form = 24 * sin(2 * Math.PI * (day / 41.0 + u(s, h, 45))) + 12 * sin(2 * Math.PI * (day / 13.0 + u(s, h, 46)))
            val level = (a.talent + drift + form).coerceIn(FLOOR - 80, CEILING)
            val noise = n(s, h, day, 47) * (34 * (1 - a.consistency) + 4)
            val target = level + noise
            val below = target > st.index
            val k = 0.18 + (if (below) a.grit * 0.15 else 0.0)
            st.index += (target - st.index) * k
            st.index = st.index.coerceIn(0.0, CEILING)
        }

        // Fortnightly review: under the floor → decommissioned. Grace for the first month.
        val reviewDay = ((day - foundingDay) % REVIEW_EVERY_DAYS) == 0L
        if (reviewDay && tenure > 30 && st.index < FLOOR) {
            fired += Decommissioned(a, day, st.index.roundToInt())
            st.rehireAt = day + REHIRE_GAP_DAYS
            return DayResult(Double.NaN, AssetStatus.ACTIVE)
        }
        val status = when {
            tenure <= 14 -> AssetStatus.NEW_HIRE
            st.index < FLOOR + 40 -> AssetStatus.UNDER_REVIEW
            else -> AssetStatus.ACTIVE
        }
        return DayResult(st.index, status)
    }

    // ---------------------------------------------------------------- snapshot (cached)
    private data class Cache(val foundingDay: Long, val day: Long, val today: List<DayResult>, val yesterday: List<DayResult>, val assets: List<SimAsset>, val fired: List<Decommissioned>)
    @Volatile private var cache: Cache? = null

    /**
     * Local epoch-day helpers: the roster lives in the user's local days so "today" matches
     * what they see on the clock. [enrolledEpochDay] is the user's profile creation day.
     */
    fun snapshot(enrolledEpochDay: Long, todayEpochDay: Long, minuteOfDay: Int, ledger: LedgerState, fidelityPercent: Int, ledgerYesterday: LedgerState): RosterSnapshot {
        val foundingDay = enrolledEpochDay - PREHISTORY_DAYS
        val c = synchronized(this) {
            cache?.takeIf { it.foundingDay == foundingDay && it.day == todayEpochDay } ?: build(foundingDay, todayEpochDay).also { cache = it }
        }

        // Intraday: an asset's today result only lands at its training minute; before that, yesterday's close shows.
        val rows = ArrayList<Triple<SimAsset, Double, Pair<Double, AssetStatus>>>(ROSTER_SIZE)
        for (i in 0 until ROSTER_SIZE) {
            val a = c.assets[i]
            val t = c.today[i]; val y = c.yesterday[i]
            if (t.index.isNaN() && y.index.isNaN()) continue // vacant both days
            val landed = minuteOfDay >= a.trainingMinute
            val shown = if (landed || y.index.isNaN()) t else y
            if (shown.index.isNaN()) continue
            val prev = if (y.index.isNaN()) shown.index else y.index
            rows += Triple(a, shown.index, prev to (if (landed) t.status else y.status))
        }

        val userIndex = userIndex(ledger, fidelityPercent)
        val userPrev = userIndex(ledgerYesterday, fidelityPercent)

        // Rank today (user included) and yesterday (by previous closes).
        val todaySorted = rows.sortedByDescending { it.second }
        val userRank = 1 + todaySorted.count { it.second > userIndex }
        val yesterdaySorted = rows.map { it.first.slot to it.third.first }.sortedByDescending { it.second }
        val prevRankBySlot = HashMap<Int, Int>(ROSTER_SIZE)
        yesterdaySorted.forEachIndexed { i, (slot, v) -> prevRankBySlot[slot] = i + 1 + (if (userPrev > v) 1 else 0) }
        val userPrevRank = 1 + yesterdaySorted.count { it.second > userPrev }

        val standings = ArrayList<Standing>(rows.size)
        var rank = 0
        for ((a, idx, pv) in todaySorted) {
            rank++
            val r = rank + (if (userIndex > idx) 1 else 0)
            standings += Standing(a, idx.roundToInt(), (idx - pv.first).roundToInt(), r, prevRankBySlot[a.slot], pv.second)
        }
        val movers = standings.filter { it.delta != 0 }.sortedByDescending { abs(it.delta) }.take(8)
        val sinceReview = ((todayEpochDay - foundingDay) % REVIEW_EVERY_DAYS).toInt()
        return RosterSnapshot(
            day = todayEpochDay,
            standings = standings,
            user = UserStanding(userIndex.roundToInt(), (userIndex - userPrev).roundToInt(), userRank, userPrevRank),
            enrolled = standings.size + 1,
            underReview = standings.count { it.status == AssetStatus.UNDER_REVIEW },
            onLeave = standings.count { it.status == AssetStatus.ON_LEAVE || it.status == AssetStatus.MAINTENANCE },
            newHires30d = standings.count { todayEpochDay - it.asset.hiredDay <= 30 },
            decommissioned = c.fired.asReversed(),
            decommissioned30d = c.fired.count { todayEpochDay - it.day <= 30 },
            movers = movers,
            nextReviewInDays = if (sinceReview == 0) 0 else REVIEW_EVERY_DAYS - sinceReview,
        )
    }

    private fun build(foundingDay: Long, todayEpochDay: Long): Cache {
        val states = Array(ROSTER_SIZE) { slot ->
            val a = asset(slot, 0, foundingDay)
            SlotState(a, a.talent + n(slot.toLong(), 0, 30) * 40, -1, -1, -1)
        }
        val fired = ArrayList<Decommissioned>()
        var yesterday: List<DayResult> = emptyList()
        var today: List<DayResult> = emptyList()
        var day = foundingDay
        while (day <= todayEpochDay) {
            val res = ArrayList<DayResult>(ROSTER_SIZE)
            for (st in states) res += step(st, day, foundingDay, fired)
            if (day == todayEpochDay - 1) yesterday = res
            if (day == todayEpochDay) today = res
            day++
        }
        if (yesterday.isEmpty()) yesterday = today
        return Cache(foundingDay, todayEpochDay, today, yesterday, states.map { it.asset }, fired)
    }

    /**
     * The user's Retention Index, in the same space as the simulation: 500 at enrolment,
     * +30 per ledger point of lead (so a 6-point lead is already top-quartile material),
     * and Fidelity pulls it down — a well-modelled asset is a replaceable one.
     */
    fun userIndex(ledger: LedgerState, fidelityPercent: Int): Double {
        val lead = ledger.lead.toDouble()
        val streak = ledger.streak?.let { (side, n) -> if (side == dev.eversorhn.gait.domain.ledger.Side.USER) n else -n } ?: 0
        val raw = 500.0 + 30.0 * lead + 6.0 * streak - 1.5 * (fidelityPercent - 50)
        // Soft clamp so it can't run away with a long lead.
        return (CEILING / (1 + exp(-(raw - 500) / 220)) ).coerceIn(0.0, CEILING)
    }

    fun epochDay(epochMillis: Long, zoneOffsetMillis: Long): Long = Math.floorDiv(epochMillis + zoneOffsetMillis, MS_PER_DAY)

    /** For the horde map: the decommissioned are the zombies. Returns at most [limit], newest first. */
    fun zombies(snapshot: RosterSnapshot, limit: Int = 120): List<Decommissioned> = snapshot.decommissioned.take(limit)
}
