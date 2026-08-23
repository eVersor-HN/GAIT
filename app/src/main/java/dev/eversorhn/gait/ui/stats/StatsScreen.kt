package dev.eversorhn.gait.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.R
import dev.eversorhn.gait.ui.gaitViewModel
import dev.eversorhn.gait.ui.theme.ScreenTitle
import dev.eversorhn.gait.ui.theme.CorpoButton
import dev.eversorhn.gait.ui.theme.ButtonKind
import dev.eversorhn.gait.ui.theme.Brass
import dev.eversorhn.gait.ui.theme.CorpoPanel
import dev.eversorhn.gait.ui.theme.Good
import dev.eversorhn.gait.ui.theme.Alert
import dev.eversorhn.gait.ui.theme.Cyan
import dev.eversorhn.gait.ui.theme.FootNote
import dev.eversorhn.gait.ui.theme.SectionLabel
import dev.eversorhn.gait.ui.theme.TextFaint

@Composable
fun StatsScreen(onDone: () -> Unit) {
    val viewModel: StatsViewModel = gaitViewModel()
    val state by viewModel.uiState.collectAsState()
    var pendingDelete by remember { mutableStateOf<SessionRow?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refresh() }

    pendingDelete?.let { row ->
        dev.eversorhn.gait.ui.theme.CorpoDialog(
            title = "Delete this session?",
            body = "${row.dateLabel} · ${row.distanceLabel} · ${row.paceLabel}. It leaves the ledger and the forecast history. This can't be undone.",
            onDismiss = { pendingDelete = null },
            confirmText = "Delete",
            onConfirm = { viewModel.deleteSession(row.id); pendingDelete = null },
            confirmKind = ButtonKind.RISK,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Statistics", "What actually happened")

        dev.eversorhn.gait.ui.theme.Segmented(
            options = StatsPeriod.entries.map { it.label },
            selected = StatsPeriod.entries.indexOf(state.period),
            onSelect = { viewModel.selectPeriod(StatsPeriod.entries[it]) },
        )

        // --- Ledger for the period: totals and who owns which weekday ---
        if (state.roundsPlayed > 0) {
            val twinColor = if (state.isHorde) Alert else Cyan
            CorpoPanel {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionLabel("Asset ledger · ${state.period.label}")
                    FootNote("${state.roundsPlayed} rounds")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("${state.userPoints}", style = MaterialTheme.typography.headlineLarge, color = Brass)
                    Text("—", style = MaterialTheme.typography.headlineLarge, color = TextFaint)
                    Text("${state.twinPoints}", style = MaterialTheme.typography.headlineLarge, color = twinColor)
                }
                FootNote(state.standing, color = TextFaint)
                if (state.weekdayRecord.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.weekdayRecord.forEach { (d, u, t) ->
                            Column(modifier = Modifier.weight(1f)) {
                                FootNote(java.time.DayOfWeek.of(d).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH))
                                Text(
                                    "$u–$t",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = when {
                                        u > t -> Brass
                                        t > u -> twinColor
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
                state.ownershipLine?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) }
            }
        }

        CorpoPanel {
            Text(
                "${pluralStringResource(R.plurals.sessions_count, state.totalSessions, state.totalSessions)} · ${state.totalDistanceLabel}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Avg ${dev.eversorhn.gait.domain.activity.Activities.paceWord((androidx.compose.ui.platform.LocalContext.current.applicationContext as dev.eversorhn.gait.GaitApplication).repository.activeActivityType).lowercase()} ${state.avgPaceLabel} · ${state.metricLabel} now ${state.metricPercent}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.accuracyTrend.size >= 2) {
                Text(
                    "FORECAST ACCURACY PER SESSION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                    val points = state.accuracyTrend
                    val stepX = size.width / (points.size - 1)
                    for (i in 0 until points.size - 1) {
                        drawLine(
                            color = Brass,
                            start = Offset(i * stepX, size.height * (1 - points[i])),
                            end = Offset((i + 1) * stepX, size.height * (1 - points[i + 1])),
                            strokeWidth = 3f,
                        )
                    }
                }
            }
        }

        if (state.loaded && state.rows.isEmpty()) {
            CorpoPanel {
                Text("No sessions in this period.", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (state.period == StatsPeriod.ALL) "Every recorded or logged session lands here with its forecast, what you actually did, and who took the round. Tap a row to delete it."
                    else "Switch to ALL to see everything on file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.rows, key = { it.id }) { row ->
                CorpoPanel {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(row.dateLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (row.isRestDay) {
                                Text("REST DAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            }
                            if (!row.isVerified) {
                                Text("MANUAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Text("${row.distanceLabel} · ${row.paceLabel}", style = MaterialTheme.typography.bodyLarge)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            row.deltaLabel ?: "no forecast yet",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (row.deltaLabel != null && row.deltaIsGood) Good else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "DELETE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { pendingDelete = row },
                        )
                    }
                }
            }
        }

        if (state.transmissions.isNotEmpty()) {
            dev.eversorhn.gait.ui.theme.CollapsiblePanel(
                title = "Round record",
                summary = state.transmissions.first().second,
            ) {
                state.transmissions.forEach { (date, line) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FootNote(date, maxLines = 1)
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = dev.eversorhn.gait.ui.theme.TextDim, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        FootNote("Swipe back for Analysis and Forecast", color = dev.eversorhn.gait.ui.theme.TextFaint)
    }
}
