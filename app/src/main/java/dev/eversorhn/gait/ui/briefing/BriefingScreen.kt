package dev.eversorhn.gait.ui.briefing

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.eversorhn.gait.domain.roster.RosterEngine
import dev.eversorhn.gait.domain.trial.DecommissionTrial
import dev.eversorhn.gait.ui.theme.ButtonKind
import dev.eversorhn.gait.ui.theme.CollapsiblePanel
import dev.eversorhn.gait.ui.theme.CorpoButton
import dev.eversorhn.gait.ui.theme.FootNote
import dev.eversorhn.gait.ui.theme.ScreenTitle
import dev.eversorhn.gait.ui.theme.TextDim

/**
 * Everything the app used to explain on its own screens, collected here. The working screens
 * carry numbers; this carries the rules. Written as the division would file it.
 */
@Composable
fun BriefingScreen(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenTitle("Briefing", "How this works")

        Entry(
            "The premise",
            "A model of you is being trained on your own sessions. The division is deciding which of you to keep.",
            listOf(
                "Every session you record teaches it.",
                "The better it predicts you, the less you are needed.",
                "You do not want it to improve.",
            ),
        )
        Entry(
            "Rounds and the ledger",
            "Each forecasted session is one round.",
            listOf(
                "Beat the forecast pace → the round is yours. Match or miss it → the model's.",
                "Motor-assisted activities are judged on a new route or steadier ride instead of pace.",
                "Points sum on the ledger. The ledger survives everything else.",
            ),
        )
        Entry(
            "Stakes",
            "When the model is confident it puts points on its own forecast — once a day.",
            listOf(
                "A staked round is worth 2 points; 3 once you lead by 4, 4 at 8.",
                "Counter-stake doubles it, both ways.",
                "The stake is consumed by the day's first scored session.",
            ),
        )
        Entry(
            "Fidelity and the trial",
            "Fidelity is how well the model predicts you — not how fast you are.",
            listOf(
                "It rises when your sessions land where it said they would.",
                "At ${(DecommissionTrial.THRESHOLD * 100).toInt()}% a ${DecommissionTrial.REVIEW_WINDOW_DAYS}-day substitution review opens.",
                "Contest it by beating your own best over ≥ 1 km. Win: Fidelity resets, a new generation starts. Lapse: the model is ratified.",
            ),
        )
        Entry(
            "The board",
            "You are one of ~${RosterEngine.ROSTER_SIZE} assets ranked by Retention Index.",
            listOf(
                "New enrolments start at the bottom. Every round won is roughly 30 index points.",
                "Every ${RosterEngine.CULL_EVERY_DAYS} days the bottom ${RosterEngine.CULL_COUNT} are released.",
                "New hires are protected for the first ${RosterEngine.CULL_GRACE_DAYS} days. Caught by a cull, the enrolment ends.",
            ),
        )
        Entry(
            "The horde",
            "Chosen instead of a Twin. No name, no words.",
            listOf(
                "Proximity replaces Fidelity: how well they read your pace.",
                "Everything is distance and proximity; there is no board and no ticker.",
                "They are built from released assets.",
            ),
        )
        Entry(
            "Availability",
            "Three ways to be away without it counting against you.",
            listOf(
                "A weekly rest pattern.",
                "Days marked off in advance on the calendar.",
                "A block from the yearly vacation bank.",
                "On any of them: sessions still count, Fidelity is frozen, nothing reacts, no stakes.",
            ),
        )
        Entry(
            "Your data",
            "Everything stays on this device.",
            listOf(
                "No account, no server, no analytics.",
                "Location is read only while a session is recording.",
                "The roster of 1,300 assets is a simulation — those are not real people.",
            ),
        )

        CorpoButton("Back", onClick = onDone, kind = ButtonKind.GHOST, modifier = Modifier.fillMaxWidth())
        FootNote("GAIT · Asset Performance Division")
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Entry(title: String, summary: String, points: List<String>) {
    CollapsiblePanel(title = title, summary = summary) {
        points.forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium, color = TextDim) }
    }
}
