package dev.eversorhn.gait.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.GaitApplication
import dev.eversorhn.gait.domain.activity.Activities
import dev.eversorhn.gait.ui.theme.ButtonKind
import dev.eversorhn.gait.ui.theme.CorpoButton
import dev.eversorhn.gait.ui.theme.CorpoChip
import dev.eversorhn.gait.ui.theme.CorpoPanel
import dev.eversorhn.gait.ui.theme.FootNote
import dev.eversorhn.gait.ui.theme.ScreenTitle
import dev.eversorhn.gait.ui.theme.SectionLabel

/** Setup step 1 of 3 (the demo's "Wähle deine Sportart"): which activity this profile tracks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityScreen(onContinue: () -> Unit) {
    val app = LocalContext.current.applicationContext as GaitApplication
    var selected by rememberSaveable { mutableStateOf(app.repository.activeActivityType) }
    val activity = Activities.byKey(selected)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FootNote("Setup · step 1/3")
        ScreenTitle("Activity selection", "What are you training against?")
        Text(
            "Every activity gets its own opponent, its own Fidelity and its own history. You can add more later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Activities.all.forEach { a ->
                CorpoChip(label = a.label, active = a.key == selected, onClick = { selected = a.key })
            }
        }
        CorpoPanel {
            SectionLabel("Dimensions for ${activity.label}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                activity.dimensions.forEach { CorpoChip(label = it, active = true, onClick = {}) }
            }
            FootNote(
                if (activity.paceMeaningful) "v1 scores on pace against the forecast; the other dimensions arrive with route storage."
                else "Motor-assisted: pace says little here. v1 still scores on pace — consistency and route novelty follow with route storage."
            )
        }
        CorpoButton(
            text = "Continue",
            onClick = { app.repository.activeActivityType = selected; onContinue() },
            kind = ButtonKind.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
