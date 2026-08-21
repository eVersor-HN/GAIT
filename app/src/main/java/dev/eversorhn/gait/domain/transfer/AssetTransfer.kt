package dev.eversorhn.gait.domain.transfer

import dev.eversorhn.gait.data.db.entity.SessionEntity
import dev.eversorhn.gait.domain.ledger.LedgerState
import dev.eversorhn.gait.domain.ledger.Side
import dev.eversorhn.gait.domain.roster.Archetype
import dev.eversorhn.gait.domain.roster.AssetKind
import dev.eversorhn.gait.domain.roster.RosterEngine
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A portable asset: the division's assessment of *you*, written from your real sessions and
 * ledger, in a form another GAIT can take in. On import it becomes a roster slot in the other
 * user's division — with your archetype, talent, consistency, grit, drift, training time and
 * rest days — and lives on there: climbs, gets reviewed, can be culled. Deliberately a plain,
 * readable text block (no base64): you can read what the division thinks of you before you
 * send it.
 */
data class TransferAsset(
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
    val indexAtExport: Int,
    val tenureDays: Long,
    val rounds: Int,
    val assessment: String,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val exportedEpochDay: Long,
    val origin: String,
)

object AssetTransfer {

    const val FORMAT = "GAIT-ASSET/1"

    /** The assessment itself: what the division would write in your file, from the numbers. */
    fun assess(
        sessionsNewestFirst: List<SessionEntity>,
        ledger: LedgerState,
        fidelityPercent: Int,
        displayName: String,
        kind: AssetKind,
        enrolledEpochDay: Long,
        todayEpochDay: Long,
        restDayMask: Int,
        zone: ZoneId,
    ): TransferAsset {
        val paces = sessionsNewestFirst.map { it.avgPaceSecPerKm }.filter { it > 0 }
        val mean = paces.average().takeIf { !it.isNaN() } ?: 0.0
        val cv = if (paces.size >= 3 && mean > 0) sqrt(paces.sumOf { (it - mean) * (it - mean) } / paces.size) / mean else 0.25
        val consistency = (1.0 - cv * 4.0).coerceIn(0.2, 0.95)

        // Grit: how often a lost round is followed by a won one.
        val rounds = ledger.rounds.asReversed() // oldest first
        var recoveries = 0; var losses = 0
        for (i in 0 until rounds.size - 1) if (rounds[i].winner == Side.TWIN) { losses++; if (rounds[i + 1].winner == Side.USER) recoveries++ }
        val grit = if (losses >= 2) (0.25 + 0.7 * recoveries / losses).coerceIn(0.2, 1.0) else 0.5

        // Trend: pace slope over the last 10 sessions → index points per 30 days (faster = up).
        val recent = sessionsNewestFirst.take(10).asReversed()
        val trend = if (recent.size >= 4) {
            val n = recent.size
            val xs = (0 until n).map { it.toDouble() }
            val ys = recent.map { it.avgPaceSecPerKm }
            val xm = xs.average(); val ym = ys.average()
            val slope = xs.zip(ys).sumOf { (x, y) -> (x - xm) * (y - ym) } / xs.sumOf { (it - xm) * (it - xm) }
            (-slope * 1.5).coerceIn(-12.0, 14.0) // −1 s/km per session ≈ +1.5 index/30 d
        } else 0.0

        val minutes = sessionsNewestFirst.map { Instant.ofEpochMilli(it.startTimeEpochMillis).atZone(zone).let { z -> z.hour * 60 + z.minute } }.sorted()
        val trainingMinute = if (minutes.isEmpty()) 7 * 60 else minutes[minutes.size / 2]

        // Rest days: declared ones plus weekdays never trained in the last 8 weeks (if there's enough history).
        val cutoff = todayEpochDay - 56
        val trainedDays = sessionsNewestFirst.filter { Instant.ofEpochMilli(it.startTimeEpochMillis).atZone(zone).toLocalDate().toEpochDay() >= cutoff }.map { it.dayOfWeek }.toSet()
        var restMask = restDayMask
        if (sessionsNewestFirst.size >= 8) for (d in 1..7) if (d !in trainedDays) restMask = restMask or (1 shl (d - 1))
        if (Integer.bitCount(restMask) >= 6) restMask = restDayMask // nonsense guard: not "rests every day"

        val index = RosterEngine.userIndex(ledger, fidelityPercent)
        val talent = index.coerceIn(350.0, 760.0)
        val weekender = (restMask and 0b0011111) == 0b0011111
        val archetype = when {
            weekender -> Archetype.WEEKENDER
            trend <= -5 -> Archetype.FADER
            trend >= 5 && grit >= 0.6 -> Archetype.COMEBACK
            consistency >= 0.8 -> Archetype.METRONOME
            trainingMinute < 6 * 60 + 30 -> Archetype.EARLY_BIRD
            trainingMinute >= 20 * 60 -> Archetype.NIGHT_OWL
            consistency <= 0.4 -> Archetype.SPRINTER
            grit >= 0.7 -> Archetype.GRINDER
            else -> Archetype.STEADY
        }

        val strengths = ArrayList<String>()
        val weaknesses = ArrayList<String>()
        if (consistency >= 0.7) strengths += "consistent pacing" else if (consistency <= 0.4) weaknesses += "erratic pacing"
        if (grit >= 0.65) strengths += "recovers after a loss" else if (grit <= 0.35) weaknesses += "folds after a loss"
        if (trend >= 4) strengths += "improving month on month" else if (trend <= -4) weaknesses += "fading month on month"
        ledger.streak?.let { (s, n) -> if (s == Side.USER && n >= 3) strengths += "on a $n-round streak" else if (s == Side.TWIN && n >= 3) weaknesses += "$n rounds behind its model" }
        if (fidelityPercent >= 80) weaknesses += "highly predictable (fidelity $fidelityPercent%)" else if (fidelityPercent <= 55) strengths += "hard to model (fidelity $fidelityPercent%)"
        if (strengths.isEmpty()) strengths += "no distinguishing strength on file"
        if (weaknesses.isEmpty()) weaknesses += "no flagged weakness on file"

        val tenure = todayEpochDay - enrolledEpochDay
        val assessment = buildString {
            append("Reads as ${archetype.label}. ")
            append("${ledger.roundsPlayed} rounds on the ledger, ${ledger.userPoints}–${ledger.twinPoints} against its model; ")
            append("index ${index.toInt()} after $tenure days on the floor. ")
            append(if (trend >= 3) "Drifting up. " else if (trend <= -3) "Drifting down. " else "Flat. ")
            append("Results land around %02d:%02d.".format(trainingMinute / 60, trainingMinute % 60))
        }
        val id = "TX-" + java.lang.Long.toHexString((displayName.hashCode().toLong() shl 20) xor enrolledEpochDay xor (todayEpochDay shl 8)).takeLast(8).uppercase().padStart(8, '0')
        return TransferAsset(id, displayName, kind, archetype, talent, consistency, grit, trend, trainingMinute, restMask, index.toInt(), tenure, ledger.roundsPlayed, assessment, strengths, weaknesses, todayEpochDay, "GAIT")
    }

    // ---------------------------------------------------------------- text format
    fun encode(a: TransferAsset): String = buildString {
        appendLine("=== $FORMAT ===")
        appendLine("id: ${a.id}")
        appendLine("name: ${a.name}")
        appendLine("kind: ${a.kind.name}")
        appendLine("archetype: ${a.archetype.name}")
        appendLine("talent: %.1f".format(java.util.Locale.ROOT, a.talent))
        appendLine("consistency: %.3f".format(java.util.Locale.ROOT, a.consistency))
        appendLine("grit: %.3f".format(java.util.Locale.ROOT, a.grit))
        appendLine("trend: %.2f".format(java.util.Locale.ROOT, a.trend))
        appendLine("trainingMinute: ${a.trainingMinute}")
        appendLine("restMask: ${a.restMask}")
        appendLine("index: ${a.indexAtExport}")
        appendLine("tenureDays: ${a.tenureDays}")
        appendLine("rounds: ${a.rounds}")
        appendLine("exported: ${a.exportedEpochDay}")
        appendLine("origin: ${a.origin}")
        appendLine("strengths: ${a.strengths.joinToString("; ")}")
        appendLine("weaknesses: ${a.weaknesses.joinToString("; ")}")
        appendLine("assessment: ${a.assessment.replace("\n", " ")}")
        appendLine("=== END ===")
    }

    /** Null if the text isn't a recognisable asset block. Tolerant of surrounding text and blank lines. */
    fun decode(text: String): TransferAsset? {
        if (!text.contains(FORMAT)) return null
        val body = text.substringAfter("=== $FORMAT ===").substringBefore("=== END ===")
        val kv = HashMap<String, String>()
        body.lines().forEach { line ->
            val i = line.indexOf(':')
            if (i > 0) kv[line.substring(0, i).trim()] = line.substring(i + 1).trim()
        }
        return try {
            TransferAsset(
                id = kv["id"] ?: return null,
                name = kv["name"]?.takeIf { it.isNotBlank() } ?: return null,
                kind = AssetKind.valueOf(kv["kind"] ?: "HUMAN_M"),
                archetype = Archetype.valueOf(kv["archetype"] ?: "STEADY"),
                talent = kv["talent"]!!.toDouble().coerceIn(300.0, 800.0),
                consistency = kv["consistency"]!!.toDouble().coerceIn(0.1, 1.0),
                grit = kv["grit"]!!.toDouble().coerceIn(0.1, 1.0),
                trend = kv["trend"]!!.toDouble().coerceIn(-15.0, 15.0),
                trainingMinute = kv["trainingMinute"]!!.toInt().coerceIn(0, 1439),
                restMask = kv["restMask"]!!.toInt() and 0b1111111,
                indexAtExport = kv["index"]?.toIntOrNull() ?: 500,
                tenureDays = kv["tenureDays"]?.toLongOrNull() ?: 0L,
                rounds = kv["rounds"]?.toIntOrNull() ?: 0,
                assessment = kv["assessment"] ?: "",
                strengths = kv["strengths"]?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                weaknesses = kv["weaknesses"]?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                exportedEpochDay = kv["exported"]?.toLongOrNull() ?: 0L,
                origin = kv["origin"] ?: "GAIT",
            )
        } catch (e: Exception) {
            null
        }
    }
}
