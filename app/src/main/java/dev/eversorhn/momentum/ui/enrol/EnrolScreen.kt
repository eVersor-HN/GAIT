package dev.eversorhn.momentum.ui.enrol

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.eversorhn.momentum.data.db.entity.OpponentType
import dev.eversorhn.momentum.domain.activity.Activities
import dev.eversorhn.momentum.domain.horde.HordeIntensity
import dev.eversorhn.momentum.ui.momentumViewModel
import dev.eversorhn.momentum.ui.theme.Alert
import dev.eversorhn.momentum.ui.theme.Brass
import dev.eversorhn.momentum.ui.theme.BrassDim
import dev.eversorhn.momentum.ui.theme.ButtonKind
import dev.eversorhn.momentum.ui.theme.CorpoButton
import dev.eversorhn.momentum.ui.theme.CorpoPanel
import dev.eversorhn.momentum.ui.theme.FootNote
import dev.eversorhn.momentum.ui.theme.Line
import dev.eversorhn.momentum.ui.theme.LineSoft
import dev.eversorhn.momentum.ui.theme.ScreenTitle
import dev.eversorhn.momentum.ui.theme.SectionLabel
import dev.eversorhn.momentum.ui.theme.Segmented
import dev.eversorhn.momentum.ui.theme.TextDim
import dev.eversorhn.momentum.ui.theme.TextFaint
import dev.eversorhn.momentum.ui.theme.TextPrimary
import dev.eversorhn.momentum.ui.theme.pressable

/**
 * One enrolment form instead of a four-screen wizard: activity, opponent, identity, name —
 * each a proper list with a selection mark, in the order the division would ask. The single
 * action at the bottom creates the profile and opens it.
 */
@Composable
fun EnrolScreen(onCreated: () -> Unit, onCancel: () -> Unit) {
    val viewModel: EnrolViewModel = momentumViewModel()
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.createdId) { if (state.createdId != null) onCreated() }

    // Back would drop a half-filled enrolment: ask before it does.
    dev.eversorhn.momentum.ui.theme.DiscardGuard(
        enabled = state.twinName.isNotBlank() || state.profileName != Activities.byKey(state.activityKey).label,
        title = "Discard this enrolment?",
        body = "Nothing has been created yet. The activity, opponent and name you picked are lost.",
        onDiscard = onCancel,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Enrolment", "New asset")

        // --- 1. Activity ---
        CorpoPanel {
            SectionLabel("1 · Activity")
            // One group open at a time: 25 activities are a wall otherwise. The group holding
            // the current selection is the one that starts open.
            var openGroup by androidx.compose.runtime.saveable.rememberSaveable {
                mutableStateOf(Activities.byKey(state.activityKey).group)
            }
            Activities.groups.forEach { (group, list) ->
                val open = openGroup == group
                val picked = list.firstOrNull { it.key == state.activityKey }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable(onClick = { openGroup = if (open) "" else group })
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        group.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (picked != null) Brass else TextFaint,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    if (!open && picked != null) {
                        Text(picked.label, style = MaterialTheme.typography.labelSmall, color = Brass, maxLines = 1)
                    } else if (!open) {
                        Text("${list.size}", style = MaterialTheme.typography.labelSmall, color = TextFaint)
                    }
                    Text(if (open) "–" else "+", style = MaterialTheme.typography.labelLarge, color = TextFaint)
                }
                if (open) {
                    list.forEach { a ->
                        SelectRow(
                            title = a.label,
                            detail = a.dimensions.joinToString(" · "),
                            selected = state.activityKey == a.key,
                            onClick = { viewModel.selectActivity(a.key) },
                        )
                    }
                }
            }
        }

        // --- 2. Opponent ---
        CorpoPanel {
            SectionLabel("2 · Opponent")
            SelectRow(
                title = "Rival Twin",
                detail = "A named model of you. Forecast, standing, board.",
                selected = state.opponentType == OpponentType.TWIN,
                onClick = { viewModel.selectOpponent(OpponentType.TWIN) },
            )
            SelectRow(
                title = "Zombie Horde",
                detail = "No name. Distance, proximity, containment map.",
                selected = state.opponentType == OpponentType.HORDE,
                onClick = { viewModel.selectOpponent(OpponentType.HORDE) },
            )
        }

        // --- 3. Identity ---
        CorpoPanel {
            SectionLabel("3 · Identity")
            if (state.opponentType == OpponentType.HORDE) {
                Segmented(
                    options = HordeIntensity.all.map { HordeIntensity.label(it) },
                    selected = HordeIntensity.all.indexOf(state.hordeIntensity).coerceAtLeast(0),
                    onSelect = { viewModel.selectIntensity(HordeIntensity.all[it]) },
                )
            } else {
                OutlinedTextField(
                    value = state.twinName,
                    onValueChange = viewModel::setTwinName,
                    label = { Text("OPPONENT NAME", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    textStyle = MaterialTheme.typography.titleLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Brass, unfocusedBorderColor = BrassDim, cursorColor = Brass, focusedLabelColor = Brass,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // --- 4. Label ---
        CorpoPanel {
            SectionLabel("4 · Enrolment name")
            OutlinedTextField(
                value = state.profileName,
                onValueChange = viewModel::setProfileName,
                label = { Text("SHOWN IN YOUR LIST", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Brass, unfocusedBorderColor = LineSoft, cursorColor = Brass, focusedLabelColor = Brass,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        CorpoButton(
            text = "Enrol",
            onClick = viewModel::create,
            enabled = state.canCreate,
            kind = ButtonKind.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
        )
        CorpoButton("Cancel", onClick = onCancel, kind = ButtonKind.GHOST, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
    }
}

/** A list row with a radio mark — the app's one way of choosing one thing from many. */
@Composable
fun SelectRow(title: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().pressable(onClick = onClick).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(14.dp).border(BorderStroke(1.dp, if (selected) Brass else Line), CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(8.dp).background(Brass, CircleShape))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (selected) Brass else TextPrimary, maxLines = 1)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
