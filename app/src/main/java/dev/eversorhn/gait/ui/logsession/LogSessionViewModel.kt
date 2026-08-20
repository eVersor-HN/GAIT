package dev.eversorhn.gait.ui.logsession

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.session.DebriefResult
import dev.eversorhn.gait.domain.session.SessionFinalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LogSessionUiState(
    val distanceKm: String = "",
    val durationMinutes: String = "",
    val submitting: Boolean = false,
    val result: DebriefResult? = null,
)

class LogSessionViewModel(
    private val repository: GaitRepository,
    private val appContext: Context,
) : ViewModel() {

    private val finalizer = SessionFinalizer(repository, appContext)

    private val _uiState = MutableStateFlow(LogSessionUiState())
    val uiState: StateFlow<LogSessionUiState> = _uiState.asStateFlow()

    fun updateDistance(value: String) {
        _uiState.value = _uiState.value.copy(distanceKm = value)
    }

    fun updateDuration(value: String) {
        _uiState.value = _uiState.value.copy(durationMinutes = value)
    }

    fun reset() {
        _uiState.value = LogSessionUiState()
    }

    fun submit() {
        val distanceKm = _uiState.value.distanceKm.toDoubleOrNull() ?: return
        val durationMinutes = _uiState.value.durationMinutes.toDoubleOrNull() ?: return
        if (distanceKm <= 0.0 || durationMinutes <= 0.0) return

        _uiState.value = _uiState.value.copy(submitting = true)

        viewModelScope.launch {
            val result = finalizer.finalize(
                distanceMeters = distanceKm * 1000.0,
                durationSeconds = (durationMinutes * 60).toInt(),
            )
            _uiState.value = _uiState.value.copy(submitting = false, result = result)
        }
    }
}
