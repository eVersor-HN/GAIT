package dev.eversorhn.gait.ui.forecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.db.entity.MessageKind
import dev.eversorhn.gait.data.db.entity.isHorde
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.composure.ComposureState
import dev.eversorhn.gait.domain.directive.Directive
import dev.eversorhn.gait.domain.fidelity.FidelityReplay
import dev.eversorhn.gait.domain.forecast.ForecastEngine
import dev.eversorhn.gait.domain.horde.HordeIntensity
import dev.eversorhn.gait.domain.horde.HordeSoundCues
import dev.eversorhn.gait.domain.intel.Intel
import dev.eversorhn.gait.domain.ledger.Ledger
import dev.eversorhn.gait.domain.ledger.LedgerState
import dev.eversorhn.gait.domain.persona.Personas
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
data class LastMessage(val line: String, val state: ComposureState?, val daysAgo: Long, val kind: String)

/** The opponent's open stake on today's forecast. */
data class OpenStake(val points: Int, val called: Boolean, val claim: String, val calledPoints: Int)

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
        val forecastLine: String,
        /** Horde only: the atmospheric caption shown above the numbers. Null for a Twin. */
        val hordeCaption: String?,
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
        val lastMessage: LastMessage?,
        // --- v0.6.0: the competitive layer ---
        val ledger: LedgerState,
        val standing: String,
        val memo: Directive.Memo,
        val intel: Intel.Observation?,
        val stake: OpenStake?,
        val activityLabel: String,
    ) : ForecastUiState
}

class ForecastViewModel(private val repository: GaitRepository) : ViewModel() {

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
            val line = if (profile.isHorde) HordeSoundCues.callCaption()
            else Personas.byKey(profile.personaKey).callLines.random(Random)
            repository.recordMessage(MessageKind.CALL, line, ComposureState.PREDATORY.name)
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
                isVacation -> "On vacation. Sessions still count, but $metricLabel stays frozen and nobody reacts."
                isRestDay -> "Rest day (declared or planned). Train anyway if you want — $metricLabel stays frozen and nobody reacts."
                else -> null
            }

            val persona = if (isHorde) null else Personas.byKey(profile.personaKey)
            val opponentLabel = if (isHorde) HordeIntensity.label(profile.hordeIntensity ?: HordeIntensity.STANDARD) else persona!!.label
            val generationLabel = if (isHorde) "Wave" else "Generation"
            val paceLabel = forecast?.let { formatPace(it.forecastPaceSecPerKm) } ?: "—"
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
                val claim = if (isHorde) HordeSoundCues.stakeCaption(paceLabel, stakePts)
                else persona!!.stakeLine(paceLabel, stakePts)
                profile = profile.copy(wagerStake = stakePts, wagerCalled = false, wagerEpochDay = todayEpochDay, wagerClaim = claim)
                repository.updateTwinProfile(profile)
                repository.recordMessage(MessageKind.STAKE, claim, ComposureState.WATCHFUL.name, now.toEpochMilli())
            } else if (unevaluatedToday) {
                // No stake today, but mark the day so we don't re-evaluate on every refresh.
                profile = profile.copy(wagerStake = 0, wagerCalled = false, wagerEpochDay = todayEpochDay, wagerClaim = null)
                repository.updateTwinProfile(profile)
            }
            val openStake = if (profile.wagerStake > 0 && profile.wagerEpochDay == todayEpochDay && profile.wagerClaim != null) {
                OpenStake(points = profile.wagerStake, called = profile.wagerCalled, claim = profile.wagerClaim!!, calledPoints = WagerPolicy.calledStakeFor(profile.wagerStake))
            } else null

            // --- Inbox teaser: newest of (debrief lines, unprompted messages) ---
            val newestSessionLine = sessions.firstOrNull { it.twinLine != null }
            val newestMessage = repository.getMessages().firstOrNull()
            val lastMessage = listOfNotNull(
                newestSessionLine?.let { s ->
                    LastMessage(
                        line = s.twinLine!!,
                        state = s.composureState?.let { runCatching { ComposureState.valueOf(it) }.getOrNull() },
                        daysAgo = ChronoUnit.DAYS.between(Instant.ofEpochMilli(s.startTimeEpochMillis), now),
                        kind = "debrief",
                    ) to s.startTimeEpochMillis
                },
                newestMessage?.let { m ->
                    LastMessage(
                        line = m.line,
                        state = m.composureState?.let { runCatching { ComposureState.valueOf(it) }.getOrNull() },
                        daysAgo = ChronoUnit.DAYS.between(Instant.ofEpochMilli(m.epochMillis), now),
                        kind = m.kind,
                    ) to m.epochMillis
                },
            ).maxByOrNull { it.second }?.first

            val ledger = Ledger.from(sessions)
            val trialEligible = DecommissionTrial.isEligible(profile.fidelity) && forecast != null
            val memo = Directive.forForecast(
                ledger = ledger,
                fidelityPercent = (profile.fidelity * 100).toInt(),
                trialThresholdPercent = (DecommissionTrial.THRESHOLD * 100).toInt(),
                trialEligible = trialEligible,
                opponentName = profile.twinName,
                isHorde = isHorde,
                generation = profile.generation,
            )
            val intel = Intel.observe(sessions, ledger, now.toEpochMilli(), todayIso, if (isHorde) "the horde" else profile.twinName) { formatPace(it) }

            val line = when {
                forecast == null -> "No baseline on you yet. Log a session first."
                isHorde -> "Projected: pace $paceLabel, finish around $finishLabel."
                else -> persona!!.forecastLine(forecast.basedOnSessions, paceLabel, finishLabel)
            }

            _uiState.value = ForecastUiState.Ready(
                isHorde = isHorde,
                opponentName = profile.twinName,
                opponentLabel = opponentLabel,
                metricLabel = metricLabel,
                metricPercent = (profile.fidelity * 100).toInt(),
                generationLabel = generationLabel,
                generation = profile.generation,
                coldStart = forecast == null,
                forecastLine = line,
                hordeCaption = if (isHorde) HordeSoundCues.forecastCaption(forecast?.basedOnSessions ?: 0) else null,
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
                lastMessage = lastMessage,
                ledger = ledger,
                standing = Directive.standing(ledger, profile.twinName, isHorde),
                memo = memo,
                intel = intel,
                stake = openStake,
                activityLabel = dev.eversorhn.gait.domain.activity.Activities.byKey(repository.activeActivityType).label,
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
