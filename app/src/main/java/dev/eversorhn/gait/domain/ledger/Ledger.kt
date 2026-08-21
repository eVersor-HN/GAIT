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

    /** A round goes to the user only if they actually beat the forecast pace; a tie is a correct prediction. */
    fun winnerOf(session: SessionEntity): Side? {
        val forecast = session.forecastPaceSecPerKm ?: return null
        if (session.isRestDay) return null
        return if (session.avgPaceSecPerKm < forecast) Side.USER else Side.TWIN
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
}
