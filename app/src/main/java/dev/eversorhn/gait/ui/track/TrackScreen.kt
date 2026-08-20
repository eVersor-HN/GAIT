package dev.eversorhn.gait.ui.track

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.eversorhn.gait.ui.debrief.DebriefContent
import dev.eversorhn.gait.ui.gaitViewModel

@Composable
fun TrackScreen(onDone: () -> Unit) {
    val viewModel: TrackViewModel = gaitViewModel()
    val context = LocalContext.current
    val snapshot by viewModel.trackingSnapshot.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var showLeaveConfirmation by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasLocationPermission = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    // Intercept the system back gesture/button while a recording is live, instead of
    // silently losing (or ambiguously keeping) an in-progress session.
    BackHandler(enabled = snapshot.isTracking) {
        showLeaveConfirmation = true
    }

    if (showLeaveConfirmation) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmation = false },
            title = { Text("Still tracking") },
            text = { Text("Your session is still recording. Stop it before leaving, or keep going.") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveConfirmation = false
                    viewModel.stop()
                }) { Text("STOP TRACKING") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmation = false }) { Text("KEEP TRACKING") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val result = uiState.result
        when {
            result != null -> {
                DebriefContent(result = result, onDone = {
                    viewModel.reset()
                    onDone()
                })
            }

            !hasLocationPermission -> {
                Text("TRACK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("GAIT needs your location", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Used only to record pace, distance, and route while a session is active — recording runs " +
                        "as a foreground service with an ongoing notification, and nothing leaves this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("GRANT LOCATION ACCESS")
                }
            }

            snapshot.isTracking -> {
                Text("LIVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                Text(formatElapsed(snapshot.elapsedSeconds), style = MaterialTheme.typography.headlineLarge)
                Text(
                    "${"%.2f".format(snapshot.distanceMeters / 1000.0)} km" +
                        (snapshot.currentPaceSecPerKm?.let { "  ·  ${formatLivePace(it)}/km" } ?: ""),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (snapshot.gpsFixCount == 0) {
                    Text(
                        "Waiting for a GPS fix…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = viewModel::stop, enabled = !uiState.finishing, modifier = Modifier.fillMaxWidth()) {
                    Text(if (uiState.finishing) "SAVING…" else "STOP")
                }
            }

            else -> {
                Text("TRACK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("Ready when you are", style = MaterialTheme.typography.headlineLarge)
                Button(onClick = viewModel::start, modifier = Modifier.fillMaxWidth()) {
                    Text("START ACTIVITY")
                }
                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("BACK")
                }
            }
        }
    }
}

private fun formatElapsed(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}

private fun formatLivePace(secPerKm: Double): String {
    val total = secPerKm.toInt()
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
