package dev.eversorhn.gait.domain.forecast

import dev.eversorhn.gait.data.db.entity.SessionEntity
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

        val weightedPace = neighbors.sumOf { (s, w) -> s.avgPaceSecPerKm * w } / totalWeight
        val weightedDistance = neighbors.sumOf { (s, w) -> s.distanceMeters * w } / totalWeight
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
