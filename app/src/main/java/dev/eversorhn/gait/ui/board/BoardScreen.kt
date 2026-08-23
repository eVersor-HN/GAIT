package dev.eversorhn.gait.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.domain.roster.AssetKind
import dev.eversorhn.gait.domain.roster.AssetStatus
import dev.eversorhn.gait.domain.roster.RosterEngine
import dev.eversorhn.gait.domain.roster.RosterSnapshot
import dev.eversorhn.gait.domain.roster.Standing
import dev.eversorhn.gait.ui.gaitViewModel
import dev.eversorhn.gait.ui.theme.Alert
import dev.eversorhn.gait.ui.theme.Brass
import dev.eversorhn.gait.ui.theme.ButtonKind
import dev.eversorhn.gait.ui.theme.CorpoButton
import dev.eversorhn.gait.ui.theme.CorpoPanel
import dev.eversorhn.gait.ui.theme.Cyan
import dev.eversorhn.gait.ui.theme.FootNote
import dev.eversorhn.gait.ui.theme.Good
import dev.eversorhn.gait.ui.theme.Ink
import dev.eversorhn.gait.ui.theme.Ink2
import dev.eversorhn.gait.ui.theme.LineSoft
import dev.eversorhn.gait.ui.theme.PanelTone
import dev.eversorhn.gait.ui.theme.Meter
import dev.eversorhn.gait.ui.theme.pressable
import dev.eversorhn.gait.ui.theme.ScreenTitle
import dev.eversorhn.gait.ui.theme.SectionLabel
import dev.eversorhn.gait.ui.theme.Sparkline
import dev.eversorhn.gait.ui.theme.StatTile
import dev.eversorhn.gait.ui.theme.TextDim
import dev.eversorhn.gait.ui.theme.TextFaint
import dev.eversorhn.gait.ui.theme.TextPrimary
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * The first thing you see when the app opens with an opponent on file.
 * Twin: the division's Asset Board — the top 15 by Retention Index, ▲▼ against yesterday's
 * close, your own row pinned with your rank; tap any row for the division's file on them.
 * Horde: the containment map — you ahead, the decommissioned (the horde) behind you.
 * Re-evaluates every minute while open: a row moves when that asset's result lands.
 * If a quarterly cull caught you in the bottom 400, this is the termination notice.
 */
@Composable
fun BoardScreen(
    onGo: () -> Unit,
    onStartDuel: () -> Unit,
    onEnrolNew: () -> Unit,
    onProfiles: () -> Unit,
) {
    val viewModel: BoardViewModel = gaitViewModel()
    val state by viewModel.uiState.collectAsState()

    state.dossier?.let { d ->
        DossierDialog(d, state.dossierStanding, state.snapshot?.day ?: 0L, onClose = viewModel::closeDossier)
    }

    val snap = state.snapshot
    if (state.isHorde && state.termination == null) {
        dev.eversorhn.gait.ui.horde.HordeScreen(
            snapshot = snap,
            proximityPercent = state.proximityPercent,
            separationMeters = state.separationMeters,
            releasedTotal = state.releasedTotal,
            daysSinceLast = state.daysSinceLast,
            signals = state.transmissions,
            tenureDays = state.career?.tenureDays ?: 0L,
            onGo = onGo,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when {
            !state.loaded || snap == null -> {
                CorpoButton("GO", onClick = onGo, kind = ButtonKind.PRIMARY, modifier = Modifier.fillMaxWidth())
                ScreenTitle("Standings", "Compiling…")
                FootNote("First open of the day: the division re-runs the whole roster")
            }
            state.termination != null -> TerminationNotice(state.termination!!, snap, state.career, onEnrolNew = { viewModel.enrolNewAsset(onEnrolNew) })
            else -> Board(
                snap = snap,
                state = state,
                onGo = onGo,
                onStartDuel = onStartDuel,
                onProfiles = onProfiles,
                onRow = viewModel::openDossier,
            )
        }
    }
}

// ------------------------------------------------------------------ Twin: the board

@Composable
private fun Board(
    snap: RosterSnapshot,
    state: BoardUiState,
    onGo: () -> Unit,
    onStartDuel: () -> Unit,
    onProfiles: () -> Unit,
    onRow: (Int) -> Unit,
) {
    val opponentName = state.opponentName
    val u = snap.user
    val safe = u.rank <= snap.cullLine
    val protectedDaysLeft = state.career?.let { (RosterEngine.CULL_GRACE_DAYS - it.tenureDays).coerceAtLeast(0) } ?: 0

    // --- The one action, first ---
    CorpoButton(if (state.trialEligible) "GO · TRIAL OPEN" else "GO", onClick = onGo, kind = ButtonKind.PRIMARY, modifier = Modifier.fillMaxWidth())

    // --- Where you stand against the model, in one line of numbers ---
    CorpoPanel {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FootNote("You", maxLines = 1)
                Text("${state.userPoints}", style = MaterialTheme.typography.headlineLarge, color = Brass)
            }
            Column(Modifier.weight(1f)) {
                FootNote(opponentName, maxLines = 1)
                Text("${state.opponentPoints}", style = MaterialTheme.typography.headlineLarge, color = Cyan)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1.2f)) {
                FootNote("${state.roundsPlayed} rounds", maxLines = 1)
                dev.eversorhn.gait.ui.theme.FormDots(form = state.form, twinColor = Cyan)
            }
        }
        FootNote(state.standingLine, maxLines = 1, color = if (state.userPoints >= state.opponentPoints) Brass else Alert)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Rank", "#${u.rank}", accent = if (safe) Brass else Alert, sub = "of ${"%,d".format(snap.enrolled)}")
        StatTile("Index", "${u.index}", accent = if (u.delta >= 0) Brass else Alert, sub = signed(u.delta) + " today")
        StatTile("Next cull", if (snap.nextCullInDays == 0) "today" else "${snap.nextCullInDays} d", accent = if (safe) MaterialTheme.colorScheme.onSurface else Alert, sub = "line #${snap.cullLine}")
    }

    // --- You and the model, same structure ---
    CorpoPanel(tone = if (safe || protectedDaysLeft > 0) PanelTone.NEUTRAL else PanelTone.WARN) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("#${u.rank}", style = MaterialTheme.typography.titleLarge, color = Brass, modifier = Modifier.width(72.dp))
            Arrow(u.prevRank?.let { it - u.rank } ?: 0)
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text("YOU", style = MaterialTheme.typography.titleMedium, color = Brass)
                FootNote(
                    when {
                        u.rank <= 15 -> "On the board"
                        !safe && protectedDaysLeft > 0 -> "New hire · protected $protectedDaysLeft d"
                        !safe -> "Below the line · ${snap.nextCullInDays} d"
                        else -> "${snap.cullLine - u.rank} above the line"
                    },
                    maxLines = 1,
                    color = if (safe || protectedDaysLeft > 0) TextFaint else Alert,
                )
            }
            IndexCell(u.index, u.delta)
        }
    }
    snap.twin?.let { t ->
        CorpoPanel {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("#${t.rank}", style = MaterialTheme.typography.titleLarge, color = Cyan, modifier = Modifier.width(72.dp))
                Arrow(t.prevRank?.let { it - t.rank } ?: 0)
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(opponentName.uppercase(), style = MaterialTheme.typography.titleMedium, color = Cyan)
                    FootNote(
                        when {
                            t.rank < u.rank -> "Ahead of you by ${u.rank - t.rank}"
                            t.rank > u.rank -> "${t.rank - u.rank} behind you"
                            else -> "Level with you"
                        },
                        maxLines = 1,
                        color = if (t.rank < u.rank) Alert else TextFaint,
                    )
                }
                IndexCell(t.index, t.delta)
            }
        }
    }

    // --- Substitution review ---
    if (state.trialEligible) {
        CorpoPanel(tone = PanelTone.WARN) {
            SectionLabel("Substitution review", color = Alert)
            Text("$opponentName — ${state.proximityPercent}%", style = MaterialTheme.typography.titleLarge, color = Alert)
            Meter(fraction = state.proximityPercent / 100f, color = Alert, threshold = 0.95f)
            CorpoButton("Contest · 3 pts", onClick = onStartDuel, kind = ButtonKind.RISK, modifier = Modifier.fillMaxWidth())
            FootNote(state.trialDeadlineDays?.let { if (it == 0) "Auto-review today" else "Auto-review in $it d" } ?: "Beat your best over 1 km", color = Alert)
        }
    }

    // --- The table ---
    var tableOpen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val tableRows = if (tableOpen) 15 else 5
    CorpoPanel {
        Row(
            modifier = Modifier.fillMaxWidth().pressable(onClick = { tableOpen = !tableOpen }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("#", color = TextFaint); Spacer(Modifier.width(40.dp))
            SectionLabel("Asset", color = TextFaint)
            Spacer(Modifier.weight(1f))
            SectionLabel("Index · Δ", color = TextFaint)
            Text(if (tableOpen) "  –" else "  +", style = MaterialTheme.typography.labelLarge, color = TextFaint)
        }
        val twinRow = snap.twin
        var placed = 0
        var i = 0
        var userPlaced = false
        var twinPlaced = false
        while (placed < tableRows) {
            val next = snap.standings.getOrNull(i)
            val nextRank = next?.rank ?: Int.MAX_VALUE
            when {
                !userPlaced && u.rank < nextRank && (twinRow == null || twinPlaced || u.rank < twinRow.rank) -> { UserRowInline(u.rank, u.delta, u.index, u.prevRank); userPlaced = true }
                twinRow != null && !twinPlaced && twinRow.rank < nextRank -> { TwinRowInline(opponentName, twinRow); twinPlaced = true }
                next != null -> { StandingRow(next, onClick = { onRow(next.asset.slot) }); i++ }
                else -> break
            }
            placed++
        }
        Row(
            modifier = Modifier.fillMaxWidth().pressable(onClick = { tableOpen = !tableOpen }),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FootNote("${if (snap.nextReviewInDays == 0) "Review today" else "Review in ${snap.nextReviewInDays} d"} · ${snap.onLeave} on leave", maxLines = 1)
            FootNote(if (tableOpen) "show 5" else "show 15", color = Brass, maxLines = 1)
        }
    }

    // --- The last rounds, as they landed ---
    if (state.transmissions.isNotEmpty()) {
        CorpoPanel {
            SectionLabel("Recent rounds")
            state.transmissions.take(3).forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    // --- Movers (folded; the biggest one is the summary) ---
    if (snap.movers.isNotEmpty()) {
        val top = snap.movers.first()
        dev.eversorhn.gait.ui.theme.CollapsiblePanel(
            title = "Movers today",
            summary = "${if (top.delta > 0) "▲" else "▼"} ${top.asset.name} ${signed(top.delta)} · and ${snap.movers.size - 1} more",
        ) {
            snap.movers.take(5).forEach { m ->
                Row(
                    modifier = Modifier.fillMaxWidth().pressable(onClick = { onRow(m.asset.slot) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Arrow(m.delta)
                    Text(m.asset.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("#${m.rank}", style = MaterialTheme.typography.labelSmall, color = TextFaint, modifier = Modifier.padding(end = 10.dp))
                    Text(signed(m.delta), style = MaterialTheme.typography.titleMedium, color = if (m.delta > 0) Good else Alert)
                }
            }
        }
    }

    // --- Decommissioned (folded) ---
    if (snap.decommissioned.isNotEmpty()) {
        dev.eversorhn.gait.ui.theme.CollapsiblePanel(
            title = "Decommissioned · recent",
            summary = "${snap.decommissioned30d} this month · last: ${snap.decommissioned.first().asset.name}",
            tone = PanelTone.WARN,
        ) {
            snap.decommissioned.take(3).forEach { d ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("${d.asset.id} · ${d.asset.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    FootNote("${snap.day - d.day}d ago · ${d.lastIndex}", maxLines = 1)
                }
            }
        }
    }

    state.career?.let { c ->
        FootNote("Tenure ${c.tenureDays} d · survived ${c.cullsSurvived} ${if (c.cullsSurvived == 1) "cull" else "culls"} · best streak ${c.bestStreak}", maxLines = 1)
    }
    CorpoButton("Enrolments", onClick = onProfiles, kind = ButtonKind.GHOST, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun StandingRow(row: Standing, onClick: () -> Unit) {
    val rankDelta = row.prevRank?.let { it - row.rank } ?: 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${row.rank}", style = MaterialTheme.typography.titleMedium, color = TextDim, modifier = Modifier.width(34.dp))
        Arrow(rankDelta)
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    row.asset.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (row.asset.kind == AssetKind.SYNTH) Cyan else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                statusTag(row.status)?.let { (t, c) -> Text(t, style = MaterialTheme.typography.labelSmall, color = c) }
                if (row.asset.transferId != null) Text("TRANSFER", style = MaterialTheme.typography.labelSmall, color = Brass)
            }
            FootNote("${row.asset.id} · ${row.asset.unit}")
        }
        IndexCell(row.index, row.delta)
    }
}

@Composable
private fun UserRowInline(rank: Int, delta: Int, index: Int, prevRank: Int?) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Brass.copy(alpha = 0.08f), RoundedCornerShape(4.dp)).padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", style = MaterialTheme.typography.titleMedium, color = Brass, modifier = Modifier.width(34.dp))
        Arrow(prevRank?.let { it - rank } ?: 0)
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text("YOU", style = MaterialTheme.typography.bodyMedium, color = Brass)
            FootNote("Asset · enrolled")
        }
        IndexCell(index, delta)
    }
}

@Composable
private fun TwinRowInline(name: String, t: dev.eversorhn.gait.domain.roster.TwinStanding) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Cyan.copy(alpha = 0.07f), RoundedCornerShape(4.dp)).padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${t.rank}", style = MaterialTheme.typography.titleMedium, color = Cyan, modifier = Modifier.width(34.dp))
        Arrow(t.prevRank?.let { it - t.rank } ?: 0)
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(name.uppercase(), style = MaterialTheme.typography.bodyMedium, color = Cyan)
            FootNote("Model · replacement candidate")
        }
        IndexCell(t.index, t.delta)
    }
}

@Composable
private fun IndexCell(index: Int, delta: Int) {
    Column(horizontalAlignment = Alignment.End) {
        Text("$index", style = MaterialTheme.typography.titleMedium)
        Text(
            signed(delta),
            style = MaterialTheme.typography.labelSmall,
            color = when { delta > 0 -> Good; delta < 0 -> Alert; else -> TextFaint },
        )
    }
}

/** ▲ green up / ▼ red down / — flat, by rank movement. */
@Composable
private fun Arrow(rankDelta: Int) {
    val (glyph, color) = when {
        rankDelta > 0 -> "▲" to Good
        rankDelta < 0 -> "▼" to Alert
        else -> "·" to TextFaint
    }
    Text(glyph, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.width(18.dp), textAlign = TextAlign.Center)
}

private fun statusTag(s: AssetStatus): Pair<String, Color>? = when (s) {
    AssetStatus.NEW_HIRE -> "NEW HIRE" to Cyan
    AssetStatus.ON_LEAVE -> "ON LEAVE" to TextFaint
    AssetStatus.MAINTENANCE -> "MAINTENANCE" to TextFaint
    AssetStatus.UNDER_REVIEW -> "UNDER REVIEW" to Alert
    AssetStatus.INJURED -> "INJURED" to Alert
    AssetStatus.ACTIVE -> null
}

private fun signed(v: Int) = when { v > 0 -> "+$v"; v < 0 -> "−${-v}"; else -> "±0" }

// ------------------------------------------------------------------ the file on one asset

@Composable
private fun DossierDialog(d: RosterEngine.Dossier, standing: Standing?, today: Long, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Ink2,
        titleContentColor = TextPrimary,
        textContentColor = TextDim,
        title = {
            Column {
                FootNote("${d.asset.id} · ${d.asset.unit}")
                Text(d.asset.name, style = MaterialTheme.typography.titleLarge, color = if (d.asset.kind == AssetKind.SYNTH) Cyan else TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Rank", standing?.let { "#${it.rank}" } ?: "—")
                    StatTile("Index", standing?.let { "${it.index}" } ?: "—", accent = Brass)
                    StatTile("Tenure", "${d.tenureDays} d")
                }
                if (d.history14.size >= 2) {
                    SectionLabel("Last ${d.history14.size} days · ${d.worstIndex14}–${d.bestIndex14}", color = TextFaint)
                    Sparkline(points = d.history14, color = if (d.asset.kind == AssetKind.SYNTH) Cyan else Brass)
                }
                Text(
                    "Reads as ${d.readsAs} · ${d.trendLabel} this month · ${d.landingLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (d.restDays.isEmpty()) (if (d.asset.kind == AssetKind.SYNTH) "No rest days. Maintenance windows only." else "No fixed rest days.")
                    else "Rests " + d.restDays.joinToString(", ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) } + ".",
                    style = MaterialTheme.typography.bodyMedium,
                )
                standing?.let { st ->
                    statusTag(st.status)?.let { (t, c) -> Text(t, style = MaterialTheme.typography.labelSmall, color = c) }
                }
                FootNote(
                    when (d.asset.kind) {
                        AssetKind.SYNTH -> "Humanoid synth · series ${d.asset.name.substringBefore('-')} · the division's most consistent"
                        else -> "Hire #${d.asset.hireIndex + 1} in slot ${d.asset.slot} · ${today - d.asset.hiredDay} days on the floor"
                    }
                )
            }
        },
        confirmButton = { CorpoButton("Close file", onClick = onClose, kind = ButtonKind.GHOST, modifier = Modifier.fillMaxWidth()) },
    )
}

// ------------------------------------------------------------------ the end of the line

@Composable
private fun TerminationNotice(t: Termination, snap: RosterSnapshot, career: Career?, onEnrolNew: () -> Unit) {
    ScreenTitle("Asset Performance Division · notice", "Asset decommissioned", headlineColor = Alert)
    CorpoPanel(tone = PanelTone.WARN) {
        SectionLabel("Quarterly review · ${t.daysAgo} d ago", color = Alert)
        Text(
            "At the cull you ranked #${t.rank} of ${"%,d".format(t.headcount)}. The line was #${t.cullLine}. " +
                "The bottom ${RosterEngine.CULL_COUNT} were released; you were one of them.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        career?.let { c ->
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Tenure", "${c.tenureDays} d", sub = "company days")
                StatTile("Culls survived", "${c.cullsSurvived}")
                StatTile("Best streak", "${c.bestStreak}", sub = "${c.roundsPlayed} rounds")
            }
        }
        FootNote("This enrolment is closed. Its ledger and history stay with it.")
    }
    CorpoPanel {
        SectionLabel("Re-enrolment")
        Text(
            "A new asset can enrol under a new name, with a new opponent and a clean ledger. The history of this one stays closed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    CorpoButton("Enrol a new asset", onClick = onEnrolNew, kind = ButtonKind.RISK, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
}

