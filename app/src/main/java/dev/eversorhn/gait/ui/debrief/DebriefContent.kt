package dev.eversorhn.gait.ui.debrief

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.data.db.entity.SessionSource
import dev.eversorhn.gait.domain.composure.ComposureState
import dev.eversorhn.gait.domain.session.DebriefResult

/** Shared by both entry points into a finished session: manual logging and GPS tracking. */
@Composable
fun DebriefContent(result: DebriefResult, onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("DEBRIEF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text("Forecast vs. Actual", style = MaterialTheme.typography.headlineLarge)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (result.hadForecast) {
                Text("Forecast: ${result.forecastPaceLabel}", style = MaterialTheme.typography.bodyLarge)
                Text("Actual: ${result.actualPaceLabel}", style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    "No forecast existed yet for this session — it's now part of the baseline.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                "Fidelity now ${result.newFidelityPercent}% · Composure: ${result.composureState.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (result.dataSource == SessionSource.MANUAL) {
                Text(
                    "SELF-REPORTED · NOT GPS-VERIFIED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (result.twinLine != null) {
            val tint = when (result.composureState) {
                ComposureState.PREDATORY -> MaterialTheme.colorScheme.error
                ComposureState.COWED -> MaterialTheme.colorScheme.onSurfaceVariant
                ComposureState.WATCHFUL -> MaterialTheme.colorScheme.onSurface
            }
            Text("“${result.twinLine}”", style = MaterialTheme.typography.bodyLarge, color = tint)
        }

        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("BACK TO FORECAST")
        }
    }
}
