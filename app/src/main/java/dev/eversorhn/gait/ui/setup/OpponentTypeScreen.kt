package dev.eversorhn.gait.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.ui.theme.ButtonKind
import dev.eversorhn.gait.ui.theme.CorpoButton
import dev.eversorhn.gait.ui.theme.FootNote
import dev.eversorhn.gait.ui.theme.ScreenTitle
import dev.eversorhn.gait.ui.theme.SelectCard

@Composable
fun OpponentTypeScreen(onTwin: () -> Unit, onHorde: () -> Unit) {
    var choice by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FootNote("Setup · step 2/3")
        ScreenTitle("Opponent selection", "Who's coming after you?")
        Text(
            "Pick one opponent type. Either way, it's built entirely from your own data — never another person's.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SelectCard(
            title = "Rival Twin",
            description = "A single named predictor of you, with a voice. It forecasts every session, comments on every " +
                "result, and gets sharper the more predictable you are. You name it yourself.",
            selected = choice == "twin",
            onClick = { choice = "twin" },
            badge = "17 personas",
        )
        SelectCard(
            title = "Zombie Horde",
            description = "No name, no words — just a distance closing behind you, and the sound of it. Built from every " +
                "Twin that ever lost its Decommission Trial.",
            selected = choice == "horde",
            onClick = { choice = "horde" },
            badge = "3 intensities",
        )

        Spacer(Modifier.height(4.dp))
        CorpoButton(
            text = "Continue",
            onClick = { if (choice == "horde") onHorde() else onTwin() },
            enabled = choice != null,
            kind = ButtonKind.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
        )
        FootNote("Switchable later in Settings · everything stays on this device")
    }
}
