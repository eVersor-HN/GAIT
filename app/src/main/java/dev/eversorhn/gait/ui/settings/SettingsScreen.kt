package dev.eversorhn.gait.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.R
import dev.eversorhn.gait.domain.horde.HordeIntensity
import dev.eversorhn.gait.ui.gaitViewModel
import dev.eversorhn.gait.ui.theme.pressable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import dev.eversorhn.gait.ui.theme.FootNote
import dev.eversorhn.gait.ui.theme.SectionLabel
import kotlinx.coroutines.launch
import dev.eversorhn.gait.ui.theme.ScreenTitle
import dev.eversorhn.gait.ui.theme.CorpoButton
import dev.eversorhn.gait.ui.theme.CorpoChip
import dev.eversorhn.gait.ui.theme.ButtonKind
import dev.eversorhn.gait.ui.theme.CorpoPanel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onDone: () -> Unit, onWiped: () -> Unit) {
    val viewModel: SettingsViewModel = gaitViewModel()
    val state by viewModel.uiState.collectAsState()
    var confirmSwitch by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }

    LaunchedEffect(state.wiped) { if (state.wiped) onWiped() }
    LaunchedEffect(state.savedTick) { if (state.savedTick > 0) showSaved = true }

    if (confirmSwitch) {
        dev.eversorhn.gait.ui.theme.CorpoDialog(
            title = if (state.isHorde) "Switch to a Rival Twin?" else "Release the Horde?",
            body = "This is a new opponent: ${if (state.isHorde) "Fidelity and Generation" else "Proximity and Wave"} start over. Your session history is untouched — it's still yours.",
            onDismiss = { confirmSwitch = false },
            confirmText = "Switch opponent",
            onConfirm = { confirmSwitch = false; viewModel.switchOpponentType() },
            confirmKind = dev.eversorhn.gait.ui.theme.ButtonKind.RISK,
        )
    }

    if (confirmWipe) {
        dev.eversorhn.gait.ui.theme.CorpoDialog(
            title = "Erase everything?",
            body = "Every session, message, planned day and the opponent profile on this device. The division's roster stays (it's a simulation); your asset is gone. This can't be undone.",
            onDismiss = { confirmWipe = false },
            confirmText = "Erase all data",
            onConfirm = { confirmWipe = false; viewModel.wipeEverything() },
            confirmKind = dev.eversorhn.gait.ui.theme.ButtonKind.RISK,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Settings", "Your opponent, your terms")

        if (!state.loaded) {
            Text("Loading…", style = MaterialTheme.typography.bodyLarge)
            return@Column
        }

        CorpoPanel {
            Text(
                if (state.isHorde) "ZOMBIE HORDE" else "RIVAL TWIN",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "${if (state.isHorde) "Wave" else "Generation"} ${state.generation} · " +
                    "${if (state.isHorde) "Proximity" else "Fidelity"} ${state.metricPercent}% · " +
                    pluralStringResource(R.plurals.sessions_count, state.sessionCount, state.sessionCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.isHorde) {
            dev.eversorhn.gait.ui.theme.CollapsiblePanel(title = "Intensity", summary = HordeIntensity.label(state.hordeIntensity)) {
                dev.eversorhn.gait.ui.theme.Segmented(
                    options = HordeIntensity.all.map { HordeIntensity.label(it) },
                    selected = HordeIntensity.all.indexOf(state.hordeIntensity).coerceAtLeast(0),
                    onSelect = { viewModel.selectIntensity(HordeIntensity.all[it]) },
                )
                FootNote("Only changes how fast the horde closes")
            }
        } else {
            dev.eversorhn.gait.ui.theme.CollapsiblePanel(title = "Name", summary = state.twinName) {
                OutlinedTextField(
                    value = state.twinName,
                    onValueChange = viewModel::updateName,
                    label = { Text("Opponent name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                FootNote("Shown on the board, the ledger and every forecast")
            }
        }

        // --- Activity: every activity has its own opponent profile; switching to one without a profile goes to setup ---
        val appRepo = (androidx.compose.ui.platform.LocalContext.current.applicationContext as dev.eversorhn.gait.GaitApplication).repository
        var activeActivity by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(appRepo.activeActivityType) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()

        // --- Asset transfer: export the division's file on you; import someone else's asset ---
        val appCtx = androidx.compose.ui.platform.LocalContext.current
        // --- Heart rate: a strap or watch, read over the standard Bluetooth service ---
        run {
            val monitor = androidx.compose.runtime.remember { dev.eversorhn.gait.sensors.HeartRateMonitor(appCtx) }
            var paired by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(dev.eversorhn.gait.sensors.HeartRatePrefs.name(appCtx))
            }
            var scanning by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            val found = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateListOf<dev.eversorhn.gait.sensors.HeartRateMonitor.Found>() }
            val btLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
            ) { grants ->
                if (grants.values.all { it }) {
                    scanning = true; found.clear()
                    monitor.scan(onFound = { found += it }, onDone = { scanning = false })
                }
            }
            fun startScan() {
                val needed = if (android.os.Build.VERSION.SDK_INT >= 31) {
                    arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
                }
                btLauncher.launch(needed)
            }
            if (monitor.isSupported()) {
                dev.eversorhn.gait.ui.theme.CollapsiblePanel(
                    title = "Heart rate",
                    summary = paired?.let { "Paired · $it" } ?: "No monitor paired",
                ) {
                    Text(
                        "A chest strap or watch that speaks the standard Bluetooth heart-rate service. " +
                            "Pace says how fast you went; this says what it cost. Read during the session only.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (paired != null) {
                        CorpoButton("Forget monitor", onClick = {
                            dev.eversorhn.gait.sensors.HeartRatePrefs.forget(appCtx); paired = null
                        }, kind = ButtonKind.RISK, modifier = Modifier.fillMaxWidth())
                    } else {
                        CorpoButton(
                            if (scanning) "Searching…" else "Search for a monitor",
                            onClick = { startScan() },
                            enabled = !scanning,
                            kind = ButtonKind.SAFE,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        found.forEach { device ->
                            dev.eversorhn.gait.ui.enrol.SelectRow(
                                title = device.name,
                                detail = device.address,
                                selected = false,
                                onClick = {
                                    dev.eversorhn.gait.sensors.HeartRatePrefs.remember(appCtx, device.address, device.name)
                                    paired = device.name
                                },
                            )
                        }
                        if (!scanning && found.isEmpty()) {
                            FootNote("Put the strap on first — most only advertise once they read a pulse.")
                        }
                    }
                }
            }
        }

        // --- Health Connect: import recordings other apps made ---
        if (dev.eversorhn.gait.health.HealthImport.isAvailable(appCtx)) {
            var hcNote by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            val hcPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
            ) { granted ->
                if (granted.containsAll(dev.eversorhn.gait.health.HealthImport.PERMISSIONS)) {
                    scope.launch {
                        val r = dev.eversorhn.gait.health.HealthImport.importRecent(appCtx, appRepo)
                        hcNote = r.error?.let { "Import failed: $it" } ?: "${r.imported} imported · ${r.skipped} skipped (already known / no distance)"
                    }
                } else hcNote = "Permission not granted."
            }
            dev.eversorhn.gait.ui.theme.CollapsiblePanel(title = "Health Connect", summary = "Import last 30 days of ${dev.eversorhn.gait.domain.activity.Activities.byKey(appRepo.activeActivityType).label.lowercase()} from other apps") {
                Text(
                    "Reads exercise sessions and distance from Health Connect (watch, other tracker apps) and adds the ones GAIT doesn't have — as baseline material, tagged HEALTH.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CorpoButton("Import from Health Connect", onClick = {
                    scope.launch {
                        if (dev.eversorhn.gait.health.HealthImport.hasPermissions(appCtx)) {
                            val r = dev.eversorhn.gait.health.HealthImport.importRecent(appCtx, appRepo)
                            hcNote = r.error?.let { "Import failed: $it" } ?: "${r.imported} imported · ${r.skipped} skipped"
                        } else hcPermLauncher.launch(dev.eversorhn.gait.health.HealthImport.PERMISSIONS)
                    }
                }, kind = ButtonKind.SAFE, modifier = Modifier.fillMaxWidth())
                hcNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) }

                var exportOn by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(dev.eversorhn.gait.health.ExportPrefs.isEnabled(appCtx)) }
                val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
                ) { granted ->
                    val ok = granted.containsAll(dev.eversorhn.gait.health.HealthExport.PERMISSIONS)
                    dev.eversorhn.gait.health.ExportPrefs.setEnabled(appCtx, ok)
                    exportOn = ok
                }
                dev.eversorhn.gait.ui.theme.CorpoSwitch(
                    label = "Write sessions back",
                    description = "Every session you finish in GAIT is written to Health Connect, so your rings, your watch and your other apps see the same run. Sessions imported from there are never written back.",
                    checked = exportOn,
                    onChange = { want ->
                        if (want) exportLauncher.launch(dev.eversorhn.gait.health.HealthExport.PERMISSIONS)
                        else { dev.eversorhn.gait.health.ExportPrefs.setEnabled(appCtx, false); exportOn = false }
                    },
                )
            }
        }

        dev.eversorhn.gait.ui.theme.CollapsiblePanel(title = "Asset transfer", summary = "Export the division's file on you · import someone else's asset") {
        Text(
            "Export the division's assessment of you as a text block. Another GAIT can import it: your asset then lives on in their division — climbs, gets reviewed, can be culled. Import someone's block below to take their asset into yours.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        var exportName by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        OutlinedTextField(
            value = exportName,
            onValueChange = { exportName = it },
            label = { Text("Your name on the transfer") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        var importText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        var importNote by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
        var importedList by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<dev.eversorhn.gait.data.db.entity.ImportedAssetEntity>>(emptyList()) }
        androidx.compose.runtime.LaunchedEffect(Unit) { importedList = appRepo.getImportedAssets() }
        CorpoButton("Export my asset · share", onClick = {
            scope.launch {
                val profile = appRepo.getTwinProfile() ?: return@launch
                val sessions = appRepo.getSessions()
                val zone = java.time.ZoneId.systemDefault()
                val today = java.time.LocalDate.now(zone).toEpochDay()
                val enrolled = java.time.Instant.ofEpochMilli(appRepo.earliestEnrolmentEpochMillis() ?: profile.createdAtEpochMillis).atZone(zone).toLocalDate().toEpochDay()
                val asset = dev.eversorhn.gait.domain.transfer.AssetTransfer.assess(
                    sessions, dev.eversorhn.gait.domain.ledger.Ledger.from(sessions), (profile.fidelity * 100).toInt(),
                    exportName.ifBlank { "Asset vs. ${profile.twinName}" },
                    dev.eversorhn.gait.domain.roster.AssetKind.HUMAN_M, enrolled, today, profile.restDayMask, zone,
                )
                val text = dev.eversorhn.gait.domain.transfer.AssetTransfer.encode(asset)
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "GAIT asset transfer · ${asset.id}")
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                }
                appCtx.startActivity(android.content.Intent.createChooser(send, "Send your asset").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }, kind = ButtonKind.SAFE, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = importText,
            onValueChange = { importText = it },
            label = { Text("Paste a GAIT-ASSET block") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
        CorpoButton("Import asset", onClick = {
            val parsed = dev.eversorhn.gait.domain.transfer.AssetTransfer.decode(importText)
            if (parsed == null) { importNote = "That isn't a GAIT-ASSET block."; return@CorpoButton }
            scope.launch {
                appRepo.importAsset(parsed.id, parsed.name, importText, java.time.LocalDate.now().toEpochDay())
                importedList = appRepo.getImportedAssets()
                importText = ""
                importNote = "${parsed.name} joined your division as ${parsed.id}. ${parsed.assessment}"
            }
        }, kind = ButtonKind.RISK, modifier = Modifier.fillMaxWidth(), enabled = importText.isNotBlank())
        importNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) }
        if (importedList.isNotEmpty()) {
            Text("In your division", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            importedList.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${row.id} · ${row.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    CorpoChip(label = "Remove", active = false, onClick = { scope.launch { appRepo.deleteImportedAsset(row.id); importedList = appRepo.getImportedAssets() } })
                }
            }
        }

        }

        // --- Voice: the spoken commentator during a session ---
        var voiceOn by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(dev.eversorhn.gait.audio.VoicePrefs.isEnabled(appCtx)) }
        CorpoPanel {
            SectionLabel("Sound & notifications")
            var hapticsOn by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(dev.eversorhn.gait.audio.HapticPrefs.isEnabled(appCtx)) }
            dev.eversorhn.gait.ui.theme.CorpoSwitch(
                label = "Pocket feedback",
                description = "A tick at every kilometre, a knock when the lead changes, and a pulse that tightens as the horde closes.",
                checked = hapticsOn,
                onChange = { dev.eversorhn.gait.audio.HapticPrefs.setEnabled(appCtx, it); hapticsOn = it },
            )
            dev.eversorhn.gait.ui.theme.CorpoSwitch(
                label = "Spoken readout",
                description = "Reads the live figures aloud at kilometre marks and lead changes. Ducks your music.",
                checked = voiceOn,
                onChange = { dev.eversorhn.gait.audio.VoicePrefs.setEnabled(appCtx, it); voiceOn = it },
            )

        // --- Notifications: the exit dialog can mute them; this is where they come back ---
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var muted by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(dev.eversorhn.gait.notification.NotificationPrefs.isMuted(ctx)) }
            dev.eversorhn.gait.ui.theme.CorpoSwitch(
                label = "Notifications",
                description = if (muted) "Muted — nothing reaches the shade." else "Session tracking and review deadlines reach you outside the app.",
                checked = !muted,
                onChange = { dev.eversorhn.gait.notification.NotificationPrefs.setMuted(ctx, !it); muted = !it },
            )
        }

        if (dev.eversorhn.gait.BuildConfig.DEBUG) {
            // Debug builds only: shortcuts for exercising the rare states without weeks of running.
            Text("Developer (debug build)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CorpoButton("Fidelity → 96 %", onClick = { scope.launch { appRepo.getTwinProfile()?.let { appRepo.updateTwinProfile(it.copy(fidelity = 0.96f)) }; onDone() } }, kind = ButtonKind.GHOST, modifier = Modifier.weight(1f))
                CorpoButton("Seed 6 sessions", onClick = {
                    scope.launch {
                        val now = System.currentTimeMillis()
                        repeat(6) { i ->
                            val pace = 320.0 + (i % 3) * 6 - (i / 3) * 4
                            appRepo.logSession(
                                dev.eversorhn.gait.data.db.entity.SessionEntity(
                                    activityType = appRepo.activeActivityType,
                                    startTimeEpochMillis = now - (6 - i) * 86_400_000L,
                                    dayOfWeek = ((java.time.Instant.ofEpochMilli(now - (6 - i) * 86_400_000L).atZone(java.time.ZoneId.systemDefault()).dayOfWeek.value)),
                                    durationSeconds = (pace * 5).toInt(),
                                    distanceMeters = 5000.0,
                                    avgPaceSecPerKm = pace,
                                    forecastPaceSecPerKm = if (i == 0) null else 326.0,
                                    forecastFinishSeconds = if (i == 0) null else 1630,
                                    dataSource = dev.eversorhn.gait.data.db.entity.SessionSource.MANUAL,
                                    stake = 1,
                                )
                            )
                        }
                        onDone()
                    }
                }, kind = ButtonKind.GHOST, modifier = Modifier.weight(1f))
            }
        }

        CorpoButton("Save changes", onClick = viewModel::save, kind = ButtonKind.PRIMARY, modifier = Modifier.fillMaxWidth())
        if (showSaved) {
            Text("Saved.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }

        CorpoButton(if (state.isHorde) "Switch to Rival Twin" else "Switch to Zombie Horde", onClick = { confirmSwitch = true }, kind = ButtonKind.SAFE, modifier = Modifier.fillMaxWidth())

        // --- Demo data: see the app lived-in without six weeks of running ---
        var demoNote by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
        var demoLoaded by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(dev.eversorhn.gait.domain.demo.DemoSeeder.isLoaded(appCtx, appRepo.activeProfileId))
        }
        dev.eversorhn.gait.ui.theme.CollapsiblePanel(
            title = "Demo data",
            summary = if (demoLoaded) "Loaded — can be removed again" else "Load six weeks of sample history",
        ) {
            Text(
                "Adds ~26 sample sessions (rounds, stakes, a won duel, routes, rest days) so you can see every screen with data. " +
                    "Removing takes back exactly what it added — anything you recorded yourself stays.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (demoLoaded) {
                CorpoButton("Remove demo data", onClick = {
                    scope.launch {
                        val n = dev.eversorhn.gait.domain.demo.DemoSeeder.remove(appRepo, appCtx)
                        viewModel.refresh()
                        demoLoaded = false
                        demoNote = "Removed $n sample ${if (n == 1) "session" else "sessions"}."
                    }
                }, kind = ButtonKind.RISK, modifier = Modifier.fillMaxWidth())
            } else {
                CorpoButton("Load demo data", onClick = {
                    scope.launch {
                        dev.eversorhn.gait.domain.demo.DemoSeeder.seed(appRepo, appCtx)
                        viewModel.refresh()
                        demoLoaded = true
                        demoNote = "Loaded. Swipe to the Board and Forecast."
                    }
                }, kind = ButtonKind.SAFE, modifier = Modifier.fillMaxWidth())
            }
            demoNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) }
        }

        dev.eversorhn.gait.ui.theme.CollapsiblePanel(title = "Danger zone", summary = "Erase everything on this device", tone = dev.eversorhn.gait.ui.theme.PanelTone.WARN) {
            CorpoButton("Erase all data", onClick = { confirmWipe = true }, kind = ButtonKind.RISK, modifier = Modifier.fillMaxWidth())
        }

        CorpoButton("Back", onClick = onDone, kind = ButtonKind.GHOST, modifier = Modifier.fillMaxWidth())
    }
}
