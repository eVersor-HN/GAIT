package dev.eversorhn.gait.ui.forecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.forecast.ForecastEngine
import dev.eversorhn.gait.domain.persona.Personas
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
        val twinName: String,
        val personaLabel: String,
        val fidelityPercent: Int,
        val generation: Int,
        val coldStart: Boolean,
        val forecastLine: String,
        val basedOnSessions: Int,
        val confidencePercent: Int,
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
            val persona = Personas.byKey(profile.personaKey)
            val sessions = repository.getSessions()
            val now = Instant.now()
            val todayIso = now.atZone(ZoneId.systemDefault()).dayOfWeek.value // 1..7
            val forecast = engine.forecast(sessions, todayIso, now.toEpochMilli())

            _uiState.value = if (forecast == null) {
                ForecastUiState.Ready(
                    twinName = profile.twinName,
                    personaLabel = persona.label,
                    fidelityPercent = (profile.fidelity * 100).toInt(),
                    generation = profile.generation,
                    coldStart = true,
                    forecastLine = "No baseline on you yet. Log a session first.",
                    basedOnSessions = 0,
                    confidencePercent = 0,
                )
            } else {
                val paceLabel = formatPace(forecast.forecastPaceSecPerKm)
                val finishLabel = formatDuration(forecast.forecastFinishSeconds)
                ForecastUiState.Ready(
                    twinName = profile.twinName,
                    personaLabel = persona.label,
                    fidelityPercent = (profile.fidelity * 100).toInt(),
                    generation = profile.generation,
                    coldStart = false,
                    forecastLine = persona.forecastLine(forecast.basedOnSessions, paceLabel, finishLabel),
                    basedOnSessions = forecast.basedOnSessions,
                    confidencePercent = forecast.confidencePercent,
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
