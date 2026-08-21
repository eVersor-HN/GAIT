package dev.eversorhn.gait.ui.track

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.eversorhn.gait.domain.fidelity.FidelityReplay
import dev.eversorhn.gait.ui.debrief.DebriefContent
import dev.eversorhn.gait.ui.forecast.formatDuration
import dev.eversorhn.gait.ui.forecast.formatPace
import dev.eversorhn.gait.ui.gaitViewModel
import dev.eversorhn.gait.ui.theme.Alert
import dev.eversorhn.gait.ui.theme.Brass
import dev.eversorhn.gait.ui.theme.ButtonKind
import dev.eversorhn.gait.ui.theme.CorpoButton
import dev.eversorhn.gait.ui.theme.CorpoPanel
import dev.eversorhn.gait.ui.theme.Cyan
import dev.eversorhn.gait.ui.theme.FootNote
import dev.eversorhn.gait.ui.theme.Good
import dev.eversorhn.gait.ui.theme.LiveTrack
import dev.eversorhn.gait.ui.theme.Meter
import dev.eversorhn.gait.ui.theme.PanelTone
import dev.eversorhn.gait.ui.theme.PhaseTrack
import dev.eversorhn.gait.ui.theme.RecDot
import dev.eversorhn.gait.ui.theme.ScreenTitle
import dev.eversorhn.gait.ui.theme.SectionLabel
import dev.eversorhn.gait.ui.theme.SelectCard
import dev.eversorhn.gait.ui.theme.StatTile
import dev.eversorhn.gait.ui.theme.TextFaint
import dev.eversorhn.gait.ui.theme.TrackLegend
import dev.eversorhn.gait.ui.theme.formatSignedTenths
import kotlin.math.abs

@Composable
fun TrackScreen(duel: Boolean, onDone: () -> Unit) {
    val viewModel: TrackViewModel = gaitViewModel()
    val context = LocalContext.current
    val snapshot by viewModel.trackingSnapshot.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(duel) { viewModel.setDuel(duel) }

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

    val opponent = uiState.opponent
    val isDuel = uiState.duel
    val opponentName = opponent?.name ?: "Twin"
    val screenEyebrow = if (isDuel) (if (opponent?.isHorde == true) "Outrun Trial" else "Decommission Trial") else "Track"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
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
                ScreenTitle(screenEyebrow, "What did the machine say?")
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
                CorpoButton(
                    text = if (uiState.finishing) "Saving…" else "Submit",
                    onClick = viewModel::submitIndoorDistance,
                    enabled = !uiState.finishing,
                    kind = ButtonKind.PRIMARY,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            uiState.mode == null -> {
                if (isDuel) DuelBriefing(opponent) else PhaseTrack(current = 2)
                ScreenTitle(screenEyebrow, "Indoor or outdoor?")
                uiState.stopMessage?.let { StopNotice(it) }
                val act = dev.eversorhn.gait.domain.activity.Activities.byKey(
                    (LocalContext.current.applicationContext as dev.eversorhn.gait.GaitApplication).repository.activeActivityType
                )
                // What you're up against, right here — no need to go back to the Forecast.
                if (opponent != null && opponent.forecastPaceSecPerKm != null) {
                    CorpoPanel {
                        SectionLabel(if (isDuel) "Target" else "${opponentName}'s number for today")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile(if (act.usesSpeed) "Speed" else "Pace", dev.eversorhn.gait.domain.activity.Activities.formatPaceOrSpeed((if (isDuel) opponent.duelTargetPaceSecPerKm else null) ?: opponent.forecastPaceSecPerKm, act.key), accent = if (opponent.isHorde) Alert else Cyan)
                            StatTile("Distance", opponent.forecastDistanceMeters?.let { "%.2f km".format(it / 1000.0) } ?: "—")
                            StatTile("Riding", "${opponent.stake} pt${if (opponent.stake == 1) "" else "s"}", accent = if (opponent.stake > 1) Alert else MaterialTheme.colorScheme.onSurface, sub = if (opponent.stakeCalled) "called" else if (opponent.stake > 1) "staked" else "base round")
                        }
                    }
                }
                SelectCard(
                    title = "Outdoor · GPS",
                    description = "${if (act.usesSpeed) "Speed" else "Pace"}, distance and route verified by the device. Live comparison, splits, commentary. Tap to start.",
                    selected = false,
                    onClick = { viewModel.chooseMode(TrackMode.OUTDOOR); if (hasLocationPermission) viewModel.start() },
                    badge = "verified",
                )
                SelectCard(
                    title = "Indoor · ${act.indoorLabel}",
                    description = "Timed only. You enter the distance the ${if (act.indoorLabel == "timed only") "trip" else "machine"} shows when you stop — tagged as self-reported. Tap to start.",
                    selected = false,
                    onClick = { viewModel.chooseMode(TrackMode.INDOOR); viewModel.start() },
                    badge = "self-reported",
                )
                CorpoButton("Back", onClick = onDone, kind = ButtonKind.GHOST, modifier = Modifier.fillMaxWidth())
            }

            uiState.mode == TrackMode.OUTDOOR && !hasLocationPermission -> {
                ScreenTitle(screenEyebrow, "GAIT needs your location")
                Text(
                    "Used only to record pace, distance, and route while a session is active — recording runs " +
                        "as a foreground service with an ongoing notification, and nothing leaves this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CorpoButton(
                    text = "Grant location access",
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    },
                    kind = ButtonKind.PRIMARY,
                    modifier = Modifier.fillMaxWidth(),
                )
                CorpoButton("Use indoor mode instead", onClick = { viewModel.chooseMode(TrackMode.INDOOR) }, kind = ButtonKind.GHOST, modifier = Modifier.fillMaxWidth())
            }

            snapshot.isTracking -> {
                LiveSession(
                    snapshot = snapshot,
                    mode = uiState.mode ?: TrackMode.OUTDOOR,
                    opponent = opponent,
                    isDuel = isDuel,
                    callouts = uiState.callouts,
                    projection = uiState.projection,
                )
                CorpoButton(
                    text = if (uiState.finishing) "Saving…" else "Stop",
                    onClick = viewModel::stop,
                    enabled = !uiState.finishing,
                    kind = ButtonKind.PRIMARY,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            else -> {
                if (isDuel) DuelBriefing(opponent) else PhaseTrack(current = 2)
                ScreenTitle(screenEyebrow, "Ready when you are")
                Text(
                    if (uiState.mode == TrackMode.OUTDOOR) "Outdoor · GPS-verified" else "Indoor · timed, distance entered on stop",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (opponent?.forecastPaceSecPerKm != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("$opponentName pace", formatPace(opponent.forecastPaceSecPerKm), accent = Cyan)
                        StatTile("Finish", opponent.forecastFinishSeconds?.let { formatDuration(it) } ?: "—", accent = Cyan)
                    }
                }
                uiState.stopMessage?.let { StopNotice(it) }
                snapshot.error?.let { StopNotice(it) }
                CorpoButton(
                    text = if (isDuel) "Start duel" else "Start activity",
                    onClick = viewModel::start,
                    kind = if (isDuel) ButtonKind.RISK else ButtonKind.PRIMARY,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CorpoButton("Change mode", onClick = { viewModel.chooseMode(TrackMode.OUTDOOR).also { viewModel.reset() } }, kind = ButtonKind.GHOST, modifier = Modifier.weight(1f))
                    CorpoButton("Back", onClick = onDone, kind = ButtonKind.GHOST, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Phase 04 header shown above a Trial before it starts: what you have to beat. */
@Composable
private fun DuelBriefing(opponent: LiveOpponent?) {
    CorpoPanel(tone = PanelTone.WARN) {
        SectionLabel("Asset review", color = Alert)
        Text(
            opponent?.duelTargetPaceSecPerKm?.let { "Beat ${formatPace(it)} — ${opponent.name}'s strongest session" }
                ?: "No reference session yet — this run becomes the baseline.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FootNote("Min. 1 km · win resets ${if (opponent?.isHorde == true) "Proximity" else "Fidelity"} and advances the ${if (opponent?.isHorde == true) "wave" else "generation"}")
    }
}

/**
 * Phase 02, Live Divergence: the opponent runs alongside you at its forecast pace. Your marker
 * advances by distance, the opponent's by moving time against its forecast finish — so standing
 * at a light (auto-pause) doesn't hand it free ground, exactly as the saved pace is judged.
 */
@Composable
private fun LiveSession(
    snapshot: dev.eversorhn.gait.tracking.TrackingSnapshot,
    mode: TrackMode,
    opponent: LiveOpponent?,
    isDuel: Boolean,
    callouts: List<LiveCallout>,
    projection: LiveProjection?,
) {
    val name = opponent?.name ?: "Twin"
    val activity = dev.eversorhn.gait.domain.activity.Activities.byKey(
        (LocalContext.current.applicationContext as dev.eversorhn.gait.GaitApplication).repository.activeActivityType
    )
    // Motor-assisted / wheeled activities read better in km/h than min/km.
    val showSpeed = activity.usesSpeed
    fun paceOrSpeed(secPerKm: Double): String = dev.eversorhn.gait.domain.activity.Activities.formatPaceOrSpeed(secPerKm, activity.key)
    val twinColor = if (opponent?.isHorde == true) Alert else Cyan
    val referencePace = if (isDuel) opponent?.duelTargetPaceSecPerKm ?: opponent?.forecastPaceSecPerKm else opponent?.forecastPaceSecPerKm
    val referenceDistance = opponent?.forecastDistanceMeters
    val referenceFinish = if (isDuel && referencePace != null && referenceDistance != null) {
        (referencePace * referenceDistance / 1000.0).toInt()
    } else {
        opponent?.forecastFinishSeconds
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!snapshot.autoPaused) RecDot()
        Text(
            if (snapshot.autoPaused) "AUTO-PAUSED" else if (isDuel) "REC · DUEL" else "REC · LIVE",
            style = MaterialTheme.typography.labelSmall,
            color = if (snapshot.autoPaused) TextFaint else Alert,
        )
        Spacer(Modifier.weight(1f))
        Text(formatElapsed(snapshot.elapsedSeconds), style = MaterialTheme.typography.headlineLarge)
    }

    // --- The gap clock: the one number to watch ---
    if (mode == TrackMode.OUTDOOR && projection != null && opponent != null) {
        val p = projection
        val ahead = p.gapSeconds >= 0
        CorpoPanel(tone = when {
            opponent.isHorde && (p.separationMeters ?: 0) < 50 -> PanelTone.WARN
            opponent.isHorde -> PanelTone.NEUTRAL
            ahead -> PanelTone.GOOD
            else -> PanelTone.WARN
        }) {
            if (opponent.isHorde) {
                SectionLabel(if ((p.separationMeters ?: 0) >= 0) "Separation" else "Overrun", color = if ((p.separationMeters ?: 0) < 50) Alert else MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${kotlin.math.abs(p.separationMeters ?: 0)} m", style = MaterialTheme.typography.headlineLarge, color = if ((p.separationMeters ?: 0) < 50) Alert else Brass)
                    Text(
                        when {
                            p.closingPerMinute == null -> ""
                            p.closingPerMinute > 0 -> "closing ${p.closingPerMinute} m/min"
                            p.closingPerMinute < 0 -> "falling back ${-p.closingPerMinute} m/min"
                            else -> "holding"
                        }.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if ((p.closingPerMinute ?: 0) > 0) Alert else Good,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                FootNote("They move at your projected pace · ${p.projectedFinishSeconds?.let { "your finish if you hold this: ${formatDuration(it)}" } ?: "finding your pace"}")
            } else {
                SectionLabel(if (ahead) "Ahead of $name" else "Behind $name", color = if (ahead) Good else Alert)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        (if (ahead) "+" else "−") + formatElapsed(kotlin.math.abs(p.gapSeconds)),
                        style = MaterialTheme.typography.headlineLarge,
                        color = if (ahead) Good else Alert,
                    )
                    Text("at ${"%.2f".format(snapshot.distanceMeters / 1000.0)} km".uppercase(), style = MaterialTheme.typography.labelSmall, color = TextFaint, modifier = Modifier.padding(bottom = 6.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Finish · hold", p.projectedFinishSeconds?.let { formatDuration(it) } ?: "—", accent = Brass, sub = opponent.forecastFinishSeconds?.let { "vs ${formatDuration(it)}" })
                    StatTile("Round now", when (p.roundToUser) { true -> "YOU"; false -> name.uppercase(); null -> "—" }, accent = when (p.roundToUser) { true -> Good; false -> Alert; null -> TextFaint }, sub = "${opponent.stake} pt${if (opponent.stake == 1) "" else "s"} riding")
                    StatTile("Board → ", p.projectedRank?.let { "#$it" } ?: "—", accent = when { (p.rankDelta ?: 0) > 0 -> Good; (p.rankDelta ?: 0) < 0 -> Alert; else -> MaterialTheme.colorScheme.onSurface }, sub = p.rankDelta?.let { d -> if (d > 0) "▲ $d places" else if (d < 0) "▼ ${-d} places" else "holding" } ?: "")
                }
                p.modelConfidencePercent?.let { c ->
                    Meter(fraction = c / 100f, color = if (c < opponent.forecastConfidencePercent / 2) Good else twinColor)
                    FootNote("$name's confidence: $c% (was ${opponent.forecastConfidencePercent}%) · ${activity.label.lowercase()}")
                }
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        SectionLabel("Live comparison")
        if (opponent != null) {
            FootNote(
                when {
                    isDuel -> "${opponent.stake} pts · duel"
                    opponent.stakeCalled -> "${opponent.stake} pts · called"
                    opponent.stake > 1 -> "${opponent.stake} pts staked"
                    else -> "1 pt"
                },
                color = if (opponent.stake > 1) Alert else TextFaint,
            )
        }
    }

    val youFraction: Float
    val twinFraction: Float
    if (mode == TrackMode.OUTDOOR) {
        youFraction = if (referenceDistance != null && referenceDistance > 0) (snapshot.distanceMeters / referenceDistance).toFloat() else 0f
        twinFraction = if (referenceFinish != null && referenceFinish > 0) snapshot.movingSeconds.toFloat() / referenceFinish else 0f
    } else {
        // Indoor: no live distance. Both markers advance by time, so the track is just a clock.
        twinFraction = if (referenceFinish != null && referenceFinish > 0) snapshot.elapsedSeconds.toFloat() / referenceFinish else 0f
        youFraction = twinFraction
    }
    if (mode == TrackMode.OUTDOOR) {
        LiveTrack(youFraction = youFraction, twinFraction = twinFraction, twinColor = twinColor)
        TrackLegend(youLabel = "You", twinLabel = name, twinColor = twinColor)
    }

    if (mode == TrackMode.OUTDOOR) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(if (showSpeed) "Your speed" else "Your pace", snapshot.currentPaceSecPerKm?.let { paceOrSpeed(it) } ?: "—", accent = Brass, sub = snapshot.avgPaceSecPerKm?.let { "last 200 m · avg ${paceOrSpeed(it)}" })
            StatTile(
                if (isDuel) "Target" else if (opponent?.isHorde == true) "Horde" else name,
                referencePace?.let { paceOrSpeed(it) } ?: "—",
                accent = twinColor,
                sub = if (showSpeed) "their speed" else "their pace",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Distance", "%.2f km".format(snapshot.distanceMeters / 1000.0), sub = referenceDistance?.let { "of %.2f km".format(it / 1000.0) })
            StatTile("Moving", formatElapsed(snapshot.movingSeconds), sub = referenceFinish?.let { "${if (isDuel) "target" else "forecast"} ${formatDuration(it)}" })
        }
        if (snapshot.gpsFixCount == 0) {
            Text("Waiting for a GPS fix…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // --- Divergence card: where you stand against the forecast, live ---
        val pace = snapshot.currentPaceSecPerKm
        if (pace != null && referencePace != null && opponent != null) {
            val gap = referencePace - pace // > 0 → you're faster
            val ahead = gap > 0
            val tone = when {
                abs(gap) < 3 -> PanelTone.NEUTRAL
                ahead -> PanelTone.GOOD
                else -> PanelTone.WARN
            }
            val projected = FidelityReplay.step(opponent.fidelity, FidelityReplay.sessionFidelity(referencePace, pace))
            val impactPoints = (projected - opponent.fidelity) * 100f
            val metric = if (opponent.isHorde) "Proximity" else "Fidelity"
            CorpoPanel(tone = tone) {
                SectionLabel(
                    when {
                        abs(gap) < 3 -> "On forecast"
                        ahead -> "Divergence detected"
                        else -> "Behind forecast"
                    },
                    color = when (tone) { PanelTone.GOOD -> Good; PanelTone.WARN -> Alert; else -> MaterialTheme.colorScheme.onSurfaceVariant },
                )
                Text(
                    when {
                        abs(gap) < 3 -> "Exactly what $name expected. Do something it didn't."
                        ahead -> "${formatGap(gap)} faster than $name's forecast."
                        else -> "${formatGap(-gap)} slower than $name's forecast."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FootNote(
                    "$metric impact: ${formatSignedTenths(impactPoints)} live",
                    color = if (impactPoints <= -0.05f) Good else if (impactPoints >= 0.05f) Alert else TextFaint,
                )
            }
        } else if (!isDuel && opponent?.forecastPaceSecPerKm == null) {
            CorpoPanel {
                Text("No forecast yet — $name is only watching this one.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // --- Split ladder: every completed kilometre against the model ---
        projection?.splits?.takeIf { it.isNotEmpty() }?.let { splits ->
            CorpoPanel {
                SectionLabel("Splits · you vs. ${if (opponent?.isHorde == true) "horde" else name}")
                splits.asReversed().take(6).forEach { sp ->
                    val d = sp.modelSeconds - sp.yourSeconds
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("KM ${sp.km}", style = MaterialTheme.typography.labelSmall, color = TextFaint, modifier = Modifier.width(52.dp))
                        Text(formatElapsed(sp.yourSeconds), style = MaterialTheme.typography.titleMedium, color = Brass, modifier = Modifier.weight(1f))
                        Text(formatElapsed(sp.modelSeconds), style = MaterialTheme.typography.titleMedium, color = twinColor, modifier = Modifier.weight(1f))
                        Text((if (d >= 0) "−" else "+") + formatElapsed(kotlin.math.abs(d)), style = MaterialTheme.typography.titleMedium, color = if (d >= 0) Good else Alert)
                    }
                }
                FootNote("Your split · their split · difference")
            }
        }

        // --- Comms: the opponent talking mid-session, newest on top ---
        if (callouts.isNotEmpty()) {
            CorpoPanel {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionLabel("Comms · $name", color = twinColor)
                    FootNote("${callouts.size} callouts")
                }
                callouts.asReversed().take(3).forEach { c ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Text(
                            formatElapsed(c.atSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextFaint,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                        Text(
                            c.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when (c.zone) {
                                dev.eversorhn.gait.domain.live.LiveZone.BEHIND -> Alert
                                dev.eversorhn.gait.domain.live.LiveZone.AHEAD -> MaterialTheme.colorScheme.onSurfaceVariant
                                dev.eversorhn.gait.domain.live.LiveZone.LEVEL -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Elapsed", formatElapsed(snapshot.elapsedSeconds), accent = Brass)
            StatTile("$name finish", referenceFinish?.let { formatDuration(it) } ?: "—", accent = twinColor)
        }
        Text("Indoor session — distance logged on stop.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    snapshot.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun StopNotice(message: String) {
    CorpoPanel(tone = PanelTone.WARN) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

private fun formatGap(secPerKm: Double): String {
    val total = secPerKm.toInt()
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}/km"
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
