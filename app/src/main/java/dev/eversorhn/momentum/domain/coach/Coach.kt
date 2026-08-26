package dev.eversorhn.momentum.domain.coach

import dev.eversorhn.momentum.data.db.entity.SessionEntity
import dev.eversorhn.momentum.domain.ledger.LedgerState
import dev.eversorhn.momentum.domain.ledger.Side
import dev.eversorhn.momentum.domain.roster.RosterEngine
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

/**
 * The read-out the user actually asks for: *what do I do next?* Every item is derived from
 * their own rows — a target number, a reason, and the consequence of hitting it. No advice
 * that isn't backed by a figure in the data.
 */
object Coach {

    /** One instruction. [target] is the number to act on; [why] is the evidence. */
    data class Item(val order: Int, val title: String, val target: String, val why: String, val urgent: Boolean = false)

    fun advise(
        sessionsNewestFirst: List<SessionEntity>,
        ledger: LedgerState,
        isHorde: Boolean,
        opponentName: String,
        fidelityPercent: Int,
        forecastPaceSecPerKm: Double?,
        forecastDistanceMeters: Double?,
        userRank: Int?,
        cullLine: Int?,
        nextCullInDays: Int?,
        trialDeadlineDays: Int?,
        stakePoints: Int,
        formatPace: (Double) -> String,
        nowEpochMillis: Long,
        todayIso: Int,
    ): List<Item> {
        val out = ArrayList<Item>()
        val them = if (isHorde) "the horde" else opponentName

        // 1. Today's number — the single thing to beat.
        if (forecastPaceSecPerKm != null) {
            val margin = forecastPaceSecPerKm - 3
            out += Item(
                order = 1,
                title = if (isHorde) "Hold this or better" else "Beat this to take the round",
                target = formatPace(margin),
                why = if (isHorde) "They move at your forecast pace ${formatPace(forecastPaceSecPerKm)}. Anything slower and the gap closes."
                else "${opponentName}'s forecast is ${formatPace(forecastPaceSecPerKm)}${if (stakePoints > 1) " · $stakePoints points riding" else ""}.",
                urgent = stakePoints > 1,
            )
            if (forecastDistanceMeters != null && forecastDistanceMeters > 0) {
                out += Item(
                    order = 2,
                    title = "Distance to make it count",
                    target = "%.1f km".format(forecastDistanceMeters / 1000.0),
                    why = "Shorter and the pace is easy to fake; the model forecasts this far.",
                )
            }
        } else {
            out += Item(1, "Log a session", "any distance", "There's no forecast until $them has something to model.")
        }

        // 2. Trial deadline.
        if (trialDeadlineDays != null) {
            val best = sessionsNewestFirst.filter { it.distanceMeters >= 1000 }.minByOrNull { it.avgPaceSecPerKm }
            out += Item(
                order = 0,
                title = if (trialDeadlineDays == 0) "Trial closes today" else "Trial closes in $trialDeadlineDays d",
                target = best?.let { formatPace(it.avgPaceSecPerKm - 1) } ?: "1 km duel",
                why = "Beat your own best over ≥ 1 km to reset $them. Uncontested, it is ratified a generation.",
                urgent = true,
            )
        }

        // 3. Board: how many rounds to clear the cull line / climb.
        if (!isHorde && userRank != null && cullLine != null) {
            if (userRank > cullLine) {
                val places = userRank - cullLine
                out += Item(
                    order = 3,
                    title = "Climb above the cull line",
                    target = "${ceil(places / 30.0).toInt()} rounds",
                    why = "You're $places places under it${nextCullInDays?.let { ", $it d to the cull" } ?: ""}. Each round won is worth about 30 index points.",
                    urgent = (nextCullInDays ?: 99) <= 14,
                )
            } else if (userRank > 15) {
                out += Item(
                    order = 6,
                    title = "Onto the board",
                    target = "${ceil((userRank - 15) / 60.0).toInt().coerceAtLeast(1)} strong weeks",
                    why = "Top 15 is ${userRank - 15} places up. Streaks move the index faster than single wins.",
                )
            }
        }

        // 4. Where the rounds are being lost.
        ledger.opponentStrongestWeekday()?.let { (dow, score) ->
            val name = DayOfWeek.of(dow).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            out += Item(
                order = 4,
                title = "Fix your ${name}s",
                target = "${score.second}–${score.first} down",
                why = "That's where $them takes most of its points. Same effort, different day, is a free swing.",
                urgent = dow == todayIso,
            )
        }

        // 5. Predictability — the thing that decides Fidelity.
        if (fidelityPercent >= 78) {
            val routes = sessionsNewestFirst.count { it.route != null }
            out += Item(
                order = 5,
                title = "Break the pattern",
                target = "new route or negative split",
                why = "$them reads you at $fidelityPercent%${if (routes > 0) " and has $routes of your routes on file" else ""}. Predictability is what promotes it, not your pace.",
            )
        }

        // 6. Consistency — the quiet one.
        val cons = sessionsNewestFirst.mapNotNull { it.consistency }.take(5)
        if (cons.size >= 2) {
            val avg = cons.average()
            if (avg < 0.85) out += Item(
                order = 7,
                title = "Even out the kilometres",
                target = "${((avg + 0.06) * 100).toInt()}% steadiness",
                why = "Your last splits vary more than the model expects. Steady rides win rounds you don't have the legs for.",
            )
        }

        // 7. Absence.
        val last = sessionsNewestFirst.firstOrNull()?.startTimeEpochMillis
        val days = last?.let { ((nowEpochMillis - it) / 86_400_000L).toInt() } ?: 99
        if (days >= 3) out += Item(
            order = 0,
            title = "Get back on the file",
            target = "one session",
            why = "$days days quiet. Absence is the only thing that moves the index without you.",
            urgent = true,
        )

        // 8. Streak awareness (positive reinforcement, still a number).
        ledger.streak?.let { (side, n) ->
            if (side == Side.USER && n >= 2) out += Item(
                order = 8,
                title = "Keep the streak",
                target = "${n + 1} in a row",
                why = "Three earns a division commendation; the index rewards runs, not single days.",
            )
        }

        return out.sortedBy { it.order }
    }
}
