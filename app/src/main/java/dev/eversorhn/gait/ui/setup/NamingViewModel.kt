package dev.eversorhn.gait.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.persona.Personas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NamingUiState(
    val selectedPersonaKey: String = Personas.hatedPerson.key,
    val twinName: String = Personas.hatedPerson.defaultName,
    val saved: Boolean = false,
)

class NamingViewModel(private val repository: GaitRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(NamingUiState())
    val uiState: StateFlow<NamingUiState> = _uiState.asStateFlow()

    fun selectPersona(key: String) {
        val persona = Personas.byKey(key)
        _uiState.value = _uiState.value.copy(selectedPersonaKey = key, twinName = persona.defaultName)
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(twinName = name)
    }

    fun confirm() {
        val state = _uiState.value
        viewModelScope.launch {
            repository.createTwinProfile(
                personaKey = state.selectedPersonaKey,
                twinName = state.twinName.ifBlank { Personas.byKey(state.selectedPersonaKey).defaultName },
            )
            _uiState.value = state.copy(saved = true)
        }
    }
}
