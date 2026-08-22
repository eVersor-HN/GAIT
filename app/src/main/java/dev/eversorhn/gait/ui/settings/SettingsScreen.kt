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
import dev.eversorhn.gait.domain.persona.Personas
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
                FootNote("Only changes how the horde sounds when it's closing in")
            }
        } else {
            val currentPersona = Personas.byKey(state.personaKey)
            dev.eversorhn.gait.ui.theme.CollapsiblePanel(title = "Name & voice", summary = "${state.twinName} · ${currentPersona.label}") {
                OutlinedTextField(
                    value = state.twinName,
                    onValueChange = viewModel::updateName,
                    label = { Text("Twin name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionLabel("Voice · 17 personas")
                // A list with a radio mark, not a cloud of chips: the eye scans top-down, the selected one is obvious.
                Personas.mvpRoster.forEach { persona ->
                    val selected = state.personaKey == persona.key
                    Row(
                        modifier = Modifier.fillMaxWidth().pressable(onClick = { viewModel.selectPersona(persona.key) }).padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        androidx.compose.foundation.layout.Box(Modifier.size(14.dp).border(androidx.compose.foundation.BorderStroke(1.dp, if (selected) dev.eversorhn.gait.ui.theme.Brass else dev.eversorhn.gait.ui.theme.LineSoft), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                            if (selected) androidx.compose.foundation.layout.Box(Modifier.size(8.dp).background(dev.eversorhn.gait.ui.theme.Brass, androidx.compose.foundation.shape.CircleShape))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(persona.label, style = MaterialTheme.typography.titleMedium, color = if (selected) dev.eversorhn.gait.ui.theme.Brass else MaterialTheme.colorScheme.onSurface)
                            Text(persona.predatoryLines.first(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // --- Activity: every activity has its own opponent profile; switching to one without a profile goes to setup ---
        val appRepo = (androidx.compose.ui.platform.LocalContext.current.applicationContext as dev.eversorhn.gait.GaitApplication).repository
        var activeActivity by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(appRepo.activeActivityType) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        dev.eversorhn.gait.ui.theme.CollapsiblePanel(title = "Activity", summary = dev.eversorhn.gait.domain.activity.Activities.byKey(activeActivity).label + " · own opponent, ledger and history per activity") {
            // 2 × 4 grid of equal chips.
            dev.eversorhn.gait.domain.activity.Activities.all.chunked(4).forEach { rowActs ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowActs.forEach { a ->
                        CorpoChip(label = a.label, active = a.key == activeActivity, modifier = Modifier.weight(1f), onClick = {
                            if (a.key == activeActivity) return@CorpoChip
                            scope.launch {
                                appRepo.activeActivityType = a.key
                                activeActivity = a.key
                                val hasProfile = appRepo.getTwinProfile(a.key) != null
                                if (hasProfile) onDone() else onWiped()
                            }
                        })
                    }
                    repeat(4 - rowActs.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                }
            }
            FootNote("Switching to an activity without a profile starts its setup")
        }

        // --- Asset transfer: export the division's file on you; import someone else's asset ---
        val appCtx = androidx.compose.ui.platform.LocalContext.current
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
            dev.eversorhn.gait.ui.theme.CorpoSwitch(
                label = "Live commentator",
                description = "Kilometre marks, lead changes, a status line every couple of minutes. Ducks your music.",
                checked = voiceOn,
                onChange = { dev.eversorhn.gait.audio.VoicePrefs.setEnabled(appCtx, it); voiceOn = it },
            )

        // --- Notifications: the exit dialog can mute them; this is where they come back ---
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var muted by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(dev.eversorhn.gait.notification.NotificationPrefs.isMuted(ctx)) }
            dev.eversorhn.gait.ui.theme.CorpoSwitch(
                label = "Notifications",
                description = if (muted) "Muted — messages still land in the Direct Channel." else "Same-day reactions, stakes, division notices reach you outside the app.",
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

        dev.eversorhn.gait.ui.theme.CollapsiblePanel(title = "Danger zone", summary = "Erase everything on this device", tone = dev.eversorhn.gait.ui.theme.PanelTone.WARN) {
            CorpoButton("Erase all data", onClick = { confirmWipe = true }, kind = ButtonKind.RISK, modifier = Modifier.fillMaxWidth())
        }

        CorpoButton("Back", onClick = onDone, kind = ButtonKind.GHOST, modifier = Modifier.fillMaxWidth())
    }
}
