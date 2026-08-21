package dev.eversorhn.gait.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.R
import dev.eversorhn.gait.domain.horde.HordeIntensity
import dev.eversorhn.gait.domain.persona.Personas
import dev.eversorhn.gait.ui.gaitViewModel
import dev.eversorhn.gait.ui.theme.ScreenTitle
import dev.eversorhn.gait.ui.theme.CorpoButton
import dev.eversorhn.gait.ui.theme.CorpoChip
import dev.eversorhn.gait.ui.theme.ButtonKind
import dev.eversorhn.gait.ui.theme.CorpoPanel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onDone: () -> Unit, onWiped: () -> Unit) {
    val viewModel: SettingsViewModel = gaitViewModel()
    val state by viewModel.uiState.collectAsState()
    var confirmSwitch by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }

    LaunchedEffect(state.wiped) { if (state.wiped) onWiped() }
    LaunchedEffect(state.savedTick) { if (state.savedTick > 0) showSaved = true }

    if (confirmSwitch) {
        AlertDialog(
            onDismissRequest = { confirmSwitch = false },
            title = { Text(if (state.isHorde) "Switch to a Rival Twin?" else "Release the Horde?") },
            text = {
                Text(
                    "This is a new opponent: ${if (state.isHorde) "Fidelity and Generation" else "Proximity and Wave"} " +
                        "start over at 50% / 1. Your session history is untouched — it's still yours."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSwitch = false
                    viewModel.switchOpponentType()
                }) { Text("SWITCH") }
            },
            dismissButton = { TextButton(onClick = { confirmSwitch = false }) { Text("CANCEL") } },
        )
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Erase everything?") },
            text = {
                Text(
                    "Deletes all ${state.sessionCount} sessions and your opponent, and sends you back to setup. " +
                        "There is no undo and nothing is backed up anywhere."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    viewModel.wipeEverything()
                }) { Text("ERASE ALL") }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("CANCEL") } },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Settings", "Who's coming after you")

        if (!state.loaded) {
            Text("Loading…", style = MaterialTheme.typography.bodyLarge)
            return@Column
        }

        CorpoPanel {
            Text(
                if (state.isHorde) "ZOMBIE HORDE" else "RIVAL TWIN",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "${if (state.isHorde) "Wave" else "Generation"} ${state.generation} · " +
                    "${if (state.isHorde) "Proximity" else "Fidelity"} ${state.metricPercent}% · " +
                    pluralStringResource(R.plurals.sessions_count, state.sessionCount, state.sessionCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.isHorde) {
            Text("Intensity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HordeIntensity.all.forEach { key ->
                    CorpoChip(label = HordeIntensity.label(key), active = state.hordeIntensity == key, onClick = { viewModel.selectIntensity(key) })
                }
            }
        } else {
            Text("Voice", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Personas.mvpRoster.forEach { persona ->
                    CorpoChip(label = persona.label, active = state.personaKey == persona.key, onClick = { viewModel.selectPersona(persona.key) })
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
        }

        CorpoButton("Save changes", onClick = viewModel::save, kind = ButtonKind.PRIMARY, modifier = Modifier.fillMaxWidth())
        if (showSaved) {
            Text("Saved.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }

        CorpoButton(if (state.isHorde) "Switch to Rival Twin" else "Switch to Zombie Horde", onClick = { confirmSwitch = true }, kind = ButtonKind.SAFE, modifier = Modifier.fillMaxWidth())

        Text(
            "Danger zone",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
        CorpoButton("Erase all data", onClick = { confirmWipe = true }, kind = ButtonKind.RISK, modifier = Modifier.fillMaxWidth())

        CorpoButton("Back", onClick = onDone, kind = ButtonKind.GHOST, modifier = Modifier.fillMaxWidth())
    }
}
