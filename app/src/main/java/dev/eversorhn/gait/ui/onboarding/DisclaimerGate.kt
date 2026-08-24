package dev.eversorhn.gait.ui.onboarding

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.ui.theme.ButtonKind
import dev.eversorhn.gait.ui.theme.CorpoButton
import dev.eversorhn.gait.ui.theme.CorpoPanel
import dev.eversorhn.gait.ui.theme.FootNote
import dev.eversorhn.gait.ui.theme.PanelTone
import dev.eversorhn.gait.ui.theme.ScreenTitle
import dev.eversorhn.gait.ui.theme.SectionLabel
import dev.eversorhn.gait.ui.theme.TextDim

/**
 * The first thing the app shows, once. What GAIT is, what it is not, and what it cannot do for
 * you — before it ever asks for a permission or puts a number in front of you.
 *
 * Declining closes the app. There is no version of this that is safe to skip: the whole product
 * pushes people to move faster than they otherwise would, on real roads.
 */
@Composable
fun DisclaimerGate(onAccepted: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Back is a decline, not a way past it.
    BackHandler(enabled = true) { activity?.finishAffinity() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Before you start", "Read this once")

        CorpoPanel(tone = PanelTone.WARN) {
            SectionLabel("Your safety comes before any number here")
            Text(
                "GAIT is built to make you push. It shows you a pace to hold, a gap to close and a " +
                    "ranking to climb, and it does that whether or not today is a day for it.",
                style = MaterialTheme.typography.bodyMedium, color = TextDim,
            )
            Text(
                "Watch the road, not the phone. Do not read the screen while moving in traffic; " +
                    "the spoken readout and the pocket feedback exist so you do not have to.",
                style = MaterialTheme.typography.bodyMedium, color = TextDim,
            )
        }

        CorpoPanel {
            SectionLabel("Not a medical device")
            Text(
                "GAIT is not a medical device and gives no medical, diagnostic or training advice. " +
                    "Its targets are arithmetic on your own past sessions, not a judgement about " +
                    "what your body can take today. If you have a heart, lung, joint or other " +
                    "condition, are pregnant, are returning from injury or illness, or are new to " +
                    "training, speak to a doctor before you follow anything this app suggests.",
                style = MaterialTheme.typography.bodyMedium, color = TextDim,
            )
            Text(
                "Stop if you feel pain, chest pressure, dizziness or shortness of breath. No round " +
                    "on any ledger is worth an injury.",
                style = MaterialTheme.typography.bodyMedium, color = TextDim,
            )
        }

        CorpoPanel {
            SectionLabel("You are training against a simulation")
            Text(
                "The division, its ranking and every other name in it are generated on this phone. " +
                    "There are no other people, no accounts and no competition with anyone real. " +
                    "Nothing you do here is reported to anybody.",
                style = MaterialTheme.typography.bodyMedium, color = TextDim,
            )
            Text(
                "The tone is deliberately cold and the standing is deliberately unforgiving. If " +
                    "that stops being useful to you, it is a setting, and it is also a reason to " +
                    "stop using the app.",
                style = MaterialTheme.typography.bodyMedium, color = TextDim,
            )
        }

        CorpoPanel {
            SectionLabel("What it does with your data")
            Text(
                "Sessions, routes and standings stay on this device. Nothing is uploaded, there is " +
                    "no account and no analytics. Location is used only while a session is " +
                    "recording. Erase all data in Settings removes it for good.",
                style = MaterialTheme.typography.bodyMedium, color = TextDim,
            )
        }

        CorpoPanel {
            SectionLabel("No warranty")
            Text(
                "GAIT is free software provided as is, without warranty of any kind. Distances, " +
                    "paces and elevations come from your phone's sensors and are approximate. You " +
                    "use it at your own risk.",
                style = MaterialTheme.typography.bodyMedium, color = TextDim,
            )
        }

        CorpoButton(
            "I understand and accept",
            onClick = {
                DisclaimerPrefs.accept(context)
                onAccepted()
            },
            kind = ButtonKind.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
        )
        CorpoButton(
            "Decline and close",
            onClick = { activity?.finishAffinity() },
            kind = ButtonKind.RISK,
            modifier = Modifier.fillMaxWidth(),
        )
        FootNote("Shown once. You can read it again under Briefing.")
        Spacer(Modifier.height(8.dp))
    }
}

/** Whether the disclaimer has been accepted on this device. */
object DisclaimerPrefs {
    private const val PREFS = "gait_disclaimer"
    private const val KEY = "accepted_version"

    /** Bump when the text changes materially: a new version has to be accepted again. */
    const val CURRENT_VERSION = 1

    fun isAccepted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 0) >= CURRENT_VERSION

    fun accept(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY, CURRENT_VERSION).apply()
    }
}
