package dev.eversorhn.gait.ui.forecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.db.entity.isHorde
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.composure.ComposureState
import dev.eversorhn.gait.domain.fidelity.FidelityReplay
import dev.eversorhn.gait.domain.forecast.ForecastEngine
import dev.eversorhn.gait.domain.horde.HordeIntensity
import dev.eversorhn.gait.domain.horde.HordeSoundCues
import dev.eversorhn.gait.domain.persona.Personas
import dev.eversorhn.gait.domain.restdays.RestDayPolicy
import dev.eversorhn.gait.domain.trial.DecommissionTrial
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The most recent thing the opponent said, for the Forecast screen's inbox teaser. */
data class LastMessage(val line: String, val state: ComposureState, val daysAgo: Long)

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
        // --- v0.5.0 instrument panel ---
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
    ) : ForecastUiState
}

class ForecastViewModel(private val repository: GaitRepository) : ViewModel() {

    private val engine = ForecastEngine()

    private val _uiState = MutableStateFlow<ForecastUiState>(ForecastUiState.Loading)
    val uiState: StateFlow<ForecastUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val profile = repository.getTwinProfile()
            if (profile == null) {
                _uiState.value = ForecastUiState.NoTwin
                return@launch
            }
            val isHorde = profile.isHorde
            val sessions = repository.getSessions()
            val now = Instant.now()
            val todayIso = now.atZone(ZoneId.systemDefault()).dayOfWeek.value // 1..7
            val forecast = engine.forecast(sessions, todayIso, now.toEpochMilli())

            val metricLabel = if (isHorde) "Proximity" else "Fidelity"
            val restLabel = when {
                RestDayPolicy.isOnVacation(profile, now.toEpochMilli()) ->
                    "On vacation. Sessions still count, but $metricLabel stays frozen and nobody reacts."
                RestDayPolicy.isRestDay(profile, todayIso) ->
                    "Declared rest day. Train anyway if you want — $metricLabel stays frozen and nobody reacts."
                else -> null
            }

            val opponentLabel = if (isHorde) {
                HordeIntensity.label(profile.hordeIntensity ?: HordeIntensity.STANDARD)
            } else {
                Personas.byKey(profile.personaKey).label
            }
            val generationLabel = if (isHorde) "Wave" else "Generation"

            val lastMessage = sessions.firstOrNull { it.twinLine != null }?.let { s ->
                LastMessage(
                    line = s.twinLine!!,
                    state = s.composureState?.let { runCatching { ComposureState.valueOf(it) }.getOrNull() }
                        ?: ComposureState.WATCHFUL,
                    daysAgo = ChronoUnit.DAYS.between(Instant.ofEpochMilli(s.startTimeEpochMillis), now),
                )
            }

            val common = ForecastUiState.Ready(
                isHorde = isHorde,
                opponentName = profile.twinName,
                opponentLabel = opponentLabel,
                metricLabel = metricLabel,
                metricPercent = (profile.fidelity * 100).toInt(),
                generationLabel = generationLabel,
                generation = profile.generation,
                coldStart = forecast == null,
                forecastLine = "No baseline on you yet. Log a session first.",
                hordeCaption = if (isHorde) HordeSoundCues.forecastCaption(forecast?.basedOnSessions ?: 0) else null,
                basedOnSessions = forecast?.basedOnSessions ?: 0,
                totalSessions = sessions.size,
                confidencePercent = forecast?.confidencePercent ?: 0,
                restStateLabel = restLabel,
                forecastPaceLabel = "—",
                forecastDistanceLabel = "—",
                forecastFinishLabel = "—",
                fidelityHistory = FidelityReplay.history(sessions),
                trialEligible = DecommissionTrial.isEligible(profile.fidelity) && forecast != null,
                trialProgressPercent = DecommissionTrial.progressPercent(profile.fidelity),
                trialThresholdPercent = (DecommissionTrial.THRESHOLD * 100).toInt(),
                trialLabel = if (isHorde) "Outrun Trial" else "Decommission Trial",
                lastMessage = lastMessage,
            )

            _uiState.value = if (forecast == null) {
                common
            } else {
                val paceLabel = formatPace(forecast.forecastPaceSecPerKm)
                val finishLabel = formatDuration(forecast.forecastFinishSeconds)
                // A Horde has no voice, so its "forecast line" is the plain projection --
                // the atmospheric caption sits alongside it rather than replacing the numbers.
                val line = if (isHorde) {
                    "Projected: pace $paceLabel, finish around $finishLabel."
                } else {
                    Personas.byKey(profile.personaKey).forecastLine(forecast.basedOnSessions, paceLabel, finishLabel)
                }
                common.copy(
                    forecastLine = line,
                    forecastPaceLabel = paceLabel,
                    forecastDistanceLabel = formatDistanceKm(forecast.forecastDistanceMeters),
                    forecastFinishLabel = finishLabel,
                )
            }
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
