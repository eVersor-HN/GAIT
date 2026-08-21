package dev.eversorhn.gait.domain.route

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The dimensions beyond pace (docs/activities-and-dimensions.md): a stored route, how new it
 * is against everything you've done before, how steady the session was, and how much it
 * climbed. Pure, cheap, and deliberately coarse — a 1,000-point GPS trace is reduced to a
 * few hundred ~25 m steps and compared on a ~55 m grid, which is what "the same loop again"
 * means to a person.
 */
object RouteMetrics {

    /** Minimum distance between two kept points when downsampling a trace. */
    const val KEEP_EVERY_METERS = 25.0
    /** Grid cell edge for route comparison, in degrees (~55 m at mid latitudes). */
    private const val CELL_DEG = 0.0005
    /** Altitude steps smaller than this are GPS noise, not climbing. */
    const val CLIMB_STEP_METERS = 3.0

    data class Point(val lat: Double, val lon: Double)

    /** "lat,lon;lat,lon;…" with 5 decimals — ~1 m precision, ~20 bytes per point. */
    fun encode(points: List<Point>): String =
        points.joinToString(";") { "%.5f,%.5f".format(java.util.Locale.ROOT, it.lat, it.lon) }

    fun decode(encoded: String?): List<Point> {
        if (encoded.isNullOrBlank()) return emptyList()
        return encoded.split(';').mapNotNull { pair ->
            val i = pair.indexOf(',')
            if (i <= 0) return@mapNotNull null
            val lat = pair.substring(0, i).toDoubleOrNull() ?: return@mapNotNull null
            val lon = pair.substring(i + 1).toDoubleOrNull() ?: return@mapNotNull null
            Point(lat, lon)
        }
    }

    /** Rough metres between two points (equirectangular; fine below a few km). */
    fun metersBetween(a: Point, b: Point): Double {
        val dLat = (b.lat - a.lat) * 111_320.0
        val dLon = (b.lon - a.lon) * 111_320.0 * cos(Math.toRadians((a.lat + b.lat) / 2))
        return sqrt(dLat * dLat + dLon * dLon)
    }

    /** True if [p] is far enough from [last] to be worth keeping. */
    fun shouldKeep(last: Point?, p: Point): Boolean = last == null || metersBetween(last, p) >= KEEP_EVERY_METERS

    fun cells(points: List<Point>): Set<Long> = points.map { cellOf(it) }.toSet()

    private fun cellOf(p: Point): Long {
        val x = (p.lat / CELL_DEG).roundToInt().toLong()
        val y = (p.lon / CELL_DEG).roundToInt().toLong()
        return (x shl 32) xor (y and 0xffffffffL)
    }

    /** Jaccard overlap of the cell sets, 0..1. Two empty routes are "identical" (1.0). */
    fun similarity(a: List<Point>, b: List<Point>): Double {
        val ca = cells(a); val cb = cells(b)
        if (ca.isEmpty() && cb.isEmpty()) return 1.0
        if (ca.isEmpty() || cb.isEmpty()) return 0.0
        val inter = ca.count { it in cb }
        return inter.toDouble() / (ca.size + cb.size - inter)
    }

    /**
     * How new this route is against everything before: 1 − the best overlap with any earlier
     * route. Null when there's nothing to compare against (first route ever) — the caller
     * decides how to score a first route.
     */
    fun novelty(route: List<Point>, previous: List<List<Point>>): Double? {
        if (route.isEmpty()) return null
        val prior = previous.filter { it.isNotEmpty() }
        if (prior.isEmpty()) return null
        val best = prior.maxOf { similarity(route, it) }
        return (1.0 - best).coerceIn(0.0, 1.0)
    }

    /**
     * Steadiness: 1 − coefficient of variation of the per-kilometre split times, 0..1.
     * Needs ≥ 2 splits; a single split is perfectly "steady" by construction — return null.
     */
    fun consistency(splitSeconds: List<Int>): Double? {
        val xs = splitSeconds.filter { it > 0 }
        if (xs.size < 2) return null
        val mean = xs.average()
        val sd = sqrt(xs.sumOf { (it - mean) * (it - mean) } / xs.size)
        return (1.0 - sd / mean).coerceIn(0.0, 1.0)
    }

    /** Sum of positive altitude steps ≥ CLIMB_STEP_METERS between consecutive samples. */
    fun elevationGain(altitudes: List<Double>): Double {
        var gain = 0.0
        var last: Double? = null
        for (a in altitudes) {
            val l = last
            if (l != null) {
                val d = a - l
                if (abs(d) >= CLIMB_STEP_METERS) { if (d > 0) gain += d; last = a }
            } else last = a
        }
        return gain
    }
}
