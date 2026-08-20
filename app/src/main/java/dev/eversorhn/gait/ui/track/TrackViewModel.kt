package dev.eversorhn.gait.ui.track

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.session.DebriefResult
import dev.eversorhn.gait.domain.session.SessionFinalizer
import dev.eversorhn.gait.tracking.LocationTrackingService
import dev.eversorhn.gait.tracking.TrackingSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TrackUiState(
    val finishing: Boolean = false,
    val result: DebriefResult? = null,
)

class TrackViewModel(
    private val repository: GaitRepository,
    private val appContext: Context,
) : ViewModel() {

    private val finalizer = SessionFinalizer(repository, appContext)

    val trackingSnapshot = TrackingSessionState.snapshot

    private val _uiState = MutableStateFlow(TrackUiState())
    val uiState: StateFlow<TrackUiState> = _uiState.asStateFlow()

    fun start() {
        val intent = Intent(appContext, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_START)
        appContext.startForegroundService(intent)
    }

    fun stop() {
        val snapshot = trackingSnapshot.value
        val distanceMeters = snapshot.distanceMeters
        val durationSeconds = snapshot.elapsedSeconds

        appContext.startService(
            Intent(appContext, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_STOP)
        )

        if (distanceMeters <= 0.0 || durationSeconds <= 0) return

        _uiState.value = _uiState.value.copy(finishing = true)
        viewModelScope.launch {
            val result = finalizer.finalize(distanceMeters, durationSeconds)
            _uiState.value = _uiState.value.copy(finishing = false, result = result)
        }
    }

    fun reset() {
        TrackingSessionState.reset()
        _uiState.value = TrackUiState()
    }
}
