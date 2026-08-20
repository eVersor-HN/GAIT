package dev.eversorhn.gait.ui.restdays

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.domain.restdays.RestDayPolicy
import dev.eversorhn.gait.ui.gaitViewModel
import dev.eversorhn.gait.ui.theme.CorpoPanel

private val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
fun RestDaysScreen(onDone: () -> Unit) {
    val viewModel: RestDaysViewModel = gaitViewModel()
    val state by viewModel.uiState.collectAsState()
    var vacationDaysInput by remember { mutableStateOf("7") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("REST & VACATION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text("When you're allowed to disappear", style = MaterialTheme.typography.headlineLarge)

        Text(
            "Declared rest days: no forecast, no fidelity change, no composure reaction.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..7).forEach { dayOfWeek ->
                val active = (state.restDayMask shr (dayOfWeek - 1)) and 1 == 1
                val bg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                val fg = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(bg, RoundedCornerShape(4.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { viewModel.toggleDay(dayOfWeek) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(dayLabels[dayOfWeek - 1], style = MaterialTheme.typography.labelLarge, color = fg)
                }
            }
        }

        if (RestDayPolicy.declaredRestDayCountFromMask(state.restDayMask) > RestDayPolicy.ANTI_GAMING_THRESHOLD) {
            Text(
                "Four+ rest days a week. I've stopped scheduling around them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        CorpoPanel {
            if (state.onVacationUntilLabel != null) {
                Text("ON VACATION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("Until ${state.onVacationUntilLabel}", style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = viewModel::endVacationEarly, modifier = Modifier.fillMaxWidth()) {
                    Text("END VACATION EARLY")
                }
            } else {
                Text("VACATION BANK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("${state.vacationDaysRemaining} days left this year", style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = vacationDaysInput,
                    onValueChange = { vacationDaysInput = it },
                    label = { Text("Days") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { vacationDaysInput.toIntOrNull()?.let { viewModel.startVacation(it) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("START VACATION")
                }
            }
        }

        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("BACK")
        }
    }
}
