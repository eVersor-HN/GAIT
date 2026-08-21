package dev.eversorhn.gait.domain.directive

import dev.eversorhn.gait.domain.ledger.LedgerState
import dev.eversorhn.gait.domain.ledger.Side

/**
 * The division's version of a like: a short, formal note when the asset did something the
 * numbers back — not praise for showing up. Deterministic from the ledger so the same event
 * never produces two notes; the finalizer checks [afterRound] once per scored round.
 */
object Commendation {

    data class Note(val code: String, val body: String)

    /** Called after a scored round with the ledger before and after it. At most one note. */
    fun afterRound(before: LedgerState, after: LedgerState, marginSecPerKm: Double, opponentName: String, isHorde: Boolean): Note? {
        val them = if (isHorde) "the horde" else opponentName
        val streak = after.streak
        if (streak?.first == Side.USER) {
            when (streak.second) {
                3 -> return Note("APD-C3", "Commendation: three consecutive rounds clear of $them. The division notes sustained outperformance, not a single good day.")
                5 -> return Note("APD-C5", "Commendation: five consecutive rounds. The model has been wrong about you for a week and a half. Recorded.")
                10 -> return Note("APD-C10", "Commendation: ten consecutive rounds. Retention no longer in question this quarter. Retraining of $them escalated.")
            }
        }
        if (marginSecPerKm >= 30.0 && after.rounds.firstOrNull()?.winner == Side.USER) {
            return Note("APD-M30", "Commendation: forecast beaten by ${(marginSecPerKm / 60).toInt()}:${"%02d".format((marginSecPerKm % 60).toInt())}/km. That is outside $them's error band. The division likes outside.")
        }
        if (before.lead < 0 && after.lead >= 0 && after.roundsPlayed >= 4) {
            return Note("APD-LVL", "Commendation: ledger recovered from behind to level or better. The division weights recoveries above leads.")
        }
        return null
    }

    /** The board side: called when the user climbs by a lot in a day. */
    fun forClimb(placesGained: Int, newRank: Int): Note? = when {
        placesGained >= 100 -> Note("APD-R100", "Commendation: $placesGained places gained on the board since yesterday's close. Now #$newRank. Noted at division level.")
        placesGained >= 40 -> Note("APD-R40", "Commendation: $placesGained places gained on the board in a day. Now #$newRank. Keep the slope.")
        else -> null
    }
}
