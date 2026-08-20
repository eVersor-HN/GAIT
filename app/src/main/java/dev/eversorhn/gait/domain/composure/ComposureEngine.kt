package dev.eversorhn.gait.domain.composure

import dev.eversorhn.gait.data.db.entity.SessionEntity
import kotlin.math.sqrt

enum class ComposureState { COWED, WATCHFUL, PREDATORY }

/**
 * Dominance-reactive tone (docs/composure-system.md), driven by personalized
 * z-scores rather than fixed thresholds (docs/telemetry-and-forecasting.md) —
 * "faster than usual" means something different per person.
 */
class ComposureEngine {

    private val zThreshold = 1.5

    /**
     * [recentSessionsNewestFirst] must have forecastPaceSecPerKm populated and be
     * ordered most-recent-first. Needs at least 3 forecasted sessions to say anything
     * meaningful; fewer than that stays Watchful rather than guessing.
     */
    fun evaluate(recentSessionsNewestFirst: List<SessionEntity>): ComposureState {
        val deltas = recentSessionsNewestFirst
            .mapNotNull { s -> s.forecastPaceSecPerKm?.let { it - s.avgPaceSecPerKm } }
        // delta > 0 means actual pace was faster (lower) than forecast: a win for the user.

        if (deltas.size < 3) return ComposureState.WATCHFUL

        val latest = deltas.first()
        val baseline = deltas.drop(1)
        val mean = baseline.average()
        val variance = baseline.sumOf { (it - mean) * (it - mean) } / baseline.size
        val stddev = sqrt(variance)
        if (stddev <= 0.0001) return ComposureState.WATCHFUL

        val z = (latest - mean) / stddev
        return when {
            z >= zThreshold -> ComposureState.COWED
            z <= -zThreshold -> ComposureState.PREDATORY
            else -> ComposureState.WATCHFUL
        }
    }

    /**
     * A gap well beyond this user's own rhythm is predatory on its own, independent
     * of the performance z-score above — see "Rest days" in
     * docs/telemetry-and-forecasting.md for the anti-gaming rationale.
     */
    fun isGapPredatory(daysSinceLastSession: Double, historicalGapsDays: List<Double>): Boolean {
        if (historicalGapsDays.size < 3) return false
        val mean = historicalGapsDays.average()
        val variance = historicalGapsDays.sumOf { (it - mean) * (it - mean) } / historicalGapsDays.size
        val stddev = sqrt(variance)
        if (stddev <= 0.0001) return false
        return (daysSinceLastSession - mean) / stddev >= zThreshold
    }
}
