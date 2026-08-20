package dev.eversorhn.gait.ui.forecast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ForecastScreen(onLogSession: () -> Unit) {
    val viewModel: ForecastViewModel = dev.eversorhn.gait.ui.gaitViewModel()
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (val s = state) {
            ForecastUiState.Loading, ForecastUiState.NoTwin -> {
                Text("Loading…", style = MaterialTheme.typography.bodyLarge)
            }
            is ForecastUiState.Ready -> {
                Text("PRE-SESSION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("What ${s.twinName} expects today", style = MaterialTheme.typography.headlineLarge)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(s.forecastLine, style = MaterialTheme.typography.bodyLarge)
                    if (!s.coldStart) {
                        Text(
                            "FORECAST CONFIDENCE: ${s.confidencePercent}% · BASED ON ${s.basedOnSessions} SESSIONS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    "${s.personaLabel} · Generation ${s.generation} · Fidelity ${s.fidelityPercent}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(onClick = onLogSession, modifier = Modifier.fillMaxWidth()) {
                    Text("LOG A SESSION")
                }
            }
        }
    }
}
