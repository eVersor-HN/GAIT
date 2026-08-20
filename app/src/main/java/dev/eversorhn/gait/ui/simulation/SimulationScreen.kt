package dev.eversorhn.gait.ui.simulation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.ui.gaitViewModel
import dev.eversorhn.gait.ui.theme.CorpoPanel

@Composable
fun SimulationScreen(onDone: () -> Unit) {
    val viewModel: SimulationViewModel = gaitViewModel()
    val state by viewModel.uiState.collectAsState()
    val progress = remember { Animatable(0f) }

    LaunchedEffect(state.finished) {
        if (!state.finished) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(durationMillis = 16_000, easing = LinearEasing))
            viewModel.finish()
        }
    }

    val elapsedSeconds = (progress.value * SimulationViewModel.TOTAL_SECONDS).toInt()
    val yourKm = SimulationViewModel.yourDistanceKm(elapsedSeconds)
    val twinKm = SimulationViewModel.twinDistanceKm(elapsedSeconds)
    val gapMeters = ((yourKm - twinKm) * 1000).toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "SIMULATION — NOTHING IS SAVED",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )

        if (!state.finished) {
            Text("DEMO SESSION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(formatElapsed(elapsedSeconds), style = MaterialTheme.typography.headlineLarge)

            CorpoPanel {
                Text(
                    "${"%.2f".format(yourKm)} km · you",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "${"%.2f".format(twinKm)} km · ${state.twinName}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    if (gapMeters >= 0) "+$gapMeters m ahead" else "$gapMeters m behind",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("SKIP")
            }
        } else {
            Text("DEMO COMPLETE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text("This is what a session feels like", style = MaterialTheme.typography.headlineLarge)

            CorpoPanel {
                Text("Finished 5.00 km · 25:00 · pace 5:00/km", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Beat ${state.twinName} by ${((yourKm - twinKm) * 1000).toInt()} m",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.resultLine != null) {
                Text("“${state.resultLine}”", style = MaterialTheme.typography.bodyLarge)
            }

            Text(
                "Nothing here was recorded — Fidelity, Composure, and your real session history are untouched.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(onClick = { viewModel.restart() }, modifier = Modifier.fillMaxWidth()) {
                Text("RUN AGAIN")
            }
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("DONE")
            }
        }
    }
}

private fun formatElapsed(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}
