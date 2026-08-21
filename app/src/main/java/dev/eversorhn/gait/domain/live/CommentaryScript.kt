package dev.eversorhn.gait.domain.live

import kotlin.math.abs

/**
 * What the division's voice says during a session: a neutral live commentator — distances,
 * gaps, kilometre marks, who holds the round — never the persona's taunts (those stay on
 * screen). Pure: given the live numbers it returns a line or null. Cadence is owned by the
 * caller (cooldowns, caps); this only decides *what* and *whether it's worth saying*.
 * See docs/voice-design.md for the voice itself.
 */
object CommentaryScript {

    data class Input(
        val opponentName: String,
        val isHorde: Boolean,
        val km: Int,                       // completed kilometres
        val gapSeconds: Int?,              // + ahead of the model at this distance
        val gapMetres: Int?,               // the same gap expressed in metres at your pace (+ ahead)
        val separationMetres: Int?,        // horde only
        val closingPerMinute: Int?,        // horde only
        val roundToUser: Boolean?,
        val stake: Int,
        val modelConfidence: Int?,
        val projectedFinishSeconds: Int?,
        val modelFinishSeconds: Int?,
    )

    enum class Kind { KM, LEAD_CHANGE, STATUS, START }

    fun startLine(i: Input): String =
        if (i.isHorde) "Recording. The horde starts behind you. Keep it that way."
        else "Recording. ${i.opponentName} is running its forecast beside you. ${stakeLine(i.stake)}"

    fun kmLine(i: Input): String {
        val base = "Kilometre ${i.km}."
        return if (i.isHorde) {
            val sep = i.separationMetres ?: return base
            base + " " + when {
                sep < 50 -> "They are right on you — ${abs(sep)} metres."
                sep < 0 -> "The horde is past your line by ${abs(sep)} metres."
                else -> "The horde is $sep metres behind you."
            }
        } else {
            val g = i.gapSeconds ?: return base
            base + " " + when {
                abs(g) < 3 -> "Level with the model."
                g > 0 -> "${spokenSeconds(g)} ahead of ${i.opponentName}."
                else -> "${spokenSeconds(-g)} behind ${i.opponentName}."
            }
        }
    }

    fun leadChangeLine(i: Input, nowAhead: Boolean): String =
        if (i.isHorde) (if (nowAhead) "You are pulling away from the horde." else "The horde is catching up.")
        else (if (nowAhead) "You've taken the lead. ${i.opponentName} is behind you now." else "${i.opponentName} is ahead of you now.")

    /** Periodic status: the most useful single fact right now. */
    fun statusLine(i: Input): String? {
        if (i.isHorde) {
            val sep = i.separationMetres ?: return null
            val closing = i.closingPerMinute ?: 0
            return when {
                sep < 30 -> "The horde is on you. ${abs(sep)} metres."
                closing > 15 -> "The horde is catching up — $sep metres and closing at $closing a minute."
                closing < -15 -> "You're pulling away. $sep metres, and growing."
                else -> "The horde is $sep metres behind you. Holding."
            }
        }
        val m = i.gapMetres
        val g = i.gapSeconds
        return when {
            m != null && abs(m) >= 20 -> if (m > 0) "${i.opponentName} is $m metres behind you." else "${i.opponentName} is ${-m} metres ahead of you."
            g != null && abs(g) < 3 -> "Stride for stride with ${i.opponentName}."
            i.roundToUser == true && i.stake > 1 -> "The round is yours right now. ${i.stake} points riding."
            i.modelConfidence != null && i.modelConfidence < 40 -> "Model confidence is down to ${i.modelConfidence} percent."
            i.projectedFinishSeconds != null && i.modelFinishSeconds != null ->
                "Hold this and you finish in ${spokenDuration(i.projectedFinishSeconds)} against its ${spokenDuration(i.modelFinishSeconds)}."
            else -> null
        }
    }

    private fun stakeLine(stake: Int): String = when {
        stake >= 4 -> "$stake points on this one."
        stake >= 2 -> "$stake points riding."
        else -> ""
    }

    fun spokenSeconds(s: Int): String = if (s < 60) "$s seconds" else "${s / 60} minutes ${s % 60} seconds"
    fun spokenDuration(s: Int): String = if (s < 3600) "${s / 60} ${if (s / 60 == 1) "minute" else "minutes"} ${s % 60}" else "${s / 3600} hours ${(s % 3600) / 60} minutes"
}
