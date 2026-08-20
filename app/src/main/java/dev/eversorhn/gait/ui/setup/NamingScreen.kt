package dev.eversorhn.gait.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.domain.persona.Personas
import dev.eversorhn.gait.ui.gaitViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NamingScreen(onConfirmed: () -> Unit) {
    val viewModel: NamingViewModel = gaitViewModel()
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.saved) {
        if (state.saved) onConfirmed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("SETUP · STEP 2/2", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text("Who should want to beat you?", style = MaterialTheme.typography.headlineLarge)
        Text(
            "The name and voice you pick appears in every forecast, live comparison, and message.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Personas.mvpRoster.forEach { persona ->
                FilterChip(
                    selected = state.selectedPersonaKey == persona.key,
                    onClick = { viewModel.selectPersona(persona.key) },
                    label = { Text(persona.label) },
                )
            }
        }

        OutlinedTextField(
            value = state.twinName,
            onValueChange = viewModel::updateName,
            label = { Text("Twin name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(onClick = viewModel::confirm, modifier = Modifier.fillMaxWidth()) {
            Text("CONFIRM")
        }
    }
}
