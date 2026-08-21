package dev.eversorhn.gait.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot

/*
 * The instrument panel every phone mockup in demo/asset-twin-demo.html is built from,
 * as Compose widgets: stat tiles, compare grid, sparkline, meter, message card, live
 * track, chips, buttons. Screens compose these instead of raw Text()+Button() so the app
 * reads as one console rather than a settings page.
 */

/** Tints for panels/cards — the demo's `.card`, `.warn`, `.divergence`, `.twin-msg`. */
enum class PanelTone { NEUTRAL, WARN, DIVERGENCE, TWIN, GOOD }

@Composable
private fun PanelTone.border(): Color = when (this) {
    PanelTone.NEUTRAL -> MaterialTheme.colorScheme.outlineVariant
    PanelTone.WARN -> Alert.copy(alpha = 0.45f)
    PanelTone.DIVERGENCE, PanelTone.TWIN -> Cyan.copy(alpha = 0.4f)
    PanelTone.GOOD -> Good.copy(alpha = 0.45f)
}

@Composable
private fun PanelTone.fill(): Color = when (this) {
    PanelTone.NEUTRAL -> MaterialTheme.colorScheme.surface
    PanelTone.WARN -> Alert.copy(alpha = 0.09f)
    PanelTone.DIVERGENCE, PanelTone.TWIN -> Cyan.copy(alpha = 0.07f)
    PanelTone.GOOD -> Good.copy(alpha = 0.08f)
}

/** Toned variant of [CorpoPanel]. */
@Composable
fun CorpoPanel(
    tone: PanelTone,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(tone.fill(), RoundedCornerShape(6.dp))
            .border(BorderStroke(1.dp, tone.border()), RoundedCornerShape(6.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** The demo's `.sc-title` + `.sc-headline`: a faint mono section label over a bold headline. */
@Composable
fun ScreenTitle(eyebrow: String, headline: String, headlineColor: Color = MaterialTheme.colorScheme.onBackground) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(headline, style = MaterialTheme.typography.headlineLarge, color = headlineColor)
    }
}

/** `.sc-title` on its own, for labelling a panel section. */
@Composable
fun SectionLabel(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
}

/** `.foot-note`: the smallest mono line, for provenance ("GENERATION 7 · BASIS: 41 SESSIONS"). */
@Composable
fun FootNote(text: String, color: Color = TextFaint) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
}

/** `.stat`: a small key/value tile; `accent` colours the value (brass = you, cyan = twin). */
@Composable
fun RowScope.StatTile(label: String, value: String, accent: Color = MaterialTheme.colorScheme.onSurface, sub: String? = null) {
    Column(
        modifier = Modifier
            .weight(1f)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextFaint, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleLarge, color = accent, maxLines = 1)
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = TextFaint, maxLines = 1)
    }
}

data class CompareRow(val label: String, val forecast: String, val actual: String, val actualGood: Boolean? = null)

/** `.compare`: FORECAST | ACTUAL columns, actual tinted good/alert when it's a verdict. */
@Composable
fun CompareGrid(rows: List<CompareRow>, forecastHeader: String = "Forecast", actualHeader: String = "Actual") {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Text(forecastHeader.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextFaint,
                modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
            Text(actualHeader.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextFaint,
                modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
        }
        rows.forEach { r ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(r.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                Text(r.forecast, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(0.8f), textAlign = TextAlign.End, maxLines = 1)
                Text(
                    r.actual,
                    style = MaterialTheme.typography.titleMedium,
                    color = when (r.actualGood) {
                        true -> Good
                        false -> Alert
                        null -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(0.8f), textAlign = TextAlign.End, maxLines = 1,
                )
            }
        }
    }
}

/**
 * `.spark`: a polyline over [points] (0..1), newest last, with a dot on the last value.
 * Optional [threshold] draws a faint horizontal rule (e.g. the Decommission line at 0.95).
 */
@Composable
fun Sparkline(
    points: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Brass,
    threshold: Float? = null,
    height: androidx.compose.ui.unit.Dp = 44.dp,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (threshold != null) {
            val y = size.height * (1 - threshold.coerceIn(0f, 1f))
            var x = 0f
            val dash = 6.dp.toPx()
            while (x < size.width) {
                drawLine(Alert.copy(alpha = 0.45f), Offset(x, y), Offset((x + dash).coerceAtMost(size.width), y), strokeWidth = 1f)
                x += dash * 2
            }
        }
        if (points.size < 2) {
            if (points.size == 1) {
                drawCircle(color, radius = 3.dp.toPx(), center = Offset(0f, size.height * (1 - points[0].coerceIn(0f, 1f))))
            }
            return@Canvas
        }
        val stepX = size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { i, p ->
            val pt = Offset(i * stepX, size.height * (1 - p.coerceIn(0f, 1f)))
            if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        val last = Offset(size.width, size.height * (1 - points.last().coerceIn(0f, 1f)))
        drawCircle(color, radius = 3.dp.toPx(), center = last)
    }
}

/** `.meter`: a thin filled bar with an optional threshold tick. */
@Composable
fun Meter(fraction: Float, color: Color = Brass, threshold: Float? = null, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(6.dp)) {
        val r = 3.dp.toPx()
        drawRoundRect(Ink2, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
        drawRoundRect(LineSoft, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r), style = Stroke(1f))
        val w = size.width * fraction.coerceIn(0f, 1f)
        if (w > 0f) drawRoundRect(color, size = androidx.compose.ui.geometry.Size(w, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
        if (threshold != null) {
            val x = size.width * threshold.coerceIn(0f, 1f)
            drawLine(Alert, Offset(x, -2.dp.toPx()), Offset(x, size.height + 2.dp.toPx()), strokeWidth = 2f)
        }
    }
}

/** `.quote`: a line of opponent speech with a brass rule on the left. */
@Composable
fun Quote(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(BrassDim))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.padding(start = 12.dp))
    }
}

/** Tone of an opponent message, mapped from Composure (or the duel verdict). */
enum class MessageTone { COWED, WATCHFUL, PREDATORY, TWIN }

/** `.card.twin-msg` / `.card.warn`: FROM + TAG header, body, optional footer rows. */
@Composable
fun MessageCard(
    from: String,
    tag: String,
    body: String,
    tone: MessageTone,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val panelTone = when (tone) {
        MessageTone.PREDATORY -> PanelTone.WARN
        MessageTone.TWIN -> PanelTone.TWIN
        MessageTone.COWED, MessageTone.WATCHFUL -> PanelTone.NEUTRAL
    }
    val fromColor = when (tone) {
        MessageTone.PREDATORY -> Alert
        MessageTone.TWIN -> Cyan
        MessageTone.COWED -> TextFaint
        MessageTone.WATCHFUL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    CorpoPanel(tone = panelTone) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(from.uppercase(), style = MaterialTheme.typography.labelSmall, color = fromColor)
            Text(tag.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextFaint)
        }
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = if (tone == MessageTone.COWED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        if (footer != null) {
            Spacer(Modifier.height(4.dp))
            footer()
        }
    }
}

/** `.rec-dot`: pulsing recording indicator. */
@Composable
fun RecDot(color: Color = Alert) {
    val transition = rememberInfiniteTransition(label = "rec")
    val alpha by transition.animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "recAlpha",
    )
    Box(Modifier.size(8.dp).alpha(alpha).background(color, CircleShape))
}

/** `.phase-track`: five short bars, [current] of them lit. */
@Composable
fun PhaseTrack(current: Int, total: Int = 5) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .width(22.dp)
                    .height(3.dp)
                    .background(if (i < current) Brass else LineSoft, RoundedCornerShape(2.dp))
            )
        }
    }
}

/**
 * `.track`: the live comparison. A fixed pseudo-route polyline with two markers placed by
 * distance fraction along it — brass for the user, cyan for the opponent. Fractions > 1
 * park at the end (you've outrun the forecast distance).
 */
@Composable
fun LiveTrack(
    youFraction: Float,
    twinFraction: Float,
    modifier: Modifier = Modifier,
    twinColor: Color = Cyan,
) {
    // Normalised route vertices (x 0..1, y 0..1); the same shape every session so the eye
    // learns it. Looks like the demo's SVG, not like a map.
    val route = remember {
        listOf(0.02f to 0.85f, 0.18f to 0.55f, 0.34f to 0.66f, 0.52f to 0.28f, 0.70f to 0.42f, 0.86f to 0.18f, 0.98f to 0.10f)
    }
    Canvas(modifier = modifier.fillMaxWidth().height(96.dp)) {
        val pts = route.map { (x, y) -> Offset(x * size.width, y * size.height) }
        val path = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        drawPath(path, Line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        val segLens = pts.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }
        val total = segLens.sum()
        fun at(fraction: Float): Offset {
            var remaining = fraction.coerceIn(0f, 1f) * total
            for (i in segLens.indices) {
                if (remaining <= segLens[i]) {
                    val t = if (segLens[i] == 0f) 0f else remaining / segLens[i]
                    val a = pts[i]; val b = pts[i + 1]
                    return Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                }
                remaining -= segLens[i]
            }
            return pts.last()
        }
        // Progress trail under the leader, so "ahead" is visible as a longer lit segment.
        val lead = maxOf(youFraction, twinFraction).coerceIn(0f, 1f)
        val trail = Path()
        var acc = 0f
        trail.moveTo(pts[0].x, pts[0].y)
        for (i in segLens.indices) {
            val end = at((acc + segLens[i]) / total)
            if ((acc + segLens[i]) / total <= lead) { trail.lineTo(end.x, end.y); acc += segLens[i] } else break
        }
        val tip = at(lead); trail.lineTo(tip.x, tip.y)
        drawPath(trail, LineSoft.copy(alpha = 0.9f), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))

        val twin = at(twinFraction)
        val you = at(youFraction)
        drawCircle(twinColor.copy(alpha = 0.25f), radius = 9.dp.toPx(), center = twin)
        drawCircle(twinColor, radius = 5.dp.toPx(), center = twin)
        drawCircle(Brass.copy(alpha = 0.25f), radius = 9.dp.toPx(), center = you)
        drawCircle(Brass, radius = 5.dp.toPx(), center = you)
    }
}

/** Legend row for [LiveTrack]. */
@Composable
fun TrackLegend(youLabel: String, twinLabel: String, twinColor: Color = Cyan) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).background(Brass, CircleShape))
            Text(youLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = Brass)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).background(twinColor, CircleShape))
            Text(twinLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = twinColor, maxLines = 1)
        }
    }
}

/** `.preset`: a small mono chip, brass-outlined when active. Replaces Material FilterChip. */
@Composable
fun CorpoChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (active) Brass else TextFaint,
        modifier = Modifier
            .background(if (active) Brass.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(4.dp))
            .border(BorderStroke(1.dp, if (active) BrassDim else LineSoft), RoundedCornerShape(4.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

enum class ButtonKind { PRIMARY, SAFE, RISK, GHOST }

/**
 * `.btn`: mono-label console buttons. PRIMARY is filled brass (the one action per screen),
 * SAFE is brass-outlined, RISK alert-outlined, GHOST the faint secondary row.
 */
@Composable
fun CorpoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.SAFE,
    enabled: Boolean = true,
) {
    val (bg, border, fg) = when (kind) {
        ButtonKind.PRIMARY -> Triple(Brass, Brass, Ink)
        ButtonKind.SAFE -> Triple(Color.Transparent, BrassDim, TextPrimary)
        ButtonKind.RISK -> Triple(Alert.copy(alpha = 0.06f), Alert.copy(alpha = 0.6f), Alert)
        ButtonKind.GHOST -> Triple(Color.Transparent, LineSoft, TextDim)
    }
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .background(bg, RoundedCornerShape(4.dp))
            .border(BorderStroke(1.dp, border), RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = if (kind == ButtonKind.PRIMARY) 15.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = if (kind == ButtonKind.GHOST) 11.sp else 13.sp,
                fontWeight = if (kind == ButtonKind.PRIMARY) FontWeight.Bold else FontWeight.SemiBold,
            ),
            color = fg,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** A big selectable option (opponent type): title, description, brass outline when selected. */
@Composable
fun SelectCard(title: String, description: String, selected: Boolean, onClick: () -> Unit, badge: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Brass.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
            .border(BorderStroke(1.dp, if (selected) BrassDim else MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(6.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = if (selected) Brass else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false))
            if (badge != null) Text(badge.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextFaint, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        }
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Signed percent-point delta with one decimal, e.g. "−1.4%" — the live impact readout. */
fun formatSignedTenths(delta: Float): String = when {
    delta >= 0.05f -> "+%.1f%%".format(delta)
    delta <= -0.05f -> "−%.1f%%".format(-delta)
    else -> "±0.0%"
}

/** Signed whole percent-point delta, e.g. "−2%". */
fun formatSignedPoints(delta: Int): String = when {
    delta > 0 -> "+$delta%"
    delta < 0 -> "−${-delta}%"
    else -> "±0%"
}

/**
 * The Asset Ledger on every screen: YOU n ── tug-of-war ── n TWIN, marker at the user's share
 * of all points, with the standing underneath. Brass = you, cyan/alert = the opponent. This
 * is the one readout that says, at a glance, how far apart you are.
 */
@Composable
fun LedgerStrip(
    userPoints: Int,
    twinPoints: Int,
    userShare: Float,
    opponentLabel: String,
    standing: String,
    twinColor: Color = Cyan,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("YOU $userPoints", style = MaterialTheme.typography.labelLarge, color = Brass, maxLines = 1)
            Canvas(modifier = Modifier.weight(1f).height(10.dp)) {
                val r = 2.dp.toPx()
                val midY = size.height / 2
                // Track, split at the centre: left half brass-tinted (retain), right half twin-tinted (replace).
                drawLine(Brass.copy(alpha = 0.25f), Offset(0f, midY), Offset(size.width / 2, midY), strokeWidth = 3f)
                drawLine(twinColor.copy(alpha = 0.25f), Offset(size.width / 2, midY), Offset(size.width, midY), strokeWidth = 3f)
                drawLine(LineSoft, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 1f)
                // Marker: user share → left is "you lead"; invert so a bigger user share pulls left.
                val x = size.width * (1f - userShare.coerceIn(0f, 1f))
                val markerColor = when {
                    userShare > 0.5f -> Brass
                    userShare < 0.5f -> twinColor
                    else -> TextDim
                }
                drawCircle(markerColor.copy(alpha = 0.3f), radius = 7.dp.toPx(), center = Offset(x, midY))
                drawCircle(markerColor, radius = 4.dp.toPx(), center = Offset(x, midY))
                drawRoundRect(Color.Transparent, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
            }
            Text("$twinPoints ${opponentLabel.uppercase()}", style = MaterialTheme.typography.labelLarge, color = twinColor, maxLines = 1)
        }
        Text(standing.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextFaint, maxLines = 1)
    }
}

/** W/L form dots, oldest first: filled brass = you, twin-coloured = the opponent. */
@Composable
fun FormDots(form: List<Boolean>, twinColor: Color = Cyan) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        form.forEach { userWon ->
            Box(Modifier.size(8.dp).background(if (userWon) Brass else twinColor, CircleShape))
        }
        if (form.isEmpty()) Text("—", style = MaterialTheme.typography.labelSmall, color = TextFaint)
    }
}

/** One entry on the ticker: who moved, by how much (index points), and whether it's you. */
data class TickerItem(val label: String, val delta: Int, val isUser: Boolean = false)

/**
 * The stock-ticker strip: one line of today's movers scrolling right-to-left at a constant
 * speed, ▲ green / ▼ red, your own entry in brass. Fixed height, clipped to its own box, so
 * it never overlaps what's below; the content is duplicated so the loop is seamless.
 */
@Composable
fun TickerStrip(items: List<TickerItem>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    var contentWidth by androidx.compose.runtime.remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // ~56 dp per second: readable, news-ticker pace. Duration follows the measured width.
    val durationMs = with(density) { ((contentWidth / 56.dp.toPx()) * 1000).toInt() }.coerceAtLeast(4000)
    val transition = rememberInfiniteTransition(label = "ticker")
    val progress by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = durationMs, easing = LinearEasing)),
        label = "tickerX",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(Ink2)
            .border(BorderStroke(1.dp, LineSoft))
            .clipToBounds(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val x = if (contentWidth == 0) 0 else -((progress * contentWidth).toInt() % contentWidth)
        Row(
            modifier = Modifier
                .offset { IntOffset(x, 0) }
                .wrapContentWidth(align = Alignment.Start, unbounded = true),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(2) { copy ->
                Row(
                    modifier = if (copy == 0) Modifier.onSizeChanged { contentWidth = it.width } else Modifier,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEach { it ->
                        val color = when {
                            it.isUser -> Brass
                            it.delta > 0 -> Good
                            it.delta < 0 -> Alert
                            else -> TextFaint
                        }
                        val glyph = when { it.delta > 0 -> "▲"; it.delta < 0 -> "▼"; else -> "·" }
                        Text(
                            "$glyph ${it.label.uppercase()} ${if (it.delta > 0) "+" else if (it.delta < 0) "−" else ""}${kotlin.math.abs(it.delta)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                        Text("·", style = MaterialTheme.typography.labelSmall, color = LineSoft)
                    }
                }
            }
        }
    }
}
