package dev.eversorhn.gait.domain.demo

import dev.eversorhn.gait.data.db.entity.MessageKind
import dev.eversorhn.gait.data.db.entity.SessionEntity
import dev.eversorhn.gait.data.db.entity.SessionSource
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.composure.ComposureState
import dev.eversorhn.gait.domain.route.RouteMetrics
import java.time.Instant
import java.time.ZoneId
import kotlin.math.sin
import kotlin.random.Random

/**
 * Six weeks of plausible history in one tap, so the app can be seen "lived in": ~26 sessions
 * (mostly GPS, some manual, one Health import), forecasts from session 2 on, wins and losses
 * with a streak and a recovery, staked and called rounds, two rest-day sessions, three route
 * variants (so novelty/steadiness exist), one won duel (generation 2), and the inbox filled
 * with stakes, a call, idle jabs, a gap ping and two commendations. Deterministic (fixed seed).
 * Adds on top of whatever exists; intended for a fresh profile.
 */
object DemoSeeder {

    suspend fun seed(repository: GaitRepository) {
        val rng = Random(42)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val day = 86_400_000L

        // Three route shapes around a base point: the usual loop, a variant, a one-off elsewhere.
        fun route(kind: Int, jitter: Double): String {
            val baseLat = 48.2082 + kind * 0.02
            val baseLon = 16.3738 + kind * 0.015
            val pts = (0 until 60).map { i ->
                RouteMetrics.Point(
                    baseLat + i * 0.00025 + sin(i / 7.0) * 0.0008 + jitter,
                    baseLon + sin(i / 9.0) * 0.0012 + jitter / 2,
                )
            }
            return RouteMetrics.encode(pts)
        }

        data class Spec(val daysAgo: Int, val km: Double, val paceSec: Int, val forecast: Int?, val stake: Int,
                        val line: String?, val mood: String?, val rest: Boolean = false, val src: String = SessionSource.GPS,
                        val routeKind: Int? = 0, val duelWon: Boolean? = null, val climb: Double? = null, val cons: Double? = null)

        val specs = listOf(
            Spec(42, 5.0, 352, null, 1, null, null),
            Spec(40, 5.2, 348, 352, 1, "Noted. Still watching.", "WATCHFUL"),
            Spec(38, 4.8, 355, 349, 1, "About what I expected. We'll see.", "WATCHFUL"),
            Spec(36, 5.5, 340, 351, 2, "...Okay. That was fast. I don't have anything for that.", "COWED"),
            Spec(34, 5.0, 358, 344, 2, "There it is. Every time things get hard, you fold.", "PREDATORY"),
            Spec(32, 5.1, 361, 347, 1, "I was starting to think you'd changed. Good to know you haven't.", "PREDATORY"),
            Spec(30, 5.3, 344, 352, 4, "Fine. You win this one.", "COWED", cons = 0.91),
            Spec(28, 6.0, 338, 349, 2, "...I'll need a minute.", "COWED", routeKind = 1, cons = 0.88),
            Spec(26, 5.0, 342, 345, 2, "Nothing to say yet. Don't mistake that for approval.", "WATCHFUL"),
            Spec(24, 5.2, 350, 343, 1, null, null, rest = true),
            Spec(22, 5.4, 336, 344, 2, "Okay. That one hurt.", "COWED", climb = 84.0, cons = 0.93),
            Spec(21, 4.6, 347, 340, 1, "Back within the band. Predictable.", "WATCHFUL", src = SessionSource.HEALTH),
            Spec(19, 5.0, 334, 341, 3, "Stop doing that.", "COWED", routeKind = 2, cons = 0.9),
            Spec(17, 5.8, 331, 339, 3, "I've adjusted. You'll feel it tomorrow.", "WATCHFUL", climb = 120.0),
            Spec(15, 5.0, 352, 336, 2, "Three days of this and you're already negotiating with yourself.", "PREDATORY"),
            Spec(14, 5.1, 349, 338, 1, "At least you're consistent about something.", "PREDATORY", src = SessionSource.MANUAL),
            Spec(12, 5.6, 329, 340, 4, "…What did you do differently. Tell me.", "COWED", cons = 0.94),
            Spec(11, 5.0, 327, 336, 3, null, null, duelWon = true),
            Spec(9, 5.2, 335, 401, 1, "Generation 2 is watching now. Do it again.", "WATCHFUL"),
            Spec(8, 5.0, 342, 371, 1, "Recalibrating. Enjoy the quiet.", "WATCHFUL"),
            Spec(7, 5.4, 331, 356, 2, "Twice is a pattern. I model patterns.", "WATCHFUL", climb = 62.0, cons = 0.9),
            Spec(5, 5.0, 345, 342, 2, "There's the fade. Right on schedule.", "PREDATORY"),
            Spec(4, 4.9, 339, 341, 1, null, null, rest = true),
            Spec(3, 5.5, 330, 340, 2, "You're sharper on Thursdays. Noted, and priced in.", "COWED", routeKind = 1, cons = 0.92),
            Spec(2, 5.0, 328, 337, 3, "Fine. FINE.", "COWED", cons = 0.95),
            Spec(1, 5.2, 333, 334, 2, "One second. That's all the room you left me. I only need one.", "WATCHFUL"),
        )

        for (s in specs) {
            val start = now - s.daysAgo * day - rng.nextLong(0, 4 * 3_600_000L)
            val durationSec = (s.paceSec * s.km).toInt()
            repository.logSession(
                SessionEntity(
                    activityType = repository.activeActivityType,
                    startTimeEpochMillis = start,
                    dayOfWeek = Instant.ofEpochMilli(start).atZone(zone).dayOfWeek.value,
                    durationSeconds = durationSec,
                    distanceMeters = s.km * 1000.0,
                    avgPaceSecPerKm = s.paceSec.toDouble(),
                    forecastPaceSecPerKm = s.forecast?.toDouble(),
                    forecastFinishSeconds = s.forecast?.let { (it * s.km).toInt() },
                    isRestDay = s.rest,
                    dataSource = s.src,
                    twinLine = s.line,
                    composureState = s.mood,
                    isDuel = s.duelWon != null,
                    duelWon = s.duelWon,
                    stake = if (s.duelWon != null) 3 else s.stake,
                    route = s.routeKind?.let { route(it, rng.nextDouble(0.0, 0.0004)) },
                    elevationGainMeters = s.climb,
                    consistency = s.cons,
                    routeNovelty = when (s.routeKind) { 2 -> 0.87; 1 -> 0.44; else -> 0.08 },
                    forecastConsistency = s.cons?.let { (it - 0.03).coerceAtLeast(0.5) },
                )
            )
        }

        // Inbox: what the opponent and the division said along the way.
        val profile = repository.getTwinProfile()
        val name = profile?.twinName ?: "The model"
        repository.recordMessage(MessageKind.STAKE, "You won't beat 5:40/km today. I'd put money on it — so I'm putting 2 points on it.", ComposureState.WATCHFUL.name, now - 30 * day)
        repository.recordMessage(MessageKind.CALL, "Doubled. Remember you did that to yourself.", ComposureState.PREDATORY.name, now - 30 * day + 3_600_000)
        repository.recordMessage(MessageKind.IDLE, "Thinking about you. Not in a good way.", null, now - 20 * day)
        repository.recordMessage(MessageKind.GAP, "Three days. Three days and you're already negotiating with yourself.", ComposureState.PREDATORY.name, now - 15 * day)
        repository.recordMessage(MessageKind.COMMENDATION, "APD-C3 · Commendation: three consecutive rounds clear of $name. The division notes sustained outperformance, not a single good day.", null, now - 12 * day)
        repository.recordMessage(MessageKind.STAKE, "Confidence sufficient. 3 points staked: actual pace will not beat 5:36/km.", ComposureState.WATCHFUL.name, now - 3 * day)
        repository.recordMessage(MessageKind.COMMENDATION, "APD-LVL · Commendation: ledger recovered from behind to level or better. The division weights recoveries above leads.", null, now - 2 * day)
        repository.recordMessage(MessageKind.IDLE, "Still here. Are you?", null, now - 1 * day)

        // Profile: generation 2 (the won duel), fidelity mid-high, a couple of vacation days used.
        profile?.let {
            repository.updateTwinProfile(
                it.copy(
                    generation = 2,
                    fidelity = 0.72f,
                    vacationDaysUsedThisYear = 4,
                    vacationYear = Instant.ofEpochMilli(now).atZone(zone).year,
                )
            )
        }
    }
}
