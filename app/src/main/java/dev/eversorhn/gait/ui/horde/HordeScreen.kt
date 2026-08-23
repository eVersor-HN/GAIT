package dev.eversorhn.gait.ui.horde

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.domain.roster.RosterSnapshot
import dev.eversorhn.gait.ui.theme.Alert
import dev.eversorhn.gait.ui.theme.Brass
import dev.eversorhn.gait.ui.theme.ButtonKind
import dev.eversorhn.gait.ui.theme.CorpoButton
import dev.eversorhn.gait.ui.theme.CorpoPanel
import dev.eversorhn.gait.ui.theme.FootNote
import dev.eversorhn.gait.ui.theme.Ink
import dev.eversorhn.gait.ui.theme.LineSoft
import dev.eversorhn.gait.ui.theme.PanelTone
import dev.eversorhn.gait.ui.theme.ScreenTitle
import dev.eversorhn.gait.ui.theme.SectionLabel
import dev.eversorhn.gait.ui.theme.StatTile
import dev.eversorhn.gait.ui.theme.TextFaint
import kotlin.math.cos
import kotlin.math.sin

/**
 * Horde mode's own first page. No board, no ticker, no forecast talk, no sentences — a
 * containment display: how much ground you have, how fast it is closing, and what the last
 * things you heard were. Everything that belongs to the Twin stays out.
 */
@Composable
fun HordeScreen(
    snapshot: RosterSnapshot?,
    proximityPercent: Int,
    separationMeters: Int,
    releasedTotal: Int,
    daysSinceLast: Long?,
    signals: List<String>,
    tenureDays: Long,
    onGo: () -> Unit,
) {
    val closing = proximityPercent >= 80
    val transition = rememberInfiniteTransition(label = "horde")
    // One slow breath drives everything on the page — faster and harder the closer they are.
    val periodMs = (2600 - proximityPercent * 14).coerceIn(700, 2600)
    val pulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CorpoButton(if (closing) "RUN" else "GO", onClick = onGo, kind = ButtonKind.PRIMARY, modifier = Modifier.fillMaxWidth())

        ScreenTitle("Containment", if (closing) "They have your line" else "Ground held")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Separation", "$separationMeters m", accent = if (closing) Alert else Brass, sub = if (closing) "closing" else "holding")
            StatTile("Proximity", "$proximityPercent%", accent = if (closing) Alert else MaterialTheme.colorScheme.onSurface, sub = "how well they read you")
            StatTile("Behind you", "$releasedTotal", sub = "released assets")
        }

        CorpoPanel(tone = if (closing) PanelTone.WARN else PanelTone.NEUTRAL) {
            Canvas(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                val you = Offset(size.width / 2, size.height * 0.2f)
                val maxR = size.height * 0.78f
                for (i in 1..4) drawCircle(LineSoft, radius = maxR * i / 4, center = you, style = Stroke(1f))
                // The wave: a band sweeping outward, brighter and tighter the closer they are.
                val waveR = maxR * (0.25f + 0.75f * pulse)
                drawCircle(Alert.copy(alpha = (0.35f * (1f - pulse)).coerceIn(0f, 1f)), radius = waveR, center = you, style = Stroke(2f))
                val base = (1.0 - proximityPercent / 100.0).coerceIn(0.10, 1.0)
                val count = 70
                for (i in 0 until count) {
                    val seed = i * 2654435761L
                    val spread = ((seed % 1000L).toDouble() / 1000.0)
                    val angle = Math.PI * (0.18 + 0.64 * spread)
                    val jitter = ((seed / 7 % 100L).toDouble() / 100.0) * 0.10
                    // Each dot drifts in on the breath; the newest releases sit closest.
                    val recency = i.toDouble() / count
                    val r = maxR * (base * (0.42 + 0.5 * recency) + jitter - 0.03 * pulse).coerceIn(0.08, 1.0)
                    val p = Offset(you.x + (r * cos(angle)).toFloat(), you.y + (r * sin(angle)).toFloat())
                    if (p.y > size.height) continue
                    val a = if (i < 12) 0.95f else 0.5f
                    drawCircle(Alert.copy(alpha = a * 0.28f), radius = 6.dp.toPx(), center = p)
                    drawCircle(Alert.copy(alpha = a), radius = 2.4.dp.toPx(), center = p)
                }
                drawCircle(Brass.copy(alpha = 0.22f), radius = (9 + 3 * (1 - pulse)).dp.toPx(), center = you)
                drawCircle(Brass, radius = 5.dp.toPx(), center = you)
                drawCircle(Ink, radius = 2.dp.toPx(), center = you)
                drawLine(Brass.copy(alpha = 0.45f), you, Offset(you.x, you.y - 26.dp.toPx()), 2.dp.toPx())
            }
            FootNote("Rings 100 m · you at the top, heading away")
        }

        if (signals.isNotEmpty()) {
            CorpoPanel {
                SectionLabel("Recent contact", color = Alert)
                signals.take(4).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = if (closing) Alert else TextFaint) }
            }
        }

        FootNote(
            "Wave ${snapshot?.let { "" } ?: ""}" +
                (daysSinceLast?.let { if (it == 0L) "last run today" else "$it d since your last run" } ?: "no run on file") +
                " · $tenureDays d in the field",
        )
        Spacer(Modifier.height(8.dp))
    }
}
