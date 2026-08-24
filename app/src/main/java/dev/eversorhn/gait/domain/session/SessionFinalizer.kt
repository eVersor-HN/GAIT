package dev.eversorhn.gait.domain.session

import android.content.Context
import dev.eversorhn.gait.data.db.entity.OpponentType
import dev.eversorhn.gait.data.db.entity.SessionEntity
import dev.eversorhn.gait.data.db.entity.SessionSource
import dev.eversorhn.gait.data.db.entity.isHorde
import dev.eversorhn.gait.data.repository.ACTIVITY_RUNNING
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.composure.ComposureEngine
import dev.eversorhn.gait.domain.composure.ComposureState
import dev.eversorhn.gait.domain.fidelity.FidelityReplay
import dev.eversorhn.gait.domain.forecast.ForecastEngine
import dev.eversorhn.gait.domain.ledger.Ledger
import dev.eversorhn.gait.domain.ledger.LedgerState
import dev.eversorhn.gait.domain.ledger.Side
import dev.eversorhn.gait.domain.wager.WagerPolicy
import dev.eversorhn.gait.domain.restdays.RestDayPolicy
import dev.eversorhn.gait.domain.trial.DecommissionTrial
import dev.eversorhn.gait.notification.TwinNotifier
import dev.eversorhn.gait.ui.forecast.formatDistanceKm
import dev.eversorhn.gait.ui.forecast.formatDuration
import dev.eversorhn.gait.ui.forecast.formatPace
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** Outcome of a session run as a Decommission Trial (Twin) / Outrun Trial (Horde). */
data class DuelOutcome(
    val verdict: DecommissionTrial.Verdict,
    val targetPaceLabel: String,
    /** Set on a win: the generation/wave that just spun up. */
    val newGeneration: Int?,
    /** Set on a win: the opponent's handoff line (Phase 05), quoting the user's own data. */
)

data class DebriefResult(
    val hadForecast: Boolean,
    val forecastPaceLabel: String,
    val actualPaceLabel: String,
    val composureState: ComposureState,
    val newFidelityPercent: Int,
    val dataSource: String,
    val opponentType: String = OpponentType.TWIN,
    /** "Fidelity" for a Twin, "Proximity" for a Horde. */
    val metricLabel: String = "Fidelity",
    /**
     * Set when the session landed on a declared rest day or during vacation: it was still
     * recorded as real training, but Fidelity was frozen and Composure didn't react.
     */
    val restNote: String? = null,
    // --- v0.5.0: everything the Debrief screen in the concept demo shows ---
    val opponentName: String = "",
    val previousFidelityPercent: Int = newFidelityPercent,
    /** Running Fidelity replayed over the whole history incl. this session, oldest first. */
    val fidelityHistory: List<Float> = emptyList(),
    val forecastDistanceLabel: String = "—",
    val actualDistanceLabel: String = "—",
    val forecastFinishLabel: String = "—",
    val actualFinishLabel: String = "—",
    /** Actual pace faster than forecast (lower sec/km). Null without a forecast. */
    val beatForecast: Boolean? = null,
    val generation: Int = 1,
    val generationLabel: String = "Generation",
    val trialThresholdPercent: Int = (DecommissionTrial.THRESHOLD * 100).toInt(),
    val duel: DuelOutcome? = null,
    // --- v0.6.0: the round on the ledger ---
    /** Who took this round; null when it wasn't scored (no forecast / rest day). */
    val roundWinner: Side? = null,
    /** Points this round moved. */
    val stake: Int = 1,
    /** True when the opponent had staked on today's forecast; [stakeCalled] when the user doubled it. */
    val stakeWasOpen: Boolean = false,
    val stakeCalled: Boolean = false,
    val ledger: LedgerState = LedgerState(0, 0, emptyList()),
    val ledgerBefore: LedgerState = LedgerState(0, 0, emptyList()),
    /** "Pace" or "Speed", per the active activity. */
    val paceWord: String = "Pace",
    /** Signed distance from the forecast — the number the session closes on. Null without one. */
    val marginLabel: String? = null,
    /** What it cost, when a monitor was connected. */
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    /** Motor-assisted activities: the round was judged on novelty/steadiness, not on pace. */
    val scoredOnDimensions: Boolean = false,
    val routeNoveltyPercent: Int? = null,
    val consistencyPercent: Int? = null,
    val forecastConsistencyPercent: Int? = null,
    val elevationGainLabel: String? = null,
    val forecastElevationLabel: String? = null,
)

/**
 * The one place a completed session (manually logged or GPS-tracked) turns into a saved
 * SessionEntity, an updated Fidelity/Proximity, a Composure verdict, and — for
 * Predatory/Swarming — a same-day notification. Shared by LogSessionViewModel and
 * TrackViewModel so both entry points behave identically, and shared across opponent types
 * (Twin or Horde) so neither duplicates this pipeline. See docs/zombie-mode.md.
 *
 * Rest days / vacation (docs/telemetry-and-forecasting.md): the session is still saved —
 * training on a rest day is real training — but Fidelity is frozen rather than moved,
 * Composure stays neutral, and no notification fires. The Forecast screen's "no fidelity
 * change while you're away" promise is kept here, not just displayed.
 *
 * Decommission Trial (domain/trial): a session flagged [duel] is judged against the
 * opponent's strongest prior session. A win resets Fidelity and advances the generation;
 * a loss is an ordinary session plus a pointed remark.
 */
class SessionFinalizer(
    private val repository: GaitRepository,
    private val appContext: Context,
) {
    private val forecastEngine = ForecastEngine()
    private val composureEngine = ComposureEngine()

    suspend fun finalize(
        distanceMeters: Double,
        durationSeconds: Int,
        dataSource: String = SessionSource.GPS,
        duel: Boolean = false,
        route: String? = null,
        elevationGainMeters: Double? = null,
        splitSeconds: List<Int> = emptyList(),
        avgHeartRate: Int? = null,
        maxHeartRate: Int? = null,
    ): DebriefResult {
        require(distanceMeters > 0.0 && durationSeconds > 0) { "finalize() needs a positive distance and duration" }

        val now = Instant.now()
        val avgPace = durationSeconds / (distanceMeters / 1000.0)
        val dayOfWeek = now.atZone(ZoneId.systemDefault()).dayOfWeek.value

        val profile = repository.getTwinProfile()
        val isRestDay = profile != null && (
            RestDayPolicy.isRestDay(profile, dayOfWeek) ||
                repository.isPlannedDayOff(java.time.LocalDate.now(ZoneId.systemDefault()).toEpochDay())
            )
        val isOnVacation = profile != null && RestDayPolicy.isOnVacation(profile, now.toEpochMilli())
        val isRestPeriod = isRestDay || isOnVacation

        val priorSessions = repository.getSessions()
        val forecast = forecastEngine.forecast(priorSessions, dayOfWeek, now.toEpochMilli())

        // --- The dimensions beyond pace: route novelty, steadiness, climb ---
        val activity = dev.eversorhn.gait.domain.activity.Activities.byKey(repository.activeActivityType)
        val routePoints = dev.eversorhn.gait.domain.route.RouteMetrics.decode(route)
        val priorRoutes = priorSessions.mapNotNull { it.route }.take(60).map { dev.eversorhn.gait.domain.route.RouteMetrics.decode(it) }
        val novelty = dev.eversorhn.gait.domain.route.RouteMetrics.novelty(routePoints, priorRoutes)
        val consistency = dev.eversorhn.gait.domain.route.RouteMetrics.consistency(splitSeconds)
        // The model's expected steadiness: EWMA of your last steady sessions (newest weighs most).
        val priorCons = priorSessions.mapNotNull { it.consistency }.take(8)
        val forecastConsistency = if (priorCons.size >= 2) priorCons.asReversed().fold(priorCons.last()) { acc, c -> acc * 0.7 + c * 0.3 } else null
        val forecastClimb = priorSessions.mapNotNull { it.elevationGainMeters }.take(8).takeIf { it.isNotEmpty() }?.average()

        val isHorde = profile?.isHorde == true
        val metricLabel = if (isHorde) "Proximity" else "Fidelity"
        val generationLabel = if (isHorde) "Wave" else "Generation"

        // --- Stake: the opponent's open commitment on today's forecast (domain/wager) ---
        val zoneOffset = now.atZone(ZoneId.systemDefault()).offset.totalSeconds * 1000L
        val todayEpochDay = WagerPolicy.epochDay(now.toEpochMilli(), zoneOffset)
        val stakeOpen = profile != null && profile.wagerStake > 0 && profile.wagerEpochDay == todayEpochDay && forecast != null && !isRestPeriod
        val stakeCalled = stakeOpen && profile!!.wagerCalled
        val ledgerBefore = Ledger.from(priorSessions)

        // --- Decommission Trial verdict: judged against *prior* history, before Fidelity moves ---
        val duelTarget = if (duel && profile != null) DecommissionTrial.targetPaceSecPerKm(priorSessions) else null
        val verdict = duelTarget?.let { DecommissionTrial.judge(distanceMeters, avgPace, it) }
        val duelWon = verdict == DecommissionTrial.Verdict.WON
        val roundStake = when {
            verdict == DecommissionTrial.Verdict.WON || verdict == DecommissionTrial.Verdict.LOST -> Ledger.DUEL_STAKE
            else -> WagerPolicy.roundStake(stakeOpen, stakeCalled, profile?.wagerStake ?: WagerPolicy.STAKE)
        }

        // On a rest day / vacation, Composure is deliberately neutral and Fidelity is frozen.
        // A decided duel overrides the z-score: losing one is Predatory by definition.
        val composureState = when {
            isRestPeriod -> ComposureState.WATCHFUL
            verdict == DecommissionTrial.Verdict.LOST -> ComposureState.PREDATORY
            verdict == DecommissionTrial.Verdict.WON -> ComposureState.COWED
            else -> composureEngine.evaluate(
                listOf(stubForComposure(avgPace, forecast?.forecastPaceSecPerKm)) + repository.getRecentSessions(limit = 9)
            )
        }

        val previousFidelity = profile?.fidelity ?: FidelityReplay.INITIAL_FIDELITY
        var newFidelity = previousFidelity
        var newGeneration = profile?.generation ?: 1

        if (profile != null) {
            when {
                duelWon -> {
                    newFidelity = DecommissionTrial.RESET_FIDELITY
                    newGeneration = profile.generation + 1
                    repository.updateTwinProfile(profile.copy(fidelity = newFidelity, generation = newGeneration, trialDeadlineEpochDay = -1L))
                }
                forecast != null && !isRestPeriod -> {
                    newFidelity = FidelityReplay.step(
                        previousFidelity,
                        FidelityReplay.sessionFidelity(forecast.forecastPaceSecPerKm, avgPace),
                    )
                    repository.updateTwinProfile(profile.copy(fidelity = newFidelity))
                }
            }
        }


        val storedSession = SessionEntity(
                activityType = repository.activeActivityType,
                startTimeEpochMillis = now.toEpochMilli(),
                dayOfWeek = dayOfWeek,
                durationSeconds = durationSeconds,
                distanceMeters = distanceMeters,
                avgPaceSecPerKm = avgPace,
                forecastPaceSecPerKm = forecast?.forecastPaceSecPerKm,
                forecastFinishSeconds = forecast?.forecastFinishSeconds,
                isRestDay = isRestPeriod,
                dataSource = dataSource,
                composureState = if (isRestPeriod) null else composureState.name,
                isDuel = duelTarget != null,
                duelWon = when (verdict) {
                    DecommissionTrial.Verdict.WON -> true
                    DecommissionTrial.Verdict.LOST -> false
                    else -> null
                },
                stake = roundStake,
                route = route,
                elevationGainMeters = elevationGainMeters,
                consistency = consistency,
                routeNovelty = novelty,
                forecastConsistency = forecastConsistency,
                avgHeartRate = avgHeartRate,
                maxHeartRate = maxHeartRate,
        )
        repository.logSession(storedSession)
        // What you recorded here counts everywhere else on the phone, if you asked for that.
        appContext?.let { dev.eversorhn.gait.health.HealthExport.write(it, storedSession, indoor = dataSource != SessionSource.GPS) }

        // The stake is consumed by this round whether it paid out or not; one per day.
        if (stakeOpen) {
            repository.getTwinProfile()?.let { fresh ->
                repository.updateTwinProfile(fresh.copy(wagerStake = 0, wagerCalled = false))
            }
        }

        val restNote = when {
            isOnVacation -> "Logged during vacation — counted as training, $metricLabel frozen, round not scored."
            isRestDay -> "Logged on a declared rest day — counted as training, $metricLabel frozen, round not scored."
            else -> null
        }

        val allSessions = repository.getSessions()
        val history = FidelityReplay.history(allSessions)
        val ledgerAfter = Ledger.from(allSessions)


        return DebriefResult(
            hadForecast = forecast != null,
            forecastPaceLabel = forecast?.let { dev.eversorhn.gait.domain.activity.Activities.formatPaceOrSpeed(it.forecastPaceSecPerKm, repository.activeActivityType) } ?: "—",
            actualPaceLabel = dev.eversorhn.gait.domain.activity.Activities.formatPaceOrSpeed(avgPace, repository.activeActivityType),
            paceWord = dev.eversorhn.gait.domain.activity.Activities.paceWord(repository.activeActivityType),
            avgHeartRate = avgHeartRate,
            maxHeartRate = maxHeartRate,
            marginLabel = forecast?.let { f ->
                val a = dev.eversorhn.gait.domain.activity.Activities.byKey(repository.activeActivityType)
                if (a.usesSpeed) {
                    val d = 3600.0 / avgPace.coerceAtLeast(1.0) - 3600.0 / f.forecastPaceSecPerKm.coerceAtLeast(1.0)
                    "%+.1f km/h".format(d)
                } else {
                    val d = ((f.forecastPaceSecPerKm - avgPace) * a.paceUnitMeters / 1000.0).toInt()
                    val unit = if (a.paceUnitMeters == 1000) "km" else "${a.paceUnitMeters}m"
                    val sign = if (d >= 0) "+" else "−"
                    val abs = kotlin.math.abs(d)
                    "$sign${abs / 60}:${(abs % 60).toString().padStart(2, '0')}/$unit"
                }
            },
            composureState = composureState,
            scoredOnDimensions = !activity.paceMeaningful,
            routeNoveltyPercent = novelty?.let { (it * 100).toInt() },
            consistencyPercent = consistency?.let { (it * 100).toInt() },
            forecastConsistencyPercent = forecastConsistency?.let { (it * 100).toInt() },
            elevationGainLabel = elevationGainMeters?.let { "${it.toInt()} m" },
            forecastElevationLabel = forecastClimb?.let { "~${it.toInt()} m" },
            newFidelityPercent = (newFidelity * 100).toInt(),
            dataSource = dataSource,
            opponentType = profile?.opponentType ?: OpponentType.TWIN,
            metricLabel = metricLabel,
            restNote = restNote,
            opponentName = profile?.twinName ?: "",
            previousFidelityPercent = (previousFidelity * 100).toInt(),
            fidelityHistory = history,
            forecastDistanceLabel = forecast?.let { formatDistanceKm(it.forecastDistanceMeters) } ?: "—",
            actualDistanceLabel = formatDistanceKm(distanceMeters),
            forecastFinishLabel = forecast?.let { formatDuration(it.forecastFinishSeconds) } ?: "—",
            actualFinishLabel = formatDuration(durationSeconds),
            beatForecast = forecast?.let { avgPace < it.forecastPaceSecPerKm },
            generation = newGeneration,
            generationLabel = generationLabel,
            duel = verdict?.let {
                DuelOutcome(
                    verdict = it,
                    targetPaceLabel = dev.eversorhn.gait.domain.activity.Activities.formatPaceOrSpeed(duelTarget!!, repository.activeActivityType),
                    newGeneration = if (duelWon) newGeneration else null,
                )
            },
            roundWinner = Ledger.winnerOf(
                stubForComposure(avgPace, forecast?.forecastPaceSecPerKm).copy(
                    activityType = repository.activeActivityType, isRestDay = isRestPeriod,
                    routeNovelty = novelty, consistency = consistency, forecastConsistency = forecastConsistency,
                )
            ),
            stake = roundStake,
            stakeWasOpen = stakeOpen,
            stakeCalled = stakeCalled,
            ledger = ledgerAfter,
            ledgerBefore = ledgerBefore,
        )
    }

    /**
     * Composure is evaluated on the *new* session plus the nine before it. The row isn't in
     * the database yet at that point (the verdict decides what gets stored with it), so a
     * throwaway entity carries just the two fields ComposureEngine reads.
     */
    private fun stubForComposure(avgPace: Double, forecastPace: Double?) = SessionEntity(
        activityType = ACTIVITY_RUNNING,
        startTimeEpochMillis = 0L,
        dayOfWeek = 1,
        durationSeconds = 1,
        distanceMeters = 1.0,
        avgPaceSecPerKm = avgPace,
        forecastPaceSecPerKm = forecastPace,
        forecastFinishSeconds = null,
    )
}
