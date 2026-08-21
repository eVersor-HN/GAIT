package dev.eversorhn.gait.domain.directive

import dev.eversorhn.gait.domain.ledger.LedgerState
import dev.eversorhn.gait.domain.ledger.Side

/**
 * The company's voice. The Twin wants you to lose; the *division* simply requires you to
 * outperform it — memos, not taunts. One memo per Forecast, picked from the ledger and
 * Fidelity so it always reads the current standing back at you. Cyberpunk-corpo framing from
 * docs/concept.md: you're an asset under review, the model is your proposed replacement.
 */
object Directive {

    data class Memo(val ref: String, val body: String)

    fun forForecast(
        ledger: LedgerState,
        fidelityPercent: Int,
        trialThresholdPercent: Int,
        trialEligible: Boolean,
        opponentName: String,
        isHorde: Boolean,
        generation: Int,
    ): Memo {
        val unit = if (isHorde) "containment" else "model"
        val ref = "APD/${generation}-${ledger.roundsPlayed.toString().padStart(3, '0')}"
        val lead = ledger.lead
        val body = when {
            trialEligible ->
                "Substitution review open. $unit fidelity ${fidelityPercent}% exceeds the ${trialThresholdPercent}% retention ceiling. " +
                    "Asset may contest via a single Trial. Absent a win, primary-asset status lapses at the next review."
            ledger.roundsPlayed == 0 ->
                "Asset enrolled at the bottom of the board — everyone does. The $unit is training on the asset's first sessions; " +
                    "every round won from here is a place earned. New hires are protected from the quarterly cull for 60 days."
            lead <= -4 ->
                "Asset trails its $unit by ${-lead} points over ${ledger.roundsPlayed} rounds. Continued underperformance is " +
                    "being logged toward substitution. Division recommends immediate correction."
            lead < 0 ->
                "Asset trails its $unit by ${-lead}. The division does not fund assets that lose to their own forecast. " +
                    "Close the gap."
            lead == 0 ->
                "Asset and $unit at parity after ${ledger.roundsPlayed} rounds. Parity is not retention. Decide it."
            lead < 4 ->
                "Asset leads its $unit by $lead. Noted, not secured. Retraining budget for ${if (isHorde) "the horde" else opponentName} approved."
            else ->
                "Asset leads its $unit by $lead. Division flags the $unit for accelerated retraining; expect sharper forecasts. " +
                    "Complacency is a measurable variable."
        }
        return Memo(ref, body)
    }

    /** One-line ruling for a Debrief header: who the round went to and what it cost. */
    fun ruling(userWon: Boolean, stake: Int, opponentName: String, isHorde: Boolean): String =
        (if (userWon) "Round to asset · +$stake" else "Round to ${if (isHorde) "the horde" else opponentName} · +$stake") + if (stake == 1) " pt" else " pts"

    /** The ledger strip's one-liner. */
    fun standing(ledger: LedgerState, opponentName: String, isHorde: Boolean): String {
        val them = if (isHorde) "horde" else opponentName
        val streak = ledger.streak?.let { (side, n) -> if (n >= 2) " · streak ${n} ${if (side == Side.USER) "you" else them}" else "" } ?: ""
        return when (ledger.leader) {
            Side.USER -> "You lead by ${ledger.lead}$streak"
            Side.TWIN -> "$them leads by ${-ledger.lead}$streak"
            null -> if (ledger.roundsPlayed == 0) "No rounds yet" else "Level$streak"
        }
    }
}
