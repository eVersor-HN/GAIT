package dev.eversorhn.gait.domain.intel

import dev.eversorhn.gait.data.db.entity.SessionEntity
import dev.eversorhn.gait.domain.ledger.LedgerState
import dev.eversorhn.gait.domain.ledger.Side
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * The opponent's dossier on you: one data-grounded observation for the Forecast screen,
 * chosen by how pointed it is. Never invented — every line cites a number from the session
 * log, which is exactly what makes it land ("it *knows*"). Voice-neutral on purpose: this is
 * the file, not the Twin talking; the Twin's own lines come from its persona.
 */
object Intel {

    data class Observation(val line: String, val tag: String)

    fun observe(
        sessionsNewestFirst: List<SessionEntity>,
        ledger: LedgerState,
        nowEpochMillis: Long,
        todayDayOfWeek: Int,
        opponentName: String,
        formatPace: (Double) -> String,
    ): Observation? {
        if (sessionsNewestFirst.isEmpty()) return null
        val candidates = ArrayList<Pair<Int, Observation>>() // priority, obs

        // 1. Absence. The sharpest one.
        val daysSince = ((nowEpochMillis - sessionsNewestFirst.first().startTimeEpochMillis) / 86_400_000.0).toInt()
        if (daysSince >= 3) {
            candidates += 90 to Observation(
                "$daysSince days since your last session. $opponentName counted every one.",
                "absence",
            )
        }

        // 2. A streak against you.
        ledger.streak?.let { (side, n) ->
            if (side == Side.TWIN && n >= 2) {
                candidates += 80 to Observation(
                    "$n rounds in a row to $opponentName. The forecast has been right every time.",
                    "streak",
                )
            }
            if (side == Side.USER && n >= 3) {
                candidates += 40 to Observation(
                    "$n rounds in a row to you. $opponentName is recalibrating.",
                    "streak",
                )
            }
        }

        // 3. Weekday ownership — "your Mondays".
        ledger.opponentStrongestWeekday()?.let { (dow, score) ->
            if (dow == todayDayOfWeek) {
                candidates += 85 to Observation(
                    "${dayName(dow)}s: ${score.second}–${score.first} to $opponentName. It's ${dayName(dow)}.",
                    "weekday",
                )
            } else {
                candidates += 50 to Observation(
                    "$opponentName owns your ${dayName(dow)}s, ${score.second}–${score.first}.",
                    "weekday",
                )
            }
        }

        // 4. Slow-day pattern from paces (needs ≥ 3 sessions on that weekday and ≥ 6 overall).
        if (sessionsNewestFirst.size >= 6) {
            val overall = sessionsNewestFirst.map { it.avgPaceSecPerKm }.average()
            val today = sessionsNewestFirst.filter { it.dayOfWeek == todayDayOfWeek }
            if (today.size >= 3) {
                val avg = today.map { it.avgPaceSecPerKm }.average()
                if (avg - overall >= 8.0) {
                    candidates += 70 to Observation(
                        "${dayName(todayDayOfWeek)}s are your slow days: ${formatPace(avg)} on average, ${formatPace(overall)} otherwise.",
                        "pattern",
                    )
                } else if (overall - avg >= 8.0) {
                    candidates += 45 to Observation(
                        "${dayName(todayDayOfWeek)}s are your fast days: ${formatPace(avg)} against ${formatPace(overall)} otherwise. Expected.",
                        "pattern",
                    )
                }
            }
        }

        // 5. Personal best and how long ago it was.
        sessionsNewestFirst.filter { it.distanceMeters >= 1000.0 }.minByOrNull { it.avgPaceSecPerKm }?.let { best ->
            val ageDays = ((nowEpochMillis - best.startTimeEpochMillis) / 86_400_000.0).toInt()
            if (ageDays >= 14 && sessionsNewestFirst.size >= 4) {
                candidates += 60 to Observation(
                    "Your best is ${formatPace(best.avgPaceSecPerKm)}, $ageDays days ago. Nothing since has come close.",
                    "best",
                )
            } else if (sessionsNewestFirst.size >= 2) {
                candidates += 20 to Observation(
                    "Best on file: ${formatPace(best.avgPaceSecPerKm)}. That's the bar $opponentName models against.",
                    "best",
                )
            }
        }

        // 6. Fallback: sample size.
        candidates += 10 to Observation(
            "${sessionsNewestFirst.size} sessions on file. Every one of them made the model sharper.",
            "file",
        )

        return candidates.maxByOrNull { it.first }?.second
    }

    private fun dayName(iso: Int): String =
        DayOfWeek.of(iso).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
}
