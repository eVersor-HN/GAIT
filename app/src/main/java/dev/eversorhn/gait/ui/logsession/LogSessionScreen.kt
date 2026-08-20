package dev.eversorhn.gait.ui.logsession

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.ui.debrief.DebriefContent
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
                "Prefer a real recording? Go back and use “START ACTIVITY” instead — this is the manual fallback.",
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
            DebriefContent(result = result, onDone = {
                viewModel.reset()
                onDone()
            })
        }
    }
}
