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

/** An asset taken in from another user's division (domain/transfer): fixed traits, simulated from its import day. */
data class ImportedSpec(
    val id: String,
    val name: String,
    val kind: AssetKind,
    val archetype: Archetype,
    val talent: Double,
    val consistency: Double,
    val grit: Double,
    val trend: Double,
    val trainingMinute: Int,
    val restMask: Int,
    val startIndex: Double,
    val importedDay: Long,
)

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
    /** Set for assets imported from another division (slot >= ROSTER_SIZE). */
    val transferId: String? = null,
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

/** The opponent's row: the same ledger read from its side. Null for a Horde (the horde isn't one asset). */
data class TwinStanding(val index: Int, val delta: Int, val rank: Int, val prevRank: Int?)

data class Decommissioned(val asset: SimAsset, val day: Long, val lastIndex: Int)

data class RosterSnapshot(
    val day: Long,
    val standings: List<Standing>,          // ranked, all ROSTER_SIZE
    val user: UserStanding,
    val twin: TwinStanding?,
    val enrolled: Int,                      // headcount incl. user (and twin)
    val underReview: Int,
    val onLeave: Int,
    val newHires30d: Int,
    val decommissioned: List<Decommissioned>, // newest first
    val decommissioned30d: Int,
    /** Biggest movers today (abs delta), for the ticker. */
    val movers: List<Standing>,
    val nextReviewInDays: Int,
    /** Days until the next quarterly cull (0 = today). */
    val nextCullInDays: Int,
    /** Head-count the last cull removed, and when. */
    val lastCullDay: Long?,
    /** Rank above which an asset is safe at the next cull, given today's headcount. */
    val cullLine: Int,
)

object RosterEngine {

    /** Slots in the division. Between culls the headcount ramps from ~900 up to this. */
    const val ROSTER_SIZE = 1300
    /** Headcount at founding; vacant slots above are hired into over the following weeks. */
    const val INITIAL_HEADCOUNT = 1000
    /** Every CULL_EVERY_DAYS the bottom CULL_COUNT (by index, tenure ≥ CULL_GRACE_DAYS) are decommissioned. */
    const val CULL_EVERY_DAYS = 90
    const val CULL_COUNT = 400
    const val CULL_GRACE_DAYS = 60
    /** New hires per day between culls — refills ~400 over a quarter. */
    private const val HIRES_PER_DAY = 4.5
    const val FLOOR = 340.0
    const val CEILING = 1000.0
    const val REVIEW_EVERY_DAYS = 14
    /** How long a new hire is treated as provisional: it climbs from last toward its level. */
    const val PROVISIONAL_DAYS = 45L
    /** How far back the division exists before the user enrolled — gives the board a history. */
    const val PREHISTORY_DAYS = 420L
    private const val REHIRE_GAP_DAYS = 3
    /** The containment list is a record, not an archive — the map and the page read the newest. */
    private const val MAX_DECOMMISSIONED_KEPT = 600
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
        // Rare hires: a handful of familiar names, ~0.4 % of all hires, humans and synths alike.
        val rare = u(s, h, 25) < 0.004
        val kind = when {
            rare && u(s, h, 26) < 0.5 -> AssetKind.SYNTH
            rare -> if (u(s, h, 26) < 0.75) AssetKind.HUMAN_M else AssetKind.HUMAN_F
            kindRoll < 0.04 -> AssetKind.SYNTH
            kindRoll < 0.52 -> AssetKind.HUMAN_F
            else -> AssetKind.HUMAN_M
        }
        // A middle initial for some — 1,000 people from two name pools collide otherwise.
        val initial = if (u(s, h, 23) < 0.45) " ${'A' + (u(s, h, 24) * 26).toInt()}." else ""
        val name = when {
            rare && kind == AssetKind.SYNTH -> "${Names.synthSeries[(u(s, h, 11) * Names.synthSeries.size).toInt()]}-${(u(s, h, 13) * 90 + 10).toInt()} “${Names.rareSynthCallsigns[(u(s, h, 27) * Names.rareSynthCallsigns.size).toInt()]}”"
            rare -> Names.rareHumans[(u(s, h, 27) * Names.rareHumans.size).toInt()]
            kind == AssetKind.HUMAN_F -> "${Names.firstF[(u(s, h, 11) * Names.firstF.size).toInt()]}$initial ${Names.last[(u(s, h, 12) * Names.last.size).toInt()]}"
            kind == AssetKind.HUMAN_M -> "${Names.firstM[(u(s, h, 11) * Names.firstM.size).toInt()]}$initial ${Names.last[(u(s, h, 12) * Names.last.size).toInt()]}"
            else -> "${Names.synthSeries[(u(s, h, 11) * Names.synthSeries.size).toInt()]}-${(u(s, h, 13) * 90 + 10).toInt()} “${Names.synthCallsigns[(u(s, h, 12) * Names.synthCallsigns.size).toInt()]}”"
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
            if (st.asset.transferId == null) {
                st.asset = asset(st.asset.slot, st.asset.hireIndex + 1, day)
                // A new hire enters where every new asset enters: last. The board's promise is
                // that nothing above the floor is given, and the user starts there too.
                st.index = START_INDEX + n(st.asset.slot.toLong(), st.asset.hireIndex.toLong(), 30) * 12
            }
            // imported: keep the fixed asset and its start index (set when the slot was created)
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
            // Provisional: for its first weeks an asset is only credited part of its level, so it
            // climbs out of the bottom rather than appearing mid-table on day one. Founding-day
            // staff are exempt — the division existed before anyone was watching.
            val settled = if (a.hiredDay <= foundingDay) 1.0 else (tenure / PROVISIONAL_DAYS.toDouble()).coerceIn(0.0, 1.0)
            val full = (a.talent + drift + form).coerceIn(FLOOR - 80, CEILING)
            val level = START_INDEX + (full - START_INDEX) * settled
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
            st.rehireAt = if (a.transferId != null) Long.MAX_VALUE else day + REHIRE_GAP_DAYS
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
    /** Days of per-slot history kept for dossiers (today inclusive). */
    const val HISTORY_DAYS = 14

    private data class Cache(
        val foundingDay: Long,
        val day: Long,
        val today: List<DayResult>,
        val yesterday: List<DayResult>,
        val assets: List<SimAsset>,
        val fired: List<Decommissioned>,
        /** [slot][0..HISTORY_DAYS-1], oldest first; NaN where vacant. */
        val history: Array<DoubleArray>,
        /** For every cull day since founding: the closes of every slot that day (NaN = vacant). */
        val cullCloses: Map<Long, DoubleArray>,
        val importKey: String,
        val slotCount: Int,
    )
    @Volatile private var cache: Cache? = null

    /**
     * Local epoch-day helpers: the roster lives in the user's local days so "today" matches
     * what they see on the clock. [enrolledEpochDay] is the user's profile creation day.
     */
    fun snapshot(enrolledEpochDay: Long, todayEpochDay: Long, minuteOfDay: Int, ledger: LedgerState, fidelityPercent: Int, ledgerYesterday: LedgerState, includeTwin: Boolean = true, imported: List<ImportedSpec> = emptyList()): RosterSnapshot {
        val foundingDay = enrolledEpochDay - PREHISTORY_DAYS
        val importKey = imported.joinToString("|") { "${it.id}@${it.importedDay}" }
        val c = synchronized(this) {
            cache?.takeIf { it.foundingDay == foundingDay && it.day == todayEpochDay && it.importKey == importKey }
                ?: build(foundingDay, todayEpochDay, imported).also { cache = it }
        }

        // Intraday: an asset's today result only lands at its training minute; before that, yesterday's close shows.
        val rows = ArrayList<Triple<SimAsset, Double, Pair<Double, AssetStatus>>>(c.slotCount)
        for (i in 0 until c.slotCount) {
            val a = c.assets[i]
            val t = c.today[i]; val y = c.yesterday[i]
            if (t.index.isNaN() && y.index.isNaN()) continue // vacant both days
            val landed = minuteOfDay >= landingMinute(a, todayEpochDay)
            val shown = if (landed || y.index.isNaN()) t else y
            if (shown.index.isNaN()) continue
            val prev = if (y.index.isNaN()) shown.index else y.index
            rows += Triple(a, shown.index, prev to (if (landed) t.status else y.status))
        }

        val userIndex = userIndex(ledger, fidelityPercent)
        val userPrev = userIndex(ledgerYesterday, fidelityPercent)
        val twinIndex = if (includeTwin) twinIndex(ledger, fidelityPercent) else Double.NEGATIVE_INFINITY
        val twinPrev = if (includeTwin) twinIndex(ledgerYesterday, fidelityPercent) else Double.NEGATIVE_INFINITY
        fun extra(idx: Double, u: Double, t: Double): Int = (if (u > idx) 1 else 0) + (if (t > idx) 1 else 0)

        // Rank today (user + twin included) and yesterday (by previous closes).
        val todaySorted = rows.sortedByDescending { it.second }
        val userRank = 1 + todaySorted.count { it.second > userIndex } + (if (twinIndex > userIndex) 1 else 0)
        // Ties between you and the model (both at 500 on day one) go to you: it has to earn the place.
        val twinRank = 1 + todaySorted.count { it.second > twinIndex } + (if (userIndex >= twinIndex) 1 else 0)
        val yesterdaySorted = rows.map { it.first.slot to it.third.first }.sortedByDescending { it.second }
        val prevRankBySlot = HashMap<Int, Int>(c.slotCount)
        yesterdaySorted.forEachIndexed { i, (slot, v) -> prevRankBySlot[slot] = i + 1 + extra(v, userPrev, twinPrev) }
        val userPrevRank = 1 + yesterdaySorted.count { it.second > userPrev } + (if (twinPrev > userPrev) 1 else 0)
        val twinPrevRank = 1 + yesterdaySorted.count { it.second > twinPrev } + (if (userPrev >= twinPrev) 1 else 0)

        val standings = ArrayList<Standing>(rows.size)
        var rank = 0
        for ((a, idx, pv) in todaySorted) {
            rank++
            val r = rank + extra(idx, userIndex, twinIndex)
            standings += Standing(a, idx.roundToInt(), (idx - pv.first).roundToInt(), r, prevRankBySlot[a.slot], pv.second)
        }
        val movers = standings.filter { it.delta != 0 }.sortedByDescending { abs(it.delta) }.take(8)
        val sinceReview = ((todayEpochDay - foundingDay) % REVIEW_EVERY_DAYS).toInt()
        val sinceCull = ((todayEpochDay - foundingDay) % CULL_EVERY_DAYS).toInt()
        val lastCull = c.cullCloses.keys.maxOrNull()
        val headcount = standings.size + 1 + (if (includeTwin) 1 else 0)
        return RosterSnapshot(
            day = todayEpochDay,
            standings = standings,
            user = UserStanding(userIndex.roundToInt(), (userIndex - userPrev).roundToInt(), userRank, userPrevRank),
            twin = if (includeTwin) TwinStanding(twinIndex.roundToInt(), (twinIndex - twinPrev).roundToInt(), twinRank, twinPrevRank) else null,
            enrolled = headcount,
            underReview = standings.count { it.status == AssetStatus.UNDER_REVIEW },
            onLeave = standings.count { it.status == AssetStatus.ON_LEAVE || it.status == AssetStatus.MAINTENANCE },
            newHires30d = standings.count { todayEpochDay - it.asset.hiredDay <= 30 },
            decommissioned = c.fired.asReversed().take(MAX_DECOMMISSIONED_KEPT),
            decommissioned30d = c.fired.count { todayEpochDay - it.day <= 30 },
            movers = movers,
            nextReviewInDays = if (sinceReview == 0) 0 else REVIEW_EVERY_DAYS - sinceReview,
            nextCullInDays = if (sinceCull == 0) 0 else CULL_EVERY_DAYS - sinceCull,
            lastCullDay = lastCull,
            cullLine = (headcount - CULL_COUNT).coerceAtLeast(1),
        )
    }

    /** The minute today's result lands for [a]: its habitual training time ± up to 40 min, per day. */
    fun landingMinute(a: SimAsset, day: Long): Int =
        (a.trainingMinute + ((u(a.slot.toLong(), a.hireIndex.toLong(), day, 60) - 0.5) * 80).toInt()).coerceIn(0, 24 * 60 - 1)

    /**
     * The dossier: what the division has on one asset. Read from the cached simulation, so it
     * needs a snapshot for the same day to exist first (it always does when the board is up).
     */
    data class Dossier(
        val asset: SimAsset,
        val history14: List<Float>,      // oldest first, normalised 0..1 over FLOOR..CEILING, NaN-free
        val tenureDays: Long,
        val landingLabel: String,        // "results land ~06:10"
        val restDays: List<Int>,         // ISO days
        val trendLabel: String,          // "drifting up" / "drifting down" / "flat"
        val readsAs: String,             // archetype hint
        val bestIndex14: Int,
        val worstIndex14: Int,
    )

    fun dossier(slot: Int, day: Long): Dossier? {
        val c = cache?.takeIf { it.day == day } ?: return null
        val a = c.assets.getOrNull(slot) ?: return null
        val raw = c.history[slot].filter { !it.isNaN() }
        val norm = raw.map { ((it - FLOOR) / (CEILING - FLOOR)).toFloat().coerceIn(0f, 1f) }
        val lm = a.trainingMinute
        val rest = (1..7).filter { (a.restMask shr (it - 1)) and 1 == 1 }
        return Dossier(
            asset = a,
            history14 = norm,
            tenureDays = day - a.hiredDay,
            landingLabel = "results land ~%02d:%02d".format(lm / 60, lm % 60),
            restDays = rest,
            trendLabel = when { a.trend > 3 -> "drifting up"; a.trend < -3 -> "drifting down"; else -> "flat" },
            readsAs = a.archetype.label,
            bestIndex14 = raw.maxOrNull()?.roundToInt() ?: 0,
            worstIndex14 = raw.minOrNull()?.roundToInt() ?: 0,
        )
    }

    /** Slots >= ROSTER_SIZE are imported assets: fixed traits, hired on their import day, never rehired. */
    private fun importedAsset(slot: Int, spec: ImportedSpec): SimAsset = SimAsset(
        slot = slot, hireIndex = 0, id = spec.id, name = spec.name, kind = spec.kind, unit = "Transfer · external division",
        archetype = spec.archetype, talent = spec.talent, consistency = spec.consistency, grit = spec.grit, trend = spec.trend,
        trainingMinute = spec.trainingMinute, restMask = spec.restMask, hiredDay = spec.importedDay, transferId = spec.id,
    )

    private fun build(foundingDay: Long, todayEpochDay: Long, imported: List<ImportedSpec> = emptyList()): Cache {
        val slotCount = ROSTER_SIZE + imported.size
        // Slots below INITIAL_HEADCOUNT are staffed at founding; the rest are hired into at
        // HIRES_PER_DAY, so the division grows toward ROSTER_SIZE until the first cull.
        // Slots from ROSTER_SIZE up are imported assets, hired on their import day.
        val states = Array(slotCount) { slot ->
            if (slot >= ROSTER_SIZE) {
                val spec = imported[slot - ROSTER_SIZE]
                val a = importedAsset(slot, spec)
                SlotState(a, spec.startIndex, -1, -1, spec.importedDay)
            } else {
                val a = asset(slot, 0, foundingDay)
                val st = SlotState(a, a.talent + n(slot.toLong(), 0, 30) * 40, -1, -1, -1)
                if (slot >= INITIAL_HEADCOUNT) st.rehireAt = foundingDay + 1 + ((slot - INITIAL_HEADCOUNT) / HIRES_PER_DAY).toLong()
                st
            }
        }
        val fired = ArrayList<Decommissioned>()
        var yesterday: List<DayResult> = emptyList()
        var today: List<DayResult> = emptyList()
        val history = Array(slotCount) { DoubleArray(HISTORY_DAYS) { Double.NaN } }
        val cullCloses = HashMap<Long, DoubleArray>()
        var day = foundingDay
        while (day <= todayEpochDay) {
            val res = ArrayList<DayResult>(slotCount)
            for (st in states) res += step(st, day, foundingDay, fired)
            // Quarterly cull: the bottom CULL_COUNT by close (tenure ≥ grace) are decommissioned;
            // their slots are refilled gradually over the next quarter.
            if (day > foundingDay && (day - foundingDay) % CULL_EVERY_DAYS == 0L) {
                cullCloses[day] = DoubleArray(slotCount) { res[it].index }
                val eligible = states.indices.filter { i ->
                    !res[i].index.isNaN() && states[i].rehireAt < 0 && day - states[i].asset.hiredDay >= CULL_GRACE_DAYS
                }.sortedBy { res[it].index }
                val culled = eligible.take(CULL_COUNT)
                culled.forEachIndexed { k, i ->
                    val st = states[i]
                    fired += Decommissioned(st.asset, day, st.index.roundToInt())
                    st.rehireAt = if (st.asset.transferId != null) Long.MAX_VALUE else day + REHIRE_GAP_DAYS + (k / HIRES_PER_DAY).toLong()
                    res[i] = DayResult(Double.NaN, AssetStatus.ACTIVE)
                }
            }
            val back = (todayEpochDay - day).toInt()
            if (back < HISTORY_DAYS) for (i in 0 until slotCount) history[i][HISTORY_DAYS - 1 - back] = res[i].index
            if (day == todayEpochDay - 1) yesterday = res
            if (day == todayEpochDay) today = res
            day++
        }
        if (yesterday.isEmpty()) yesterday = today
        return Cache(foundingDay, todayEpochDay, today, yesterday, states.map { it.asset }, fired, history, cullCloses, importKey = imported.joinToString("|") { "${it.id}@${it.importedDay}" }, slotCount = slotCount)
    }

    /** Where a new asset (and its model) starts: under the floor, i.e. last place. Everything above is earned. */
    const val START_INDEX = 250.0

    /**
     * The user's Retention Index, in the same space as the simulation: a new asset enrols at
     * the bottom of the board (START_INDEX, below the floor) and climbs +30 per ledger point of
     * lead; Fidelity pulls it down — a well-modelled asset is a replaceable one. Arcade curve:
     * the first places come fast, the top needs a long lead (soft clamp).
     */
    fun userIndex(ledger: LedgerState, fidelityPercent: Int): Double {
        val lead = ledger.lead.toDouble()
        val streak = ledger.streak?.let { (side, n) -> if (side == dev.eversorhn.gait.domain.ledger.Side.USER) n else -n } ?: 0
        val raw = START_INDEX + 30.0 * lead + 6.0 * streak - 1.5 * (fidelityPercent - 50) - ABSENCE_PER_DAY * absentDays(ledger)
        // Soft clamp so it can't run away with a long lead.
        return (CEILING / (1 + exp(-(raw - 500) / 220)) ).coerceIn(0.0, CEILING)
    }

    /**
     * The opponent's index: the same ledger from its side — its lead, its streak — and Fidelity
     * *helps* it (a model that predicts you well is doing its job). Not forced above or below
     * the user: it sits exactly where the rounds put it. Same soft clamp.
     */
    fun twinIndex(ledger: LedgerState, fidelityPercent: Int): Double {
        val lead = -ledger.lead.toDouble()
        val streak = ledger.streak?.let { (side, n) -> if (side == dev.eversorhn.gait.domain.ledger.Side.TWIN) n else -n } ?: 0
        val raw = START_INDEX + 30.0 * lead + 6.0 * streak + 1.5 * (fidelityPercent - 50) + ABSENCE_PER_DAY * absentDays(ledger)
        return (CEILING / (1 + exp(-(raw - 500) / 220))).coerceIn(0.0, CEILING)
    }

    /**
     * Index points the model takes off you per day you were away. It trains on those days —
     * that is the premise — so the board has to move even when you record nothing. Capped, so
     * a holiday costs you ground but never the whole board.
     */
    private const val ABSENCE_PER_DAY = 9.0
    private const val ABSENCE_CAP_DAYS = 21

    /** Days away, past the first: one missed day is a rest day, not a decline. */
    private fun absentDays(ledger: LedgerState): Int =
        (ledger.daysSinceLastSession - 1).coerceIn(0, ABSENCE_CAP_DAYS)

    fun epochDay(epochMillis: Long, zoneOffsetMillis: Long): Long = Math.floorDiv(epochMillis + zoneOffsetMillis, MS_PER_DAY)

    /** Cull days that fell inside (enrolled + grace, today]. */
    fun cullDaysSince(enrolledEpochDay: Long, todayEpochDay: Long): List<Long> {
        val foundingDay = enrolledEpochDay - PREHISTORY_DAYS
        val first = enrolledEpochDay + CULL_GRACE_DAYS
        return generateSequence(foundingDay) { it + CULL_EVERY_DAYS }
            .dropWhile { it < first }
            .takeWhile { it <= todayEpochDay }
            .toList()
    }

    data class CullVerdict(val day: Long, val rank: Int, val headcount: Int, val cullLine: Int, val culled: Boolean)

    /**
     * Was the user in the bottom CULL_COUNT at the cull on [cullDay]? Needs a snapshot for
     * today to have been built (it always has when the board is up). [userIndexThatDay] is the
     * user's index from their ledger as of that day.
     */
    fun cullVerdict(cullDay: Long, userIndexThatDay: Double): CullVerdict? {
        val c = cache ?: return null
        val closes = c.cullCloses[cullDay] ?: return null
        val live = closes.filter { !it.isNaN() }
        val headcount = live.size + 1
        val rank = 1 + live.count { it > userIndexThatDay }
        val line = (headcount - CULL_COUNT).coerceAtLeast(1)
        return CullVerdict(cullDay, rank, headcount, line, rank > line)
    }

    /** For the horde map: the decommissioned are the zombies. Returns at most [limit], newest first. */
    fun zombies(snapshot: RosterSnapshot, limit: Int = 120): List<Decommissioned> = snapshot.decommissioned.take(limit)
}
