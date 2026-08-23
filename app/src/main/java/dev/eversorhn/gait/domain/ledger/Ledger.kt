package dev.eversorhn.gait.domain.ledger

import dev.eversorhn.gait.data.db.entity.SessionEntity

/** Who took a round. */
enum class Side { USER, TWIN }

/** One scored round: a session that had a forecast and wasn't on a rest day. */
data class Round(
    val sessionId: Long,
    val epochMillis: Long,
    val dayOfWeek: Int,
    val winner: Side,
    val stake: Int,
    /** forecast − actual in sec/km; positive = user beat the forecast by that much. */
    val marginSecPerKm: Double,
    val isDuel: Boolean,
)

/**
 * The running score between you and the opponent — the one number that says how far apart
 * you are. Derived entirely from stored sessions, so it can never disagree with history and
 * survives a generation handoff (the new generation inherits the ledger; that's the point).
 */
data class LedgerState(
    val userPoints: Int,
    val twinPoints: Int,
    /** Newest first. */
    val rounds: List<Round>,
) {
    val roundsPlayed: Int get() = rounds.size
    val lead: Int get() = userPoints - twinPoints
    val leader: Side? get() = when {
        lead > 0 -> Side.USER
        lead < 0 -> Side.TWIN
        else -> null
    }

    /** Fraction of all points held by the user, 0.5 when nothing has been scored. For the tug-of-war bar. */
    val userShare: Float get() {
        val total = userPoints + twinPoints
        return if (total == 0) 0.5f else userPoints.toFloat() / total
    }

    /** Current streak: who holds it and for how many rounds. Null before the first round. */
    val streak: Pair<Side, Int>? get() {
        val first = rounds.firstOrNull() ?: return null
        val n = rounds.takeWhile { it.winner == first.winner }.size
        return first.winner to n
    }

    /** Last [n] rounds, oldest first — W/L "form" dots. */
    fun form(n: Int = 5): List<Side> = rounds.take(n).asReversed().map { it.winner }

    /** Rounds won per ISO weekday (1..7) by each side — "Markus K. owns your Mondays". */
    fun byWeekday(): Map<Int, Pair<Int, Int>> =
        rounds.groupBy { it.dayOfWeek }.mapValues { (_, rs) ->
            rs.count { it.winner == Side.USER } to rs.count { it.winner == Side.TWIN }
        }

    /** The weekday the opponent dominates most clearly (≥ 2 rounds, opponent ahead), or null. */
    fun opponentStrongestWeekday(): Pair<Int, Pair<Int, Int>>? =
        byWeekday().entries
            .filter { (_, v) -> v.first + v.second >= 2 && v.second > v.first }
            .maxByOrNull { (_, v) -> v.second - v.first }
            ?.let { it.key to it.value }

    /** The weekday the user dominates most clearly (≥ 2 rounds, user ahead), or null. */
    fun userStrongestWeekday(): Pair<Int, Pair<Int, Int>>? =
        byWeekday().entries
            .filter { (_, v) -> v.first + v.second >= 2 && v.first > v.second }
            .maxByOrNull { (_, v) -> v.first - v.second }
            ?.let { it.key to it.value }
}

object Ledger {

    /** A duel is worth this many points to whoever takes it. */
    const val DUEL_STAKE = 3

    /** New route beyond this → the model didn't see it coming. */
    const val NOVELTY_WIN = 0.4
    /** Steadier than the model's expectation by at least this. */
    const val CONSISTENCY_MARGIN = 0.02

    /**
     * Pace activities: a round goes to the user only if they beat the forecast pace (a tie is a
     * correct prediction). Motor-assisted activities (e-scooter, e-bike — docs/activities-and-
     * dimensions.md): pace says nothing, so the round is judged on what the model can't script —
     * a genuinely new route, or a steadier ride than it expected. Needs a prior steadiness
     * expectation or a route to compare; otherwise the session is baseline, not a round.
     */
    fun winnerOf(session: SessionEntity): Side? {
        if (session.isRestDay) return null
        val paceBased = dev.eversorhn.gait.domain.activity.Activities.byKey(session.activityType).paceMeaningful
        if (paceBased) {
            val forecast = session.forecastPaceSecPerKm ?: return null
            return if (session.avgPaceSecPerKm < forecast) Side.USER else Side.TWIN
        }
        val novelty = session.routeNovelty
        val cons = session.consistency
        val expected = session.forecastConsistency
        if (novelty == null && (cons == null || expected == null)) return null
        val newRoute = novelty != null && novelty >= NOVELTY_WIN
        val steadier = cons != null && expected != null && cons >= expected + CONSISTENCY_MARGIN
        return if (newRoute || steadier) Side.USER else Side.TWIN
    }

    fun from(sessionsNewestFirst: List<SessionEntity>): LedgerState {
        val rounds = sessionsNewestFirst.mapNotNull { s ->
            val winner = winnerOf(s) ?: return@mapNotNull null
            Round(
                sessionId = s.id,
                epochMillis = s.startTimeEpochMillis,
                dayOfWeek = s.dayOfWeek,
                winner = winner,
                stake = s.stake.coerceAtLeast(1),
                marginSecPerKm = s.forecastPaceSecPerKm!! - s.avgPaceSecPerKm,
                isDuel = s.isDuel,
            )
        }
        return LedgerState(
            userPoints = rounds.filter { it.winner == Side.USER }.sumOf { it.stake },
            twinPoints = rounds.filter { it.winner == Side.TWIN }.sumOf { it.stake },
            rounds = rounds,
        )
    }

    /** The ledger strip's one-liner. Numbers only — nobody is talking. */
    fun standingLabel(ledger: LedgerState, opponentName: String, isHorde: Boolean): String {
        val them = if (isHorde) "the horde" else opponentName
        val streak = ledger.streak?.let { (side, n) -> if (n >= 2) " · streak $n ${if (side == Side.USER) "you" else them}" else "" } ?: ""
        return when (ledger.leader) {
            Side.USER -> "You lead by ${ledger.lead}$streak"
            Side.TWIN -> "$them leads by ${-ledger.lead}$streak"
            null -> if (ledger.roundsPlayed == 0) "No rounds yet" else "Level$streak"
        }
    }

    /** Who took the round and what it moved — the Debrief header. */
    fun rulingLabel(userWon: Boolean, stake: Int, opponentName: String, isHorde: Boolean): String =
        (if (userWon) "Round to you · +$stake" else "Round to ${if (isHorde) "the horde" else opponentName} · +$stake") +
            if (stake == 1) " pt" else " pts"
}
