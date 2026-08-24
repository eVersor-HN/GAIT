package dev.eversorhn.gait.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.db.entity.OpponentType
import dev.eversorhn.gait.data.db.entity.isHorde
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.horde.HordeIntensity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loaded: Boolean = false,
    val isHorde: Boolean = false,
    val twinName: String = "",
    val hordeIntensity: String = HordeIntensity.STANDARD,
    val generation: Int = 1,
    val metricPercent: Int = 50,
    val sessionCount: Int = 0,
    val savedTick: Int = 0,
    /** True after a full wipe -- the nav graph sends the user back through setup. */
    val wiped: Boolean = false,
)

/**
 * Everything about the opponent that used to be locked in at setup, plus a full reset.
 * Switching opponent *type* (Twin <-> Horde) starts a fresh Wave/Generation at 50%, since
 * in the fiction it's a new opponent -- your session history stays, it's still your history.
 */
class SettingsViewModel(private val repository: GaitRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Public so loading or removing demo data can re-read the numbers it just changed. */
    fun refresh() {
        viewModelScope.launch {
            val profile = repository.getTwinProfile() ?: return@launch
            val count = repository.getSessions().size
            _uiState.value = _uiState.value.copy(
                loaded = true,
                isHorde = profile.isHorde,
                twinName = profile.twinName,
                hordeIntensity = profile.hordeIntensity ?: HordeIntensity.STANDARD,
                generation = profile.generation,
                metricPercent = (profile.fidelity * 100).toInt(),
                sessionCount = count,
            )
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(twinName = name)
    }

    fun selectIntensity(key: String) {
        _uiState.value = _uiState.value.copy(hordeIntensity = key)
    }

    /** Persist name / persona / intensity edits for the current opponent type. */
    fun save() {
        val s = _uiState.value
        viewModelScope.launch {
            val profile = repository.getTwinProfile() ?: return@launch
            val updated = if (profile.isHorde) {
                profile.copy(hordeIntensity = s.hordeIntensity)
            } else {
                profile.copy(
                    twinName = s.twinName.ifBlank { "The model" },
                    personaKey = null,
                )
            }
            repository.updateTwinProfile(updated)
            _uiState.value = s.copy(savedTick = s.savedTick + 1, twinName = updated.twinName)
        }
    }

    /** Twin <-> Horde. A new opponent: Fidelity/Proximity and Generation/Wave reset. */
    fun switchOpponentType() {
        val s = _uiState.value
        viewModelScope.launch {
            val profile = repository.getTwinProfile() ?: return@launch
            val switched = if (profile.isHorde) {
                profile.copy(
                    opponentType = OpponentType.TWIN,
                    personaKey = null,
                    hordeIntensity = null,
                    twinName = "The model",
                    fidelity = 0.5f,
                    generation = 1,
                )
            } else {
                profile.copy(
                    opponentType = OpponentType.HORDE,
                    personaKey = null,
                    hordeIntensity = s.hordeIntensity,
                    twinName = "The Horde",
                    fidelity = 0.5f,
                    generation = 1,
                )
            }
            repository.updateTwinProfile(switched)
            refresh()
        }
    }

    /** Everything gone: sessions and opponent. The nav graph returns to setup afterwards. */
    fun wipeEverything() {
        viewModelScope.launch {
            repository.wipeAll()
            _uiState.value = _uiState.value.copy(wiped = true)
        }
    }
}
