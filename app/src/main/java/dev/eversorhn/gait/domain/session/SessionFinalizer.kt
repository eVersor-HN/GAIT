package dev.eversorhn.gait.domain.session

import android.content.Context
import dev.eversorhn.gait.data.db.entity.SessionEntity
import dev.eversorhn.gait.data.repository.ACTIVITY_RUNNING
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.composure.ComposureEngine
import dev.eversorhn.gait.domain.composure.ComposureState
import dev.eversorhn.gait.domain.forecast.ForecastEngine
import dev.eversorhn.gait.domain.persona.Personas
import dev.eversorhn.gait.notification.TwinNotifier
import dev.eversorhn.gait.ui.forecast.formatPace
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

/**
 * The one place a completed session (manually logged or GPS-tracked) turns into a saved
 * SessionEntity, an updated Fidelity, a Composure verdict, and — for Predatory — a
 * same-day notification. Shared by LogSessionViewModel and TrackViewModel so both entry
 * points behave identically.
 */
class SessionFinalizer(
    private val repository: GaitRepository,
    private val appContext: Context,
) {
    private val forecastEngine = ForecastEngine()
    private val composureEngine = ComposureEngine()
    private val fidelityAlpha = 0.2f

    suspend fun finalize(distanceMeters: Double, durationSeconds: Int): DebriefResult {
        val now = Instant.now()
        val avgPace = durationSeconds / (distanceMeters / 1000.0)
        val dayOfWeek = now.atZone(ZoneId.systemDefault()).dayOfWeek.value

        val priorSessions = repository.getSessions()
        val forecast = forecastEngine.forecast(priorSessions, dayOfWeek, now.toEpochMilli())

        repository.logSession(
            SessionEntity(
                activityType = ACTIVITY_RUNNING,
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

        if (composureState == ComposureState.PREDATORY && profile != null && twinLine != null) {
            TwinNotifier.postTwinMessage(appContext, profile.twinName, twinLine)
        }

        return DebriefResult(
            hadForecast = forecast != null,
            forecastPaceLabel = forecast?.let { formatPace(it.forecastPaceSecPerKm) } ?: "—",
            actualPaceLabel = formatPace(avgPace),
            composureState = composureState,
            twinLine = twinLine,
            newFidelityPercent = newFidelityPercent,
        )
    }
}
