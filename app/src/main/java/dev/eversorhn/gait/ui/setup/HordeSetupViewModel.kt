package dev.eversorhn.gait.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.db.entity.OpponentType
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.horde.HordeIntensity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HordeSetupUiState(
    val intensityKey: String = HordeIntensity.STANDARD,
    val saved: Boolean = false,
)

class HordeSetupViewModel(private val repository: GaitRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HordeSetupUiState())
    val uiState: StateFlow<HordeSetupUiState> = _uiState.asStateFlow()

    fun selectIntensity(key: String) {
        _uiState.value = _uiState.value.copy(intensityKey = key)
    }

    fun confirm() {
        val state = _uiState.value
        viewModelScope.launch {
            repository.createTwinProfile(
                personaKey = state.intensityKey,
                twinName = "The Horde",
                opponentType = OpponentType.HORDE,
            )
            _uiState.value = state.copy(saved = true)
        }
    }
}
