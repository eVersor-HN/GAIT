package dev.eversorhn.gait.ui.logsession

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
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
import dev.eversorhn.gait.ui.theme.ButtonKind
import dev.eversorhn.gait.ui.theme.CorpoButton
import dev.eversorhn.gait.ui.theme.FootNote
import dev.eversorhn.gait.ui.theme.ScreenTitle

@Composable
fun LogSessionScreen(onDone: () -> Unit) {
    val viewModel: LogSessionViewModel = gaitViewModel()
    val state by viewModel.uiState.collectAsState()

    // Back with figures in the fields would throw the session away without a word.
    dev.eversorhn.gait.ui.theme.DiscardGuard(
        enabled = state.result == null && (state.distanceKm.isNotBlank() || state.durationMinutes.isNotBlank()),
        title = "Discard this session?",
        body = "It has not been submitted — the distance and duration you entered are lost.",
        onDiscard = onDone,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val result = state.result
        if (result == null) {
            ScreenTitle("Log session", "What did you actually do?")
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

            CorpoButton(
                text = if (state.submitting) "Saving…" else "Submit",
                onClick = viewModel::submit,
                enabled = !state.submitting,
                kind = ButtonKind.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
            )
            CorpoButton("Back", onClick = onDone, kind = ButtonKind.GHOST, modifier = Modifier.fillMaxWidth())
            FootNote("Self-reported sessions are tagged · not GPS-verified")
        } else {
            DebriefContent(result = result, onDone = {
                viewModel.reset()
                onDone()
            })
        }
    }
}
