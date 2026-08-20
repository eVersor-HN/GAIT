package dev.eversorhn.gait.ui.track

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.db.entity.SessionSource
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.session.DebriefResult
import dev.eversorhn.gait.domain.session.SessionFinalizer
import dev.eversorhn.gait.tracking.LocationTrackingService
import dev.eversorhn.gait.tracking.TrackingSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TrackMode { OUTDOOR, INDOOR }

data class TrackUiState(
    val mode: TrackMode? = null,
    val finishing: Boolean = false,
    /** Indoor only: timer stopped, waiting for the user to type the distance off the machine. */
    val awaitingIndoorDistance: Boolean = false,
    val indoorElapsedSeconds: Int = 0,
    val indoorDistanceKm: String = "",
    val result: DebriefResult? = null,
)

/** Below this, GPS jitter alone can look like "a session" and produce a nonsense pace. */
private const val MIN_DISTANCE_TO_SAVE_METERS = 20.0

class TrackViewModel(
    private val repository: GaitRepository,
    private val appContext: Context,
) : ViewModel() {

    private val finalizer = SessionFinalizer(repository, appContext)

    val trackingSnapshot = TrackingSessionState.snapshot

    private val _uiState = MutableStateFlow(TrackUiState())
    val uiState: StateFlow<TrackUiState> = _uiState.asStateFlow()

    fun chooseMode(mode: TrackMode) {
        _uiState.value = _uiState.value.copy(mode = mode)
    }

    fun start() {
        val action = when (_uiState.value.mode) {
            TrackMode.INDOOR -> LocationTrackingService.ACTION_START_INDOOR
            else -> LocationTrackingService.ACTION_START_OUTDOOR
        }
        val intent = Intent(appContext, LocationTrackingService::class.java).setAction(action)
        appContext.startForegroundService(intent)
    }

    fun stop() {
        val snapshot = trackingSnapshot.value
        val durationSeconds = snapshot.elapsedSeconds

        appContext.startService(
            Intent(appContext, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_STOP)
        )

        if (_uiState.value.mode == TrackMode.INDOOR) {
            if (durationSeconds <= 0) return
            // No GPS distance to fall back on -- ask for what the machine showed.
            _uiState.value = _uiState.value.copy(awaitingIndoorDistance = true, indoorElapsedSeconds = durationSeconds)
            return
        }

        val distanceMeters = snapshot.distanceMeters
        if (distanceMeters < MIN_DISTANCE_TO_SAVE_METERS || durationSeconds <= 0) return

        _uiState.value = _uiState.value.copy(finishing = true)
        viewModelScope.launch {
            val result = finalizer.finalize(distanceMeters, durationSeconds, dataSource = SessionSource.GPS)
            _uiState.value = _uiState.value.copy(finishing = false, result = result)
        }
    }

    fun updateIndoorDistance(value: String) {
        _uiState.value = _uiState.value.copy(indoorDistanceKm = value)
    }

    fun submitIndoorDistance() {
        val distanceKm = _uiState.value.indoorDistanceKm.toDoubleOrNull() ?: return
        if (distanceKm <= 0.0) return

        _uiState.value = _uiState.value.copy(finishing = true)
        viewModelScope.launch {
            val result = finalizer.finalize(
                distanceMeters = distanceKm * 1000.0,
                durationSeconds = _uiState.value.indoorElapsedSeconds,
                dataSource = SessionSource.MANUAL,
            )
            _uiState.value = _uiState.value.copy(finishing = false, awaitingIndoorDistance = false, result = result)
        }
    }

    fun reset() {
        TrackingSessionState.reset()
        _uiState.value = TrackUiState()
    }
}
