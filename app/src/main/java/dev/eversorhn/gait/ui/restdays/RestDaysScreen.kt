package dev.eversorhn.gait.ui.restdays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.domain.restdays.RestDayPolicy
import dev.eversorhn.gait.ui.gaitViewModel
import dev.eversorhn.gait.ui.theme.Alert
import dev.eversorhn.gait.ui.theme.Brass
import dev.eversorhn.gait.ui.theme.BrassDim
import dev.eversorhn.gait.ui.theme.ButtonKind
import dev.eversorhn.gait.ui.theme.CorpoButton
import dev.eversorhn.gait.ui.theme.CorpoChip
import dev.eversorhn.gait.ui.theme.CorpoPanel
import dev.eversorhn.gait.ui.theme.Cyan
import dev.eversorhn.gait.ui.theme.FootNote
import dev.eversorhn.gait.ui.theme.Ink2
import dev.eversorhn.gait.ui.theme.LineSoft
import dev.eversorhn.gait.ui.theme.ScreenTitle
import dev.eversorhn.gait.ui.theme.SectionLabel
import dev.eversorhn.gait.ui.theme.TextDim
import dev.eversorhn.gait.ui.theme.TextFaint

private val dayLabels = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

@Composable
fun RestDaysScreen(onDone: () -> Unit) {
    val viewModel: RestDaysViewModel = gaitViewModel()
    val state by viewModel.uiState.collectAsState()
    var vacationDaysInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Availability", "Declared time off")

        // --- Calendar: tap days in advance ---
        CorpoPanel {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                CorpoChip(label = "‹", active = false, onClick = { viewModel.showMonth(state.monthOffset - 1) })
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SectionLabel("Planned days off")
                    Text(state.monthLabel, style = MaterialTheme.typography.titleMedium)
                }
                CorpoChip(label = "›", active = false, onClick = { viewModel.showMonth(state.monthOffset + 1) })
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                dayLabels.forEach { Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextFaint, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            val cells: List<CalendarDay?> = List(state.leadingBlanks) { null } + state.days
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { d -> DayCell(d, onClick = { d?.let { viewModel.togglePlanned(it.epochDay) } }) }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(4.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Legend(Brass, "planned off")
                Legend(TextDim, "weekly rest")
                Legend(Cyan, "vacation")
            }
            FootNote(
                if (state.plannedUpcoming == 0) "No days declared · tap a day to declare it"
                else "${state.plannedUpcoming} day${if (state.plannedUpcoming == 1) "" else "s"} declared · saved",
                color = if (state.plannedUpcoming > 0) Brass else TextFaint,
            )
        }

        // --- Weekly pattern ---
        CorpoPanel {
            SectionLabel("Weekly rest days")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { dow ->
                    val on = (state.restDayMask shr (dow - 1)) and 1 == 1
                    CorpoChip(label = dayLabels[dow - 1], active = on, onClick = { viewModel.toggleDay(dow) }, modifier = Modifier.weight(1f))
                }
            }
            val count = RestDayPolicy.declaredRestDayCountFromMask(state.restDayMask)
            FootNote(
                if (count > RestDayPolicy.ANTI_GAMING_THRESHOLD) "$count rest days a week. The model notices patterns like that."
                else "$count rest day${if (count == 1) "" else "s"} a week.",
                color = if (count > RestDayPolicy.ANTI_GAMING_THRESHOLD) Alert else TextFaint,
            )
        }

        // --- Vacation bank (folded unless you're on one) ---
        dev.eversorhn.gait.ui.theme.CollapsiblePanel(
            title = if (state.onVacationUntilLabel != null) "On vacation" else "Vacation bank",
            summary = state.onVacationUntilLabel?.let { "Until $it" } ?: "${state.vacationDaysRemaining} days left this year · tap to book a block",
            initiallyExpanded = state.onVacationUntilLabel != null,
        ) {
            if (state.onVacationUntilLabel != null) {
                SectionLabel("On vacation", color = Cyan)
                Text("Until ${state.onVacationUntilLabel}", style = MaterialTheme.typography.bodyLarge)
                CorpoButton("End vacation early", onClick = viewModel::endVacationEarly, kind = ButtonKind.SAFE, modifier = Modifier.fillMaxWidth())
            } else {
                SectionLabel("Vacation bank")
                Text("${state.vacationDaysRemaining} days left this year", style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = vacationDaysInput,
                    onValueChange = { vacationDaysInput = it },
                    label = { Text("Days") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                CorpoButton(
                    "Start vacation",
                    onClick = { vacationDaysInput.toIntOrNull()?.let { viewModel.startVacation(it) } },
                    kind = ButtonKind.PRIMARY,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        CorpoButton("Back", onClick = onDone, kind = ButtonKind.GHOST, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.DayCell(d: CalendarDay?, onClick: () -> Unit) {
    if (d == null) { Spacer(Modifier.weight(1f)); return }
    val bg = when {
        d.planned -> Brass.copy(alpha = 0.18f)
        d.onVacation -> Cyan.copy(alpha = 0.14f)
        d.weeklyRest -> Ink2
        else -> Color.Transparent
    }
    val border = when {
        d.isToday -> Brass
        d.planned -> BrassDim
        else -> LineSoft
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .background(bg, RoundedCornerShape(4.dp))
            .border(BorderStroke(1.dp, border), RoundedCornerShape(4.dp))
            .clickable(enabled = !d.isPast, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${d.dayOfMonth}",
            style = MaterialTheme.typography.labelLarge,
            color = when {
                d.isPast -> TextFaint.copy(alpha = 0.5f)
                d.planned -> Brass
                d.onVacation -> Cyan
                d.weeklyRest -> TextDim
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.height(8.dp).aspectRatio(1f).background(color, RoundedCornerShape(2.dp)))
        FootNote(label)
    }
}
