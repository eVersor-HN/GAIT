package dev.eversorhn.gait.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.db.entity.SessionEntity
import dev.eversorhn.gait.data.db.entity.isHorde
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.composure.ComposureState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MessageRow(
    val id: Long,
    val dateLabel: String,
    val line: String,
    val state: ComposureState,
    val isDuel: Boolean,
    val duelWon: Boolean?,
)

data class MessagesUiState(
    val loaded: Boolean = false,
    val isHorde: Boolean = false,
    val opponentName: String = "",
    val generation: Int = 1,
    val rows: List<MessageRow> = emptyList(),
    val cowedCount: Int = 0,
    val predatoryCount: Int = 0,
)

/** The Direct Channel: everything the opponent has said, newest first, straight from the session log. */
class MessagesViewModel(private val repository: GaitRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("EEE · d MMM", Locale.getDefault())

    init {
        viewModelScope.launch {
            val profile = repository.getTwinProfile()
            val sessions = repository.getSessions()
            val rows = sessions.mapNotNull { it.toRow() }
            _uiState.value = MessagesUiState(
                loaded = true,
                isHorde = profile?.isHorde == true,
                opponentName = profile?.twinName ?: "",
                generation = profile?.generation ?: 1,
                rows = rows,
                cowedCount = rows.count { it.state == ComposureState.COWED },
                predatoryCount = rows.count { it.state == ComposureState.PREDATORY },
            )
        }
    }

    private fun SessionEntity.toRow(): MessageRow? {
        val line = twinLine ?: return null
        val state = composureState?.let { runCatching { ComposureState.valueOf(it) }.getOrNull() } ?: ComposureState.WATCHFUL
        return MessageRow(
            id = id,
            dateLabel = dateFormat.format(Date(startTimeEpochMillis)),
            line = line,
            state = state,
            isDuel = isDuel,
            duelWon = duelWon,
        )
    }
}
