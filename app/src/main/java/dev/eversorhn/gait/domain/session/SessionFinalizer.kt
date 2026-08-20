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
import dev.eversorhn.gait.domain.forecast.ForecastEngine
import dev.eversorhn.gait.domain.horde.HordeSoundCues
import dev.eversorhn.gait.domain.persona.Personas
import dev.eversorhn.gait.domain.restdays.RestDayPolicy
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
    val dataSource: String,
    val opponentType: String = OpponentType.TWIN,
    /** "Fidelity" for a Twin, "Proximity" for a Horde. */
    val metricLabel: String = "Fidelity",
    /**
     * Set when the session landed on a declared rest day or during vacation: it was still
     * recorded as real training, but Fidelity was frozen and Composure didn't react.
     */
    val restNote: String? = null,
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
 */
class SessionFinalizer(
    private val repository: GaitRepository,
    private val appContext: Context,
) {
    private val forecastEngine = ForecastEngine()
    private val composureEngine = ComposureEngine()
    private val fidelityAlpha = 0.2f

    suspend fun finalize(
        distanceMeters: Double,
        durationSeconds: Int,
        dataSource: String = SessionSource.GPS,
    ): DebriefResult {
        require(distanceMeters > 0.0 && durationSeconds > 0) { "finalize() needs a positive distance and duration" }

        val now = Instant.now()
        val avgPace = durationSeconds / (distanceMeters / 1000.0)
        val dayOfWeek = now.atZone(ZoneId.systemDefault()).dayOfWeek.value

        val profile = repository.getTwinProfile()
        val isRestDay = profile != null && RestDayPolicy.isRestDay(profile, dayOfWeek)
        val isOnVacation = profile != null && RestDayPolicy.isOnVacation(profile, now.toEpochMilli())
        val isRestPeriod = isRestDay || isOnVacation

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
                isRestDay = isRestPeriod,
                dataSource = dataSource,
            )
        )

        val isHorde = profile?.isHorde == true
        val persona = if (profile != null && !isHorde) Personas.byKey(profile.personaKey) else null

        // On a rest day / vacation, Composure is deliberately neutral and Fidelity is frozen.
        val composureState = if (isRestPeriod) {
            ComposureState.WATCHFUL
        } else {
            composureEngine.evaluate(repository.getRecentSessions(limit = 10))
        }

        var newFidelityPercent = ((profile?.fidelity ?: 0.5f) * 100).toInt()
        if (profile != null && forecast != null && !isRestPeriod) {
            val normalizedError = (abs(forecast.forecastPaceSecPerKm - avgPace) / forecast.forecastPaceSecPerKm)
                .coerceIn(0.0, 1.0)
            val sessionFidelity = (1.0 - normalizedError).toFloat()
            val updatedFidelity = profile.fidelity * (1 - fidelityAlpha) + sessionFidelity * fidelityAlpha
            repository.updateTwinProfile(profile.copy(fidelity = updatedFidelity))
            newFidelityPercent = (updatedFidelity * 100).toInt()
        }

        val opponentLine: String? = when {
            isRestPeriod -> null
            isHorde -> HordeSoundCues.captionFor(composureState, profile?.hordeIntensity ?: "")
            persona != null -> when (composureState) {
                ComposureState.COWED -> persona.cowedLines.random(Random)
                ComposureState.WATCHFUL -> persona.watchfulLines.random(Random)
                ComposureState.PREDATORY -> persona.predatoryLines.random(Random)
            }
            else -> null
        }

        if (!isRestPeriod && composureState == ComposureState.PREDATORY && profile != null && opponentLine != null) {
            TwinNotifier.postTwinMessage(appContext, profile.twinName, opponentLine)
        }

        val restNote = when {
            isOnVacation -> "Logged during vacation — counted as training, but ${if (isHorde) "Proximity" else "Fidelity"} stays frozen and nobody reacts."
            isRestDay -> "Logged on a declared rest day — counted as training, but ${if (isHorde) "Proximity" else "Fidelity"} stays frozen and nobody reacts."
            else -> null
        }

        return DebriefResult(
            hadForecast = forecast != null,
            forecastPaceLabel = forecast?.let { formatPace(it.forecastPaceSecPerKm) } ?: "—",
            actualPaceLabel = formatPace(avgPace),
            composureState = composureState,
            twinLine = opponentLine,
            newFidelityPercent = newFidelityPercent,
            dataSource = dataSource,
            opponentType = profile?.opponentType ?: OpponentType.TWIN,
            metricLabel = if (isHorde) "Proximity" else "Fidelity",
            restNote = restNote,
        )
    }
}
