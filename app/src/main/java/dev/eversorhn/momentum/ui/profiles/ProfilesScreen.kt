package dev.eversorhn.momentum.ui.profiles

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
import dev.eversorhn.momentum.data.db.entity.OpponentType
import dev.eversorhn.momentum.data.db.entity.TwinProfileEntity
import dev.eversorhn.momentum.domain.activity.Activities
import dev.eversorhn.momentum.ui.momentumViewModel
import dev.eversorhn.momentum.ui.theme.Alert
import dev.eversorhn.momentum.ui.theme.Brass
import dev.eversorhn.momentum.ui.theme.ButtonKind
import dev.eversorhn.momentum.ui.theme.CorpoButton
import dev.eversorhn.momentum.ui.theme.CorpoDialog
import dev.eversorhn.momentum.ui.theme.CorpoPanel
import dev.eversorhn.momentum.ui.theme.Cyan
import dev.eversorhn.momentum.ui.theme.FootNote
import dev.eversorhn.momentum.ui.theme.Line
import dev.eversorhn.momentum.ui.theme.LineSoft
import dev.eversorhn.momentum.ui.theme.ScreenTitle
import dev.eversorhn.momentum.ui.theme.SectionLabel
import dev.eversorhn.momentum.ui.theme.TextDim
import dev.eversorhn.momentum.ui.theme.TextFaint
import dev.eversorhn.momentum.ui.theme.TextPrimary
import dev.eversorhn.momentum.ui.theme.pressable

/**
 * The first screen: your enrolments. Pick one to enter it, or enrol a new one. Each row is a
 * profile — an activity, an opponent, a standing — with its own settings behind the gear.
 * Nothing here explains the app; that lives in Settings → Briefing.
 */
@Composable
fun ProfilesScreen(
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onBriefing: () -> Unit,
) {
    // The list is the app's root: back here asks whether to leave the floor.
    dev.eversorhn.momentum.ui.theme.ExitGuard(opponentName = "The division")

    val viewModel: ProfilesViewModel = momentumViewModel()
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
                onManage = { pendingDelete = row.profile },
            )
        }

        CorpoButton("Enrol new asset", onClick = onNew, kind = ButtonKind.PRIMARY, modifier = Modifier.fillMaxWidth())
        CorpoButton("Briefing", onClick = onBriefing, kind = ButtonKind.GHOST, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ProfileRow(row: ProfileRowState, onOpen: () -> Unit, onManage: () -> Unit) {
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
            Text(
                "⋯",
                style = MaterialTheme.typography.titleMedium,
                color = TextFaint,
                modifier = Modifier.pressable(onClick = onManage).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat("Rounds", "${row.rounds}")
            Stat(if (horde) "Proximity" else "Fidelity", "${row.metricPercent}%")
            Stat("Sessions", "${row.sessions}")
            Stat("Last", row.lastLabel)
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
