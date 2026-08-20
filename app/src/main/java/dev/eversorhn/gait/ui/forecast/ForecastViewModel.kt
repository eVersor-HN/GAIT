package dev.eversorhn.gait.ui.forecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.db.entity.isHorde
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.forecast.ForecastEngine
import dev.eversorhn.gait.domain.horde.HordeIntensity
import dev.eversorhn.gait.domain.horde.HordeSoundCues
import dev.eversorhn.gait.domain.persona.Personas
import dev.eversorhn.gait.domain.restdays.RestDayPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

sealed interface ForecastUiState {
    data object Loading : ForecastUiState
    data object NoTwin : ForecastUiState
    data class Ready(
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
        val confidencePercent: Int,
        val restStateLabel: String?,
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

            _uiState.value = if (forecast == null) {
                ForecastUiState.Ready(
                    opponentName = profile.twinName,
                    opponentLabel = opponentLabel,
                    metricLabel = metricLabel,
                    metricPercent = (profile.fidelity * 100).toInt(),
                    generationLabel = generationLabel,
                    generation = profile.generation,
                    coldStart = true,
                    forecastLine = "No baseline on you yet. Log a session first.",
                    hordeCaption = if (isHorde) HordeSoundCues.forecastCaption(0) else null,
                    basedOnSessions = 0,
                    confidencePercent = 0,
                    restStateLabel = restLabel,
                )
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
                ForecastUiState.Ready(
                    opponentName = profile.twinName,
                    opponentLabel = opponentLabel,
                    metricLabel = metricLabel,
                    metricPercent = (profile.fidelity * 100).toInt(),
                    generationLabel = generationLabel,
                    generation = profile.generation,
                    coldStart = false,
                    forecastLine = line,
                    hordeCaption = if (isHorde) HordeSoundCues.forecastCaption(forecast.basedOnSessions) else null,
                    basedOnSessions = forecast.basedOnSessions,
                    confidencePercent = forecast.confidencePercent,
                    restStateLabel = restLabel,
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
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
