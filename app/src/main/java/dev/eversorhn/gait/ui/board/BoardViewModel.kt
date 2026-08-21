package dev.eversorhn.gait.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.db.entity.isHorde
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.ledger.Ledger
import dev.eversorhn.gait.domain.roster.RosterEngine
import dev.eversorhn.gait.domain.roster.RosterSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

data class BoardUiState(
    val loaded: Boolean = false,
    val isHorde: Boolean = false,
    val opponentName: String = "",
    val proximityPercent: Int = 50,
    val snapshot: RosterSnapshot? = null,
)

/**
 * Builds the division's roster snapshot for the Asset Board (Twin) / the containment map
 * (Horde). The simulation is pure and cached in RosterEngine; this only supplies the user's
 * side of the comparison (ledger today vs. yesterday, Fidelity) and the local clock.
 */
class BoardViewModel(private val repository: GaitRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(BoardUiState())
    val uiState: StateFlow<BoardUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val profile = repository.getTwinProfile() ?: return@launch
            val sessions = repository.getSessions()
            val now = Instant.now()
            val zoned = now.atZone(ZoneId.systemDefault())
            val offset = zoned.offset.totalSeconds * 1000L
            val today = RosterEngine.epochDay(now.toEpochMilli(), offset)
            val enrolled = RosterEngine.epochDay(profile.createdAtEpochMillis, offset)
            val startOfToday = (today * 86_400_000L) - offset
            val ledger = Ledger.from(sessions)
            val ledgerYesterday = Ledger.from(sessions.filter { it.startTimeEpochMillis < startOfToday })
            val fidelity = (profile.fidelity * 100).toInt()
            val snapshot = withContext(Dispatchers.Default) {
                RosterEngine.snapshot(enrolled, today, zoned.hour * 60 + zoned.minute, ledger, fidelity, ledgerYesterday)
            }
            _uiState.value = BoardUiState(
                loaded = true,
                isHorde = profile.isHorde,
                opponentName = profile.twinName,
                proximityPercent = fidelity,
                snapshot = snapshot,
            )
        }
    }
}
