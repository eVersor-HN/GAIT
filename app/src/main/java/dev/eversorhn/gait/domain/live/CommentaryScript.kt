package dev.eversorhn.gait.domain.live

import kotlin.math.abs

/**
 * The spoken instrument readout during a session: distances, gaps, kilometre marks, what is
 * riding on the round. Nobody is talking — these are the numbers read aloud for when the phone
 * is in a pocket. Pure: given the live values it returns a line or null. Cadence (cooldowns,
 * per-session cap) is the caller's.
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

    fun startLine(i: Input): String {
        val riding = stakeLine(i.stake)
        return if (i.isHorde) {
            "Recording." + (i.separationMetres?.let { " Horde at $it metres." } ?: "") + riding
        } else {
            "Recording." + riding
        }
    }

    fun kmLine(i: Input): String {
        val base = "Kilometre ${i.km}."
        return if (i.isHorde) {
            val sep = i.separationMetres ?: return base
            base + " " + if (sep < 0) "Horde ahead by ${abs(sep)} metres." else "Horde at $sep metres."
        } else {
            val g = i.gapSeconds ?: return base
            base + " " + when {
                abs(g) < 3 -> "Level."
                g > 0 -> "${spokenSeconds(g)} up."
                else -> "${spokenSeconds(-g)} down."
            }
        }
    }

    fun leadChangeLine(i: Input, nowAhead: Boolean): String =
        if (i.isHorde) (if (nowAhead) "Separation growing." else "Separation closing.")
        else (if (nowAhead) "Lead change. You are ahead." else "Lead change. ${i.opponentName} is ahead.")

    /** Periodic status: the most useful single figure right now. */
    fun statusLine(i: Input): String? {
        if (i.isHorde) {
            val sep = i.separationMetres ?: return null
            val closing = i.closingPerMinute ?: 0
            return when {
                sep < 30 -> "Horde at ${abs(sep)} metres."
                closing > 15 -> "Horde at $sep metres, closing $closing a minute."
                closing < -15 -> "Horde at $sep metres, falling back ${-closing} a minute."
                else -> "Horde at $sep metres."
            }
        }
        val m = i.gapMetres
        val g = i.gapSeconds
        return when {
            m != null && abs(m) >= 20 -> if (m > 0) "$m metres up." else "${-m} metres down."
            g != null && abs(g) < 3 -> "Level."
            i.roundToUser == true && i.stake > 1 -> "Round to you. ${i.stake} points riding."
            i.modelConfidence != null && i.modelConfidence < 40 -> "Model confidence ${i.modelConfidence} percent."
            i.projectedFinishSeconds != null && i.modelFinishSeconds != null ->
                "Projected finish ${spokenDuration(i.projectedFinishSeconds)} against ${spokenDuration(i.modelFinishSeconds)}."
            else -> null
        }
    }

    private fun stakeLine(stake: Int): String = when {
        stake >= 2 -> " $stake points riding."
        else -> ""
    }

    fun spokenSeconds(s: Int): String = if (s < 60) "$s seconds" else "${s / 60} minutes ${s % 60} seconds"
    fun spokenDuration(s: Int): String = if (s < 3600) "${s / 60} ${if (s / 60 == 1) "minute" else "minutes"} ${s % 60}" else "${s / 3600} hours ${(s % 3600) / 60} minutes"
}
