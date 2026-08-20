package dev.eversorhn.gait.ui.logsession

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.db.entity.SessionEntity
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.composure.ComposureEngine
import dev.eversorhn.gait.domain.composure.ComposureState
import dev.eversorhn.gait.domain.forecast.ForecastEngine
import dev.eversorhn.gait.domain.persona.Personas
import dev.eversorhn.gait.notification.TwinNotifier
import dev.eversorhn.gait.ui.forecast.formatPace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.random.Random

data class DebriefResult(
    val hadForecast: Boolean,
    val forecastPaceLabel: String,
    val actualPaceLabel: String,
    val composureState: ComposureState,
    val twinLine: String?,
    val newFidelityPercent: Int,
)

data class LogSessionUiState(
    val distanceKm: String = "",
    val durationMinutes: String = "",
    val submitting: Boolean = false,
    val result: DebriefResult? = null,
)

class LogSessionViewModel(
    private val repository: GaitRepository,
    private val appContext: Context,
) : ViewModel() {

    private val forecastEngine = ForecastEngine()
    private val composureEngine = ComposureEngine()
    private val fidelityAlpha = 0.2f

    private val _uiState = MutableStateFlow(LogSessionUiState())
    val uiState: StateFlow<LogSessionUiState> = _uiState.asStateFlow()

    fun updateDistance(value: String) {
        _uiState.value = _uiState.value.copy(distanceKm = value)
    }

    fun updateDuration(value: String) {
        _uiState.value = _uiState.value.copy(durationMinutes = value)
    }

    fun reset() {
        _uiState.value = LogSessionUiState()
    }

    fun submit() {
        val distanceKm = _uiState.value.distanceKm.toDoubleOrNull() ?: return
        val durationMinutes = _uiState.value.durationMinutes.toDoubleOrNull() ?: return
        if (distanceKm <= 0.0 || durationMinutes <= 0.0) return

        _uiState.value = _uiState.value.copy(submitting = true)

        viewModelScope.launch {
            val now = Instant.now()
            val durationSeconds = (durationMinutes * 60).toInt()
            val distanceMeters = distanceKm * 1000.0
            val avgPace = durationSeconds / distanceKm
            val dayOfWeek = now.atZone(ZoneId.systemDefault()).dayOfWeek.value

            val priorSessions = repository.getSessions()
            val forecast = forecastEngine.forecast(priorSessions, dayOfWeek, now.toEpochMilli())

            repository.logSession(
                SessionEntity(
                    activityType = dev.eversorhn.gait.data.repository.ACTIVITY_RUNNING,
                    startTimeEpochMillis = now.toEpochMilli(),
                    dayOfWeek = dayOfWeek,
                    durationSeconds = durationSeconds,
                    distanceMeters = distanceMeters,
                    avgPaceSecPerKm = avgPace,
                    forecastPaceSecPerKm = forecast?.forecastPaceSecPerKm,
                    forecastFinishSeconds = forecast?.forecastFinishSeconds,
                )
            )

            val profile = repository.getTwinProfile()
            val persona = profile?.let { Personas.byKey(it.personaKey) }

            val recentWithForecast = repository.getRecentSessions(limit = 10)
            val composureState = composureEngine.evaluate(recentWithForecast)

            var newFidelityPercent = ((profile?.fidelity ?: 0.5f) * 100).toInt()
            if (profile != null && forecast != null) {
                val normalizedError = (abs(forecast.forecastPaceSecPerKm - avgPace) / forecast.forecastPaceSecPerKm)
                    .coerceIn(0.0, 1.0)
                val sessionFidelity = (1.0 - normalizedError).toFloat()
                val updatedFidelity = profile.fidelity * (1 - fidelityAlpha) + sessionFidelity * fidelityAlpha
                repository.updateTwinProfile(profile.copy(fidelity = updatedFidelity))
                newFidelityPercent = (updatedFidelity * 100).toInt()
            }

            val twinLine = if (persona != null) {
                when (composureState) {
                    ComposureState.COWED -> persona.cowedLines.random(Random)
                    ComposureState.PREDATORY -> persona.predatoryLines.random(Random)
                    ComposureState.WATCHFUL -> null
                }
            } else null

            // Predatory is the one state allowed to break containment same-day —
            // see "Where it's allowed to reach you" in docs/composure-system.md.
            if (composureState == ComposureState.PREDATORY && profile != null && twinLine != null) {
                TwinNotifier.postTwinMessage(appContext, profile.twinName, twinLine)
            }

            _uiState.value = _uiState.value.copy(
                submitting = false,
                result = DebriefResult(
                    hadForecast = forecast != null,
                    forecastPaceLabel = forecast?.let { formatPace(it.forecastPaceSecPerKm) } ?: "—",
                    actualPaceLabel = formatPace(avgPace),
                    composureState = composureState,
                    twinLine = twinLine,
                    newFidelityPercent = newFidelityPercent,
                ),
            )
        }
    }
}
