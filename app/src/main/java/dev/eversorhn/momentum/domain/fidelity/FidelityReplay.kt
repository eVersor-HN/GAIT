package dev.eversorhn.momentum.domain.fidelity

import dev.eversorhn.momentum.data.db.entity.SessionEntity
import kotlin.math.abs

/**
 * Fidelity is an EWMA over per-session prediction accuracy. The running value lives on the
 * profile; the *history* isn't stored -- but every input to it is (each session carries its
 * forecast and actual pace), so the curve can be replayed deterministically for the Debrief
 * sparkline. Kept pure so SessionFinalizer and the UI agree on one definition.
 */
object FidelityReplay {

    /** Weight of the newest session in the running Fidelity. */
    const val ALPHA = 0.2f

    const val INITIAL_FIDELITY = 0.5f

    /** How well one session matched its forecast: 1.0 = exact, 0.0 = off by 100 % or more. */
    fun sessionFidelity(forecastPaceSecPerKm: Double, actualPaceSecPerKm: Double): Float {
        if (forecastPaceSecPerKm <= 0.0) return 0f
        val normalizedError = (abs(forecastPaceSecPerKm - actualPaceSecPerKm) / forecastPaceSecPerKm).coerceIn(0.0, 1.0)
        return (1.0 - normalizedError).toFloat()
    }

    fun step(current: Float, sessionFidelity: Float): Float =
        current * (1 - ALPHA) + sessionFidelity * ALPHA

    /**
     * Replays the running Fidelity over [sessionsNewestFirst], oldest session first. Sessions
     * without a forecast or on a rest day didn't move Fidelity and are skipped; a won duel
     * reset it. The returned list always starts at [INITIAL_FIDELITY] and has one entry per
     * applied session after it -- so a brand-new profile yields a single point.
     */
    fun history(sessionsNewestFirst: List<SessionEntity>): List<Float> {
        val out = ArrayList<Float>(sessionsNewestFirst.size + 1)
        var f = INITIAL_FIDELITY
        out += f
        for (s in sessionsNewestFirst.asReversed()) {
            if (s.isDuel && s.duelWon == true) {
                f = dev.eversorhn.momentum.domain.trial.DecommissionTrial.RESET_FIDELITY
                out += f
                continue
            }
            val forecast = s.forecastPaceSecPerKm ?: continue
            if (s.isRestDay) continue
            f = step(f, sessionFidelity(forecast, s.avgPaceSecPerKm))
            out += f
        }
        return out
    }
}
