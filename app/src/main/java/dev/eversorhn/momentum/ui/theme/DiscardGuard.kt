package dev.eversorhn.momentum.ui.theme

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Back on a screen holding unsaved input asks first. Nothing the user typed is thrown away
 * silently — the same promise the recording screen already makes.
 *
 * @param enabled whether there is anything to lose right now
 * @param title what would be discarded, in the user's terms
 * @param onDiscard leave and drop it
 */
@Composable
fun DiscardGuard(enabled: Boolean, title: String, body: String, onDiscard: () -> Unit) {
    var ask by remember { mutableStateOf(false) }
    BackHandler(enabled = enabled) { ask = true }
    if (!ask) return
    CorpoDialog(
        title = title,
        body = body,
        onDismiss = { ask = false },
        confirmText = "Keep editing",
        onConfirm = { ask = false },
        confirmKind = ButtonKind.SAFE,
        dismissText = null,
        extraText = "Discard",
        onExtra = { ask = false; onDiscard() },
    )
}
