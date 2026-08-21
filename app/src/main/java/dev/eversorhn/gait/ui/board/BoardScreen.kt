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
fun BoardScreen(onContinue: () -> Unit, onEnrolNew: () -> Unit) {
    val viewModel: BoardViewModel = gaitViewModel()
    val state by viewModel.uiState.collectAsState()

    state.dossier?.let { d ->
        DossierDialog(d, state.dossierStanding, state.snapshot?.day ?: 0L, onClose = viewModel::closeDossier)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val snap = state.snapshot
        when {
            !state.loaded || snap == null -> {
                ScreenTitle("Asset board", "Compiling standings…")
                FootNote("First open of the day: the division re-runs the whole roster · a few seconds")
            }
            state.termination != null -> TerminationNotice(state.termination!!, snap, state.career, onEnrolNew = { viewModel.enrolNewAsset(onEnrolNew) })
            state.isHorde -> HordeMap(snap, state.proximityPercent, state.career, onContinue)
            else -> Board(snap, state.opponentName, state.career, onContinue, onRow = viewModel::openDossier)
        }
    }
}

// ------------------------------------------------------------------ Twin: the board

@Composable
private fun Board(snap: RosterSnapshot, opponentName: String, career: Career?, onContinue: () -> Unit, onRow: (Int) -> Unit) {
    ScreenTitle("Asset Performance Division · standings", "Asset board")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Enrolled", "%,d".format(snap.enrolled), sub = "+${snap.newHires30d} in 30 d")
        StatTile("Under review", "${snap.underReview}", accent = Alert, sub = "floor ${RosterEngine.FLOOR.toInt()}")
        StatTile("Next cull", if (snap.nextCullInDays == 0) "today" else "${snap.nextCullInDays} d", accent = Alert, sub = "bottom ${RosterEngine.CULL_COUNT} go")
    }

    // --- Your row, always visible ---
    val u = snap.user
    val safe = u.rank <= snap.cullLine
    CorpoPanel(tone = if (safe) PanelTone.TWIN else PanelTone.WARN) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("#${u.rank}", style = MaterialTheme.typography.titleLarge, color = Brass, modifier = Modifier.width(64.dp))
            Arrow(u.prevRank?.let { it - u.rank } ?: 0)
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text("YOU", style = MaterialTheme.typography.titleMedium, color = Brass)
                FootNote("Asset · vs. $opponentName · of ${"%,d".format(snap.enrolled)}")
            }
            IndexCell(u.index, u.delta)
        }
        val protectedDaysLeft = career?.let { (RosterEngine.CULL_GRACE_DAYS - it.tenureDays).coerceAtLeast(0) } ?: 0
        FootNote(
            when {
                u.rank <= 15 -> "On the board. Stay there."
                !safe && protectedDaysLeft > 0 -> "New hire: protected from the cull for $protectedDaysLeft more days. Everyone starts last. Climb ${u.rank - snap.cullLine} places to be safe."
                !safe -> "Below the cull line (#${snap.cullLine}). ${if (snap.nextCullInDays == 0) "Today." else "${snap.nextCullInDays} days to climb ${u.rank - snap.cullLine}."}"
                u.rank <= 100 -> "${u.rank - 15} places off the board · ${u.rank - 15 + (snap.cullLine - u.rank)} above the cull line"
                else -> "Top 15 is ${u.rank - 15} places away · cull line #${snap.cullLine}, ${snap.cullLine - u.rank} below you"
            },
            color = if (safe || protectedDaysLeft > 0) TextFaint else Alert,
        )
        career?.let { c ->
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Tenure", "${c.tenureDays} d", sub = "company days")
                StatTile("Survived", "${c.cullsSurvived}", sub = if (c.cullsSurvived == 1) "cull" else "culls")
                StatTile("Best streak", "${c.bestStreak}", sub = "${c.roundsPlayed} rounds")
            }
        }
    }

    // --- The opponent's own row: where the rounds put it, not where the story wants it ---
    snap.twin?.let { t ->
        CorpoPanel {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("#${t.rank}", style = MaterialTheme.typography.titleLarge, color = Cyan, modifier = Modifier.width(64.dp))
                Arrow(t.prevRank?.let { it - t.rank } ?: 0)
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(opponentName.uppercase(), style = MaterialTheme.typography.titleMedium, color = Cyan)
                    FootNote("Model · your replacement candidate")
                }
                IndexCell(t.index, t.delta)
            }
            FootNote(
                when {
                    t.rank < u.rank -> "${t.rank - 0} — it is ahead of you by ${u.rank - t.rank} places. It earned that on the ledger."
                    t.rank > u.rank -> "${u.rank - 0} — you are ahead of it by ${t.rank - u.rank} places. Keep it there."
                    else -> "Level with you."
                }.substringAfter("— "),
                color = if (t.rank < u.rank) Alert else TextFaint,
            )
        }
    }

    // --- Top 15 ---
    CorpoPanel {
        Row(modifier = Modifier.fillMaxWidth()) {
            SectionLabel("#", color = TextFaint); Spacer(Modifier.width(40.dp))
            SectionLabel("Asset · tap for file", color = TextFaint)
            Spacer(Modifier.weight(1f))
            SectionLabel("Index · Δ", color = TextFaint)
        }
        // Merge the sim rows with the user's (and the opponent's) by rank, show the first 15.
        val twinRow = snap.twin
        var placed = 0
        var i = 0
        var userPlaced = false
        var twinPlaced = false
        while (placed < 15) {
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
        FootNote("${if (snap.nextReviewInDays == 0) "Review today" else "Next review in ${snap.nextReviewInDays} d"} · ${snap.onLeave} on leave · ${snap.decommissioned30d} decommissioned (30 d)")
    }

    // --- Movers ---
    if (snap.movers.isNotEmpty()) {
        CorpoPanel {
            SectionLabel("Movers today")
            snap.movers.take(5).forEach { m ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onRow(m.asset.slot) },
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

    // --- Decommissioned ---
    if (snap.decommissioned.isNotEmpty()) {
        CorpoPanel(tone = PanelTone.WARN) {
            SectionLabel("Decommissioned · recent", color = Alert)
            snap.decommissioned.take(3).forEach { d ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("${d.asset.id} · ${d.asset.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    FootNote("${snap.day - d.day}d ago · ${d.lastIndex}")
                }
            }
            FootNote(snap.lastCullDay?.let { "Last cull ${snap.day - it} d ago · ${snap.decommissioned.count { d -> d.day == it }} removed" } ?: "Individual reviews only so far")
        }
    }

    CorpoButton("Continue to forecast", onClick = onContinue, kind = ButtonKind.PRIMARY, modifier = Modifier.fillMaxWidth())
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
        FootNote("Your model keeps running. It always did. It's just not yours to beat any more.")
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

// ------------------------------------------------------------------ Horde: containment map

/**
 * You're running from them, so you're ahead: the user sits in the upper third, the horde
 * spreads behind in a cone below, the closest ones by Proximity. Rings are 100 m.
 */
@Composable
private fun HordeMap(snap: RosterSnapshot, proximityPercent: Int, career: Career?, onContinue: () -> Unit) {
    val zombies = RosterEngine.zombies(snap, limit = 90)
    ScreenTitle("Containment unit · live", "Where they are")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Behind you", "${snap.decommissioned.size}", accent = Alert, sub = "decommissioned")
        StatTile("Proximity", "$proximityPercent%", accent = Alert)
        StatTile("Nearest", "${(100 - proximityPercent).coerceAtLeast(1) * 4} m", sub = "and closing")
    }
    CorpoPanel {
        SectionLabel("Containment map · you ahead, heading up")
        Canvas(modifier = Modifier.fillMaxWidth().height(320.dp)) {
            val you = Offset(size.width / 2, size.height * 0.22f)
            val maxR = size.height * 0.74f
            // Range rings behind you (lower half-circles), 100 m apart.
            for (i in 1..4) drawCircle(LineSoft, radius = maxR * i / 4, center = you, style = Stroke(1f))
            drawLine(LineSoft, Offset(you.x, 0f), Offset(you.x, size.height), 1f)
            // Your heading: a short brass tick forward.
            drawLine(Brass.copy(alpha = 0.5f), you, Offset(you.x, you.y - 26.dp.toPx()), 2.dp.toPx())
            // The horde: a cone behind you (angles 35°..145° below the horizontal), newest closest.
            val baseDist = (1.0 - proximityPercent / 100.0).coerceIn(0.10, 1.0)
            zombies.forEachIndexed { i, z ->
                val seed = z.asset.slot * 31 + z.asset.hireIndex * 7
                val spread = ((seed * 2654435761L) % 1000L).toDouble() / 1000.0   // 0..1
                val angle = Math.PI * (0.20 + 0.60 * spread)                     // 36°..144°, i.e. below you
                val recency = i.toDouble() / zombies.size.coerceAtLeast(1)       // 0 = newest
                val r = maxR * (baseDist * (0.45 + 0.50 * recency) + ((seed % 17) / 17.0) * 0.10).coerceIn(0.10, 1.0)
                val p = Offset(you.x + (r * cos(angle)).toFloat(), you.y + (r * sin(angle)).toFloat())
                if (p.y > size.height - 2.dp.toPx()) return@forEachIndexed
                val alpha = if (i < 10) 0.95f else 0.55f
                drawCircle(Alert.copy(alpha = alpha * 0.3f), radius = 6.dp.toPx(), center = p)
                drawCircle(Alert.copy(alpha = alpha), radius = 2.5.dp.toPx(), center = p)
            }
            drawCircle(Brass.copy(alpha = 0.25f), radius = 10.dp.toPx(), center = you)
            drawCircle(Brass, radius = 5.dp.toPx(), center = you)
            drawCircle(Ink, radius = 2.dp.toPx(), center = you)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FootNote("● you", color = Brass)
            FootNote("● behind you · ${zombies.size} in range", color = Alert)
        }
        FootNote("Rings 100 m · the newest decommissions are closest · proximity ${proximityPercent}% sets how close")
    }
    career?.let { c ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Tenure", "${c.tenureDays} d", sub = "company days")
            StatTile("Survived", "${c.cullsSurvived}", sub = if (c.cullsSurvived == 1) "cull" else "culls")
            StatTile("Best streak", "${c.bestStreak}", sub = "${c.roundsPlayed} rounds")
        }
    }
    FootNote("${"%,d".format(snap.enrolled)} assets enrolled · ${snap.underReview} under review · next cull ${if (snap.nextCullInDays == 0) "today" else "in ${snap.nextCullInDays} d"}")
    CorpoButton("Continue to forecast", onClick = onContinue, kind = ButtonKind.PRIMARY, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
}
