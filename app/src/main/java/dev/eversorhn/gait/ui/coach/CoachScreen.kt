package dev.eversorhn.gait.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import dev.eversorhn.gait.ui.gaitViewModel
import dev.eversorhn.gait.ui.theme.Alert
import dev.eversorhn.gait.ui.theme.Brass
import dev.eversorhn.gait.ui.theme.CorpoPanel
import dev.eversorhn.gait.ui.theme.FootNote
import dev.eversorhn.gait.ui.theme.PanelTone
import dev.eversorhn.gait.ui.theme.ScreenTitle
import dev.eversorhn.gait.ui.theme.SectionLabel
import dev.eversorhn.gait.ui.theme.TextDim
import dev.eversorhn.gait.ui.theme.TextFaint
import dev.eversorhn.gait.ui.theme.TextPrimary

/** What to do, in order, with the number to act on. No prose, no encouragement — instructions. */
@Composable
fun CoachScreen() {
    val viewModel: CoachViewModel = gaitViewModel()
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Analysis", if (state.isHorde) "How to keep the ground" else "How to stay ahead")

        state.headline?.let { h ->
            CorpoPanel(tone = if (state.headlineUrgent) PanelTone.WARN else PanelTone.NEUTRAL) {
                SectionLabel("Standing", color = if (state.headlineUrgent) Alert else TextFaint)
                Text(h, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            }
        }

        state.items.forEachIndexed { i, item ->
            CorpoPanel(tone = if (item.urgent) PanelTone.WARN else PanelTone.NEUTRAL) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("%02d".format(i + 1), style = MaterialTheme.typography.titleLarge, color = if (item.urgent) Alert else TextFaint, modifier = Modifier.width(34.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(item.target, style = MaterialTheme.typography.headlineLarge, color = if (item.urgent) Alert else Brass)
                        Text(item.why, style = MaterialTheme.typography.bodyMedium, color = TextDim)
                    }
                }
            }
        }

        if (state.loaded && state.items.isEmpty()) {
            CorpoPanel { Text("Nothing to act on yet.", style = MaterialTheme.typography.bodyLarge) }
        }
        FootNote("Every figure here comes from your own sessions", color = TextFaint)
        Spacer(Modifier.height(8.dp))
    }
}
