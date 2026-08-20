package dev.eversorhn.gait.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TrackingSnapshot(
    val isTracking: Boolean = false,
    val startEpochMillis: Long? = null,
    val elapsedSeconds: Int = 0,
    val distanceMeters: Double = 0.0,
    val currentPaceSecPerKm: Double? = null,
    val gpsFixCount: Int = 0,
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
