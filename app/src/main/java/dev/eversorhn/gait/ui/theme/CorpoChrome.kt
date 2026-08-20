package dev.eversorhn.gait.ui.theme

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Faint grid + scanlines behind every screen, matching demo/asset-twin-demo.html. Both are
 * drawn once per recomposition, not animated per frame -- a live GPS session is already
 * running a location callback and a 1s ticker, so this stays a cheap static texture rather
 * than a second animation loop competing for battery.
 */
@Composable
fun CorpoBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridStep = 40.dp.toPx()
            val gridColor = LineSoft.copy(alpha = 0.5f)
            var x = 0f
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += gridStep
            }
            var y = 0f
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += gridStep
            }

            val scanlineColor = TextPrimary.copy(alpha = 0.02f)
            var sy = 0f
            while (sy < size.height) {
                drawLine(scanlineColor, Offset(0f, sy), Offset(size.width, sy), strokeWidth = 1f)
                sy += 3.dp.toPx()
            }
        }
        content()
    }
}

/**
 * The HUD statusbar row every phone mockup in the concept demo has, now on every real
 * screen: a live clock, the current screen's label, and the device's actual battery level
 * -- a small authenticity touch, not a decorative fake number.
 */
@Composable
fun CorpoStatusBar(label: String) {
    val context = LocalContext.current
    val clock by produceState(initialValue = currentTimeLabel()) {
        while (true) {
            value = currentTimeLabel()
            delay(15_000L)
        }
    }
    val batteryPercent = rememberBatteryPercent(context)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(clock, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(
            batteryPercent?.let { "$it%" } ?: "—",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The bordered data panel every card in the HTML demo uses -- a filled block with a hairline
 * outline, not a shadowed Material card. Used for anything presenting a readout (forecast
 * quotes, debrief comparisons, live stats).
 */
@Composable
fun CorpoPanel(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(6.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

private fun currentTimeLabel(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

@Composable
private fun rememberBatteryPercent(context: Context): Int? {
    val state by produceState<Int?>(initialValue = null) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky: Intent? = context.registerReceiver(null, filter)
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        value = if (level >= 0 && scale > 0) (level * 100) / scale else null
    }
    return state
}
