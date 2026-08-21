package dev.eversorhn.gait.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import dev.eversorhn.gait.ui.theme.Ink2
import dev.eversorhn.gait.ui.theme.Line
import dev.eversorhn.gait.ui.theme.LineSoft
import dev.eversorhn.gait.ui.theme.PanelTone
import dev.eversorhn.gait.ui.theme.ScreenTitle
import dev.eversorhn.gait.ui.theme.SectionLabel
import dev.eversorhn.gait.ui.theme.StatTile
import dev.eversorhn.gait.ui.theme.TextDim
import dev.eversorhn.gait.ui.theme.TextFaint
import kotlin.math.cos
import kotlin.math.sin

/**
 * The first thing you see when the app opens with an opponent on file.
 * Twin: the division's Asset Board — the top 15 of 1,001 enrolled assets by Retention Index,
 * ▲▼ against yesterday's close, your own row pinned with your rank. Horde: the containment
 * map — you at the centre, the decommissioned (the horde) as dots closing in by Proximity.
 */
@Composable
fun BoardScreen(onContinue: () -> Unit) {
    val viewModel: BoardViewModel = gaitViewModel()
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val snap = state.snapshot
        if (!state.loaded || snap == null) {
            ScreenTitle("Asset board", "Compiling standings…")
            FootNote("1,000 assets · Retention Index · since yesterday's close")
        } else if (state.isHorde) {
            HordeMap(snap, state.proximityPercent, onContinue)
        } else {
            Board(snap, state.opponentName, onContinue)
        }
    }
}

@Composable
private fun Board(snap: RosterSnapshot, opponentName: String, onContinue: () -> Unit) {
    ScreenTitle("Asset Performance Division · standings", "Asset board")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Enrolled", "%,d".format(snap.enrolled))
        StatTile("Under review", "${snap.underReview}", accent = Alert)
        StatTile("Decom. 30d", "${snap.decommissioned30d}", accent = TextDim)
    }

    // --- Your row, always visible ---
    val u = snap.user
    CorpoPanel(tone = PanelTone.TWIN) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("#${u.rank}", style = MaterialTheme.typography.titleLarge, color = Brass, modifier = Modifier.width(64.dp))
            Arrow(u.prevRank?.let { it - u.rank } ?: 0)
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text("YOU", style = MaterialTheme.typography.titleMedium, color = Brass)
                FootNote("Asset · vs. $opponentName · of ${"%,d".format(snap.enrolled)}")
            }
            IndexCell(u.index, u.delta)
        }
        FootNote(
            when {
                u.rank <= 15 -> "On the board. Stay there."
                u.rank <= 100 -> "${u.rank - 15} places off the board."
                else -> "Top 15 is ${u.rank - 15} places away. The division is watching the bottom, not the top."
            }
        )
    }

    // --- Top 15 ---
    CorpoPanel {
        Row(modifier = Modifier.fillMaxWidth()) {
            SectionLabel("#", color = TextFaint); Spacer(Modifier.width(40.dp))
            SectionLabel("Asset", color = TextFaint)
            Spacer(Modifier.weight(1f))
            SectionLabel("Index · Δ", color = TextFaint)
        }
        val top = snap.standings.take(15)
        val userInTop = u.rank <= 15
        var shown = 0
        for (row in snap.standings) {
            if (shown >= 15) break
            if (userInTop && row.rank > u.rank && shown == u.rank - 1) {
                UserRowInline(u.rank, u.delta, u.index, u.prevRank)
                shown++
                if (shown >= 15) break
            }
            StandingRow(row)
            shown++
        }
        if (userInTop && shown < 15) UserRowInline(u.rank, u.delta, u.index, u.prevRank)
        FootNote("${if (snap.nextReviewInDays == 0) "Review today" else "Next review in ${snap.nextReviewInDays} d"} · floor ${dev.eversorhn.gait.domain.roster.RosterEngine.FLOOR.toInt()} · ${snap.onLeave} on leave · ${snap.newHires30d} new hires (30 d)")
    }

    // --- Movers ---
    if (snap.movers.isNotEmpty()) {
        CorpoPanel {
            SectionLabel("Movers today")
            snap.movers.take(5).forEach { m ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
            FootNote("Folded into containment. They don't leave — they follow.")
        }
    }

    CorpoButton("Continue to forecast", onClick = onContinue, kind = ButtonKind.PRIMARY, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun StandingRow(row: Standing) {
    val rankDelta = row.prevRank?.let { it - row.rank } ?: 0
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
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

// ------------------------------------------------------------------ Horde: containment map

@Composable
private fun HordeMap(snap: RosterSnapshot, proximityPercent: Int, onContinue: () -> Unit) {
    val zombies = dev.eversorhn.gait.domain.roster.RosterEngine.zombies(snap)
    ScreenTitle("Containment unit · live", "Where they are")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Horde", "${snap.decommissioned.size}", accent = Alert, sub = "decommissioned assets")
        StatTile("Proximity", "$proximityPercent%", accent = Alert)
        StatTile("Nearest", "${(100 - proximityPercent).coerceAtLeast(1) * 4} m", sub = "closing")
    }
    CorpoPanel {
        SectionLabel("Containment map · you at centre")
        Canvas(modifier = Modifier.fillMaxWidth().height(300.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            val maxR = size.minDimension / 2 - 8.dp.toPx()
            // Range rings.
            for (i in 1..4) drawCircle(LineSoft, radius = maxR * i / 4, center = c, style = Stroke(1f))
            drawLine(LineSoft, Offset(c.x, c.y - maxR), Offset(c.x, c.y + maxR), 1f)
            drawLine(LineSoft, Offset(c.x - maxR, c.y), Offset(c.x + maxR, c.y), 1f)
            // The horde: each decommissioned asset is a dot. Distance = 1 − proximity (closer
            // when the horde models you well), jittered deterministically per asset; the
            // most recently decommissioned are the closest — they remember you best.
            val baseDist = (1.0 - proximityPercent / 100.0).coerceIn(0.08, 1.0)
            zombies.forEachIndexed { i, z ->
                val seed = z.asset.slot * 31 + z.asset.hireIndex * 7
                val angle = ((seed * 2654435761L) % 360L).toDouble() * Math.PI / 180.0
                val recency = (i.toDouble() / zombies.size.coerceAtLeast(1)) // 0 = newest
                val r = maxR * (baseDist * (0.55 + 0.45 * recency) + ((seed % 17) / 17.0) * 0.12).coerceIn(0.08, 1.0)
                val p = Offset(c.x + (r * cos(angle)).toFloat(), c.y + (r * sin(angle)).toFloat())
                val alpha = if (i < 8) 0.95f else 0.55f
                drawCircle(Alert.copy(alpha = alpha * 0.3f), radius = 6.dp.toPx(), center = p)
                drawCircle(Alert.copy(alpha = alpha), radius = 2.5.dp.toPx(), center = p)
            }
            // You.
            drawCircle(Brass.copy(alpha = 0.25f), radius = 10.dp.toPx(), center = c)
            drawCircle(Brass, radius = 5.dp.toPx(), center = c)
            drawCircle(Ink2, radius = 2.dp.toPx(), center = c)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FootNote("● you", color = Brass)
            FootNote("● decommissioned · ${zombies.size} plotted", color = Alert)
        }
        FootNote("Rings: 100 m · newest decommissions closest · proximity ${proximityPercent}% sets the range")
    }
    if (snap.decommissioned.isNotEmpty()) {
        CorpoPanel(tone = PanelTone.WARN) {
            SectionLabel("Joined the horde · recent", color = Alert)
            snap.decommissioned.take(4).forEach { d ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("${d.asset.id} · ${d.asset.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    FootNote("${snap.day - d.day}d ago")
                }
            }
            FootNote("Every Twin that loses its Trial. ${snap.decommissioned30d} this month.")
        }
    }
    FootNote("${"%,d".format(snap.enrolled)} assets enrolled · ${snap.underReview} under review · ${if (snap.nextReviewInDays == 0) "review today" else "next review in ${snap.nextReviewInDays} d"}")
    CorpoButton("Continue to forecast", onClick = onContinue, kind = ButtonKind.PRIMARY, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
}
