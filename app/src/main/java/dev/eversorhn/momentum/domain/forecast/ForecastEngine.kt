package dev.eversorhn.momentum.domain.forecast

import dev.eversorhn.momentum.data.db.entity.SessionEntity
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Weighted k-nearest-historical-analog forecasting — see docs/telemetry-and-forecasting.md
 * for the rationale. Deliberately not a black-box model: a single user's history is a few
 * hundred rows at most, and the result has to explain itself ("based on N sessions").
 */
data class ForecastResult(
    val basedOnSessions: Int,
    val forecastPaceSecPerKm: Double,
    val forecastDistanceMeters: Double,
    val forecastFinishSeconds: Int,
    val confidencePercent: Int,
)

class ForecastEngine {

    /** Recency half-life: a session ~3 weeks old carries half the weight of today's. */
    private val recencyHalfLifeDays = 21.0

    private companion object {
        /**
         * Riegel's endurance exponent: time ≈ time₁ × (d/d₁)^1.06 across distances, fitted on
         * real race data and accurate to a few percent between neighbouring distances.
         */
        const val RIEGEL_EXPONENT = 1.06
    }

    fun forecast(
        history: List<SessionEntity>,
        targetDayOfWeek: Int,
        nowEpochMillis: Long,
        neighborCount: Int = 12,
    ): ForecastResult? {
        if (history.isEmpty()) return null

        val weighted = history.map { session ->
            val ageDays = (nowEpochMillis - session.startTimeEpochMillis) / 86_400_000.0
            val recencyWeight = exp(-ageDays / recencyHalfLifeDays)
            val dayWeight = if (session.dayOfWeek == targetDayOfWeek) 1.5 else 1.0
            session to (recencyWeight * dayWeight)
        }

        val neighbors = weighted.sortedByDescending { it.second }.take(neighborCount)
        val totalWeight = neighbors.sumOf { it.second }
        if (totalWeight <= 0.0) return null

        val weightedDistance = neighbors.sumOf { (s, w) -> s.distanceMeters * w } / totalWeight

        // Pace depends on how far you go: a 3 km effort and a 12 km effort are not run at the
        // same pace, so averaging them raw predicts a pace nobody ran. Each neighbour's pace is
        // first projected onto the forecast distance by the endurance power law (Riegel: time
        // scales with distance^1.06, so pace scales with distance^0.06), then averaged. With one
        // distance in the history this changes nothing; with a mixed history it stops the
        // forecast landing between two kinds of session.
        val weightedPace = if (weightedDistance > 200.0) {
            neighbors.sumOf { (s, w) ->
                val d = s.distanceMeters.coerceAtLeast(200.0)
                s.avgPaceSecPerKm * Math.pow(weightedDistance / d, RIEGEL_EXPONENT - 1.0) * w
            } / totalWeight
        } else {
            neighbors.sumOf { (s, w) -> s.avgPaceSecPerKm * w } / totalWeight
        }
        val finishSeconds = (weightedPace * (weightedDistance / 1000.0)).toInt()

        val confidence = confidencePercent(neighbors.map { it.first.avgPaceSecPerKm }, weightedPace)

        return ForecastResult(
            basedOnSessions = neighbors.size,
            forecastPaceSecPerKm = weightedPace,
            forecastDistanceMeters = weightedDistance,
            forecastFinishSeconds = finishSeconds,
            confidencePercent = confidence,
        )
    }

    /**
     * Confidence is the tightness of the neighbor cluster, not a made-up number:
     * a low coefficient of variation (paces agree with each other) means high confidence.
     * Small sample sizes get an explicit ceiling regardless of how tight they look.
     */
    private fun confidencePercent(paces: List<Double>, mean: Double): Int {
        if (paces.size < 2 || mean <= 0.0) return 35
        val variance = paces.sumOf { (it - mean) * (it - mean) } / paces.size
        val coefficientOfVariation = sqrt(variance) / mean
        val raw = 1.0 - (coefficientOfVariation * 3.0)
        val sampleCeiling = (40 + paces.size * 8).coerceAtMost(97)
        return (raw.coerceIn(0.15, 0.97) * 100).toInt().coerceAtMost(sampleCeiling)
    }
}
