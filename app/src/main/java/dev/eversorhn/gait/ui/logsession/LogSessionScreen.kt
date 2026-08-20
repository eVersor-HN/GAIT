package dev.eversorhn.gait.ui.logsession

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.domain.composure.ComposureState
import dev.eversorhn.gait.ui.gaitViewModel

@Composable
fun LogSessionScreen(onDone: () -> Unit) {
    val viewModel: LogSessionViewModel = gaitViewModel()
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val result = state.result
        if (result == null) {
            Text("LOG SESSION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text("What did you actually do?", style = MaterialTheme.typography.headlineLarge)
            Text(
                "No live GPS tracking yet (see docs/scope-and-stack.md) — log it manually for now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.distanceKm,
                onValueChange = viewModel::updateDistance,
                label = { Text("Distance (km)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.durationMinutes,
                onValueChange = viewModel::updateDuration,
                label = { Text("Duration (minutes)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(onClick = viewModel::submit, enabled = !state.submitting, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.submitting) "SAVING…" else "SUBMIT")
            }
        } else {
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
                    Text("No forecast existed yet for this session — it's now part of the baseline.", style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    "Fidelity now ${result.newFidelityPercent}% · Composure: ${result.composureState.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (result.twinLine != null) {
                val tint = when (result.composureState) {
                    ComposureState.PREDATORY -> MaterialTheme.colorScheme.error
                    ComposureState.COWED -> MaterialTheme.colorScheme.onSurfaceVariant
                    ComposureState.WATCHFUL -> MaterialTheme.colorScheme.onSurface
                }
                Text("“${result.twinLine}”", style = MaterialTheme.typography.bodyLarge, color = tint)
            }

            OutlinedButton(
                onClick = {
                    viewModel.reset()
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("BACK TO FORECAST")
            }
        }
    }
}
