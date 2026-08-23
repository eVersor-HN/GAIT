package dev.eversorhn.gait.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.data.db.entity.OpponentType
import dev.eversorhn.gait.data.db.entity.TwinProfileEntity
import dev.eversorhn.gait.domain.activity.Activities
import dev.eversorhn.gait.ui.gaitViewModel
import dev.eversorhn.gait.ui.theme.Alert
import dev.eversorhn.gait.ui.theme.Brass
import dev.eversorhn.gait.ui.theme.ButtonKind
import dev.eversorhn.gait.ui.theme.CorpoButton
import dev.eversorhn.gait.ui.theme.CorpoDialog
import dev.eversorhn.gait.ui.theme.CorpoPanel
import dev.eversorhn.gait.ui.theme.Cyan
import dev.eversorhn.gait.ui.theme.FootNote
import dev.eversorhn.gait.ui.theme.Line
import dev.eversorhn.gait.ui.theme.LineSoft
import dev.eversorhn.gait.ui.theme.ScreenTitle
import dev.eversorhn.gait.ui.theme.SectionLabel
import dev.eversorhn.gait.ui.theme.TextDim
import dev.eversorhn.gait.ui.theme.TextFaint
import dev.eversorhn.gait.ui.theme.TextPrimary
import dev.eversorhn.gait.ui.theme.pressable

/**
 * The first screen: your enrolments. Pick one to enter it, or enrol a new one. Each row is a
 * profile — an activity, an opponent, a standing — with its own settings behind the gear.
 * Nothing here explains the app; that lives in Settings → Briefing.
 */
@Composable
fun ProfilesScreen(
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onSettings: (Long) -> Unit,
    onBriefing: () -> Unit,
) {
    val viewModel: ProfilesViewModel = gaitViewModel()
    val state by viewModel.uiState.collectAsState()
    var pendingDelete by remember { mutableStateOf<TwinProfileEntity?>(null) }

    pendingDelete?.let { p ->
        CorpoDialog(
            title = "Delete ${p.profileName.ifBlank { Activities.byKey(p.activityType).label }}?",
            body = "Its sessions, ledger and messages go with it. The division keeps no copy.",
            onDismiss = { pendingDelete = null },
            confirmText = "Delete enrolment",
            onConfirm = { viewModel.delete(p); pendingDelete = null },
            confirmKind = ButtonKind.RISK,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Asset Performance Division", "Enrolments")

        if (state.loaded && state.profiles.isEmpty()) {
            CorpoPanel {
                Text("No enrolment on file.", style = MaterialTheme.typography.bodyLarge)
                FootNote("Enrol to be assigned an opponent")
            }
        }

        state.profiles.forEach { row ->
            ProfileRow(
                row = row,
                onOpen = { viewModel.select(row.profile.id) { onOpen(row.profile.id) } },
                onSettings = { viewModel.select(row.profile.id) { onSettings(row.profile.id) } },
                onDelete = { pendingDelete = row.profile },
            )
        }

        CorpoButton("Enrol new asset", onClick = onNew, kind = ButtonKind.PRIMARY, modifier = Modifier.fillMaxWidth())
        CorpoButton("Briefing & settings", onClick = onBriefing, kind = ButtonKind.GHOST, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ProfileRow(row: ProfileRowState, onOpen: () -> Unit, onSettings: () -> Unit, onDelete: () -> Unit) {
    val horde = row.profile.opponentType == OpponentType.HORDE
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
            .border(BorderStroke(1.dp, if (row.active) Brass else Line), RoundedCornerShape(6.dp))
            .pressable(onClick = onOpen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(10.dp).background(if (row.active) Brass else LineSoft, CircleShape))
            Column(Modifier.weight(1f)) {
                Text(
                    row.profile.profileName.ifBlank { Activities.byKey(row.profile.activityType).label },
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    maxLines = 1,
                )
                FootNote(
                    "${Activities.byKey(row.profile.activityType).label} · ${if (horde) "Horde" else row.profile.twinName}",
                    maxLines = 1,
                )
            }
            Text(row.standing.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (row.leadPositive) Brass else if (row.leadNegative) Alert else TextFaint)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat("Rounds", "${row.rounds}")
            Stat(if (horde) "Proximity" else "Fidelity", "${row.metricPercent}%")
            Stat("Sessions", "${row.sessions}")
            Stat("Last", row.lastLabel)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CorpoButton("Open", onClick = onOpen, kind = ButtonKind.SAFE, modifier = Modifier.weight(1f))
            CorpoButton("Settings", onClick = onSettings, kind = ButtonKind.GHOST, modifier = Modifier.weight(1f))
            CorpoButton("Delete", onClick = onDelete, kind = ButtonKind.GHOST, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        FootNote(label, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, color = TextDim, maxLines = 1)
    }
}
