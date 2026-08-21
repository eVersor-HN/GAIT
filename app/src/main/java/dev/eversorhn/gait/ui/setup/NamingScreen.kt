package dev.eversorhn.gait.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import dev.eversorhn.gait.ui.theme.Brass
import dev.eversorhn.gait.ui.theme.BrassDim
import dev.eversorhn.gait.ui.theme.ButtonKind
import dev.eversorhn.gait.ui.theme.CorpoButton
import dev.eversorhn.gait.ui.theme.CorpoChip
import dev.eversorhn.gait.ui.theme.CorpoPanel
import dev.eversorhn.gait.ui.theme.FootNote
import dev.eversorhn.gait.ui.theme.Quote
import dev.eversorhn.gait.ui.theme.ScreenTitle
import dev.eversorhn.gait.ui.theme.SectionLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NamingScreen(onConfirmed: () -> Unit) {
    val viewModel: NamingViewModel = gaitViewModel()
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.saved) {
        if (state.saved) onConfirmed()
    }

    val persona = Personas.byKey(state.selectedPersonaKey)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FootNote("Setup · step 2/2")
        ScreenTitle("Twin identity assignment", "Who should want to beat you?")
        Text(
            "The name and voice you pick appear in every forecast, live comparison, and message. " +
                "Name it after someone you never want to see win again — or after the version of you that already does.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.twinName,
            onValueChange = viewModel::updateName,
            label = { Text("TWIN NAME", style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            textStyle = MaterialTheme.typography.titleLarge,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Brass,
                unfocusedBorderColor = BrassDim,
                cursorColor = Brass,
                focusedLabelColor = Brass,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        SectionLabel("Persona · voice")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Personas.mvpRoster.forEach { p ->
                CorpoChip(label = p.label, active = state.selectedPersonaKey == p.key, onClick = { viewModel.selectPersona(p.key) })
            }
            CorpoChip(label = "+12 in v1.1", active = false, onClick = {})
        }

        // A taste of the voice before committing: one line each from the quiet and the cruel end.
        CorpoPanel {
            SectionLabel("How ${persona.label} sounds")
            Quote(persona.cowedLines.first(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Quote(persona.predatoryLines.first(), color = MaterialTheme.colorScheme.onSurface)
            FootNote("Cowed · Predatory — tone follows how you're actually doing")
        }

        CorpoButton(
            text = "Confirm",
            onClick = viewModel::confirm,
            enabled = state.twinName.isNotBlank(),
            kind = ButtonKind.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
        )
        FootNote("5 personas available in v1 · name & voice changeable in Settings")
    }
}
