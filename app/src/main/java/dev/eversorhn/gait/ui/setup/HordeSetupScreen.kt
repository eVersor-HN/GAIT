package dev.eversorhn.gait.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.domain.horde.HordeIntensity
import dev.eversorhn.gait.domain.horde.HordeSoundCues
import dev.eversorhn.gait.ui.gaitViewModel
import dev.eversorhn.gait.ui.theme.Alert
import dev.eversorhn.gait.ui.theme.ButtonKind
import dev.eversorhn.gait.ui.theme.CorpoButton
import dev.eversorhn.gait.ui.theme.CorpoChip
import dev.eversorhn.gait.ui.theme.CorpoPanel
import dev.eversorhn.gait.ui.theme.FootNote
import dev.eversorhn.gait.ui.theme.PanelTone
import dev.eversorhn.gait.ui.theme.ScreenTitle
import dev.eversorhn.gait.ui.theme.SectionLabel

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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FootNote("Setup · step 3/3")
        ScreenTitle("Horde configuration", "Where the horde comes from")

        CorpoPanel {
            Text(
                "Every Twin that loses its Decommission Trial doesn't get deleted. GAIT recycles it — " +
                    "folds it into a shared, anonymous pool of failed prediction units. No name, no voice " +
                    "left. Just distance, and the sound of it not stopping.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionLabel("Intensity")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HordeIntensity.all.forEach { key ->
                CorpoChip(label = HordeIntensity.label(key), active = state.intensityKey == key, onClick = { viewModel.selectIntensity(key) })
            }
        }
        Text(
            "Only changes how the horde sounds when it's closing in — everything else about it stays the same.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        CorpoPanel(tone = PanelTone.WARN) {
            SectionLabel("When they're swarming", color = Alert)
            Text(
                HordeSoundCues.captionFor(dev.eversorhn.gait.domain.composure.ComposureState.PREDATORY, state.intensityKey),
                style = MaterialTheme.typography.bodyLarge,
            )
            FootNote("Proximity · wave · aggression replace fidelity · generation · composure")
        }

        CorpoButton("Confirm", onClick = viewModel::confirm, kind = ButtonKind.PRIMARY, modifier = Modifier.fillMaxWidth())
    }
}
