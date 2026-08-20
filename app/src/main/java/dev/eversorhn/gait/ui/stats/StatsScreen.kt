package dev.eversorhn.gait.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.ui.gaitViewModel
import dev.eversorhn.gait.ui.theme.CorpoPanel

@Composable
fun StatsScreen(onDone: () -> Unit) {
    val viewModel: StatsViewModel = gaitViewModel()
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("STATISTICS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text("What actually happened", style = MaterialTheme.typography.headlineLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsPeriod.entries.forEach { period ->
                val active = state.period == period
                Text(
                    period.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(4.dp),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { viewModel.selectPeriod(period) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        CorpoPanel {
            Text("${state.totalSessions} sessions · ${state.totalDistanceLabel}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Avg pace ${state.avgPaceLabel} · Fidelity now ${state.fidelityPercent}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.fidelityTrend.size >= 2) {
                Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                    val points = state.fidelityTrend
                    val stepX = size.width / (points.size - 1)
                    for (i in 0 until points.size - 1) {
                        val x1 = i * stepX
                        val y1 = size.height * (1 - points[i])
                        val x2 = (i + 1) * stepX
                        val y2 = size.height * (1 - points[i + 1])
                        drawLine(
                            color = androidx.compose.ui.graphics.Color(0xFFC9A227),
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 3f,
                        )
                    }
                }
            }
        }

        if (state.loaded && state.rows.isEmpty()) {
            Text(
                "No sessions in this period.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.rows, key = { it.id }) { row ->
                CorpoPanel {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(row.dateLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!row.isVerified) {
                            Text("MANUAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Text("${row.distanceLabel} · ${row.paceLabel}", style = MaterialTheme.typography.bodyLarge)
                    if (row.deltaLabel != null) {
                        Text(
                            row.deltaLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (row.deltaIsGood) dev.eversorhn.gait.ui.theme.Good else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("BACK")
        }
    }
}
