package dev.eversorhn.momentum.domain.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteMetricsTest {

    private fun line(lat0: Double, lon0: Double, n: Int, dLat: Double) =
        (0 until n).map { RouteMetrics.Point(lat0 + it * dLat, lon0) }

    @Test
    fun `encode and decode round-trip`() {
        val pts = line(52.52, 13.405, 20, 0.0003)
        val back = RouteMetrics.decode(RouteMetrics.encode(pts))
        assertEquals(pts.size, back.size)
        assertEquals(pts.last().lat, back.last().lat, 0.00001)
        assertTrue(RouteMetrics.decode("").isEmpty())
        assertTrue(RouteMetrics.decode("garbage").isEmpty())
    }

    @Test
    fun `same loop is not novel, a different street is`() {
        val usual = line(52.52, 13.405, 40, 0.0003)
        val again = line(52.5201, 13.4051, 40, 0.0003)        // same line, ~10 m off: GPS jitter
        val elsewhere = line(52.53, 13.42, 40, 0.0003)
        assertTrue(RouteMetrics.similarity(usual, again) > 0.6)
        assertTrue(RouteMetrics.novelty(again, listOf(usual))!! < 0.4)
        assertTrue(RouteMetrics.novelty(elsewhere, listOf(usual))!! > 0.9)
        assertNull(RouteMetrics.novelty(usual, emptyList()))
    }

    @Test
    fun `consistency is one minus the split CV, and climb ignores noise`() {
        assertEquals(1.0, RouteMetrics.consistency(listOf(300, 300, 300))!!, 0.0001)
        assertTrue(RouteMetrics.consistency(listOf(280, 320, 300))!! < 0.96)
        assertNull(RouteMetrics.consistency(listOf(300)))
        assertEquals(0.0, RouteMetrics.elevationGain(listOf(100.0, 101.0, 100.5, 101.5)), 0.0001)
        assertEquals(25.0, RouteMetrics.elevationGain(listOf(100.0, 110.0, 105.0, 120.0, 119.0)), 0.0001)
    }

    @Test
    fun `downsampling keeps ~25 m steps`() {
        val a = RouteMetrics.Point(52.52, 13.405)
        assertTrue(RouteMetrics.shouldKeep(null, a))
        assertTrue(!RouteMetrics.shouldKeep(a, RouteMetrics.Point(52.52005, 13.405)))   // ~5.5 m
        assertTrue(RouteMetrics.shouldKeep(a, RouteMetrics.Point(52.5203, 13.405)))     // ~33 m
    }
}
