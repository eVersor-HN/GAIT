package dev.eversorhn.gait.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.domain.horde.HordeIntensity
import dev.eversorhn.gait.ui.gaitViewModel
import dev.eversorhn.gait.ui.theme.CorpoPanel

@Composable
fun HordeSetupScreen(onConfirmed: () -> Unit) {
    val viewModel: HordeSetupViewModel = gaitViewModel()
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
        Text("Where the horde comes from", style = MaterialTheme.typography.headlineLarge)

        CorpoPanel {
            Text(
                "Every Twin that loses its Decommission Trial doesn't get deleted. GAIT recycles it — " +
                    "folds it into a shared, anonymous pool of failed prediction units. No name, no voice " +
                    "left. Just distance, and the sound of it not stopping.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Text(
            "Intensity",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HordeIntensity.all.forEach { key ->
                FilterChip(
                    selected = state.intensityKey == key,
                    onClick = { viewModel.selectIntensity(key) },
                    label = { Text(HordeIntensity.label(key)) },
                )
            }
        }
        Text(
            "Only changes how the horde sounds when it's closing in — everything else about it stays the same.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(onClick = viewModel::confirm, modifier = Modifier.fillMaxWidth()) {
            Text("CONFIRM")
        }
    }
}
