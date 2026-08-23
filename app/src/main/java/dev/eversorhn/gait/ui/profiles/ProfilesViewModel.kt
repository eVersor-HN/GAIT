package dev.eversorhn.gait.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.db.entity.OpponentType
import dev.eversorhn.gait.data.db.entity.TwinProfileEntity
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.ledger.Ledger
import dev.eversorhn.gait.domain.ledger.Side
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class ProfileRowState(
    val profile: TwinProfileEntity,
    val active: Boolean,
    val rounds: Int,
    val sessions: Int,
    val metricPercent: Int,
    val standing: String,
    val leadPositive: Boolean,
    val leadNegative: Boolean,
    val lastLabel: String,
)

data class ProfilesUiState(val loaded: Boolean = false, val profiles: List<ProfileRowState> = emptyList())

class ProfilesViewModel(private val repository: GaitRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfilesUiState())
    val uiState: StateFlow<ProfilesUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val rows = repository.listProfiles().map { p ->
                val sessions = repository.getSessions(p.id)
                val ledger = Ledger.from(sessions)
                val last = sessions.firstOrNull()?.startTimeEpochMillis
                val days = last?.let { TimeUnit.MILLISECONDS.toDays(now - it) }
                ProfileRowState(
                    profile = p,
                    active = p.id == repository.activeProfileId,
                    rounds = ledger.roundsPlayed,
                    sessions = sessions.size,
                    metricPercent = (p.fidelity * 100).toInt(),
                    standing = when {
                        ledger.roundsPlayed == 0 -> "no rounds"
                        ledger.leader == Side.USER -> "+${ledger.lead}"
                        ledger.leader == Side.TWIN -> "−${-ledger.lead}"
                        else -> "level"
                    },
                    leadPositive = ledger.lead > 0,
                    leadNegative = ledger.lead < 0,
                    lastLabel = when {
                        days == null -> "—"
                        days == 0L -> "today"
                        days == 1L -> "1 d"
                        else -> "$days d"
                    },
                )
            }
            _uiState.value = ProfilesUiState(loaded = true, profiles = rows)
        }
    }

    fun select(id: Long, then: () -> Unit) {
        viewModelScope.launch {
            repository.selectProfile(id)
            refresh()
            then()
        }
    }

    fun delete(profile: TwinProfileEntity) {
        viewModelScope.launch {
            repository.deleteProfile(profile)
            refresh()
        }
    }

    /** True when the opponent of [profile] is a horde — used for the row's wording. */
    fun isHorde(profile: TwinProfileEntity) = profile.opponentType == OpponentType.HORDE
}
