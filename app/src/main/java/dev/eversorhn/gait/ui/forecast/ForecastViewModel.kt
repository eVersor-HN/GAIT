package dev.eversorhn.gait.ui.forecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.db.entity.MessageKind
import dev.eversorhn.gait.data.db.entity.isHorde
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.composure.ComposureState
import dev.eversorhn.gait.domain.fidelity.FidelityReplay
import dev.eversorhn.gait.domain.forecast.ForecastEngine
import dev.eversorhn.gait.domain.horde.HordeIntensity
import dev.eversorhn.gait.domain.ledger.Ledger
import dev.eversorhn.gait.domain.ledger.LedgerState
import dev.eversorhn.gait.domain.restdays.RestDayPolicy
import dev.eversorhn.gait.domain.trial.DecommissionTrial
import dev.eversorhn.gait.domain.wager.WagerPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/** The most recent thing the opponent said, for the Forecast screen's inbox teaser. */

/** The opponent's open stake on today's forecast. */
data class OpenStake(val points: Int, val called: Boolean, val claim: String, val calledPoints: Int)

/**
 * How far the opponent is from making its own estimates, and from trusting them. Shown on the
 * Forecast as a bar so a new enrolment can see what is still missing instead of an empty screen.
 */
data class Calibration(
    val sessions: Int,
    val forecasting: Boolean,
    val staking: Boolean,
    /** Sessions still needed for the next stage; null once the model is at full strength. */
    val remaining: Int?,
    val label: String,
    val progress: Float,
) {
    companion object {
        const val FORECAST_AT = 1
        const val STAKES_AT = 3
        const val FULL_AT = 7

        fun of(sessions: Int): Calibration {
            val n = sessions.coerceAtLeast(0)
            val remaining = when {
                n < FORECAST_AT -> FORECAST_AT - n
                n < STAKES_AT -> STAKES_AT - n
                n < FULL_AT -> FULL_AT - n
                else -> null
            }
            fun s(k: Int) = if (k == 1) "session" else "sessions"
            val label = when {
                n < FORECAST_AT -> "No baseline yet — first estimate after ${FORECAST_AT - n} ${s(FORECAST_AT - n)}"
                n < STAKES_AT -> "Estimating — points on the line after ${STAKES_AT - n} more ${s(STAKES_AT - n)}"
                n < FULL_AT -> "Estimating and staking — full strength in ${FULL_AT - n} more ${s(FULL_AT - n)}"
                else -> "Model at full strength on $n sessions"
            }
            return Calibration(
                sessions = n,
                forecasting = n >= FORECAST_AT,
                staking = n >= STAKES_AT,
                remaining = remaining,
                label = label,
                progress = (n.toFloat() / FULL_AT).coerceIn(0f, 1f),
            )
        }
    }
}

sealed interface ForecastUiState {
    data object Loading : ForecastUiState
    data object NoTwin : ForecastUiState
    data class Ready(
        val isHorde: Boolean,
        val opponentName: String,
        val opponentLabel: String,
        val metricLabel: String,
        val metricPercent: Int,
        val generationLabel: String,
        val generation: Int,
        val coldStart: Boolean,
        /** Horde only: the atmospheric caption shown above the numbers. Null for a Twin. */
        val basedOnSessions: Int,
        val totalSessions: Int,
        val confidencePercent: Int,
        val restStateLabel: String?,
        val forecastPaceLabel: String,
        val forecastDistanceLabel: String,
        val forecastFinishLabel: String,
        val fidelityHistory: List<Float>,
        val trialEligible: Boolean,
        val trialProgressPercent: Int,
        val trialThresholdPercent: Int,
        /** "Decommission Trial" for a Twin, "Outrun Trial" for a Horde. */
        val trialLabel: String,
        /** Days until the review lapses; null when no Trial is open. 0 = today. */
        val trialDeadlineDays: Int?,
        // --- v0.6.0: the competitive layer ---
        val ledger: LedgerState,
        val standing: String,
        /** How far the model is from making, then trusting, its own estimates. */
        val calibration: Calibration,
        /** Motor-assisted activities: the rule that decides the round. Not a voice — a rule. */
        val ruleNote: String?,
        val stake: OpenStake?,
        val activityLabel: String,
        val paceWord: String,
        /** Motor-assisted: the round is judged on route novelty / steadiness, not pace. */
        val scoredOnDimensions: Boolean,
        val forecastConsistencyPercent: Int?,
        val forecastClimbLabel: String?,
        val usualRouteShare: Int?,
    ) : ForecastUiState
}

class ForecastViewModel(private val repository: GaitRepository, private val appContextForNotifications: android.content.Context? = null) : ViewModel() {

    private val engine = ForecastEngine()

    private val _uiState = MutableStateFlow<ForecastUiState>(ForecastUiState.Loading)
    val uiState: StateFlow<ForecastUiState> = _uiState.asStateFlow()

    /** refresh() is called from init *and* the screen's LaunchedEffect; the stake must be made once. */
    private val refreshLock = Mutex()

    init {
        refresh()
    }

    /** The user calls the opponent's stake: the round is now worth [WagerPolicy.CALLED_STAKE] either way. */
    fun callStake() {
        viewModelScope.launch {
            val profile = repository.getTwinProfile() ?: return@launch
            if (profile.wagerStake <= 0 || profile.wagerCalled) return@launch
            repository.updateTwinProfile(profile.copy(wagerCalled = true))
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshLock.withLock { refreshLocked() }
        }
    }

    private suspend fun refreshLocked() {
        run {
            var profile = repository.getTwinProfile()
            if (profile == null) {
                _uiState.value = ForecastUiState.NoTwin
                return@run
            }
            val isHorde = profile.isHorde
            val sessions = repository.getSessions()
            val now = Instant.now()
            val zoned = now.atZone(ZoneId.systemDefault())
            val todayIso = zoned.dayOfWeek.value // 1..7
            val forecast = engine.forecast(sessions, todayIso, now.toEpochMilli())

            val metricLabel = if (isHorde) "Proximity" else "Fidelity"
            val isVacation = RestDayPolicy.isOnVacation(profile, now.toEpochMilli())
            val isRestDay = RestDayPolicy.isRestDay(profile, todayIso) || repository.isPlannedDayOff(java.time.LocalDate.now(ZoneId.systemDefault()).toEpochDay())
            val restLabel = when {
                isVacation -> "On vacation. Sessions count, $metricLabel frozen, rounds not scored."
                isRestDay -> "Rest day. Train anyway if you want — $metricLabel frozen, round not scored."
                else -> null
            }

            val opponentLabel = if (isHorde) HordeIntensity.label(profile.hordeIntensity ?: HordeIntensity.STANDARD) else "Model"
            val generationLabel = if (isHorde) "Wave" else "Generation"
            val activityKey = repository.activeActivityType
            val paceLabel = forecast?.let { dev.eversorhn.gait.domain.activity.Activities.formatPaceOrSpeed(it.forecastPaceSecPerKm, activityKey) } ?: "—"
            val finishLabel = forecast?.let { formatDuration(it.forecastFinishSeconds) } ?: "—"

            // --- The opponent commits to today's forecast (once per local day) ---
            val todayEpochDay = WagerPolicy.epochDay(now.toEpochMilli(), zoned.offset.totalSeconds * 1000L)
            // Evaluated once per day -- but a day that had no stake yet (no claim on file) is
            // re-evaluated, so a morning with too little history can still turn into a stake
            // after the session that pushed it over the bar. A consumed stake keeps its claim.
            val unevaluatedToday = profile.wagerEpochDay != todayEpochDay || (profile.wagerStake == 0 && profile.wagerClaim == null)
            if (unevaluatedToday && forecast != null &&
                WagerPolicy.shouldStake(forecast.confidencePercent, forecast.basedOnSessions, isVacation || isRestDay)
            ) {
                val stakePts = WagerPolicy.stakeFor(Ledger.from(sessions).lead)
                profile = profile.copy(wagerStake = stakePts, wagerCalled = false, wagerEpochDay = todayEpochDay, wagerClaim = paceLabel)
                repository.updateTwinProfile(profile)
            } else if (unevaluatedToday) {
                // No stake today, but mark the day so we don't re-evaluate on every refresh.
                profile = profile.copy(wagerStake = 0, wagerCalled = false, wagerEpochDay = todayEpochDay, wagerClaim = null)
                repository.updateTwinProfile(profile)
            }
            val openStake = if (profile.wagerStake > 0 && profile.wagerEpochDay == todayEpochDay && profile.wagerClaim != null) {
                OpenStake(points = profile.wagerStake, called = profile.wagerCalled, claim = profile.wagerClaim!!, calledPoints = WagerPolicy.calledStakeFor(profile.wagerStake))
            } else null


            val ledger = Ledger.from(sessions)
            var trialEligible = DecommissionTrial.isEligible(profile.fidelity) && forecast != null
            // --- Trial deadline lifecycle: open on first eligibility, ratify the model on lapse ---
            var deadlineDays: Int? = null
            if (trialEligible) {
                if (profile.trialDeadlineEpochDay < 0) {
                    profile = profile.copy(trialDeadlineEpochDay = todayEpochDay + DecommissionTrial.REVIEW_WINDOW_DAYS)
                    repository.updateTwinProfile(profile)
                } else if (todayEpochDay > profile.trialDeadlineEpochDay) {
                    // Lapsed: the replacement candidate is ratified a generation. Fidelity stays; pressure rises.
                    val newGen = profile.generation + 1
                    profile = profile.copy(generation = newGen, trialDeadlineEpochDay = todayEpochDay + DecommissionTrial.REVIEW_WINDOW_DAYS)
                    repository.updateTwinProfile(profile)
                }
                deadlineDays = (profile.trialDeadlineEpochDay - todayEpochDay).toInt().coerceAtLeast(0)
            } else if (profile.trialDeadlineEpochDay >= 0) {
                // Fidelity dropped back under the threshold (a won duel or a wild session): review closes.
                profile = profile.copy(trialDeadlineEpochDay = -1L)
                repository.updateTwinProfile(profile)
            }

            val activityObj = dev.eversorhn.gait.domain.activity.Activities.byKey(activityKey)
            val priorCons = sessions.mapNotNull { it.consistency }.take(8)
            val expectedCons = if (priorCons.size >= 2) priorCons.asReversed().fold(priorCons.last()) { acc, c -> acc * 0.7 + c * 0.3 } else null
            val climbAvg = sessions.mapNotNull { it.elevationGainMeters }.take(8).takeIf { it.isNotEmpty() }?.average()
            // "Usual route": share of past routes that overlap ≥ 60 % with the most recent one.
            val routes = sessions.mapNotNull { it.route }.take(20).map { dev.eversorhn.gait.domain.route.RouteMetrics.decode(it) }.filter { it.isNotEmpty() }
            val usualShare = if (routes.size >= 2) (routes.drop(1).count { dev.eversorhn.gait.domain.route.RouteMetrics.similarity(routes.first(), it) >= 0.6 } * 100 / (routes.size - 1)) else null
            val ruleNote = if (!activityObj.paceMeaningful && forecast != null) {
                "A new route or a steadier ${activityObj.verb} takes the round — speed doesn't." +
                    (usualShare?.let { " Same route as $it% of your recent ones." } ?: "") +
                    (expectedCons?.let { " Expected steadiness ${(it * 100).toInt()}%." } ?: "")
            } else null

            _uiState.value = ForecastUiState.Ready(
                isHorde = isHorde,
                opponentName = profile.twinName,
                opponentLabel = opponentLabel,
                metricLabel = metricLabel,
                metricPercent = (profile.fidelity * 100).toInt(),
                generationLabel = generationLabel,
                generation = profile.generation,
                coldStart = forecast == null,
                basedOnSessions = forecast?.basedOnSessions ?: 0,
                totalSessions = sessions.size,
                confidencePercent = forecast?.confidencePercent ?: 0,
                restStateLabel = restLabel,
                forecastPaceLabel = paceLabel,
                forecastDistanceLabel = forecast?.let { formatDistanceKm(it.forecastDistanceMeters) } ?: "—",
                forecastFinishLabel = finishLabel,
                fidelityHistory = FidelityReplay.history(sessions),
                trialEligible = trialEligible,
                trialProgressPercent = DecommissionTrial.progressPercent(profile.fidelity),
                trialThresholdPercent = (DecommissionTrial.THRESHOLD * 100).toInt(),
                trialLabel = if (isHorde) "Outrun Trial" else "Decommission Trial",
                trialDeadlineDays = deadlineDays,
                ledger = ledger,
                standing = Ledger.standingLabel(ledger, profile.twinName, isHorde),
                calibration = Calibration.of(sessions.size),
                ruleNote = ruleNote,
                stake = openStake,
                activityLabel = dev.eversorhn.gait.domain.activity.Activities.byKey(repository.activeActivityType).label,
                paceWord = dev.eversorhn.gait.domain.activity.Activities.paceWord(repository.activeActivityType),
                scoredOnDimensions = !activityObj.paceMeaningful,
                forecastConsistencyPercent = expectedCons?.let { (it * 100).toInt() },
                forecastClimbLabel = climbAvg?.let { "~${it.toInt()} m" },
                usualRouteShare = usualShare,
            )
        }
    }
}

fun formatPace(secPerKm: Double): String {
    val totalSec = secPerKm.toInt()
    return "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}/km"
}

fun formatDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}

fun formatDistanceKm(meters: Double): String = "%.2f km".format(meters / 1000.0)
