package dev.eversorhn.gait.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TrackingMode { OUTDOOR, INDOOR }

data class TrackingSnapshot(
    val isTracking: Boolean = false,
    val mode: TrackingMode? = null,
    /** Monotonic clock (SystemClock.elapsedRealtime) at start — immune to wall-clock changes. */
    val startElapsedRealtimeMillis: Long? = null,
    /** Wall-clock at start, for display / persistence only. */
    val startEpochMillis: Long? = null,
    val elapsedSeconds: Int = 0,
    val distanceMeters: Double = 0.0,
    /**
     * Outdoor only: seconds actually spent moving — excludes the wait for the first GPS fix
     * and any auto-paused stretches (standing at a light). Pace is computed from this, not
     * from [elapsedSeconds], so idle time doesn't quietly inflate it.
     */
    val movingSeconds: Int = 0,
    /**
     * Outdoor only: pace over roughly the last 200 m of moving (rolling), so the live comparison
     * and the opponent's callouts react to what you're doing *now*. Null until enough ground.
     */
    val currentPaceSecPerKm: Double? = null,
    /** Outdoor only: whole-session moving pace — what the Debrief will judge. */
    val avgPaceSecPerKm: Double? = null,
    /** Outdoor only: downsampled trace "lat,lon;…" (~25 m steps), grows as you go. */
    val routePolyline: String = "",
    /** Outdoor only: positive altitude gain so far, metres (GPS altitude, 3 m step filter). */
    val elevationGainMeters: Double = 0.0,
    /** Outdoor only: moving seconds per completed kilometre. */
    val splitSeconds: List<Int> = emptyList(),
    val gpsFixCount: Int = 0,
    /** Outdoor only: the last GPS interval showed effectively no movement. */
    val autoPaused: Boolean = false,
    /** Set if the service failed to start (e.g. a permission/FGS-type problem); UI shows it instead of a dead timer. */
    val error: String? = null,
)

/**
 * In-process shared state between LocationTrackingService (producer) and the Track UI
 * (consumer). A single-process app doesn't need real Android service binding/IPC for this.
 */
object TrackingSessionState {
    private val _snapshot = MutableStateFlow(TrackingSnapshot())
    val snapshot: StateFlow<TrackingSnapshot> = _snapshot.asStateFlow()

    fun update(transform: (TrackingSnapshot) -> TrackingSnapshot) {
        _snapshot.value = transform(_snapshot.value)
    }

    fun reset() {
        _snapshot.value = TrackingSnapshot()
    }
}
