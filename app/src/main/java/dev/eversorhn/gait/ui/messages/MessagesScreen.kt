package dev.eversorhn.gait.ui.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.data.db.entity.MessageKind
import dev.eversorhn.gait.domain.composure.ComposureState
import dev.eversorhn.gait.ui.forecast.composureTag
import dev.eversorhn.gait.ui.gaitViewModel
import dev.eversorhn.gait.ui.theme.Alert
import dev.eversorhn.gait.ui.theme.Brass
import dev.eversorhn.gait.ui.theme.ButtonKind
import dev.eversorhn.gait.ui.theme.CorpoButton
import dev.eversorhn.gait.ui.theme.CorpoPanel
import dev.eversorhn.gait.ui.theme.Cyan
import dev.eversorhn.gait.ui.theme.FootNote
import dev.eversorhn.gait.ui.theme.Good
import dev.eversorhn.gait.ui.theme.MessageCard
import dev.eversorhn.gait.ui.theme.MessageTone
import dev.eversorhn.gait.ui.theme.ScreenTitle
import dev.eversorhn.gait.ui.theme.StatTile
import dev.eversorhn.gait.ui.theme.TextFaint

/**
 * The demo's "Same Twin. Same Week." message log, now a real inbox: Debrief lines and
 * unprompted messages on one timeline, each tagged with when, in what mood, and — for a
 * scored round — who took the points.
 */
@Composable
fun MessagesScreen(onDone: () -> Unit) {
    val viewModel: MessagesViewModel = gaitViewModel()
    val state by viewModel.uiState.collectAsState()
    val twinColor = if (state.isHorde) Alert else Cyan

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Direct channel", if (state.isHorde) "Everything you've heard" else "Same twin. Same weeks.")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Messages", "${state.rows.size}", sub = "${state.unpromptedCount} unprompted")
            StatTile(if (state.isHorde) "Fallen back" else "Cowed", "${state.cowedCount}", accent = Good)
            StatTile(if (state.isHorde) "Swarming" else "Predatory", "${state.predatoryCount}", accent = Alert)
        }

        if (state.loaded && state.rows.isEmpty()) {
            CorpoPanel {
                Text(
                    if (state.isHorde) "Nothing yet. Finish a session and listen." else "Nothing yet. ${state.opponentName} speaks after your first session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.rows, key = { it.key }) { row ->
                val tone = when {
                    row.kind == MessageKind.COMMENDATION -> MessageTone.COWED
                    row.duelWon == true -> MessageTone.TWIN
                    row.kind == MessageKind.STAKE -> MessageTone.TWIN
                    row.state == ComposureState.PREDATORY -> MessageTone.PREDATORY
                    row.state == ComposureState.COWED -> MessageTone.COWED
                    else -> MessageTone.WATCHFUL
                }
                val tag = row.dateLabel + " · " + when {
                    row.duelWon == true -> "handoff"
                    row.duelWon == false -> "duel lost"
                    row.kind == MessageKind.COMMENDATION -> "division · commendation"
                    row.kind == MessageKind.STAKE -> "stake"
                    row.kind == MessageKind.CALL -> "stake called"
                    row.kind == MessageKind.IDLE -> "unprompted"
                    row.kind == MessageKind.GAP -> "you went quiet"
                    row.state != null -> composureTag(row.state, state.isHorde)
                    else -> row.kind
                }
                MessageCard(
                    from = if (row.kind == MessageKind.COMMENDATION) "Asset Performance Division" else if (state.isHorde) "The Horde" else state.opponentName,
                    tag = tag,
                    body = row.line,
                    tone = tone,
                    footer = row.roundToUser?.let { toUser ->
                        {
                            Text(
                                (if (toUser) "Round to you · +${row.stake}" else "Round to ${if (state.isHorde) "the horde" else state.opponentName} · +${row.stake}").uppercase() + if (row.stake == 1) " PT" else " PTS",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (toUser) Brass else twinColor,
                            )
                        }
                    },
                )
            }
        }

        FootNote("Predatory lines also arrive as notifications · stakes are made when you open the forecast", color = TextFaint)
        CorpoButton("Back", onClick = onDone, kind = ButtonKind.GHOST, modifier = Modifier.fillMaxWidth())
    }
}
