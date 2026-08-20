package dev.eversorhn.gait.ui.simulation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.persona.Persona
import dev.eversorhn.gait.domain.persona.Personas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * A wholly fake, animated session for showing the app off -- nothing here ever touches
 * SessionDao or TwinProfileDao. See docs/simulation-mode.md.
 */
data class SimulationUiState(
    val persona: Persona = Personas.hatedPerson,
    val twinName: String = Personas.hatedPerson.defaultName,
    val finished: Boolean = false,
    val resultLine: String? = null,
)

private const val SIM_TOTAL_SECONDS = 1500 // a simulated 25:00
private const val YOUR_PACE_SEC_PER_KM = 300.0 // 5:00/km
private const val TWIN_PACE_SEC_PER_KM = 315.0 // 5:15/km

class SimulationViewModel(private val repository: GaitRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SimulationUiState())
    val uiState: StateFlow<SimulationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = repository.getTwinProfile()
            if (profile != null) {
                _uiState.value = _uiState.value.copy(
                    persona = Personas.byKey(profile.personaKey),
                    twinName = profile.twinName,
                )
            }
        }
    }

    fun finish() {
        if (_uiState.value.finished) return
        val line = _uiState.value.persona.cowedLines.random(Random)
        _uiState.value = _uiState.value.copy(finished = true, resultLine = line)
    }

    fun restart() {
        _uiState.value = _uiState.value.copy(finished = false, resultLine = null)
    }

    companion object {
        const val TOTAL_SECONDS = SIM_TOTAL_SECONDS
        fun yourDistanceKm(elapsedSeconds: Int) = elapsedSeconds / YOUR_PACE_SEC_PER_KM
        fun twinDistanceKm(elapsedSeconds: Int) = elapsedSeconds / TWIN_PACE_SEC_PER_KM
    }
}
