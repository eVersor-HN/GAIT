package dev.eversorhn.gait.ui.forecast

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.R
import dev.eversorhn.gait.ui.theme.CorpoPanel

@Composable
fun ForecastScreen(
    onStartActivity: () -> Unit,
    onLogSession: () -> Unit,
    onRestDays: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
) {
    val viewModel: ForecastViewModel = dev.eversorhn.gait.ui.gaitViewModel()
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    // Notification permission is asked for here -- after setup, once the user has an opponent
    // that could actually message them -- rather than as the very first thing on launch.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* denial just means opponent pings stay silent */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (val s = state) {
            ForecastUiState.Loading, ForecastUiState.NoTwin -> {
                Text("Loading…", style = MaterialTheme.typography.bodyLarge)
            }
            is ForecastUiState.Ready -> {
                Text("PRE-SESSION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("What ${s.opponentName} expects today", style = MaterialTheme.typography.headlineLarge)

                if (s.restStateLabel != null) {
                    Text(
                        s.restStateLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }

                CorpoPanel {
                    if (s.hordeCaption != null) {
                        Text(
                            s.hordeCaption,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Text(s.forecastLine, style = MaterialTheme.typography.bodyLarge)
                    if (!s.coldStart) {
                        Text(
                            "FORECAST CONFIDENCE: ${s.confidencePercent}% · BASED ON " +
                                pluralStringResource(R.plurals.sessions_count, s.basedOnSessions, s.basedOnSessions).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    "${s.opponentLabel} · ${s.generationLabel} ${s.generation} · ${s.metricLabel} ${s.metricPercent}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(onClick = onStartActivity, modifier = Modifier.fillMaxWidth()) {
                    Text("START ACTIVITY")
                }
                TextButton(onClick = onLogSession, modifier = Modifier.fillMaxWidth()) {
                    Text("Log manually instead")
                }
                TextButton(onClick = onStats, modifier = Modifier.fillMaxWidth()) {
                    Text("Statistics")
                }
                TextButton(onClick = onRestDays, modifier = Modifier.fillMaxWidth()) {
                    Text("Rest days & vacation")
                }
                TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Settings")
                }
            }
        }
    }
}
