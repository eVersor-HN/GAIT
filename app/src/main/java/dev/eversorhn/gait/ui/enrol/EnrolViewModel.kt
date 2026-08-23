package dev.eversorhn.gait.ui.enrol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.db.entity.OpponentType
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.activity.Activities
import dev.eversorhn.gait.domain.horde.HordeIntensity
import dev.eversorhn.gait.domain.persona.Personas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EnrolUiState(
    val activityKey: String = Activities.RUNNING.key,
    val opponentType: String = OpponentType.TWIN,
    val personaKey: String = Personas.hatedPerson.key,
    val twinName: String = Personas.hatedPerson.defaultName,
    val hordeIntensity: String = HordeIntensity.STANDARD,
    val profileName: String = "",
    val createdId: Long? = null,
) {
    val canCreate: Boolean get() = opponentType == OpponentType.HORDE || twinName.isNotBlank()
}

class EnrolViewModel(private val repository: GaitRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(EnrolUiState(profileName = Activities.RUNNING.label))
    val uiState: StateFlow<EnrolUiState> = _uiState.asStateFlow()

    /** The enrolment name follows the activity until the user types their own. */
    private var nameTouched = false

    fun selectActivity(key: String) {
        _uiState.value = _uiState.value.copy(
            activityKey = key,
            profileName = if (nameTouched) _uiState.value.profileName else Activities.byKey(key).label,
        )
    }

    fun selectOpponent(type: String) { _uiState.value = _uiState.value.copy(opponentType = type) }

    fun selectPersona(key: String) {
        val p = Personas.byKey(key)
        val s = _uiState.value
        // Adopting a persona also adopts its default name, unless the user already typed one.
        val keepName = s.twinName.isNotBlank() && Personas.mvpRoster.none { it.defaultName == s.twinName }
        _uiState.value = s.copy(personaKey = key, twinName = if (keepName) s.twinName else p.defaultName)
    }

    fun setTwinName(name: String) { _uiState.value = _uiState.value.copy(twinName = name) }

    fun selectIntensity(key: String) { _uiState.value = _uiState.value.copy(hordeIntensity = key) }

    fun setProfileName(name: String) {
        nameTouched = true
        _uiState.value = _uiState.value.copy(profileName = name)
    }

    fun create() {
        val s = _uiState.value
        if (!s.canCreate) return
        viewModelScope.launch {
            val horde = s.opponentType == OpponentType.HORDE
            val id = repository.createProfile(
                profileName = s.profileName.ifBlank { Activities.byKey(s.activityKey).label },
                activityType = s.activityKey,
                opponentType = s.opponentType,
                personaKey = if (horde) null else s.personaKey,
                hordeIntensity = if (horde) s.hordeIntensity else null,
                opponentName = if (horde) "The Horde" else s.twinName,
            )
            _uiState.value = _uiState.value.copy(createdId = id)
        }
    }
}
