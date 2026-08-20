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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.eversorhn.gait.ui.debrief.DebriefContent
import dev.eversorhn.gait.ui.gaitViewModel
import dev.eversorhn.gait.ui.theme.CorpoPanel

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
        hasLocationPermission = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    // Intercept the system back gesture/button whenever there's a live recording or an
    // unsaved indoor result waiting on a distance, instead of silently losing either.
    val hasUnsavedWork = snapshot.isTracking || uiState.awaitingIndoorDistance
    BackHandler(enabled = hasUnsavedWork) {
        showLeaveConfirmation = true
    }

    if (showLeaveConfirmation) {
        if (snapshot.isTracking) {
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
        } else {
            AlertDialog(
                onDismissRequest = { showLeaveConfirmation = false },
                title = { Text("Discard this session?") },
                text = { Text("You timed a session but haven't entered a distance yet. Leaving now discards it.") },
                confirmButton = {
                    TextButton(onClick = {
                        showLeaveConfirmation = false
                        viewModel.discardIndoor()
                        viewModel.reset()
                        onDone()
                    }) { Text("DISCARD") }
                },
                dismissButton = {
                    TextButton(onClick = { showLeaveConfirmation = false }) { Text("KEEP EDITING") }
                },
            )
        }
    }

    uiState.recoverable?.let { r ->
        AlertDialog(
            onDismissRequest = { /* force a choice */ },
            title = { Text("Interrupted session found") },
            text = {
                Text(
                    when (r.mode) {
                        TrackMode.OUTDOOR -> "GAIT was stopped mid-run. Captured so far: " +
                            "${"%.2f".format(r.distanceMeters / 1000.0)} km over ${formatElapsed(r.movingSeconds)} moving. Save it?"
                        TrackMode.INDOOR -> "GAIT was stopped mid-session. ${formatElapsed(r.durationSeconds)} were timed. " +
                            "Save it? You'll be asked for the distance next."
                    }
                )
            },
            confirmButton = { TextButton(onClick = viewModel::saveRecovered) { Text("SAVE") } },
            dismissButton = { TextButton(onClick = viewModel::discardRecovered) { Text("DISCARD") } },
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

            uiState.awaitingIndoorDistance -> {
                Text("TRACK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("What did the machine say?", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Timed at ${formatElapsed(uiState.indoorElapsedSeconds)}. Enter the distance shown on the console.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = uiState.indoorDistanceKm,
                    onValueChange = viewModel::updateIndoorDistance,
                    label = { Text("Distance (km)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = viewModel::submitIndoorDistance,
                    enabled = !uiState.finishing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (uiState.finishing) "SAVING…" else "SUBMIT")
                }
            }

            uiState.mode == null -> {
                Text("TRACK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("Indoor or outdoor?", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Outdoor uses GPS to verify pace and distance. Indoor (treadmill, ergometer, ...) is timed only — you enter the distance the machine shows.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                uiState.stopMessage?.let { StopNotice(it) }
                Button(onClick = { viewModel.chooseMode(TrackMode.OUTDOOR) }, modifier = Modifier.fillMaxWidth()) {
                    Text("OUTDOOR — GPS")
                }
                Button(onClick = { viewModel.chooseMode(TrackMode.INDOOR) }, modifier = Modifier.fillMaxWidth()) {
                    Text("INDOOR — TREADMILL / ERGOMETER")
                }
                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("BACK")
                }
            }

            uiState.mode == TrackMode.OUTDOOR && !hasLocationPermission -> {
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
                OutlinedButton(onClick = { viewModel.chooseMode(TrackMode.INDOOR) }, modifier = Modifier.fillMaxWidth()) {
                    Text("USE INDOOR MODE INSTEAD")
                }
            }

            snapshot.isTracking -> {
                Text(
                    if (snapshot.autoPaused) "AUTO-PAUSED" else "LIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (snapshot.autoPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
                Text(formatElapsed(snapshot.elapsedSeconds), style = MaterialTheme.typography.headlineLarge)
                if (uiState.mode == TrackMode.OUTDOOR) {
                    Text(
                        "${"%.2f".format(snapshot.distanceMeters / 1000.0)} km" +
                            (snapshot.currentPaceSecPerKm?.let { "  ·  ${formatLivePace(it)}/km" } ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "moving ${formatElapsed(snapshot.movingSeconds)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (snapshot.gpsFixCount == 0) {
                        Text(
                            "Waiting for a GPS fix…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        "Indoor session — distance logged on stop.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                snapshot.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                Button(onClick = viewModel::stop, enabled = !uiState.finishing, modifier = Modifier.fillMaxWidth()) {
                    Text(if (uiState.finishing) "SAVING…" else "STOP")
                }
            }

            else -> {
                Text("TRACK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("Ready when you are", style = MaterialTheme.typography.headlineLarge)
                Text(
                    if (uiState.mode == TrackMode.OUTDOOR) "Outdoor · GPS-verified" else "Indoor · timed, distance entered on stop",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                uiState.stopMessage?.let { StopNotice(it) }
                snapshot.error?.let { StopNotice(it) }
                Button(onClick = viewModel::start, modifier = Modifier.fillMaxWidth()) {
                    Text("START ACTIVITY")
                }
                OutlinedButton(onClick = { viewModel.chooseMode(TrackMode.OUTDOOR).also { viewModel.reset() } }, modifier = Modifier.fillMaxWidth()) {
                    Text("CHANGE MODE")
                }
                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("BACK")
                }
            }
        }
    }
}

@Composable
private fun StopNotice(message: String) {
    CorpoPanel {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
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
