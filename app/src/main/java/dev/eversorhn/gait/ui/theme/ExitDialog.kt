package dev.eversorhn.gait.ui.theme

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.notification.NotificationPrefs

/**
 * Intercepts the system back gesture on a root screen (Board, Forecast) and asks before the
 * app closes: stay, close but keep the opponent's notifications, or close and mute them.
 * "Close" finishes the task so the app is actually gone, not just backgrounded.
 */
@Composable
fun ExitGuard(opponentName: String) {
    var show by remember { mutableStateOf(false) }
    val context = LocalContext.current
    BackHandler(enabled = true) { show = true }
    if (!show) return

    val activity = context as? Activity
    AlertDialog(
        onDismissRequest = { show = false },
        containerColor = Ink2,
        titleContentColor = TextPrimary,
        textContentColor = TextDim,
        title = { Text("Leave the floor?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                "$opponentName keeps training either way. Choose whether it can still reach you while GAIT is closed.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CorpoButton(
                    text = "Close · keep notifications",
                    onClick = { NotificationPrefs.setMuted(context, false); show = false; activity?.finishAffinity() },
                    kind = ButtonKind.SAFE,
                    modifier = Modifier.fillMaxWidth(),
                )
                CorpoButton(
                    text = "Close · mute notifications",
                    onClick = { NotificationPrefs.setMuted(context, true); show = false; activity?.finishAffinity() },
                    kind = ButtonKind.RISK,
                    modifier = Modifier.fillMaxWidth(),
                )
                CorpoButton(
                    text = "Stay",
                    onClick = { show = false },
                    kind = ButtonKind.GHOST,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}
