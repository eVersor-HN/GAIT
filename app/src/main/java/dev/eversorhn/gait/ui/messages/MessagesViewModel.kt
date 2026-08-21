package dev.eversorhn.gait.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.eversorhn.gait.data.db.entity.MessageKind
import dev.eversorhn.gait.data.db.entity.isHorde
import dev.eversorhn.gait.data.repository.GaitRepository
import dev.eversorhn.gait.domain.composure.ComposureState
import dev.eversorhn.gait.domain.ledger.Ledger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MessageRow(
    val key: String,
    val epochMillis: Long,
    val dateLabel: String,
    val line: String,
    val state: ComposureState?,
    /** "debrief" for a session line, else one of [MessageKind]. */
    val kind: String,
    val isDuel: Boolean = false,
    val duelWon: Boolean? = null,
    /** For debrief lines: who took the round, and for how much. */
    val roundToUser: Boolean? = null,
    val stake: Int = 1,
)

data class MessagesUiState(
    val loaded: Boolean = false,
    val isHorde: Boolean = false,
    val opponentName: String = "",
    val generation: Int = 1,
    val rows: List<MessageRow> = emptyList(),
    val cowedCount: Int = 0,
    val predatoryCount: Int = 0,
    val unpromptedCount: Int = 0,
)

/**
 * The Direct Channel: everything the opponent has ever said, newest first — Debrief lines
 * from the session log *and* the unprompted ones (idle taunts, gap-predatory pings, stakes,
 * call reactions) from the inbox table. One timeline, so it reads like a person who keeps
 * writing, not a list of post-run summaries.
 */
class MessagesViewModel(private val repository: GaitRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("EEE · d MMM", Locale.getDefault())

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val profile = repository.getTwinProfile()
            val sessions = repository.getSessions()
            val messages = repository.getMessages()

            val fromSessions = sessions.mapNotNull { s ->
                val line = s.twinLine ?: return@mapNotNull null
                MessageRow(
                    key = "s${s.id}",
                    epochMillis = s.startTimeEpochMillis,
                    dateLabel = dateFormat.format(Date(s.startTimeEpochMillis)),
                    line = line,
                    state = s.composureState?.let { runCatching { ComposureState.valueOf(it) }.getOrNull() },
                    kind = "debrief",
                    isDuel = s.isDuel,
                    duelWon = s.duelWon,
                    roundToUser = Ledger.winnerOf(s)?.let { it == dev.eversorhn.gait.domain.ledger.Side.USER },
                    stake = s.stake,
                )
            }
            val fromInbox = messages.map { m ->
                MessageRow(
                    key = "m${m.id}",
                    epochMillis = m.epochMillis,
                    dateLabel = dateFormat.format(Date(m.epochMillis)),
                    line = m.line,
                    state = m.composureState?.let { runCatching { ComposureState.valueOf(it) }.getOrNull() },
                    kind = m.kind,
                )
            }
            val rows = (fromSessions + fromInbox).sortedByDescending { it.epochMillis }

            _uiState.value = MessagesUiState(
                loaded = true,
                isHorde = profile?.isHorde == true,
                opponentName = profile?.twinName ?: "",
                generation = profile?.generation ?: 1,
                rows = rows,
                cowedCount = rows.count { it.state == ComposureState.COWED },
                predatoryCount = rows.count { it.state == ComposureState.PREDATORY },
                unpromptedCount = fromInbox.size,
            )
        }
    }
}
