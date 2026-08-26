package dev.eversorhn.momentum.ui.theme

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.eversorhn.momentum.notification.NotificationPrefs

/**
 * Intercepts the system back gesture on the app's root screen and asks before it closes:
 * stay, close but keep the division's notices, or close and mute them. "Close" finishes the
 * task so the app is actually gone, not just backgrounded.
 *
 * The same console dialog as everywhere else — no stock system dialog anywhere in MOMENTUM.
 */
@Composable
fun ExitGuard(opponentName: String) {
    var show by remember { mutableStateOf(false) }
    val context = LocalContext.current
    BackHandler(enabled = true) { show = true }
    if (!show) return

    val activity = context as? Activity
    CorpoDialog(
        title = "Leave the floor?",
        body = "$opponentName keeps training either way. Choose whether it can still reach you while MOMENTUM is closed.",
        onDismiss = { show = false },
        confirmText = "Close · keep notifications",
        onConfirm = { NotificationPrefs.setMuted(context, false); show = false; activity?.finishAffinity() },
        confirmKind = ButtonKind.SAFE,
        extraText = "Close · mute notifications",
        onExtra = { NotificationPrefs.setMuted(context, true); show = false; activity?.finishAffinity() },
        extraKind = ButtonKind.RISK,
        dismissText = "Stay",
    )
}
